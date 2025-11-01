package com.bpm.minotaur.managers;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.math.GridPoint2;
import com.badlogic.gdx.utils.Json;
import com.bpm.minotaur.gamedata.ChunkData;
import com.bpm.minotaur.gamedata.Difficulty;
import com.bpm.minotaur.gamedata.GameMode;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.generation.ChunkGenerator;
import java.util.HashMap;
import java.util.Map;

/**
 * Manages the game world, including loading, saving, and generating
 * chunks (Maze objects) as the player moves.
 */
public class WorldManager {

    private final GameMode gameMode;
    private final Difficulty difficulty;
    private int currentLevel;
    private GridPoint2 currentPlayerChunkId;
    private final ChunkGenerator chunkGenerator;
    private final Json json; // <-- ADD THIS
    private static final String SAVE_DIRECTORY = "saves/world/"; // <-- ADD THIS

    // --- NEW: Map of all loaded chunks ---
    private final Map<GridPoint2, Maze> loadedChunks = new HashMap<>();

    public WorldManager(GameMode gameMode, Difficulty difficulty, int initialLevel) {
        this.gameMode = gameMode;
        this.difficulty = difficulty;
        this.currentLevel = initialLevel;
        this.chunkGenerator = new ChunkGenerator();
        this.currentPlayerChunkId = new GridPoint2(0, 0); // Start at origin chunk
        this.json = new Json(); // <-- ADD THIS
        this.json.setUsePrototypes(false);
    }

    /**
     * Gets the very first Maze for the game to start in.
     * @return The initial Maze object.
     */
    public Maze getInitialMaze() {
        return loadChunk(currentPlayerChunkId);
    }

    /**
     * Loads a chunk from cache, file, or generates it if it doesn't exist.
     * @param chunkId The (X,Y) coordinates of the chunk to load.
     * @return The loaded or generated Maze object.
     */
    public Maze loadChunk(GridPoint2 chunkId) {
        // --- 1. CLASSIC Mode: Just generate a new maze every time ---
        if (gameMode == GameMode.CLASSIC) {
            Gdx.app.log("WorldManager", "CLASSIC mode: Generating new chunk.");
            return chunkGenerator.generateChunk(chunkId, currentLevel, difficulty, gameMode);
        }

        // --- 2. ADVANCED Mode: Check cache first ---
        if (loadedChunks.containsKey(chunkId)) {
            Gdx.app.log("WorldManager", "Loading chunk from cache: " + chunkId);
            this.currentPlayerChunkId = chunkId;
            return loadedChunks.get(chunkId);
        }

        // --- 3. ADVANCED Mode: Check local file system ---
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

        // --- 4. ADVANCED Mode: Generate new chunk ---
        Gdx.app.log("WorldManager", "No save file for " + chunkId + ". Generating new chunk.");
        Maze newMaze = chunkGenerator.generateChunk(chunkId, currentLevel, difficulty, gameMode);
        this.currentPlayerChunkId = chunkId;

        // --- 5. ADVANCED Mode: Save and cache the new chunk ---
        loadedChunks.put(chunkId, newMaze); // Add to cache
        saveChunk(newMaze, chunkId); // Save to file

        return newMaze;
    }

    /**
     * NEW: Gets a chunk from the cache. Returns null if not loaded.
     * @param chunkId The (X,Y) coordinates of the chunk.
     * @return The Maze object, or null.
     */
    public Maze getChunk(GridPoint2 chunkId) {
        return loadedChunks.get(chunkId);
    }

    /**
     * NEW PRIVATE HELPER: Saves a specific maze to its corresponding JSON file.
     */
    private void saveChunk(Maze maze, GridPoint2 chunkId) {
        if (gameMode == GameMode.CLASSIC) {
            return; // Don't save in classic mode
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

    /**
     * NEW: Sets the active chunk ID for the player.
     * @param chunkId The new active chunk ID.
     */
    public void setCurrentChunk(GridPoint2 chunkId) {
        this.currentPlayerChunkId = chunkId;
    }

    /**
     * Saves the current state of a Maze (monster health, item positions, etc.)
     * to a file.
     * @param maze The Maze object to save.
     */
    public void saveCurrentChunk(Maze maze) {
        // --- STUB: File Saving Logic ---
        // 1. ChunkData data = new ChunkData(maze); // <-- (Needs new class + constructor)
        // 2. String json = new Json().prettyPrint(data);
        // 3. String fileName = "chunk_" + currentPlayerChunkId.x + "_" + currentPlayerChunkId.y + ".json";
        // 4. Gdx.files.local(fileName).writeString(json, false);
        // 5. Gdx.app.log("WorldManager", "Saved chunk state to " + fileName);

        Gdx.app.log("WorldManager", "saveCurrentChunk() called for " + currentPlayerChunkId + ". (Saving is stubbed)");
    }

    /**
     * Gets the player's starting position for the *initial* maze.
     * @return The (X,Y) grid coordinate.
     */
    public GridPoint2 getInitialPlayerStartPos() {
        return chunkGenerator.getInitialPlayerStartPos();
    }
}
