package com.bpm.minotaur.gamedata.player;

import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.monster.Monster;

import java.util.List;

/**
 * NetHack's nutrition scale (#191, decided in #179). Every hunger number lives here: the
 * starting store, the burn, the stage thresholds and what each stage does, choking, fainting,
 * and what food is worth. Food never heals and hunger never blocks regeneration; the two
 * systems are fully separate.
 *
 * <p>Thirst (#193) is meant to mirror this shape: a {@link Stage} enum ordered fullest to
 * emptiest whose constants carry their own effects, a pure {@link #stageOf} lookup, and pure
 * roll helpers that take their random number from the caller so they can be tested.
 *
 * <p>The counter itself is {@code PlayerStats.satiety}; the name predates this scale.
 */
public final class Hunger {

    private Hunger() {
    }

    // --- The store and its burn ---------------------------------------------------------

    /**
     * Where every expedition, respawn and new game starts. An unfed body is Hungry by turn
     * 351, and the two starting rations carry it to about turn 750 (#312).
     */
    public static final float START = 500f;
    /** Burned per standard turn; heavy loads and traits scale it in TurnManager. */
    public static final float BURN_PER_TURN = 1f;

    // --- Stage thresholds (see Stage for what each one does) -----------------------------

    /** Above this, Satiated: eating more risks choking. */
    public static final float SATIATED_ABOVE = 1000f;
    /** Below this, Hungry: a warning, nothing more. A fresh start reaches it on turn 351. */
    public static final float HUNGRY_BELOW = 150f;
    /** Below this, Weak: -2 STR, -1 to-hit. */
    public static final float WEAK_BELOW = 50f;
    /** Below this, Fainting: each turn a chance to pass out. */
    public static final float FAINTING_BELOW = 0f;
    /** Starved (death) once nutrition falls below -(STARVE_BASE + STARVE_PER_CON * CON). */
    public static final float STARVE_BASE = 100f;
    public static final float STARVE_PER_CON = 10f;

    // --- Choking -------------------------------------------------------------------------

    /** A meal started while Satiated that would carry nutrition past this chokes. Also the store's cap. */
    public static final float CHOKE_AT = 2000f;
    /** Very overfull: only a meal started at or above this can kill by choking. */
    public static final float VERY_OVERFULL = 1500f;
    /** The death chance when a very overfull eater chokes; otherwise choking is a vomit. */
    public static final float CHOKE_DEATH_CHANCE = 0.05f;
    /** Vomiting throws up the meal and this much stored nutrition with it. */
    public static final float VOMIT_LOSS = 1000f;

    // --- Fainting ------------------------------------------------------------------------

    public static final int FAINT_MIN_TURNS = 1;
    public static final int FAINT_MAX_TURNS = 5;

    // --- Food values ---------------------------------------------------------------------

    /** A travel ration: about 200 turns of walking, so the kit runs short within one expedition (#312). */
    public static final int RATION = 200;
    /** Rations a new character carries; rare afterwards. */
    public static final int STARTING_RATIONS = 2;
    /** A flour sack picked up off the floor is eaten on the spot: a range, rolled per sack. */
    public static final int FLOUR_SACK_MIN = 150;
    public static final int FLOUR_SACK_MAX = 250;
    /** Raw meat eaten off the floor (raw eating is reworked in 0.0.3, #192). */
    public static final int RAW_MEAT_MIN = 75;
    public static final int RAW_MEAT_MAX = 110;
    /** A cooked meal whose item carries no nutrition of its own. */
    public static final int DEFAULT_MEAL = 300;
    /** A food item that carries no nutrition of its own. */
    public static final int DEFAULT_FOOD = 50;
    /** The shelter hearth's warm bowl when resting there. */
    public static final int HEARTH_BOWL = 150;

