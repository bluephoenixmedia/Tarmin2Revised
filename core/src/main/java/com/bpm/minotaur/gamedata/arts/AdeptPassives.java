package com.bpm.minotaur.gamedata.arts;

import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.effects.StatusEffectType;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.monster.Monster;
import com.bpm.minotaur.gamedata.player.Mastery;
import com.bpm.minotaur.gamedata.player.Player;
import com.bpm.minotaur.telemetry.TelemetryManager;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.DoubleSupplier;

/**
 * The ten Adept Passives (spec section 2): six melee (#243) and four ranged (#244). Combat calls in
 * at four moments -- a full hit, a thrown full hit, a monster's missed melee blow, a monster first
 * closing to melee -- and a Passive fires when the soul knows the wielded Family's Adept Art and
 * the roll comes in under the body's chance. Bow, crossbow and gun shots land as full hits too.
 *
 * <p>Free strikes come back through {@link FreeStrike}, which the combat code resolves without a
 * return blow and without calling back in here, so procs never chain.
 */
public final class AdeptPassives {

    /** What the combat code lends a Passive. Only {@link #strike} must be supplied. */
    public interface FreeStrike {
        /** Resolves a free strike against {@code target}; {@code word} is shown on its damage number. */
        void strike(Monster target, String word);

        /** Swift Nock: a second shot from the held bow, spending an arrow. False if none was loosed. */
        default boolean loose(Monster target, String word) {
            return false;
        }

        /** Practised Hands: a turn off the reload under way. False if there was none to shorten. */
        default boolean cutReload() {
            return false;
        }
    }

    /** The floating words (spec section 6). */
    public static final String DAZE = "DAZE", STUN = "STUN", BLEED_AND_SLOW = "BLEED SLOW", EXTRA_CUT = "+1 CUT",
            COUNTER = "COUNTER", FIRST_STRIKE = "FIRST STRIKE", SECOND_ARROW = "SECOND ARROW",
            QUICK_LOAD = "QUICK LOAD", TANGLE = "TANGLE";

    /** Tangle: the slow lasts two turns. */
    static final int TANGLE_TURNS = 2;

    /** Cut the Sinew: the bleed and the slow each last two turns. */
    static final int SINEW_TURNS = 2;
    static final int SINEW_BLEED = 1;

    private final DoubleSupplier roll;
    /** Monsters Hold the Line has already met: the free strike comes the first time only. */
    private final Set<Monster> lineHeld = Collections.newSetFromMap(new WeakHashMap<>());

    /** @param roll a uniform draw in [0, 1): the Passive fires when it falls under the chance */
    public AdeptPassives(DoubleSupplier roll) {
        this.roll = roll;
    }

    /**
     * A full hit landed with {@code weapon} (null for bare hands) for {@code damage}. Fires the
     * wielded Family's Adept Passive if it procs, and returns its word for the damage number.
     */
    public String onFullHit(Player player, Item weapon, Monster target, Maze maze, int damage, FreeStrike strike) {
        CombatArt art = procs(player, weapon, false);
        if (art == null || target == null || target.getCurrentHP() <= 0) return null;
        String word;
        switch (art) {
            case RINGING_BLOW:
                word = target.applyDaze() == Monster.DazeOutcome.STUNNED ? STUN : DAZE;
                break;
            case CUT_THE_SINEW:
                target.applyBleed(SINEW_TURNS, SINEW_BLEED);
                if (target.getStatusManager() != null) {
                    target.getStatusManager().addEffect(StatusEffectType.SLOWED, SINEW_TURNS, 1, false);
                }
                word = BLEED_AND_SLOW;
                break;
            case QUICK_KNUCKLES: {
                // Half the blow that already landed, so resistance and armour soak are not taken twice.
                // The amount rides on the word, so the second knuckle shows: "+3 BLOW 6".
                int extra = target.takeDamage(Math.max(1, damage / 2), false, true);
                TelemetryManager.getInstance().recordDamageDealt(extra);
                word = "+" + extra + " BLOW";
                break;
            }
            case WIDE_CHOP:
                word = chopTheOthers(player, target, maze, strike) ? EXTRA_CUT : null;
                break;
            case SWIFT_NOCK:
                // The second arrow is its own shot with its own number, so it carries the word.
                if (!strike.loose(target, SECOND_ARROW)) return null;
                TelemetryManager.getInstance().recordArtUse(art.name());
                return null;
            case STAGGERING_BOLT:
                word = target.applyDaze() == Monster.DazeOutcome.STUNNED ? STUN : DAZE;
                break;
            case PRACTISED_HANDS:
                // The shot's own reload began as it fired: that is the reload this one cuts.
                word = strike.cutReload() ? QUICK_LOAD : null;
                break;
            default:
                return null; // Answering Steel, Hold the Line and Tangle fire at other moments
        }
        if (word != null) TelemetryManager.getInstance().recordArtUse(art.name());
        return word;
    }

