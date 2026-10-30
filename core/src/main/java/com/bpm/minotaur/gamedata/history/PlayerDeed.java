package com.bpm.minotaur.gamedata.history;

/**
 * Something the player did that the history records (plan D10, D45). Deeds are the only input
 * to the live history besides the seed, and the save stores them in order (ADR 0004).
 */
public class PlayerDeed {

    public enum Kind {
        /** The player killed a named figure. {@link #target} is the figure id. */
        SLEW_FIGURE,
        /**
         * An expedition ended in death. {@link #target} is the killing house id, or -1; {@link #note}
         * names what killed the seeker (#280), and is null in deaths saved before it did.
         */
        SEEKER_FELL,
        /**
         * The Doom Clock reached a stage it never had before. {@link #target} is the stage. Not
         * strictly the player's doing, but it is the run's, and the history replays it the same way.
         */
        DOOM_STAGE,
        /**
         * The player stood in a battle on the surface until it broke (plan T2.6). {@link #target}
         * held the field; {@link #other} routed.
         */
        BATTLE_WITNESSED,
        /** The player killed a megabeast. {@link #target} is its id. */
        SLEW_MEGABEAST,
        /**
         * The player did a town's work (plan T4.5). {@link #target} is the quest kind's ordinal,
         * {@link #other} the house or beast concerned, {@link #note} the town's name.
         */
        QUEST_DONE,
        /** The player bought a megabeast's peace with a trophy. {@link #target} is its id. */
        PACIFIED_MEGABEAST,
        /** The player struck a beast whose peace they had bought; it is unbought. {@link #target} is its id. */
        BROKE_BEAST_PEACE,
        /**
         * Not the player's doing, but seen in their run: a seal lord ({@link #target}, a figure) was
         * killed in its court by house {@link #other}, or by no house (-1).
         */
        LORD_SLAIN_IN_COURT,
        /**
         * Not the player's doing: a new game whose prehistory ended in peace opens on a war (Living
         * War W6). House {@link #target} declares on house {@link #other}.
         */
        OPENING_WAR,
        /**
         * The player fought in a skirmish until it broke (Living War W12): house {@link #target} held
         * the ground, {@link #other} routed. {@link #figure} is a named captain who fell in it, or -1.
         */
        SKIRMISH,
        /** The player swore to house {@link #target} (Living War W20). */
        SWORE_OATH,
        /** The player broke its oath to house {@link #target} (W21). */
        BROKE_OATH,
        /** House {@link #target}'s lord granted the player the seal of its gash (W26). */
        SEAL_GRANTED,
        /** The player slew Nemesis {@link #target} (#283): it is dead, and is placed no more. */
        SLEW_NEMESIS,
        /**
         * Not the player's doing but their run's: old god {@link #note} (an {@link OldGod} name)
         * was spent holding Tarmin back, and its shrine is dark for good (#287, #290).
         */
        GOD_SPENT,
        /** The Bridge's Warden fell, and the Bridge crumbled back to nothing (#290). */
        WARDEN_FELL,
        /** The seeker died while the Warden stood, and Tarmin crossed (#276, #290). */
        TARMIN_CROSSED,
        /** While Tarmin was gone, house {@link #target} took the most of the Maze (#276, #290). */
        HOUSE_ROSE,
        /**
         * The Champion slew Tarmin (#291): {@link #target} is 1 if they took the throne, 0 if they
         * returned the Treasure; {@link #victim} is the life's name.
         */
        TARMIN_SLAIN,
        /**
         * A battle of the armies, watched or not (Living War A17). {@link #target} held the field
         * and {@link #other} routed; see {@link #deadHouses} and the fields beside it.
         */
        ARMY_BATTLE,
        /** House {@link #target} has lost half its strength and can raise no host: it sues {@link #other} for peace (A25). */
        SUED_FOR_PEACE
    }

    public Kind kind;
    public int target = -1;
    /** A second house, where the deed needs one; -1 otherwise. */
    public int other = -1;
    /** A figure the deed carries, where it needs one beside {@link #target}; -1 otherwise. */
    public int figure = -1;
    /** A name the deed carries, where it needs one: a town's. */
    public String note;
    /** The live season the deed happened in: {@link HistoryWorld#liveSeasons()} at the time. */
    public int liveSeason;
    /**
     * For {@code SEEKER_FELL} (#283): the killer's monsters.json type (null when no monster
     * killed, and in deaths saved before Nemeses), the Nemesis it already was or -1, the life's
     * name, and the level and chunk of the death.
     */
    public String monsterType;
    public int nemesis = -1;
    public String victim;
    public int level = -1;
    /** For {@code SEEKER_FELL}: Tarmin's Hunger after the death, which may open a new Era (#292); -1 if unknown. */
    public int hunger = -1;
    public int chunkX;
    public int chunkY;

    /** For {@code ARMY_BATTLE}: each house that lost soldiers, and how many, as parallel lists. */
    public int[] deadHouses;
    public int[] deadCounts;
    /** For {@code ARMY_BATTLE}: captains slain, and captains taken by the winner. */
    public int[] captainsSlain;
    public int[] captainsTaken;
    /** For {@code ARMY_BATTLE}: fought at a seat, and whether that seat fell. */
    public boolean atSeat;
    public boolean seatFell;
    /** For {@code ARMY_BATTLE}: under about 20 soldiers in all. */
    public boolean skirmish;

    /** For the save reader. */
    public PlayerDeed() {
    }

    public PlayerDeed(Kind kind, int target, int liveSeason) {
        this.kind = kind;
        this.target = target;
        this.liveSeason = liveSeason;
    }
}
