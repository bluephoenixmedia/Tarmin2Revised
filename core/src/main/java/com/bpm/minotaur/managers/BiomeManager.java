package com.bpm.minotaur.managers;

import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.generation.Biome;
import com.bpm.minotaur.generation.FastNoiseLite;
import com.bpm.minotaur.generation.WorldConstants;

/**
 * This class uses procedural noise and static rules to determine
 * which biome should exist at any given chunk coordinate.
 */
public class BiomeManager {

    private final FastNoiseLite noise;
    private final int mazeRadius;
    private final int forestRadius;

    public BiomeManager() {
        this.noise = new FastNoiseLite(WorldConstants.WORLD_SEED);
        this.noise.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        this.noise.SetFrequency(WorldConstants.BIOME_NOISE_FREQUENCY);

        // Pre-calculate the total radius for the static zones
        this.mazeRadius = WorldConstants.CENTRAL_MAZE_RADIUS;
        this.forestRadius = this.mazeRadius + WorldConstants.FOREST_BORDER_SIZE;
    }

    /**
     * The primary public method. Gets the biome for a given chunk coordinate.
     * @param chunkId The (x, y) coordinate of the chunk.
     * @return The Biome enum for that location.
     */
    public Biome getBiome(GridPoint2 chunkId) {

        // Rule 1: Check for the central MAZE area
        // We use 'max' to check distance, creating a square boundary
        if (Math.max(Math.abs(chunkId.x), Math.abs(chunkId.y)) <= mazeRadius) {
            return Biome.MAZE;
        }

        // Rule 2: Check for the FOREST border around the maze
        if (Math.max(Math.abs(chunkId.x), Math.abs(chunkId.y)) <= forestRadius) {
            return Biome.FOREST;
        }

        // Rule 3: We are in the "Wilderness." Use noise to decide.
        // Get a noise value between -1.0 and 1.0
        float noiseValue = noise.GetNoise(chunkId.x, chunkId.y);

        // --- You can add more rules here for other noise maps (e.g., temperature, humidity) ---

        // Simple biome selection based on one noise map (elevation)
        if (noiseValue < WorldConstants.OCEAN_THRESHOLD) {
            return Biome.OCEAN;
        } else if (noiseValue > WorldConstants.MOUNTAIN_THRESHOLD) {
            return Biome.MOUNTAINS;
        } else {
            // Default to Plains/Forest for now
            // We can use another noise layer later to distinguish them
            return Biome.PLAINS;
        }

        // Example for more complex generation (e.g., Desert vs. Lakelands):
        // float humidity = anotherNoise.GetNoise(chunkId.x, chunkId.y);
        // if (noiseValue > 0 && humidity < -0.5) {
        //     return Biome.DESERT;
        // } else if (noiseValue > 0 && humidity > 0.5) {
        //     return Biome.LAKELANDS;
        // }
    }
}
