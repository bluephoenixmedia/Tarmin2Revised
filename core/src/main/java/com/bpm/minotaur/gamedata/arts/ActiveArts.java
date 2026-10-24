package com.bpm.minotaur.gamedata.arts;

import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.effects.StatusEffectType;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.monster.ForcedMovement;
import com.bpm.minotaur.gamedata.monster.Monster;
import com.bpm.minotaur.gamedata.player.Player;
import com.bpm.minotaur.telemetry.TelemetryManager;
import com.bpm.minotaur.ui.AffinityText;
import com.bpm.minotaur.ui.UiNames;

/**
 * Using an Art from the bar (#245; spec section 5): the cooldown rules every Active and capstone
 * share, and the Actives themselves -- Battering Ram (#245) and the other melee Actives (#246).
 *
 * <p>An Art that finds nothing to act on is not spent: no turn, no cooldown. One that is used
 * starts its cooldown -- doubled for a kept Art this body has not retrained -- posts a combat log
 * line and counts in {@code artUses}, whether or not its blow lands.
 */
public final class ActiveArts {

    /** What the combat code lends an Active. */
    public interface Arena {
        Maze maze();

        /**
         * The hostile exactly {@code tiles} ahead along the player's facing, with no wall, creature
         * or blocked floor between; or null.
         */
        Monster foeAhead(int tiles);

        /** Steps the player one tile forward onto open floor, as a real move; false if the way is shut. */
        boolean stepForward();

        /**
         * One blow of {@code weapon} (null for bare hands) at {@code target}, shown with {@code word},
         * with {@code toHitBonus} on the roll and {@code extraDamage} on the damage: true if it hit.
         */
        boolean blow(Monster target, Item weapon, String word, int toHitBonus, int extraDamage);

        /** Extra damage on {@code target}, shown with {@code word}. */
        void hurt(Monster target, int damage, String word);

        /** A rolled die expression, e.g. "1d6". */
        int roll(String dice);

        /** A combat log line. */
        void log(String line);
    }

    /** What pressing the key did. Only USED takes the player's turn. */
    public enum Outcome {
        USED,
        /** Nothing in the slot. */
        EMPTY,
        COOLING,
        /** No foe for it to act on. */
        NO_TARGET,
        /** An Art the game cannot use yet. */
        UNAVAILABLE
    }

    /** Battering Ram into a wall: this much more, and a Stun. */
    static final String WALL_DICE = "1d6";
    static final int WALL_STUN_TURNS = 1;
    /** Closing Thrust's lunge: this much better to hit. */
    static final int CLOSING_TO_HIT = 2;
    /** Gutting Stab: one of these for every status on the target. */
    static final String GUT_DIE = "d4";
    /** Hew Through's bleed: turns, and damage each turn. */
    static final int HEW_BLEED_TURNS = 3;
    static final int HEW_BLEED = 1;

    private ActiveArts() {
    }

    /** Uses the Art in bar slot {@code slot} (0-3). */
    public static Outcome use(Player player, int slot, Arena arena) {
        if (player == null || slot < 0 || slot >= ArtBar.SLOTS) return Outcome.EMPTY;
        CombatArt art = ArtBar.contents(player)[slot];
        if (art == null) return Outcome.EMPTY;
        ArtCooldowns cooldowns = player.getArtCooldowns();
        String name = UiNames.of(art);
        if (cooldowns.isCooling(art)) {
            arena.log(name + " is not ready: " + cooldowns.turnsLeft(art) + " turns.");
            return Outcome.COOLING;
        }
        Item weapon = handOf(player, slot);
        Outcome outcome;
        switch (art) {
            case BATTERING_RAM:
                outcome = batteringRam(player, weapon, arena);
                break;
            case CLOSING_THRUST:
                outcome = closingThrust(weapon, arena);
                break;
            case GUTTING_STAB:
                outcome = guttingStab(weapon, arena);
                break;
            case HEW_THROUGH:
                outcome = hewThrough(weapon, arena);
                break;
            case REEL_IN:
                outcome = reelIn(player, arena);
                break;
            case IRON_GRIP:
                outcome = ironGrip(arena);
                break;
            default:
                arena.log(name + " cannot be used yet.");
                return Outcome.UNAVAILABLE;
        }
        if (outcome == Outcome.NO_TARGET) {
            arena.log(name + ": no foe in reach.");
        } else if (outcome == Outcome.USED) {
            cooldowns.start(art, player.artCooldown(art));
            TelemetryManager.getInstance().recordArtUse(art.name());
        }
        return outcome;
    }

