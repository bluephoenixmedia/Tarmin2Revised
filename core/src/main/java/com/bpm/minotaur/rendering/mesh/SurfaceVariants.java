package com.bpm.minotaur.rendering.mesh;

/**
 * Per-tile texture variants for floors and ceilings.
 *
 * <p>The companion to {@link WallVariants}, which covers wall faces. Floors and
 * ceilings were one texture for a whole chunk, so eight authored floor variants
 * could only ever produce "this room is different" rather than texture variety.
 *
 * <p>Each surface draws from its own stream, so the floor of a tile is not
 * correlated with its ceiling or its walls.
 */
public final class SurfaceVariants {

    private static final long FLOOR_STREAM = 0x464C_4F4F_5256_4152L;
    private static final long CEILING_STREAM = 0x4345_494C_5641_5231L;

    /** Floors and ceilings have one orientation, so there is no face term. */
    private static final int NO_FACE = 0;

    private SurfaceVariants() {
    }

    /** Variant index for one tile's floor, weighted toward the default. */
    public static int floorVariant(long chunkSeed, int tileX, int tileY, int variantCount) {
        return WeightedVariant.pick(
                WeightedVariant.hash(chunkSeed, FLOOR_STREAM, tileX, tileY, NO_FACE), variantCount);
    }

    /** Variant index for one tile's ceiling, weighted toward the default. */
    public static int ceilingVariant(long chunkSeed, int tileX, int tileY, int variantCount) {
        return WeightedVariant.pick(
                WeightedVariant.hash(chunkSeed, CEILING_STREAM, tileX, tileY, NO_FACE), variantCount);
    }

    /**
     * Ceiling variant when there is no authored default to favour.
     *
     * <p>Weighting index 0 to 75% only makes sense when index 0 is the intended
     * default. With no ceiling.png, index 0 is just the first surviving variant,
     * and weighting it would promote an arbitrary texture to three quarters of
     * every dungeon.
     */
    public static int ceilingVariantEven(long chunkSeed, int tileX, int tileY, int variantCount) {
        return WeightedVariant.pickEven(
                WeightedVariant.hash(chunkSeed, CEILING_STREAM, tileX, tileY, NO_FACE), variantCount);
    }
}
