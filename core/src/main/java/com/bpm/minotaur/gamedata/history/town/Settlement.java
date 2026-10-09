package com.bpm.minotaur.gamedata.history.town;

/**
 * A mortal settlement of the history (ADR 0005, plan D37): its name, the power it answers to, and
 * a seat for each of its keepers, each held by a {@link Mortal}. A town on the map is one of these
 * once the game has bound it.
 */
public final class Settlement {

    /** How many settlements the history keeps: more than a run finds towns, on the strata it uses. */
    public static final int COUNT = 24;

    public final int id;
    public final String name;
    public final Allegiance allegiance;
    public final int foundedSeason;
    /** How the settlement regards a stranger before they have done anything (D20). */
    public final int welcome;
    /** The role of each seat, in the order the town's folk stand. */
    public final Town.Role[] seats;
    /** The mortal holding each seat now. */
    public final int[] holders;

    public Settlement(int id, String name, Allegiance allegiance, int foundedSeason, int welcome, Town.Role[] seats) {
        this.id = id;
        this.name = name;
        this.allegiance = allegiance;
        this.foundedSeason = foundedSeason;
        this.welcome = welcome;
        this.seats = seats;
        this.holders = new int[seats.length];
        java.util.Arrays.fill(holders, -1);
    }
}