    /** The weapon a slot's Art swings: slots 1-2 belong to the right hand, 3-4 to the left. */
    static Item handOf(Player player, int slot) {
        return slot < 2 ? player.getInventory().getRightHand() : player.getInventory().getLeftHand();
    }

    /** The statuses Gutting Stab counts: Bleeding, Slowed, Dazed, Pinned and Stunned. */
    static int statusesOn(Monster foe) {
        int n = 0;
        if (foe.getBleedTurns() > 0) n++;
        if (foe.getStatusManager() != null && foe.getStatusManager().hasEffect(StatusEffectType.SLOWED)) n++;
        if (foe.isDazed()) n++;
        if (foe.isPinned()) n++;
        if (foe.isStunned()) n++;
        return n;
    }

    /**
     * Battering Ram: a blow that knocks the foe back a tile and Dazes it. Driven into a wall it
     * takes 1d6 more and is Stunned. Heavies and bosses hold their ground but still take the Daze.
     */
    private static Outcome batteringRam(Player player, Item weapon, Arena arena) {
        Monster foe = arena.foeAhead(1);
        if (foe == null) return Outcome.NO_TARGET;
        String name = UiNames.of(CombatArt.BATTERING_RAM);
        String who = AffinityText.nameOf(foe);
        if (!arena.blow(foe, weapon, "RAM", 0, 0)) {
            arena.log(name + ": " + who + " slips the blow.");
            return Outcome.USED;
        }
        if (foe.getCurrentHP() <= 0) {
            arena.log(name + " fells " + who + ".");
            return Outcome.USED;
        }
        GridPoint2 at = new GridPoint2((int) player.getPosition().x, (int) player.getPosition().y);
        ForcedMovement.Result moved = ForcedMovement.knockBack(arena.maze(), foe, player.getFacing(), at);
        if (moved == ForcedMovement.Result.HIT_WALL) {
            arena.hurt(foe, arena.roll(WALL_DICE), "WALL");
            if (foe.getCurrentHP() <= 0) {
                arena.log(name + " drives " + who + " into the wall, and it falls.");
            } else if (foe.applyStun(WALL_STUN_TURNS)) {
                arena.log(name + " drives " + who + " into the wall. It is stunned.");
            } else {
                foe.applyDaze();
                arena.log(name + " drives " + who + " into the wall. It is dazed.");
            }
            return Outcome.USED;
        }
        String effect = dazeWord(foe);
        if (moved == ForcedMovement.Result.MOVED) {
            arena.log(name + " knocks " + who + " back. It is " + effect + ".");
        } else if (moved == ForcedMovement.Result.IMMOVABLE) {
            arena.log(who + " holds its ground against " + name + ", but is " + effect + ".");
        } else {
            arena.log(name + " staggers " + who + ". It is " + effect + ".");
        }
        return Outcome.USED;
    }

    /**
     * Closing Thrust: a lunge at +2 to hit. A foe two tiles ahead is closed on first, a step onto the
     * open floor between; a foe already beside the player is simply thrust at.
     */
    private static Outcome closingThrust(Item weapon, Arena arena) {
        String name = UiNames.of(CombatArt.CLOSING_THRUST);
        Monster foe = arena.foeAhead(1);
        boolean closed = false;
        if (foe == null) {
            foe = arena.foeAhead(2);
            if (foe == null || !arena.stepForward()) return Outcome.NO_TARGET;
            closed = true;
        }
        String who = AffinityText.nameOf(foe);
        boolean hit = arena.blow(foe, weapon, "THRUST", CLOSING_TO_HIT, 0);
        String opening = closed ? name + ": you close on " + who : name + " at " + who;
        arena.log(opening + (hit ? (foe.getCurrentHP() <= 0 ? ", and it falls." : ", and the point goes home.")
                : ", but it turns the point aside."));
        return Outcome.USED;
    }