    /** A thrown full hit with any weapon: Tangle may slow the target. Returns its word, or null. */
    public String onThrownHit(Player player, Item weapon, Monster target) {
        CombatArt art = procs(player, weapon, true);
        if (art != CombatArt.TANGLE || target == null || target.getCurrentHP() <= 0
                || target.getStatusManager() == null) return null;
        target.getStatusManager().addEffect(StatusEffectType.SLOWED, TANGLE_TURNS, 1, false);
        TelemetryManager.getInstance().recordArtUse(art.name());
        return TANGLE;
    }

    /** A monster's melee blow missed: Answering Steel may answer it with a free counter-cut. */
    public void onMonsterMeleeMiss(Player player, Item weapon, Monster attacker, FreeStrike strike) {
        CombatArt art = procs(player, weapon, false);
        if (art != CombatArt.ANSWERING_STEEL || attacker == null || attacker.getCurrentHP() <= 0) return;
        TelemetryManager.getInstance().recordArtUse(art.name());
        strike.strike(attacker, COUNTER);
    }

    /** A monster stepped next to the player: Hold the Line strikes first, once per monster. */
    public void onMonsterStepsAdjacent(Player player, Item weapon, Monster monster, FreeStrike strike) {
        if (monster == null) return;
        // Once per monster, but in a Thicket of Points (#248) every closing is struck.
        if (lineHeld.contains(monster) && !certainFor(player, weapon, false)) return;
        CombatArt art = procs(player, weapon, false);
        if (art != CombatArt.HOLD_THE_LINE) return;
        lineHeld.add(monster);
        TelemetryManager.getInstance().recordArtUse(art.name());
        strike.strike(monster, FIRST_STRIKE);
    }

    /** The wielded Family's Adept Art, if the soul knows it and the roll comes in under its chance. */
    private CombatArt procs(Player player, Item weapon, boolean thrown) {
        if (player == null) return null;
        CombatArt art = CombatArt.of(player.masteryFamilyOf(weapon, thrown), Mastery.Rank.ADEPT);
        if (art == null) return null;
        if (certainFor(player, weapon, thrown)) return art; // a capstone's window (#248)
        float chance = player.artChance(art);
        return chance > 0f && roll.getAsDouble() < chance ? art : null;
    }

    /** A capstone has made the wielded Family's Adept Passive certain, and the soul knows it. */
    private static boolean certainFor(Player player, Item weapon, boolean thrown) {
        if (player == null) return false;
        CombatArt art = CombatArt.of(player.masteryFamilyOf(weapon, thrown), Mastery.Rank.ADEPT);
        return art != null && player.knowsArt(art) && player.getArtCooldowns().isCertain(art.family);
    }

    /** Wide Chop: every other hostile beside the player takes a free cut. */
    private static boolean chopTheOthers(Player player, Monster target, Maze maze, FreeStrike strike) {
        if (maze == null) return false;
        int px = (int) player.getPosition().x, py = (int) player.getPosition().y;
        int[][] beside = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };
        boolean any = false;
        for (int[] d : beside) {
            Monster other = maze.getMonsters().get(new GridPoint2(px + d[0], py + d[1]));
            if (other == null || other == target || other.getCurrentHP() <= 0 || other.isAlly() || other.isPeaceful()) continue;
            strike.strike(other, EXTRA_CUT);
            any = true;
        }
        return any;
    }
}
