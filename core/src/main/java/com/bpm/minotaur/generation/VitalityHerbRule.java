package com.bpm.minotaur.generation;

import java.util.Random;

/**
 * Where Glowing Lichen grows (#196): the Vitality ingredient that cures illness, which must be
 * findable "especially in the strata where bad water dominates" (#180).
 *
 * <p>It used to be one weight in the debris table. The debris pool ignores everything but the
 * portal's level band and is sized by EDL, which a surface chunk and a strata chunk share, so a
 * weight could not make it rare on the surface and regular below. A chance per chunk by real
 * depth can.
 */
public final class VitalityHerbRule {

    /** Chance per surface chunk: rare, about one per five depth-1 expeditions of four chunks. */
    public static final float SURFACE_CHANCE = 0.05f;

    /** Chance per strata chunk: about one per two strata levels of four chunks each. */
    public static final float STRATA_CHANCE = 0.125f;

    private VitalityHerbRule() {
    }

    /** @param depth the real dungeon depth (1 is the surface), not the EDL */
    public static float chance(int depth) {
        return depth <= 1 ? SURFACE_CHANCE : STRATA_CHANCE;
    }

    /** True if this chunk grows a Glowing Lichen. */
    public static boolean rollLichen(int depth, Random random) {
        return random.nextFloat() < chance(depth);
    }
}
