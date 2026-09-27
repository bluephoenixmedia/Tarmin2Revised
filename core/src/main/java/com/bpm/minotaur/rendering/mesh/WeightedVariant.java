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

    /** Distinct odd multipliers per axis; one multiplier would collide (x,y) with (y,x). */
    private static final long X_MIX = 73856093L;
    private static final long Y_MIX = 19349663L;
    private static final long FACE_MIX = 83492791L;

    private WeightedVariant() {
    }

    /**
     * A well-mixed hash for one surface of one tile.
     *
     * <p>Lives here rather than in each caller because {@link #pick} consumes
     * both the low and high bits, so the avalanche step is part of this
     * contract, not an implementation detail of whoever calls it. Walls, floors
     * and ceilings had their own copies of it.
     *
     * @param stream a per-surface constant, so a tile's floor choice is not
     *               correlated with its ceiling or its walls
     * @param face   0 for surfaces with only one orientation, such as floors
     */
    public static long hash(long chunkSeed, long stream, int tileX, int tileY, int face) {
        long h = chunkSeed ^ stream;
        h ^= tileX * X_MIX;
        h ^= tileY * Y_MIX;
        h ^= face * FACE_MIX;
        return mix(h);
    }

    /**
     * Index chosen evenly rather than weighted, for a set with no real default.
     */
    public static int pickEven(long hash, int variantCount) {
        if (variantCount <= 1) return 0;
        return (int) Math.floorMod(hash, (long) variantCount);
    }

    /** MurmurHash3's 64-bit finalizer. Without it the low bits barely move. */
    private static long mix(long z) {
        z ^= (z >>> 33);
        z *= 0xff51afd7ed558ccdL;
        z ^= (z >>> 33);
        z *= 0xc4ceb9fe1a85ec53L;
        z ^= (z >>> 33);
        return z;
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
