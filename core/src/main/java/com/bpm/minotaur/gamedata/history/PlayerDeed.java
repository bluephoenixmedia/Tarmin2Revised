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
        /** Not the player's doing: house {@link #target} has camped around the seat of house {@link #other} (Living War A30). */
        SEAT_INVESTED,
        /** Not the player's doing: house {@link #target} no longer besieges the seat of house {@link #other}, which stands. */
        SIEGE_RAISED,
        /**
         * Not the player's doing: a host of house {@link #target} camps before Castle Tarmin (Living War
         * A35). {@link #figure} is the army, {@link #seasons} how long it means to stay.
         */
        CASTLE_SIEGE_LAID,
        /** Not the player's doing: the host of house {@link #target} before the castle routed. */
        CASTLE_SIEGE_BROKEN,
        /** Not the player's doing: house {@link #target} ended its siege of the castle, by its term or its war's. */
        CASTLE_SIEGE_LIFTED,
        /**
         * Not the player's doing: megabeast {@link #target} fought an army or the garrison of house
         * {@link #other} (Living War A28). Chronicled only if the beast or a captain died.
         */
        ARMY_BEAST,
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
        SUED_FOR_PEACE,
        /** The land's toll on house {@link #target}'s armies this Season (A27): {@link #deadCounts}, never chronicled. */
        ARMY_ATTRITION
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
    /** For {@code ARMY_BATTLE}: the house the player fought for, or -1, and whether they stood there at the end (A17, A24). */
    public int foughtFor = -1;
    public boolean witnessed;
    /** ARMY_BATTLE: the player stood to the end with this house; -1 if they didn't (A40). */
    public int stoodWith = -1;
    /** ARMY_BATTLE: the player turned it, a fifth or more of the enemy dead on a winning side (A40). */
    public boolean turned;
    /** ARMY_BATTLE: the armies that routed and that held the field, for the contracts that name them (A38). */
    public int[] armiesRouted;
    public int[] armiesHeld;
    /** ARMY_BEAST: it died of this, and what it has left if it did not. */
    public boolean beastSlain;
    public int beastHp;
    /** CASTLE_SIEGE_LAID: Seasons the host means to stay. */
    public int seasons;
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
