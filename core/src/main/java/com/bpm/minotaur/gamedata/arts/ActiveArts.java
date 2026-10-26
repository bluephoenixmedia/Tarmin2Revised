package com.bpm.minotaur.gamedata.arts;

import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.monster.ForcedMovement;
import com.bpm.minotaur.gamedata.monster.Monster;
import com.bpm.minotaur.gamedata.player.Mastery.Rank;
import com.bpm.minotaur.gamedata.player.Player;
import com.bpm.minotaur.telemetry.TelemetryManager;
import com.bpm.minotaur.ui.AffinityText;
import com.bpm.minotaur.ui.UiNames;

/**
 * Using an Art from the bar (#245; spec section 5): the cooldown rules every Active and capstone
 * share, and the Actives themselves -- Battering Ram (#245), the other melee Actives (#246) and the
 * ranged Actives (#247).
 *
 * <p>An Art that finds nothing to act on is not spent: no turn, no cooldown. One that is used
 * starts its cooldown -- doubled for a kept Art this body has not retrained -- posts a combat log
 * line and counts in {@code artUses}, whether or not its blow lands.
 *
 * <p>Two Arts are readied rather than used at once (spec section 4). Held Arrow is drawn and waits
 * for the first hostile to move into or along the line of fire, or to attack from inside it; Marked
 * Skull aims for a turn before its shot. Either stays readied while the player waits, and lapses
 * into its cooldown at any other action. Pressing Held Arrow's key again lowers the bow for free.
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

        // --- The ranged Actives (#247) ---

        /** The first hostile down the player's line within {@code weapon}'s reach (thrown, or shot); or null. */
        Monster foeInLine(Item weapon, boolean thrown);

        /** Whether {@code weapon} can loose a shot now: ammunition to hand, and no reload under way. */
        boolean loaded(Item weapon);

        /** Whether {@code weapon} is a gun part-way through its reload, with shot to hand to finish it. */
        default boolean reloading(Item weapon) {
            return false;
        }

        /**
         * A real shot of {@code weapon} at {@code target}: ammunition spent, flight drawn, and a gun's
         * damp powder may still misfire. True if it hit.
         */
        boolean shoot(Monster target, Item weapon, String word, Shot kind);

        /** Throws {@code weapon} down the line, as the throw key does: it leaves the hand. */
        Hurl hurl(Item weapon);

        /** {@code damage} to every creature beside {@code around}, the player included. */
        void splash(Monster around, int damage, String word);
    }

    /** How an Active's shot resolves. */
    public enum Shot {
        /** Rolled as any shot. */
        NORMAL,
        /** Marked Skull: certain to hit, and a critical. */
        HEADSHOT,
        /** Held Arrow's reaction: x0.7 accuracy, and no critical. */
        REACTION
    }

    /** What a throw struck, and for how much. */
    public static final class Hurl {
        public static final Hurl MISSED = new Hurl(null, 0);

        public final Monster struck;
        public final int damage;

        public Hurl(Monster struck, int damage) {
            this.struck = struck;
            this.damage = damage;
        }

        public boolean hit() {
            return struck != null;
        }
    }

    /** What pressing the key did. Only USED and READIED take the player's turn. */
    public enum Outcome {
        USED,
        /** Drawn or aimed, waiting (Held Arrow, Marked Skull): the turn passes, the cooldown waits. */
        READIED,
        /** Held Arrow lowered without spending the shot. */
        LOWERED,
        /** Nothing to loose: no ammunition, or a reload under way. */
        NOT_LOADED,
        /** Marked Skull aimed, the gun still loading: the aim is kept and no turn is spent (#314). */
        LOADING,
        /** Nothing in the slot. */
        EMPTY,
        COOLING,
        /** No foe for it to act on. */
        NO_TARGET,
        /** A foe, but no open floor to close on it. */
        NO_ROOM,
        /** An Art the game cannot use yet. */
        UNAVAILABLE;

        public boolean takesTurn() {
            return this == USED || this == READIED;
        }
    }

    /** Battering Ram into a wall: this much more, and a Stun. */
    static final String WALL_DICE = "1d6";
    static final int WALL_STUN_TURNS = 1;
    /** Closing Thrust's lunge: this much better to hit. */
    static final int CLOSING_TO_HIT = 2;
    /** Gutting Stab: one of these for every status on the target. */
    static final String GUT_DIE = "d4";

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
        if (player.getHeldArt() != art) lapse(player, arena); // using another Art is not waiting
        Item weapon = handOf(player, slot);
        Outcome outcome = art.kind == CombatArt.Kind.CAPSTONE ? capstone(player, art, arena)
                : active(player, art, weapon, arena);
        if (outcome == Outcome.UNAVAILABLE) return outcome;
        if (outcome == Outcome.NO_TARGET) {
            arena.log(name + ": no foe in reach.");
        } else if (outcome == Outcome.NO_ROOM) {
            arena.log(name + ": no open floor to close across.");
        } else if (outcome == Outcome.NOT_LOADED) {
            arena.log(name + ": nothing loaded to loose.");
        } else if (outcome == Outcome.LOADING) {
            arena.log(name + ": the gun is not yet loaded. You hold your aim.");
        } else if (outcome == Outcome.USED) {
            cooldowns.start(art, player.artCooldown(art));
            TelemetryManager.getInstance().recordArtUse(art.name());
        }
        return outcome;
    }

    /** The Expert Active {@code art}, swung or shot with {@code weapon}. */
    private static Outcome active(Player player, CombatArt art, Item weapon, Arena arena) {
        switch (art) {
            case BATTERING_RAM:
                return batteringRam(player, weapon, arena);
            case CLOSING_THRUST:
                return closingThrust(weapon, arena);
            case GUTTING_STAB:
                return guttingStab(weapon, arena);
            case HEW_THROUGH:
                return hewThrough(weapon, arena);
            case REEL_IN:
                return reelIn(player, arena);
            case IRON_GRIP:
                return ironGrip(arena);
            case HELD_ARROW:
                return heldArrow(player, weapon, arena);
            case NAIL_IN_PLACE:
                return nailInPlace(weapon, arena);
            case MARKED_SKULL:
                return markedSkull(player, weapon, arena);
            case SHATTERING_THROW:
                return shatteringThrow(weapon, arena);
            default:
                arena.log(UiNames.of(art) + " cannot be used yet.");
                return Outcome.UNAVAILABLE;
        }
    }

    /** The weapon a slot's Art swings: slots 1-2 belong to the right hand, 3-4 to the left. */
    static Item handOf(Player player, int slot) {
        return slot < 2 ? player.getInventory().getRightHand() : player.getInventory().getLeftHand();
    }

    /** The weapon that carries {@code art} on the bar now, or null when it is not on the bar. */
    private static Item handHolding(Player player, CombatArt art) {
        CombatArt[] slots = ArtBar.contents(player);
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] == art) return handOf(player, i);
        }
        return null;
    }

    /**
     * A player's turn passed. A readied Art the turn did not keep -- by readying it, or by
     * waiting -- lapses, and its cooldown starts.
     */
    public static void turnPassed(Player player, Arena arena) {
        if (player == null) return;
        if (!player.takeHoldKept()) lapse(player, arena);
    }

    /**
     * A readied Art lapses: the player did something other than wait. Its cooldown starts. Called
     * as the turn passes, and before any attack of the player's, so a foe struck back at by the
     * player's own swing never draws the shot the swing gave up.
     */
    public static void lapse(Player player, Arena arena) {
        CombatArt held = player == null ? null : player.getHeldArt();
        if (held == null) return;
        player.setHeldArt(null);
        player.setMarkedFoe(null);
        player.getArtCooldowns().start(held, player.artCooldown(held));
        arena.log(UiNames.of(held) + " lapses.");
    }

    /**
     * {@code foe} moved into or along the player's line, or attacked from inside it (spec section
     * 4): a drawn Held Arrow looses at it, if it is the first thing the shot would strike. One shot,
     * at x0.7 accuracy and no critical; the stance is then spent and the cooldown starts.
     *
     * @return true if the shot was loosed
     */
    public static boolean answerHeldShot(Player player, Monster foe, Arena arena) {
        if (player == null || foe == null || player.getHeldArt() != CombatArt.HELD_ARROW) return false;
        Item bow = handHolding(player, CombatArt.HELD_ARROW);
        if (bow == null || arena.foeInLine(bow, false) != foe || !arena.loaded(bow)) return false;
        player.setHeldArt(null);
        String who = AffinityText.nameOf(foe);
        boolean hit = arena.shoot(foe, bow, "HELD", Shot.REACTION);
        arena.log(UiNames.of(CombatArt.HELD_ARROW) + ": you loose at " + who
                + (hit ? (foe.getCurrentHP() <= 0 ? ", and it falls." : ", and the shaft strikes home.") : ", and miss."));
        player.getArtCooldowns().start(CombatArt.HELD_ARROW, player.artCooldown(CombatArt.HELD_ARROW));
        TelemetryManager.getInstance().recordArtUse(CombatArt.HELD_ARROW.name());
        return true;
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
            if (foe == null) return Outcome.NO_TARGET;
            if (!arena.stepForward()) return Outcome.NO_ROOM;
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
        int wounds = foe.countArtStatuses();
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

    /** Hew Through: a chop that splits the target's armour for a while and makes it Bleed ({@link Monster#hew}). */
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
            foe.hew();
            arena.log(name + " splits the armour of " + who + ", and it bleeds.");
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

    /** Held Arrow: draws one arrow and waits (see {@link #answerHeldShot}); pressed again, lowers it. */
    private static Outcome heldArrow(Player player, Item bow, Arena arena) {
        String name = UiNames.of(CombatArt.HELD_ARROW);
        if (player.getHeldArt() == CombatArt.HELD_ARROW) {
            player.setHeldArt(null);
            arena.log(name + ": you ease the arrow off the string.");
            return Outcome.LOWERED;
        }
        if (bow == null || !arena.loaded(bow)) return Outcome.NOT_LOADED;
        player.setHeldArt(CombatArt.HELD_ARROW);
        player.keepHold();
        arena.log(name + ": you draw, and wait for the first foe to cross your line.");
        return Outcome.READIED;
    }

    /** Nail in Place: a normal shot that, on a hit, pins its target where it stands for 3 turns. */
    private static Outcome nailInPlace(Item crossbow, Arena arena) {
        if (crossbow == null || !arena.loaded(crossbow)) return Outcome.NOT_LOADED;
        Monster foe = arena.foeInLine(crossbow, false);
        if (foe == null) return Outcome.NO_TARGET;
        String name = UiNames.of(CombatArt.NAIL_IN_PLACE);
        String who = AffinityText.nameOf(foe);
        if (!arena.shoot(foe, crossbow, "NAIL", Shot.NORMAL)) {
            arena.log(name + ": the bolt misses " + who + ".");
        } else if (foe.getCurrentHP() <= 0) {
            arena.log(name + " fells " + who + ".");
        } else {
            foe.applyPin();
            arena.log(name + " nails " + who + " where it stands.");
        }
        return Outcome.USED;
    }

    /**
     * Marked Skull: the first press aims at the foe in the line, for a turn; the second takes that
     * foe's head, certain to hit, a critical that Dazes. If another foe now stands first in the
     * line, the aim moves to it and the turn is spent again. A foe with no head takes a normal shot.
     *
     * <p>The aim may be taken while the gun is reloading, and that turn loads it too (#314). The shot
     * still waits for the gun: pressed early, the aim is kept and no turn is spent.
     */
    private static Outcome markedSkull(Player player, Item gun, Arena arena) {
        String name = UiNames.of(CombatArt.MARKED_SKULL);
        if (gun == null) return Outcome.NOT_LOADED;
        boolean loaded = arena.loaded(gun);
        if (!loaded && !arena.reloading(gun)) return Outcome.NOT_LOADED;
        Monster foe = arena.foeInLine(gun, false);
        if (foe == null) return Outcome.NO_TARGET;
        String who = AffinityText.nameOf(foe);
        if (player.getHeldArt() != CombatArt.MARKED_SKULL || player.getMarkedFoe() != foe) {
            player.setHeldArt(CombatArt.MARKED_SKULL);
            player.setMarkedFoe(foe);
            player.keepHold();
            arena.log(name + ": you sight on " + who + (loaded ? "." : " as you load."));
            return Outcome.READIED;
        }
        if (!loaded) return Outcome.LOADING;
        player.setHeldArt(null);
        player.setMarkedFoe(null);
        if (!foe.hasHead()) {
            boolean hit = arena.shoot(foe, gun, "SHOT", Shot.NORMAL);
            arena.log(name + ": " + who + " has no head to take" + (hit ? ", but the ball strikes it." : ", and the shot is lost."));
            return Outcome.USED;
        }
        if (!arena.shoot(foe, gun, "HEADSHOT", Shot.HEADSHOT)) {
            arena.log(name + ": the shot is lost.");
        } else if (foe.getCurrentHP() <= 0) {
            arena.log(name + " takes the head of " + who + ".");
        } else {
            arena.log(name + " strikes " + who + " in the head. It is " + dazeWord(foe) + ".");
        }
        return Outcome.USED;
    }

    /**
     * Shattering Throw: the held weapon is thrown, and on a hit it bursts, half its damage to
     * every creature beside the target -- the player too, standing close.
     */
    private static Outcome shatteringThrow(Item weapon, Arena arena) {
        if (weapon == null) return Outcome.NO_TARGET;
        Monster foe = arena.foeInLine(weapon, true);
        if (foe == null) return Outcome.NO_TARGET;
        String name = UiNames.of(CombatArt.SHATTERING_THROW);
        String who = AffinityText.nameOf(foe);
        Hurl hurl = arena.hurl(weapon);
        if (!hurl.hit()) {
            arena.log(name + ": the throw misses " + who + ".");
            return Outcome.USED;
        }
        arena.splash(hurl.struck, Math.max(1, hurl.damage / 2), "SHATTER");
        arena.log(name + " bursts on " + who + ", and the shards strike everything beside it.");
        return Outcome.USED;
    }

    /**
     * A capstone (#248): for its turns, the Family's Adept Passive is certain; when they end, the
     * Family's Expert Active goes on its cooldown. The capstone's own cooldown starts now.
     */
    private static Outcome capstone(Player player, CombatArt art, Arena arena) {
        CombatArt expert = CombatArt.of(art.family, Rank.EXPERT);
        player.getArtCooldowns().openWindow(art.family, art.durationTurns, player.artCooldown(expert));
        String text = art.describe();
        arena.log(UiNames.of(art) + ": " + Character.toLowerCase(text.charAt(0)) + text.substring(1));
        return Outcome.USED;
    }

    /** Dazes {@code foe} and says how it took it: "dazed", or "stunned" if it was already Dazed. */
    private static String dazeWord(Monster foe) {
        return foe.applyDaze() == Monster.DazeOutcome.STUNNED ? "stunned" : "dazed";
    }
}