    /** The stages, fullest first. Each carries its own combat penalty. */
    public enum Stage {
        SATIATED("Satiated", 0, 0),
        NOT_HUNGRY("Not Hungry", 0, 0),
        HUNGRY("Hungry", 0, 0),
        WEAK("Weak", 2, 1),
        FAINTING("Fainting", 2, 1),
        STARVED("Starved", 2, 1);

        public final String label;
        public final int strengthPenalty;
        public final int toHitPenalty;

        Stage(String label, int strengthPenalty, int toHitPenalty) {
            this.label = label;
            this.strengthPenalty = strengthPenalty;
            this.toHitPenalty = toHitPenalty;
        }

        /** Hungry or worse. */
        public boolean isHungry() {
            return ordinal() >= HUNGRY.ordinal();
        }

        /** Weak or worse: the stages that cost the player in a fight. */
        public boolean isWeakened() {
            return ordinal() >= WEAK.ordinal();
        }

        /** Fainting or worse: the player can pass out. */
        public boolean isFainting() {
            return ordinal() >= FAINTING.ordinal();
        }

        /** How much this stage needs attention: Hungry warns, Weak and worse are danger. */
        public Severity severity() {
            return isWeakened() ? Severity.DANGER : isHungry() ? Severity.WARNING : Severity.NONE;
        }
    }

    /** Nutrition below which a body of this Constitution has starved to death. */
    public static float starvedBelow(int constitution) {
        return SurvivalMeter.deathLine(STARVE_BASE, STARVE_PER_CON, constitution);
    }

    /** The lowest value the store holds: the starvation line itself. */
    public static float floor(int constitution) {
        return starvedBelow(constitution) - 1f;
    }

    public static Stage stageOf(float nutrition, int constitution) {
        if (nutrition > SATIATED_ABOVE) return Stage.SATIATED;
        if (nutrition >= HUNGRY_BELOW) return Stage.NOT_HUNGRY;
        if (nutrition >= WEAK_BELOW) return Stage.HUNGRY;
        if (nutrition >= FAINTING_BELOW) return Stage.WEAK;
        if (nutrition >= starvedBelow(constitution)) return Stage.FAINTING;
        return Stage.STARVED;
    }

    /** Nutrition burned over {@code time} standard turns at a load and trait multiplier. */
    public static float burn(float loadScale, float time, float hungerMult) {
        return BURN_PER_TURN * loadScale * time * hungerMult;
    }

    // --- Fainting ------------------------------------------------------------------------

    /**
     * The per-turn chance to pass out. NetHack's curve: zero until the store goes negative,
     * then {@code (n - 19) / n} with {@code n = 20 - nutrition / 10}, so it starts near 1 in 20
     * and deepens the further below zero the store falls.
     */
    public static float faintChance(float nutrition) {
        if (nutrition >= FAINTING_BELOW) return 0f;
        float n = 20f - nutrition / 10f;
        return Math.max(0f, Math.min(1f, (n - 19f) / n));
    }

    /** How long a faint lasts, from a roll in [0, 1). */
    public static int faintTurns(float roll) {
        int span = FAINT_MAX_TURNS - FAINT_MIN_TURNS + 1;
        return FAINT_MIN_TURNS + Math.min(span - 1, (int) (Math.max(0f, roll) * span));
    }

    // --- Eating --------------------------------------------------------------------------

    public enum EatOutcome {
        /** The meal goes down. */
        EAT,
        /** Choked and threw up: the meal is lost, with VOMIT_LOSS of the store. */
        VOMIT,
        /** Choked to death. */
        CHOKE_DEATH
    }

    /** {@link #CHOKE_AT} moved by the chokeLimitAdd trait modifier (Iron Stomach lowers it). */
    public static float chokeAt() {
        return CHOKE_AT + com.bpm.minotaur.gamedata.trait.TraitEffects.add("chokeLimitAdd");
    }

