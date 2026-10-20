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
 * The six melee Adept Passives (#243; spec section 2). Combat calls in at three moments -- a full
 * hit, a monster's missed melee blow, a monster first closing to melee -- and a Passive fires when
 * the soul knows the wielded Family's Adept Art and the roll comes in under the body's chance.
 *
 * <p>Free strikes come back through {@link FreeStrike}, which the combat code resolves without a
 * return blow and without calling back in here, so procs never chain.
 */
public final class MeleePassives {

    /** Resolves a free strike against {@code target}; {@code word} is shown on its damage number. */
    public interface FreeStrike {
        void strike(Monster target, String word);
    }

    /** The floating words (spec section 6). */
    public static final String DAZE = "DAZE", STUN = "STUN", BLEED_AND_SLOW = "BLEED SLOW", EXTRA_CUT = "+1 CUT",
            COUNTER = "COUNTER", FIRST_STRIKE = "FIRST STRIKE";

    /** Cut the Sinew: the bleed and the slow each last two turns. */
    static final int SINEW_TURNS = 2;
    static final int SINEW_BLEED = 1;

    private final DoubleSupplier roll;
    /** Monsters Hold the Line has already met: the free strike comes the first time only. */
    private final Set<Monster> lineHeld = Collections.newSetFromMap(new WeakHashMap<>());

    /** @param roll a uniform draw in [0, 1): the Passive fires when it falls under the chance */
    public MeleePassives(DoubleSupplier roll) {
        this.roll = roll;
    }

    /**
     * A full hit landed with {@code weapon} (null for bare hands) for {@code damage}. Fires the
     * wielded Family's Adept Passive if it procs, and returns its word for the damage number.
     */
    public String onFullHit(Player player, Item weapon, Monster target, Maze maze, int damage, FreeStrike strike) {
        CombatArt art = procs(player, weapon);
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
            default:
                return null; // Answering Steel and Hold the Line fire at other moments
        }
        if (word != null) TelemetryManager.getInstance().recordArtUse(art.name());
        return word;
    }

    /** A monster's melee blow missed: Answering Steel may answer it with a free counter-cut. */
    public void onMonsterMeleeMiss(Player player, Item weapon, Monster attacker, FreeStrike strike) {
        CombatArt art = procs(player, weapon);
        if (art != CombatArt.ANSWERING_STEEL || attacker == null || attacker.getCurrentHP() <= 0) return;
        TelemetryManager.getInstance().recordArtUse(art.name());
        strike.strike(attacker, COUNTER);
    }

    /** A monster stepped next to the player: Hold the Line strikes first, once per monster. */
    public void onMonsterStepsAdjacent(Player player, Item weapon, Monster monster, FreeStrike strike) {
        if (monster == null || lineHeld.contains(monster)) return;
        CombatArt art = procs(player, weapon);
        if (art != CombatArt.HOLD_THE_LINE) return;
        lineHeld.add(monster);
        TelemetryManager.getInstance().recordArtUse(art.name());
        strike.strike(monster, FIRST_STRIKE);
    }

    /** The wielded Family's Adept Art, if the soul knows it and the roll comes in under its chance. */
    private CombatArt procs(Player player, Item weapon) {
        if (player == null) return null;
        CombatArt art = CombatArt.of(player.masteryFamilyOf(weapon, false), Mastery.Rank.ADEPT);
        if (art == null) return null;
        float chance = player.artChance(art);
        return chance > 0f && roll.getAsDouble() < chance ? art : null;
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
