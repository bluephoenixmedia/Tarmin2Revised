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
     * A value of 10 means the maze will be 20x20 (from -10 to +10).
     */
    public static final int CENTRAL_MAZE_RADIUS = 5;

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

    //lighting off
    public static final float TORCH_FULL_BRIGHTNESS_RADIUS = 1.0f;  // Distance where lighting is at 100%
    public static final float TORCH_FADE_START = 20.0f;              // Distance where dimming starts
    public static final float TORCH_FADE_END = 20.0f;               // Distance where it's completely dark
    public static final float TORCH_MIN_BRIGHTNESS = 1.00f;         // Minimum brightness (never completely black)

   // public static final float TORCH_FULL_BRIGHTNESS_RADIUS = 2.0f;  // Distance where lighting is at 100%
   // public static final float TORCH_FADE_START = 3.0f;              // Distance where dimming starts
   // public static final float TORCH_FADE_END = 9.0f;               // Distance where it's completely dark
   // public static final float TORCH_MIN_BRIGHTNESS = 0.15f;         // Minimum brightness (never completely black)


    // Add more thresholds here as needed (e.g., DESERT_THRESHOLD)
}
