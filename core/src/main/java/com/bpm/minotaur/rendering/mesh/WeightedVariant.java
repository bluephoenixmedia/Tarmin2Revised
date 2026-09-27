package com.bpm.minotaur.rendering.mesh;

/**
 * Picks a texture variant that favours the default.
 *
 * <p>A flat roll across six wall variants showed the original masonry only
 * about a sixth of the time, which read as six different walls rather than one
 * wall with character. Weighting the default to three quarters makes the
 * variants punctuation: roughly one surface in four is something other than the
 * standard, and which one is a surprise.
 *
 * <p>Shared by walls, floors and ceilings so the three cannot drift apart.
 */
public final class WeightedVariant {

    /** Percentage of surfaces that wear variant 0, the default texture. */
    public static final int DEFAULT_WEIGHT_PERCENT = 75;

    private WeightedVariant() {
    }

    /**
     * Chooses a variant index for one surface.
     *
     * @param hash         a well-mixed hash for this surface; low and high bits
     *                     are both consumed, so it must have real avalanche
     * @param variantCount total variants including the default at index 0
     * @return 0 for the default, otherwise 1..variantCount-1
     */
    public static int pick(long hash, int variantCount) {
        if (variantCount <= 1) return 0;

        // floorMod, not %, because the hash is signed and % would bias toward 0.
        int roll = (int) Math.floorMod(hash, 100L);
        if (roll < DEFAULT_WEIGHT_PERCENT) return 0;

        // Divide before the second modulo so the two draws come from different
        // parts of the hash; reusing the same low bits would tie the choice of
        // variant to the roll that selected it.
        long spread = Math.floorMod(hash / 100L, (long) (variantCount - 1));
        return 1 + (int) spread;
    }
}
