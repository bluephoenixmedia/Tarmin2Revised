package com.bpm.minotaur.rendering.mesh;

/**
 * Per-tile texture variants for floors and ceilings.
 *
 * <p>The companion to {@link WallVariants}, which covers wall faces. Floors and
 * ceilings were one texture for a whole chunk, so eight authored floor variants
 * could only ever produce "this room is different" rather than texture variety.
 *
 * <p>Each surface draws from its own XOR stream, so the floor of a tile is not
 * correlated with its ceiling or its walls -- reusing one stream would make a
 * tile with variant floor reliably have a variant ceiling too.
 */
public final class SurfaceVariants {

    private static final long FLOOR_STREAM = 0x464C_4F4F_5256_4152L;
    private static final long CEILING_STREAM = 0x4345_494C_5641_5231L;

    private static final long X_MIX = 73856093L;
    private static final long Y_MIX = 19349663L;

    private SurfaceVariants() {
    }

    /** Variant index for one tile's floor, weighted toward the default. */
    public static int floorVariant(long chunkSeed, int tileX, int tileY, int variantCount) {
        return pick(chunkSeed, tileX, tileY, FLOOR_STREAM, variantCount);
    }

    /** Variant index for one tile's ceiling, weighted toward the default. */
    public static int ceilingVariant(long chunkSeed, int tileX, int tileY, int variantCount) {
        return pick(chunkSeed, tileX, tileY, CEILING_STREAM, variantCount);
    }

    private static int pick(long chunkSeed, int tileX, int tileY, long stream, int variantCount) {
        long h = chunkSeed ^ stream;
        h ^= tileX * X_MIX;
        h ^= tileY * Y_MIX;
        return WeightedVariant.pick(mix(h), variantCount);
    }

    /** MurmurHash3's 64-bit finalizer; see WallVariants for why it is required. */
    private static long mix(long z) {
        z ^= (z >>> 33);
        z *= 0xff51afd7ed558ccdL;
        z ^= (z >>> 33);
        z *= 0xc4ceb9fe1a85ec53L;
        z ^= (z >>> 33);
        return z;
    }
}
