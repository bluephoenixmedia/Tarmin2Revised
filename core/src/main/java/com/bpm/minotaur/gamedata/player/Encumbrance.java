package com.bpm.minotaur.gamedata.player;

/**
 * How much the player can carry before it costs them, and what it costs.
 *
 * <p>The limit follows Strength. Past it the player slows, in three tiers; the heavier two also
 * burn through food and water faster, because hauling that much is hard work. Everything carried
 * counts, equipped or in the pack, and gold has weight too. The numbers are all here and are
 * tuning, not rules.
 */
public final class Encumbrance {

    /** Weight the player can carry at no Strength, and the extra for each point of it. */
    public static final float BASE_CAPACITY = 25f;
    public static final float CAPACITY_PER_STRENGTH = 5f;

    /** Coins that weigh one unit. */
    public static final int COINS_PER_WEIGHT_UNIT = 100;

    public enum Tier {
        UNENCUMBERED("Unencumbered", 1.00f, 1.0f),
        BURDENED("Burdened", 0.75f, 1.0f),
        STRESSED("Stressed", 0.50f, 1.5f),
        OVERLOADED("Overloaded", 0.25f, 2.0f);

        public final String label;
        /** Multiplier on move speed. */
        public final float speedFactor;
        /** Multiplier on how fast food and water run down. */
        public final float drainFactor;

        Tier(String label, float speedFactor, float drainFactor) {
            this.label = label;
            this.speedFactor = speedFactor;
            this.drainFactor = drainFactor;
        }

        /** A move speed with this tier's slowdown applied; never below 1. */
        public int apply(int speed) {
            return Math.max(1, (int) (speed * speedFactor));
        }
    }

    /** Past this share of the capacity the player is Stressed, and past the next, Overloaded. */
    private static final float STRESSED_AT = 1.5f;
    private static final float OVERLOADED_AT = 2.0f;

    private Encumbrance() {
    }

    public static float capacity(int strength) {
        return BASE_CAPACITY + CAPACITY_PER_STRENGTH * strength;
    }

    public static Tier tier(float carried, float capacity) {
        if (capacity <= 0f) {
            return Tier.OVERLOADED;
        }
        float ratio = carried / capacity;
        if (ratio >= OVERLOADED_AT) {
            return Tier.OVERLOADED;
        }
        if (ratio >= STRESSED_AT) {
            return Tier.STRESSED;
        }
        if (ratio >= 1f) {
            return Tier.BURDENED;
        }
        return Tier.UNENCUMBERED;
    }

    /** Weight of a purse: one unit per hundred coins. */
    public static float goldWeight(int coins) {
        return coins <= 0 ? 0f : (float) (coins / COINS_PER_WEIGHT_UNIT);
    }
}
