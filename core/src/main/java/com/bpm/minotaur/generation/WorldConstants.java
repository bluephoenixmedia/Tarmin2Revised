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
