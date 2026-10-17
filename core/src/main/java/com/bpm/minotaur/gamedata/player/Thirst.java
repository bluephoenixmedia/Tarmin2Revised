package com.bpm.minotaur.gamedata.player;

import com.bpm.minotaur.gamedata.injury.IllnessStage;
import com.bpm.minotaur.gamedata.liquid.LiquidType;

/**
 * NetHack-style thirst scale (#193, decided in #180). Mirrors {@link Hunger}: the starting
 * store, the burn, stage thresholds and penalties, collapse, and liquid hydration values.
 * Hydration never touches healing and thirst never blocks regeneration; illness is what
 * blocks natural regen.
 *
 * <p>Thirst stages order from fullest to emptiest. Dehydration penalises Dexterity by 2 and
 * slows actions. Collapse passes out for 1-5 turns. Death occurs far below zero.
 */
public final class Thirst {

    private Thirst() {
    }

    // --- The store and its burn ---------------------------------------------------------

    /** Where every expedition, respawn and new game starts. */
    public static final float START = 900f;
    /** Burned per standard turn (drink roughly every 600 turns). */
    public static final float BURN_PER_TURN = 1.5f;

    // --- Stage thresholds (see Stage for what each one does) -----------------------------

    /** Above this, Hydrated. */
    public static final float HYDRATED_ABOVE = 150f;
    /** Below this, Thirsty: a warning notice. Reached after ~500 turns from full. */
    public static final float THIRSTY_BELOW = 150f;
    /** Below this, Dehydrated: -2 DEX, slower actions. */
    public static final float DEHYDRATED_BELOW = 50f;
    /** Below this, Collapse: pass out for 1-5 turns (like Fainting). */
    public static final float COLLAPSE_BELOW = 0f;

    /** Death once hydration falls below -(DEATH_BASE + DEATH_PER_CON * CON). */
    public static final float DEATH_BASE = 100f;
    public static final float DEATH_PER_CON = 10f;

    /** Store's maximum cap. */
    public static final float MAX_STORE = 1200f;

    // --- Collapse ------------------------------------------------------------------------

    public static final int COLLAPSE_MIN_TURNS = 1;
    public static final int COLLAPSE_MAX_TURNS = 5;

    // --- Drink values --------------------------------------------------------------------

    /** Full drink value (surface water, rain, strata water, slush). */
    public static final int DRINK_FULL = 200;
    /** Half drink value (blood, black muck). */
    public static final int DRINK_HALF = 100;
    /** Standard waterskin capacity in drinks. */
    public static final int WATERSKIN_CAPACITY = 5;

    /** The stages, fullest first. */
    public enum Stage {
        HYDRATED("Hydrated", 0, 0f),
        THIRSTY("Thirsty", 0, 0f),
        DEHYDRATED("Dehydrated", 2, 0.20f),
        COLLAPSE("Collapse", 2, 0.20f),
        DEAD("Dead", 2, 0.20f);

        public final String label;
        public final int dexterityPenalty;
        public final float speedPenalty;

        Stage(String label, int dexterityPenalty, float speedPenalty) {
            this.label = label;
            this.dexterityPenalty = dexterityPenalty;
            this.speedPenalty = speedPenalty;
        }

        public boolean isThirsty() {
            return ordinal() >= THIRSTY.ordinal();
        }

        public boolean isDehydrated() {
            return ordinal() >= DEHYDRATED.ordinal();
        }
    }

    /** Hydration below which a body of this Constitution dies of thirst. */
    public static float deadBelow(int constitution) {
        return -(DEATH_BASE + DEATH_PER_CON * Math.max(0, constitution));
    }

    /** Lowest value the store holds. */
    public static float floor(int constitution) {
        return deadBelow(constitution) - 1f;
    }

    public static Stage stageOf(float hydration, int constitution) {
        if (hydration >= THIRSTY_BELOW) return Stage.HYDRATED;
        if (hydration >= DEHYDRATED_BELOW) return Stage.THIRSTY;
        if (hydration >= COLLAPSE_BELOW) return Stage.DEHYDRATED;
        if (hydration >= deadBelow(constitution)) return Stage.COLLAPSE;
        return Stage.DEAD;
    }

    /** Hydration burned over {@code time} standard turns with load and trait multiplier. */
    public static float burn(float loadScale, float time, float thirstMult) {
        return BURN_PER_TURN * loadScale * time * thirstMult;
    }

    // --- Collapse helpers ----------------------------------------------------------------

    public static float collapseChance(float hydration) {
        if (hydration >= COLLAPSE_BELOW) return 0f;
        float n = 20f - hydration / 10f;
        return Math.max(0f, Math.min(1f, (n - 19f) / n));
    }

    public static int collapseTurns(float roll) {
        int span = COLLAPSE_MAX_TURNS - COLLAPSE_MIN_TURNS + 1;
        return COLLAPSE_MIN_TURNS + Math.min(span - 1, (int) (Math.max(0f, roll) * span));
    }

    // --- Liquid properties ---------------------------------------------------------------

    public static boolean isDrinkable(LiquidType liquid) {
        if (liquid == null) return false;
        return liquid == LiquidType.WATER
                || liquid == LiquidType.BLOOD
                || liquid == LiquidType.BLACK_MUCK
                || liquid == LiquidType.FREEZING_SLUSH;
    }

    public static int hydrationGain(LiquidType liquid) {
        if (liquid == null) return 0;
        switch (liquid) {
            case BLOOD:
            case BLACK_MUCK:
                return DRINK_HALF;
            case WATER:
            case FREEZING_SLUSH:
                return DRINK_FULL;
            default:
                return 0;
        }
    }

    public static float illnessRisk(LiquidType liquid, boolean isStrata) {
        if (liquid == null) return 0f;
        switch (liquid) {
            case WATER:
                return isStrata ? 0.10f : 0.0f;
            case BLOOD:
                return 0.70f;
            case BLACK_MUCK:
                return 1.0f;
            default:
                return 0.0f;
        }
    }

    public static IllnessStage startingIllnessStage(LiquidType liquid) {
        if (liquid == null) return IllnessStage.HEALTHY;
        switch (liquid) {
            case BLOOD:
                return IllnessStage.STAGE_2_ACUTE_FEVER;
            case BLACK_MUCK:
            case WATER:
                return IllnessStage.STAGE_1_INFECTED_WOUND;
            default:
                return IllnessStage.HEALTHY;
        }
    }

    public static boolean isChilling(LiquidType liquid) {
        return liquid == LiquidType.FREEZING_SLUSH;
    }

    // --- Save migration ------------------------------------------------------------------

    /**
     * Maps legacy 0-100 hydration onto the new 900-base scale.
     */
    public static float fromLegacyHydration(float old) {
        if (old > 100f) return old; // already on new scale
        if (old <= 0f) return 0f;
        if (old <= 20f) return old / 20f * THIRSTY_BELOW;
        if (old <= 80f) return THIRSTY_BELOW + (old - 20f) / 60f * (START - THIRSTY_BELOW);
        float over = Math.min(old, 100f) - 80f;
        return START + over / 20f * 100f;
    }
}
