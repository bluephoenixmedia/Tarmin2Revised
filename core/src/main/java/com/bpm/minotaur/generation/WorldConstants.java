package com.bpm.minotaur.generation;

/**
 * A central, static class for holding all world generation constants.
 * This allows for easy tuning of the world's structure.
 */
public class WorldConstants {

    /**
     * The seed for the world's noise generator.
     * Changing this will create an entirely new world.
     */
    public static final int WORLD_SEED = 12345;

    /**
     * Defines the radius of the central Maze area, in chunks.
     * The boundary is square: a value of 10 means chunks -10..+10 on both axes,
     * i.e. 21x21 = 441 maze chunks.
     *
     * <p>This was temporarily 1 to shorten walks while testing, which left the
     * maze at nine chunks and made themed chunks (MAZE-biome only) effectively
     * unreachable. The shelter's biome portals are the intended answer to the
     * long overland walk, so the radius is back at its designed value.
     */
    public static final int CENTRAL_MAZE_RADIUS = 10;

    /**
     * Defines the thickness of the Forest biome that
     * surrounds the central maze, in chunks.
     */
    public static final int FOREST_BORDER_SIZE = 5;

    /**
     * Controls the "zoom level" of the noise generator.
     * Smaller values = larger continents.
     * Larger values = more chaotic, smaller biomes.
     */
    public static final float BIOME_NOISE_FREQUENCY = 0.02f;

    /**
     * Controls the "sea level."
     * Noise values below this will become OCEAN.
     * (Range: -1.0 to 1.0)
     */
    public static final float OCEAN_THRESHOLD = -0.3f;

    /**
     * Controls the "mountain level."
     * Noise values above this will become MOUNTAINS.
     * (Range: -1.0 to 1.0)
     */
    public static final float MOUNTAIN_THRESHOLD = 0.6f;

    /**
     * Noise frequency for the humidity noise layer determining wilderness biomes.
     */
    public static final float HUMIDITY_NOISE_FREQUENCY = 0.08f;

    /**
     * Humidity threshold for arid biomes. Values below this produce DESERT.
     */
    public static final float DESERT_HUMIDITY_THRESHOLD = -0.30f;

    /**
     * Humidity threshold for wet biomes. Values above this produce LAKELANDS.
     */
    public static final float LAKELANDS_HUMIDITY_THRESHOLD = 0.30f;

    // --- World-gen versions -------------------------------------------------
    // A save records which algorithm laid out its world, so a world never
    // meets chunks placed by a different rule. A save without the field is
    // legacy. See docs/DEsign/Requirements_ Procedural World & The Blighted Marches.md.

    /** Fixed seed 12345, forest ring, Tundra band at y >= 16, humidity picks the rest. */
    public static final int WORLD_GEN_LEGACY = 1;
    /** Per-world seed, distance bands that follow the portal ladder, and a Castle Tarmin site. */
    public static final int WORLD_GEN_BANDS = 2;
    /** Version 2 plus the four shelter roads. See docs/DEsign/Requirements_ Shelter Roads.md. */
    public static final int WORLD_GEN_ROADS = 3;
    public static final int WORLD_GEN_CURRENT = WORLD_GEN_ROADS;

    // --- Distance bands (version 2) -----------------------------------------
    // Measured in chunks beyond the maze edge (Chebyshev), after warping. The
    // order follows the portal ladder, so walking outward meets the biomes in
    // the order the gates are priced. Overlaps are where the selector noise
    // chooses; they are what keeps band edges from reading as rings.

    public static final int FOREST_BAND_MAX = 10;
    public static final int DESERT_BAND_MIN = 6;
    public static final int DESERT_BAND_MAX = 18;
    public static final int LAKELANDS_BAND_MIN = 12;
    public static final int LAKELANDS_BAND_MAX = 24;
    public static final int TUNDRA_BAND_MIN = 18;
    public static final int TUNDRA_BAND_MAX = 32;
    public static final int BLIGHT_BAND_MIN = 28;

