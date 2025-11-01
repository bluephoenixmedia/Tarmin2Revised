package com.bpm.minotaur.managers;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.math.GridPoint2;
import com.badlogic.gdx.utils.Json;
import com.bpm.minotaur.gamedata.ChunkData;
import com.bpm.minotaur.gamedata.Difficulty;
import com.bpm.minotaur.gamedata.GameMode;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.generation.Biome;
import com.bpm.minotaur.generation.ForestChunkGenerator;
import com.bpm.minotaur.generation.IChunkGenerator;
import com.bpm.minotaur.generation.MazeChunkGenerator;
import java.util.HashMap;
import java.util.Map;
import java.util.Set; // <-- ADD IMPORT

/**
 * Manages the game world, including loading, saving, and generating
 * chunks (Maze objects) as the player moves. It uses a BiomeManager
 * to decide *which* generator to use for a given chunk.
 */
public class WorldManager {

    private final GameMode gameMode;
    private final Difficulty difficulty;
    private int currentLevel;
    private GridPoint2 currentPlayerChunkId;
    private final Json json;
    private static final String SAVE_DIRECTORY = "saves/world/";

    // --- NEW: Biome and Generator Management ---
    private final BiomeManager biomeManager;
    private final Map<Biome, IChunkGenerator> generators = new HashMap<>();
    // --- END NEW ---

    private final Map<GridPoint2, Maze> loadedChunks = new HashMap<>();

    public WorldManager(GameMode gameMode, Difficulty difficulty, int initialLevel) {
        this.gameMode = gameMode;
        this.difficulty = difficulty;
        this.currentLevel = initialLevel;
        this.json = new Json();
        this.json.setUsePrototypes(false);
        this.currentPlayerChunkId = new GridPoint2(0, 0);

        // --- NEW: Initialize Biome Brain and Generators ---
        this.biomeManager = new BiomeManager();

        // Instantiate all our generators
        MazeChunkGenerator mazeGen = new MazeChunkGenerator();
        ForestChunkGenerator forestGen = new ForestChunkGenerator();
        // ... (add new generators here, e.g., new DesertChunkGenerator()) ...

        // Map biomes to their specific generators
        // This is the core of the strategy pattern
        this.generators.put(Biome.MAZE, mazeGen);
        this.generators.put(Biome.FOREST, forestGen);
        this.generators.put(Biome.PLAINS, forestGen); // For now, Plains will just be Forests
        // ... (map Biome.DESERT to desertGen, etc.) ...
        // --- END NEW ---
    }

    /**
     * Gets the very first Maze for the game to start in (always chunk 0,0).
     * @return The initial Maze object.
     */
    public Maze getInitialMaze() {
        return loadChunk(new GridPoint2(0, 0));
    }

