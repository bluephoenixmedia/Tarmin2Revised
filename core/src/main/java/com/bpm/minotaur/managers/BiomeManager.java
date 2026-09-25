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
    private final FastNoiseLite humidityNoise;
    private final int mazeRadius;
    private final int forestRadius;

    public BiomeManager() {
        this.noise = new FastNoiseLite(WorldConstants.WORLD_SEED);
        this.noise.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        this.noise.SetFrequency(WorldConstants.BIOME_NOISE_FREQUENCY);

        this.humidityNoise = new FastNoiseLite(WorldConstants.WORLD_SEED + 1013);
        this.humidityNoise.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        this.humidityNoise.SetFrequency(WorldConstants.HUMIDITY_NOISE_FREQUENCY);

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
        // Get a noise value between -1.0 and 1.0 (elevation)
        float noiseValue = noise.GetNoise(chunkId.x, chunkId.y);

        if (noiseValue < WorldConstants.OCEAN_THRESHOLD) {
            return Biome.OCEAN;
        } else if (noiseValue > WorldConstants.MOUNTAIN_THRESHOLD) {
            return Biome.MOUNTAINS;
        } else {
            // Wilderness land biomes: determine via humidity noise
            float humidity = humidityNoise.GetNoise(chunkId.x, chunkId.y);
            if (humidity < WorldConstants.DESERT_HUMIDITY_THRESHOLD) {
                return Biome.DESERT;
            } else if (humidity > WorldConstants.LAKELANDS_HUMIDITY_THRESHOLD) {
                return Biome.LAKELANDS;
            } else {
                return Biome.FOREST;
            }
        }
    }
}
