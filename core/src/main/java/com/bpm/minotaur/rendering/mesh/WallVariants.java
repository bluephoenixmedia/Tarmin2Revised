package com.bpm.minotaur.rendering.mesh;

/**
 * Chooses which wall texture a given wall face wears.
 *
 * <p>Every maze wall used to be the same green panel. A chunk now draws from one
 * of two palettes, and each individual face picks one of six variants within it,
 * so a corridor no longer reads as the same image repeated to the horizon.
 *
 * <p>The choice is derived entirely from the chunk seed and the face's own
 * coordinates, never rolled at render time. That matters for two reasons: the
 * walls must not shimmer as the player walks out of a chunk and back in, and
 * deriving rather than storing means <b>no save-format change at all</b>. An
 * older save gets varied walls the moment it loads.
 *
 * <p>Following the convention already used for spawn and decoration sub-streams
 * ({@code chunkSeed ^ 0xDEADBEEF12345678L} and friends), this XORs its own magic
 * constant so that changing wall art can never perturb layout or monster
 * placement.
 */
public final class WallVariants {

    /** Number of texture variants per palette, i.e. wall.png plus wall_2..wall_6. */
    public static final int VARIANT_COUNT = 6;

    // Wall faces. A tile has up to four, and each picks independently, so two
    // walls facing each other across a corridor differ.
    public static final int FACE_NORTH = 0;
    public static final int FACE_SOUTH = 1;
    public static final int FACE_WEST = 2;
    public static final int FACE_EAST = 3;

    /** Keeps this stream independent of layout, spawns and decoration. */
    private static final long WALL_STREAM = 0x5741_4C4C_5641_5231L;

    // Distinct odd multipliers per axis, matching the cluster-seed mixing at
    // WorldManager. Reusing one multiplier would make (x,y) and (y,x) collide.
    private static final long X_MIX = 73856093L;
    private static final long Y_MIX = 19349663L;
    private static final long FACE_MIX = 83492791L;

    private WallVariants() {
    }

    /** The two colour schemes a chunk can wear. */
    public enum Palette {
        GREEN(""),
        GREY("grey_");

        private final String filePrefix;

        Palette(String filePrefix) {
            this.filePrefix = filePrefix;
        }
    }

    /**
     * The palette for a whole chunk, so a chunk reads as one place rather than a
     * patchwork. Split evenly between the two.
     */
    public static Palette paletteFor(long chunkSeed) {
        return (mix(chunkSeed ^ WALL_STREAM) & 1L) == 0L ? Palette.GREEN : Palette.GREY;
    }

    /**
     * The variant index, 0 to {@link #VARIANT_COUNT}-1, for one face of one tile.
     */
    public static int variantFor(long chunkSeed, int tileX, int tileY, int face) {
        long h = chunkSeed ^ WALL_STREAM;
        h ^= tileX * X_MIX;
        h ^= tileY * Y_MIX;
        h ^= face * FACE_MIX;
        // floorMod, not %, because mix() is signed and % would bias toward 0.
        return (int) Math.floorMod(mix(h), (long) VARIANT_COUNT);
    }

    /**
     * The asset path for a palette and variant, e.g. GREEN/0 is {@code wall.png}
     * and GREY/2 is {@code grey_wall_3.png}. Variant 0 is the unsuffixed original.
     */
    public static String texturePath(Palette palette, int variant) {
        if (palette == null) palette = Palette.GREEN;
        int v = Math.floorMod(variant, VARIANT_COUNT);
        String suffix = (v == 0) ? "" : "_" + (v + 1);
        return "images/" + palette.filePrefix + "wall" + suffix + ".png";
    }

    /**
     * MurmurHash3's 64-bit finalizer. Both callers consume the low bits, and the
     * raw XOR-of-products has almost no avalanche there. Per-face variants
     * survive that well enough, but the palette does not: it comes off a single
     * bit, and without this the chunk seed's low bit made the map a perfect
     * green/grey checkerboard.
     */
    private static long mix(long z) {
        z ^= (z >>> 33);
        z *= 0xff51afd7ed558ccdL;
        z ^= (z >>> 33);
        z *= 0xc4ceb9fe1a85ec53L;
        z ^= (z >>> 33);
        return z;
    }
}
