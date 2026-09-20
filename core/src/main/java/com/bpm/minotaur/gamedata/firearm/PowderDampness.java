package com.bpm.minotaur.gamedata.firearm;

import java.util.Random;

/**
 * How wet the player's powder is, and what that costs them.
 *
 * <p>Dampness is carried on the player rather than read from the weather at the moment of
 * firing. The weather's own wetness is outdoor-only, so a live read would leave firearms
 * perfectly reliable in every dungeon in the game -- and the strata are where nearly all
 * play happens, so the mechanic would never be encountered. Carrying it means walking in
 * from a storm leaves your powder damp for a while, which also gives the surface-to-
 * underground transition a consequence it did not previously have.
 *
 * <p>A misfire wastes the shot but <em>not</em> the reload. Losing both would stack a
 * random failure on top of a multi-turn commitment, which is the outcome that makes a
 * weapon feel unreliable rather than powerful.
 */
public final class PowderDampness {

    /** Misfire chance when soaked through. */
    private static final float MAX_MISFIRE_CHANCE = 0.25f;

    /** Turns from soaked to bone dry. */
    private static final int TURNS_TO_DRY = 15;
    private static final float DRY_PER_TURN = 1f / TURNS_TO_DRY;

    /**
     * Ambient weather wetness above which the player gets soaked. Below this it is
     * drizzle on the flagstones, not enough to spoil powder in a closed flask.
     */
    private static final float SOAKING_WEATHER_THRESHOLD = 0.5f;

    private PowderDampness() {
    }

    /** Chance in 0..1 that a shot fizzles, given how damp the powder is. */
    public static float misfireChance(float dampness) {
        return clamp(dampness) * MAX_MISFIRE_CHANCE;
    }

    /** Rolls a single shot. True means it fizzled: the shot is lost, the reload is not. */
    public static boolean rollMisfire(float dampness, Random random) {
        float chance = misfireChance(dampness);
        return chance > 0f && random.nextFloat() < chance;
    }

    /** Dampness after standing in rain or wading through liquid: soaked outright. */
    public static float afterExposure(float dampness) {
        return 1f;
    }

    /**
     * Dampness after one turn out of the wet.
     *
     * <p>Snaps to exactly dry once within rounding distance: repeated subtraction of
     * 1/15 leaves a float residue that would otherwise cost an extra turn and leave the
     * player fractionally damp forever.
     */
    public static float afterDryTurn(float dampness) {
        float next = clamp(dampness) - DRY_PER_TURN;
        return next <= DRY_EPSILON ? 0f : next;
    }

    /** Below this, dampness is rounding error rather than water. */
    private static final float DRY_EPSILON = 1e-4f;

    /** True if the weather is heavy enough to soak someone standing in it. */
    public static boolean isSoakingWeather(float weatherWetness) {
        return weatherWetness >= SOAKING_WEATHER_THRESHOLD;
    }

    private static float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}
