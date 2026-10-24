package com.bpm.minotaur.gamedata.arts;

import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.monster.ForcedMovement;
import com.bpm.minotaur.gamedata.monster.Monster;
import com.bpm.minotaur.gamedata.player.Player;
import com.bpm.minotaur.telemetry.TelemetryManager;
import com.bpm.minotaur.ui.AffinityText;
import com.bpm.minotaur.ui.UiNames;

/**
 * Using an Art from the bar (#245; spec section 5): the cooldown rules every Active and capstone
 * share, and the Actives themselves. Battering Ram ships with the framework as its tracer.
 *
 * <p>An Art that finds nothing to act on is not spent: no turn, no cooldown. One that is used
 * starts its cooldown -- doubled for a kept Art this body has not retrained -- posts a combat log
 * line and counts in {@code artUses}, whether or not its blow lands.
 */
public final class ActiveArts {

    /** What the combat code lends an Active. */
    public interface Arena {
        Maze maze();

        /** The hostile in the tile the player faces, or null. */
        Monster foeInFront();

        /** One blow of the held weapon at {@code target}, shown with {@code word}: true if it hit. */
        boolean blow(Monster target, String word);

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
        Outcome outcome;
        switch (art) {
            case BATTERING_RAM:
                outcome = batteringRam(player, arena);
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

    /**
     * Battering Ram: a blow that knocks the foe back a tile and Dazes it. Driven into a wall it
     * takes 1d6 more and is Stunned. Heavies and bosses hold their ground but still take the Daze.
     */
    private static Outcome batteringRam(Player player, Arena arena) {
        Monster foe = arena.foeInFront();
        if (foe == null) return Outcome.NO_TARGET;
        String name = UiNames.of(CombatArt.BATTERING_RAM);
        String who = AffinityText.nameOf(foe);
        if (!arena.blow(foe, "RAM")) {
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
        boolean stunned = foe.applyDaze() == Monster.DazeOutcome.STUNNED;
        String effect = stunned ? "stunned" : "dazed";
        if (moved == ForcedMovement.Result.MOVED) {
            arena.log(name + " knocks " + who + " back. It is " + effect + ".");
        } else if (moved == ForcedMovement.Result.IMMOVABLE) {
            arena.log(who + " holds its ground against " + name + ", but is " + effect + ".");
        } else {
            arena.log(name + " staggers " + who + ". It is " + effect + ".");
        }
        return Outcome.USED;
    }
}
