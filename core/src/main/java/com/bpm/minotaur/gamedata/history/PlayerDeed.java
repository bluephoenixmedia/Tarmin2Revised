package com.bpm.minotaur.gamedata.history;

/**
 * Something the player did that the history records (plan D10, D45). Deeds are the only input
 * to the live history besides the seed, and the save stores them in order (ADR 0004).
 */
public class PlayerDeed {

    public enum Kind {
        /** The player killed a named figure. {@link #target} is the figure id. */
        SLEW_FIGURE,
        /** An expedition ended in death. {@link #target} is the killing house id, or -1. */
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
        QUEST_DONE
    }

    public Kind kind;
    public int target = -1;
    /** A second house, where the deed needs one; -1 otherwise. */
    public int other = -1;
    /** A name the deed carries, where it needs one: a town's. */
    public String note;
    /** The live season the deed happened in: {@link HistoryWorld#liveSeasons()} at the time. */
    public int liveSeason;

    /** For the save reader. */
    public PlayerDeed() {
    }

    public PlayerDeed(Kind kind, int target, int liveSeason) {
        this.kind = kind;
        this.target = target;
        this.liveSeason = liveSeason;
    }
}