    /** Gutting Stab: a stab that deals 1d4 more for every status already on the target. */
    private static Outcome guttingStab(Item weapon, Arena arena) {
        Monster foe = arena.foeAhead(1);
        if (foe == null) return Outcome.NO_TARGET;
        String name = UiNames.of(CombatArt.GUTTING_STAB);
        String who = AffinityText.nameOf(foe);
        int wounds = statusesOn(foe);
        int extra = wounds > 0 ? arena.roll(wounds + GUT_DIE) : 0;
        if (!arena.blow(foe, weapon, "GUT", 0, extra)) {
            arena.log(name + ": " + who + " twists away.");
        } else if (wounds == 0) {
            arena.log(name + " finds no wound on " + who + " to open.");
        } else {
            arena.log(name + " opens " + wounds + (wounds == 1 ? " wound" : " wounds") + " on " + who + ".");
        }
        return Outcome.USED;
    }

    /** Hew Through: a chop that splits the target's armour, 3 AC for 5 turns, and makes it Bleed. */
    private static Outcome hewThrough(Item weapon, Arena arena) {
        Monster foe = arena.foeAhead(1);
        if (foe == null) return Outcome.NO_TARGET;
        String name = UiNames.of(CombatArt.HEW_THROUGH);
        String who = AffinityText.nameOf(foe);
        if (!arena.blow(foe, weapon, "HEW", 0, 0)) {
            arena.log(name + ": " + who + " takes the chop on its guard.");
        } else if (foe.getCurrentHP() <= 0) {
            arena.log(name + " fells " + who + ".");
        } else {
            foe.hewArmor();
            foe.applyBleed(HEW_BLEED_TURNS, HEW_BLEED);
            arena.log(name + " splits " + who + "'s armour, and it bleeds.");
        }
        return Outcome.USED;
    }

    /**
     * Reel In: hooks a foe two tiles off and draws it into reach, Dazed. Heavies and bosses will not
     * be drawn, but are Dazed all the same. Being pulled in never counts as stepping beside the player.
     */
    private static Outcome reelIn(Player player, Arena arena) {
        Monster foe = arena.foeAhead(2);
        if (foe == null) return Outcome.NO_TARGET;
        String name = UiNames.of(CombatArt.REEL_IN);
        String who = AffinityText.nameOf(foe);
        GridPoint2 at = new GridPoint2((int) player.getPosition().x, (int) player.getPosition().y);
        ForcedMovement.Result pulled = ForcedMovement.pull(arena.maze(), foe, at);
        String effect = dazeWord(foe);
        if (pulled == ForcedMovement.Result.MOVED) {
            arena.log(name + " drags " + who + " into reach. It is " + effect + ".");
        } else if (pulled == ForcedMovement.Result.IMMOVABLE) {
            arena.log(who + " will not be drawn by " + name + ", but is " + effect + ".");
        } else {
            arena.log(name + " hooks " + who + " but cannot draw it. It is " + effect + ".");
        }
        return Outcome.USED;
    }

    /** Iron Grip: the foe beside the player is held: it cannot move and fights at -2 for 3 turns. */
    private static Outcome ironGrip(Arena arena) {
        Monster foe = arena.foeAhead(1);
        if (foe == null) return Outcome.NO_TARGET;
        foe.applyGrip();
        arena.log(UiNames.of(CombatArt.IRON_GRIP) + ": you seize " + AffinityText.nameOf(foe) + " and hold it fast.");
        return Outcome.USED;
    }

    /** Dazes {@code foe} and says how it took it: "dazed", or "stunned" if it was already Dazed. */
    private static String dazeWord(Monster foe) {
        return foe.applyDaze() == Monster.DazeOutcome.STUNNED ? "stunned" : "dazed";
    }
}