    /** How far, in chunks, the warp noise may push a chunk across band edges. */
    public static final float BAND_WARP_CHUNKS = 4.0f;
    public static final float BAND_WARP_FREQUENCY = 0.035f;
    /** Picks between the biomes eligible at one distance; low so regions stay contiguous. */
    public static final float BAND_SELECTOR_FREQUENCY = 0.05f;

    /** Within this many chunks of the maze edge there is no ocean or mountain to block the way out. */
    public static final int CLEAR_GROUND_RADIUS = 6;

    // --- Castle Tarmin site (version 2) -------------------------------------

    /** Euclidean distance of the castle from the origin, in chunks. */
    public static final int CASTLE_MIN_DISTANCE = 40;
    public static final int CASTLE_MAX_DISTANCE = 60;
    /** Every chunk within roughly this radius of the castle is Blight, whatever its band. */
    public static final float CASTLE_BLIGHT_RADIUS = 8.0f;
    /** Half-width of the land corridor from the maze to the castle; >= 1 always holds a 4-connected path. */
    public static final float CASTLE_CORRIDOR_HALF_WIDTH = 1.0f;

    // --- Shelter roads (version 3) ------------------------------------------
    // Four roads leave the maze about 90 degrees apart, the first on the castle's
    // bearing. Each seal road ends at a seal site; the seed decides which road
    // gets which distance. See docs/DEsign/Requirements_ Shelter Roads.md.

    /** Euclidean distances of the three seal sites from the origin, in chunks. Keep each above 15 (outside the maze). */
    public static final int[] SEAL_SITE_DISTANCES = { 20, 30, 40 };
    /** How far, in degrees, a seal road may lean off its quarter of the compass. */
    public static final float ROAD_BEARING_JITTER_DEGREES = 7f;
    /** The first shelter on a road stands this many chunks past the maze edge. */
    public static final int ROAD_FIRST_SHELTER_OFFSET = 3;
    /** Chunks between consecutive shelters on a road. Must stay below BEACON_RANGE_CHUNKS. */
    public static final int ROAD_SHELTER_SPACING_MIN = 8;
    public static final int ROAD_SHELTER_SPACING_MAX = 10;
    /** No road shelter within this many chunks of the road's end. */
    public static final int ROAD_END_CLEARANCE = 3;
    /** Off-road shelters: one candidate per cell of this many chunks a side (about 1 per 150 chunks). */
    public static final int OFF_ROAD_CELL = 12;
    /** Minimum distance, in chunks, between any two shelters, sites or the castle. */
    public static final int SHELTER_MIN_SPACING = 8;
    /** Off-road shelters keep at least this far from any road line, so they never read as road stops. */
    public static final float OFF_ROAD_CLEARANCE = 3f;
    /** Beacons show within this many chunks. */
    public static final float BEACON_RANGE_CHUNKS = 20f;

    // Authentic Dynamic Dungeon Lighting Constants
    public static final float TORCH_FULL_BRIGHTNESS_RADIUS = 1.8f; // Distance where lighting is at 100%
    public static final float TORCH_FADE_START = 3.5f;             // Distance where torch begins fading
    public static final float TORCH_FADE_END = 5.5f;               // Outer bound of torch light pool
    public static final float TORCH_MIN_BRIGHTNESS = 0.04f;        // Pitch darkness ambient void floor

    // public static final float TORCH_FULL_BRIGHTNESS_RADIUS = 2.0f; // Distance
    // where lighting is at 100%
    // public static final float TORCH_FADE_START = 3.0f; // Distance where dimming
    // starts
    // public static final float TORCH_FADE_END = 9.0f; // Distance where it's
    // completely dark
    // public static final float TORCH_MIN_BRIGHTNESS = 0.15f; // Minimum brightness
    // (never completely black)

    // Add more thresholds here as needed (e.g., DESERT_THRESHOLD)
}
