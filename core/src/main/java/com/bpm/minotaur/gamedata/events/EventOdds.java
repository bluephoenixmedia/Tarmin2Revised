package com.bpm.minotaur.gamedata.events;

/**
 * The success chance of a choice event's check.
 *
 * <p>{@code base + (attribute - 10) * 5% + luck * 2%}, clamped to 5..95%, so an average attribute
 * leaves the designer's base untouched and nothing is ever certain either way.
 */
public final class EventOdds {

    static final float PER_ATTRIBUTE_POINT = 0.05f;
    static final float PER_LUCK_POINT = 0.02f;
    static final float MIN = 0.05f;
    static final float MAX = 0.95f;

    private EventOdds() {
    }

    public static float chance(float base, int attribute, int luck) {
        return clamp(base + (attribute - 10) * PER_ATTRIBUTE_POINT + luck * PER_LUCK_POINT);
    }

    /** A check that names no attribute: only luck moves it. */
    public static float luckChance(float base, int luck) {
        return clamp(base + luck * PER_LUCK_POINT);
    }

    public static int percent(float chance) {
        return Math.round(chance * 100f);
    }

    private static float clamp(float chance) {
        return Math.max(MIN, Math.min(MAX, chance));
    }
}