    /**
     * Loads a chunk from cache, file, or generates it based on its biome.
     * @param chunkId The (X,Y) coordinates of the chunk to load.
     * @return The loaded or generated Maze object, or NULL if the biome is impassable.
     */
    public Maze loadChunk(GridPoint2 chunkId) {

        // --- 1. CLASSIC Mode: Always generate a maze ---
        if (gameMode == GameMode.CLASSIC) {
            Gdx.app.log("WorldManager", "CLASSIC mode: Generating new chunk.");
            // In classic mode, we only ever use the Maze generator
            return generators.get(Biome.MAZE).generateChunk(chunkId, currentLevel, difficulty, gameMode);
        }

        // --- 2. ADVANCED Mode: Check cache first ---
        if (loadedChunks.containsKey(chunkId)) {
            Gdx.app.log("WorldManager", "Loading chunk from cache: " + chunkId);
            this.currentPlayerChunkId = chunkId;
            return loadedChunks.get(chunkId);
        }

        // --- 3. ADVANCED Mode: Determine Biome ---
        Biome biome = biomeManager.getBiome(chunkId);
        Gdx.app.log("WorldManager", "Chunk " + chunkId + " determined to be Biome: " + biome.name());

        // --- 4. ADVANCED Mode: Handle impassable biomes ---
        if (biome == Biome.OCEAN || biome == Biome.MOUNTAINS) {
            Gdx.app.log("WorldManager", "Cannot enter impassable biome: " + biome.name());
            return null; // Return null to signal that this chunk cannot be entered
        }

        // --- 5. ADVANCED Mode: Check local file system ---
        FileHandle file = Gdx.files.local(SAVE_DIRECTORY + "chunk_" + chunkId.x + "_" + chunkId.y + ".json");
        if (file.exists()) {
            Gdx.app.log("WorldManager", "Loading chunk from file: " + file.path());
            try {
                ChunkData data = json.fromJson(ChunkData.class, file);
                Maze maze = data.buildMaze();
                loadedChunks.put(chunkId, maze); // Add to cache
                this.currentPlayerChunkId = chunkId;
                return maze;
            } catch (Exception e) {
                Gdx.app.error("WorldManager", "Failed to load/parse chunk: " + chunkId, e);
                // Fall through to generation if loading fails
            }
        }

        // --- 6. ADVANCED Mode: Generate new chunk based on biome ---

        // Find the correct generator for this biome
        IChunkGenerator generator = generators.get(biome);
        if (generator == null) {
            Gdx.app.error("WorldManager", "No generator found for Biome: " + biome.name() + ". Defaulting to FOREST.");
            generator = generators.get(Biome.FOREST); // Use Forest as a safe default
        }

        Gdx.app.log("WorldManager", "No save file for " + chunkId + ". Generating new chunk with " + generator.getClass().getSimpleName());
        Maze newMaze = generator.generateChunk(chunkId, currentLevel, difficulty, gameMode);

        // --- 7. ADVANCED Mode: Save and cache the new chunk ---
        loadedChunks.put(chunkId, newMaze); // Add to cache
        saveChunk(newMaze, chunkId); // Save to file

        this.currentPlayerChunkId = chunkId;
        return newMaze;
    }

    public Maze getChunk(GridPoint2 chunkId) {
        return loadedChunks.get(chunkId);
    }

    public void setCurrentChunk(GridPoint2 chunkId) {
        this.currentPlayerChunkId = chunkId;
    }

    public void saveCurrentChunk(Maze maze) {
        if (gameMode == GameMode.CLASSIC) {
            return; // Don't save in classic mode
        }
        // Save the specific maze given, using its chunk ID (which we assume is the current one)
        saveChunk(maze, this.currentPlayerChunkId);
    }

    private void saveChunk(Maze maze, GridPoint2 chunkId) {
        if (gameMode == GameMode.CLASSIC) {
            return;
        }
        try {
            ChunkData data = new ChunkData(maze);
            String jsonData = json.prettyPrint(data);
            String fileName = "chunk_" + chunkId.x + "_" + chunkId.y + ".json";
            FileHandle file = Gdx.files.local(SAVE_DIRECTORY + fileName);
            file.writeString(jsonData, false); // false = overwrite
            Gdx.app.log("WorldManager", "Saved chunk state to " + file.path());
        } catch (Exception e) {
            Gdx.app.error("WorldManager", "Failed to save chunk: " + chunkId, e);
        }
    }

    public GridPoint2 getInitialPlayerStartPos() {
        // The initial start position is *always* defined by the MazeChunkGenerator
        // We can safely assume the MAZE generator exists in our map.
        return generators.get(Biome.MAZE).getInitialPlayerStartPos();
    }

    /**
     * Gets the biome manager instance.
     * @return The BiomeManager.
     */
    public BiomeManager getBiomeManager() {
        return biomeManager;
    }

    public GridPoint2 getCurrentPlayerChunkId() {
        return currentPlayerChunkId;
    }

    public Set<GridPoint2> getLoadedChunkIds() {
        return loadedChunks.keySet();
    }
}