    /**
     * What happens when a meal worth {@code gain} is eaten at {@code before}. Only a meal
     * started while Satiated can choke, and only if it would carry the store past
     * {@code chokeAt}; then a very overfull eater dies on {@code roll < CHOKE_DEATH_CHANCE},
     * and everyone else vomits.
     */
    public static EatOutcome eat(float before, float gain, float chokeAt, float roll) {
        if (before <= SATIATED_ABOVE || before + gain < chokeAt) return EatOutcome.EAT;
        if (before >= VERY_OVERFULL && roll < CHOKE_DEATH_CHANCE) return EatOutcome.CHOKE_DEATH;
        return EatOutcome.VOMIT;
    }

    // --- Cooked monster parts ------------------------------------------------------------

    /** Cooked value of a part from a medium monster; size scales it. */
    public static int cookedPartBase(Item.ItemType part) {
        if (part == null) return 0;
        switch (part) {
            case CORPSE: return 300;
            case MEAT: return 200;
            case GIB_FLESH: return 150;
            case ORGAN:
            case GIB_ORGAN: return 100;
            case GIB_BONE: return 50;
            case GIB_BILE:
            case GIB_GLAZE: return 25;
            default: return 0;
        }
    }

    public enum Size {
        SMALL(0.5f), MEDIUM(1f), LARGE(2f);

        public final float mult;

        Size(float mult) {
            this.mult = mult;
        }
    }

    /** Body size for food purposes. Unknown sources are medium. */
    public static Size sizeOf(Monster.MonsterType type) {
        if (type == null) return Size.MEDIUM;
        switch (type) {
            case KOBOLD: case GOBLIN: case JESTER: case WERERAT: case HARPY: case GIANT_ANT:
            case SPIDER: case BAT: case GIANT_BEE: case GIANT_SNAIL: case GIANT_CENTIPEDE:
            case COCKATRICE: case BEETLESCRATCH:
                return Size.SMALL;
            case DRAGON: case GIANT: case MINOTAUR: case ALLIGATOR: case BEHOLDER: case GELATINOUS_CUBE:
            case OWLBEAR: case UMBER_HULK: case TROLL: case OGRE: case WYVERN: case CHIMERA: case HYDRA:
            case PURPLE_WORM: case BASILISK: case IRON_GOLEM: case LANDSTALKER:
                return Size.LARGE;
            default:
                return Size.MEDIUM;
        }
    }

    /** Nutrition of one cooked part, by part and by the monster it came from. */
    public static int cookedPart(Item.ItemType part, Monster.MonsterType source) {
        return Math.round(cookedPartBase(part) * sizeOf(source).mult);
    }

    /**
     * Nutrition of a dish cooked from these ingredients: each monster part at its cooked
     * value, any other food at its own value, and anything else (salt, herbs) at nothing.
     */
    public static int cookedMeal(List<Item> ingredients) {
        if (ingredients == null) return 0;
        int total = 0;
        for (Item ingr : ingredients) {
            if (ingr == null) continue;
            int part = cookedPart(ingr.getType(), ingr.getCorpseSource());
            if (part > 0) {
                total += part;
            } else if (ingr.isFood()) {
                total += ingr.getNutrition() > 0 ? ingr.getNutrition() : DEFAULT_FOOD;
            }
        }
        return total;
    }

    // --- Save migration ------------------------------------------------------------------

    /**
     * Maps a pre-0.0.2 satiety (0-120: Hungry at 25, a fresh start at 80) onto this scale so
     * a loaded character keeps its stage: 0 stays 0, 25 lands on Hungry, 80 on a fresh start,
     * and 120 deep into Satiated.
     */
    public static float fromLegacySatiety(float old) {
        if (old <= 0f) return 0f;
        if (old <= 25f) return old / 25f * HUNGRY_BELOW;
        if (old <= 80f) return HUNGRY_BELOW + (old - 25f) / 55f * (START - HUNGRY_BELOW);
        float over = Math.min(old, 120f) - 80f;
        return START + over / 40f * (VERY_OVERFULL - START);
    }
}
