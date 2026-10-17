package com.bpm.minotaur.gamedata.shelter;

/**
 * Lighting a cold shelter's hearth: a few turns of work that a wound breaks.
 *
 * <p>The Tinder Bundle is spent only when the fire takes, so an interrupted
 * attempt costs time, not fuel.
 */
public final class HearthLighting {

    public enum Step { CONTINUE, COMPLETE, INTERRUPTED }

    /** World turns the hearth takes to light. */
    public static final int TURNS = 5;

    private final int woundsAtStart;
    private int turnsDone;

    public HearthLighting(int woundsTaken) {
        this.woundsAtStart = woundsTaken;
    }

    public Step afterTurn(int woundsTaken) {
        if (woundsTaken > woundsAtStart) {
            return Step.INTERRUPTED;
        }
        turnsDone++;
        return turnsDone >= TURNS ? Step.COMPLETE : Step.CONTINUE;
    }
}
