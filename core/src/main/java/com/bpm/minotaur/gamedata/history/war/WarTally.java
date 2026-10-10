package com.bpm.minotaur.gamedata.history.war;

/**
 * How a fight in the player's chunk is going, for the HUD's tug-of-war (Living War W30): the two
 * houses, and the share of the ground the first holds -- its soldiers still standing, against both.
 */
public final class WarTally {
    public final int houseA;
    public final int houseB;
    public final int standingA;
    public final int standingB;

    public WarTally(int houseA, int houseB, int standingA, int standingB) {
        this.houseA = houseA;
        this.houseB = houseB;
        this.standingA = Math.max(0, standingA);
        this.standingB = Math.max(0, standingB);
    }

    /** The first house's share, 0 to 1; even when no one stands. */
    public float shareA() {
        int all = standingA + standingB;
        return all == 0 ? 0.5f : standingA / (float) all;
    }
}
