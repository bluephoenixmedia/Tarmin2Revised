package com.bpm.minotaur.gamedata.player;

/**
 * Rules the hunger and thirst meters share (#191, #193). Both follow NetHack's shape: a store
 * that burns down through warning and danger stages, passing out once it runs dry, and death a
 * Constitution-scaled distance below empty.
 */
public final class SurvivalMeter {

    private SurvivalMeter() {
    }

    /** The store value below which the meter kills: {@code -(base + perCon * CON)}. */
    public static float deathLine(float base, float perCon, int constitution) {
        return -(base + perCon * Math.max(0, constitution));
    }
}
