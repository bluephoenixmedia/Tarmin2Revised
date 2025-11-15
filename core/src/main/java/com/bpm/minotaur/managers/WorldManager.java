package com.bpm.minotaur.managers;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.math.GridPoint2;
import com.badlogic.gdx.utils.Json;
import com.bpm.minotaur.gamedata.*;
import com.bpm.minotaur.gamedata.monster.MonsterDataManager;
import com.bpm.minotaur.gamedata.player.Player;
import com.bpm.minotaur.generation.Biome;
import com.bpm.minotaur.generation.ForestChunkGenerator;
import com.bpm.minotaur.generation.IChunkGenerator;
import com.bpm.minotaur.generation.MazeChunkGenerator;
import com.bpm.minotaur.rendering.RetroTheme;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Manages the game world, including loading, saving, and generating
 * chunks (Maze objects) as the player moves. It uses a BiomeManager
 * to decide *which* generator to use for a given chunk.
 */
public class WorldManager {

    private boolean savingEnabled = true; // <-- NEW FIELD: Add this
    private final GameMode gameMode;
    private final Difficulty difficulty;
    private int currentLevel; // <-- This is the field we need to update
    private GridPoint2 currentPlayerChunkId;
    private final Json json;
    private static final String SAVE_DIRECTORY = "saves/world/";

    private final MonsterDataManager dataManager;
    private final AssetManager assetManager;


    private final Map<Integer, RetroTheme.Theme> levelThemes = new HashMap<>();
    private RetroTheme.Theme currentLevelTheme = RetroTheme.STANDARD_THEME;

    // --- NEW: Biome and Generator Management ---
    private final BiomeManager biomeManager;
    private final Map<Biome, IChunkGenerator> generators = new HashMap<>();
    // --- END NEW ---

    private final Map<GridPoint2, Maze> loadedChunks = new HashMap<>();

    public WorldManager(GameMode gameMode, Difficulty difficulty, int initialLevel, MonsterDataManager dataManager, AssetManager assetManager) {
        this.gameMode = gameMode;
        this.difficulty = difficulty;
        this.currentLevel = initialLevel;
        this.json = new Json();
        this.json.setUsePrototypes(false);
        this.currentPlayerChunkId = new GridPoint2(0, 0);
        this.savingEnabled = true; // <-- NEW: Make sure saving is on by default
        // --- NEW: Initialize Biome Brain and Generators ---
        this.biomeManager = new BiomeManager();
        this.dataManager = dataManager;
        this.assetManager = assetManager;

        // Instantiate all our generators
        MazeChunkGenerator mazeGen = new MazeChunkGenerator();
        ForestChunkGenerator forestGen = new ForestChunkGenerator();
        // ... (add new generators here, e.g., new DesertChunkGenerator()) ...

        // Map biomes to their specific generators
        // This is the core of the strategy pattern
        this.generators.put(Biome.MAZE, mazeGen);
        this.generators.put(Biome.FOREST, forestGen);
        this.generators.put(Biome.PLAINS, forestGen);
        this.currentLevelTheme = getThemeForLevel(initialLevel);
        // For now, Plains will just be Forests
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
     * [NEW] Disables all save operations for the WorldManager.
     * This is called when the player dies to prevent the GameScreen
     * from saving chunks one last time before the GameOverScreen.
     */
    public void disableSaving() {
        this.savingEnabled = false;
        Gdx.app.log("WorldManager", "Saving has been disabled.");
    }

    /**
     * [NEW] Enables all save operations.
     * Called by the constructor or when starting a new game.
     */
    public void enableSaving() {
        this.savingEnabled = true;
        Gdx.app.log("WorldManager", "Saving has been enabled.");
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
            return generators.get(Biome.MAZE).generateChunk(chunkId, currentLevel, difficulty, gameMode, RetroTheme.STANDARD_THEME,
                this.dataManager, this.assetManager);        }

        // --- 2. ADVANCED Mode: Check cache first ---
        if (loadedChunks.containsKey(chunkId)) {
            Gdx.app.log("WorldManager", "Loading chunk from cache: " + chunkId);
            // Don't set currentPlayerChunkId here, this could be a pre-load
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
            String fileName = "chunk_L" + this.currentLevel + "_" + chunkId.x + "_" + chunkId.y + ".json";
            FileHandle file = Gdx.files.local(SAVE_DIRECTORY + fileName);        if (file.exists()) {
            Gdx.app.log("WorldManager", "Loading chunk from file: " + file.path());
            try {
                ChunkData data = json.fromJson(ChunkData.class, file);
                Maze maze = data.buildMaze(this.dataManager, this.assetManager);
                loadedChunks.put(chunkId, maze); // Add to cache
                // Don't set currentPlayerChunkId here
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
        RetroTheme.Theme themeToGenerate;
        switch (biome) {
            case MAZE:
                themeToGenerate = this.currentLevelTheme; // Use the persistent theme for this Z-level
                break;
            case FOREST:
            case PLAINS:
                themeToGenerate = RetroTheme.FOREST_THEME; // Forests always use the forest theme
                break;
            default:
                themeToGenerate = this.currentLevelTheme; // Default to level theme
                break;
        }

        Gdx.app.log("WorldManager", "No save file for " + chunkId + ". Generating new chunk with " + generator.getClass().getSimpleName());
        Maze newMaze = generator.generateChunk(chunkId, currentLevel, difficulty, gameMode, themeToGenerate,
            this.dataManager, this.assetManager);
        // --- 7. ADVANCED Mode: Save and cache the new chunk ---
        loadedChunks.put(chunkId, newMaze); // Add to cache
        saveChunk(newMaze, chunkId); // Save to file

        // Don't set currentPlayerChunkId here
        return newMaze;
    }

    public Maze getChunk(GridPoint2 chunkId) {
        return loadedChunks.get(chunkId);
    }

    public void setCurrentChunk(GridPoint2 chunkId) {
        this.currentPlayerChunkId = chunkId;
    }

    // --- [NEW METHOD TO ADD] ---
    /**
     * Sets the WorldManager's internal concept of the current Z-level (dungeon depth).
     * This is critical for generating new chunks at the correct level.
     * @param level The new level number (e.g., 2, 3, etc.)
     */
    public void setCurrentLevel(int level) {
        this.currentLevel = level;
        // --- [NEW] ---
        // Get the persistent theme for this new level
        this.currentLevelTheme = getThemeForLevel(level);

        // --- [END NEW] ---
        Gdx.app.log("WorldManager", "Set current level to: " + level);
    }
    // --- [END NEW METHOD] ---


    /**
     * [NEW] Gets the persistent theme for a given level.
     * If one does not exist, it generates and stores a new random theme.
     * @param level The Z-level to check.
     * @return The persistent RetroTheme.Theme for that level.
     */
    public RetroTheme.Theme getThemeForLevel(int level) {
        if (levelThemes.containsKey(level)) {
            return levelThemes.get(level);
        } else {
            RetroTheme.Theme newTheme = RetroTheme.getRandomTheme();
            levelThemes.put(level, newTheme);
            // [MODIFIED] This log now uses the theme name!
            Gdx.app.log("WorldManager", "Generated new theme '" + newTheme.name + "' for level " + level);
            return newTheme;
        }
    }

    public void saveCurrentChunk(Maze maze) {

        if (!savingEnabled || gameMode == GameMode.CLASSIC) {
            return; // Don't save if disabled or in classic mode
        }
        // Save the specific maze given, using its chunk ID (which we assume is the current one)
        saveChunk(maze, this.currentPlayerChunkId);
    }

    private void saveChunk(Maze maze, GridPoint2 chunkId) {
        if (!savingEnabled || gameMode == GameMode.CLASSIC) {
            return;
        }
        try {
            ChunkData data = new ChunkData(maze);
            String jsonData = json.prettyPrint(data);
            // [FIX] Filename must be level-specific
            String fileName = "chunk_L" + this.currentLevel + "_" + chunkId.x + "_" + chunkId.y + ".json";
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
     * [NEW] Clears the in-memory chunk cache.
     * This is necessary when changing Z-levels (dungeon levels),
     * as the 2D chunk coordinates (e.g., 0,0) are no longer valid
     * and a new chunk must be generated.
     */
    public void clearLoadedChunks() {
        loadedChunks.clear();
        Gdx.app.log("WorldManager", "Cleared all loaded chunks from cache.");
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

    // --- [NEW] METHODS FOR SEAMLESS TRANSITIONS ---

    /**
     * [NEW] Proactively loads a chunk if it's not already in the cache.
     * This is called by the PCL system in GameScreen.
     * @param chunkId The chunk to attempt to load.
     * @return The loaded chunk, or null if it's impassable.
     */
    public Maze requestLoadChunk(GridPoint2 chunkId) {
        // Check cache first. If it's already loaded, do nothing.
        if (loadedChunks.containsKey(chunkId)) {
            return loadedChunks.get(chunkId);
        }

        // Check for impassable biomes (we can't get this from Biome.java directly,
        // so we check the BiomeManager)
        Biome biome = biomeManager.getBiome(chunkId);
        if (biome == Biome.OCEAN || biome == Biome.MOUNTAINS) {
            Gdx.app.log("WorldManager", "PCL: Skipping impassable biome: " + biome.name());
            return null;
        }

        Gdx.app.log("WorldManager", "Proactively loading chunk: " + chunkId);
        // This method already handles loading from file or generating and caching.
        return loadChunk(chunkId);
    }

    /**
     * [NEW] Handles the player's transition between two seamless chunks.
     * @param player The player object.
     * @param newChunkId The target chunk ID.
     * @param newPlayerPos The player's new position within the target chunk.
     */
    public void transitionPlayerToChunk(Player player, GridPoint2 newChunkId, GridPoint2 newPlayerPos) {
        Maze newMaze = loadedChunks.get(newChunkId);

        if (newMaze == null) {
            Gdx.app.error("WorldManager", "CRITICAL: Player tried to transition to a chunk that was not loaded: " + newChunkId);
            newMaze = this.loadChunk(newChunkId);
            if (newMaze == null) {
                Gdx.app.error("WorldManager", "CRITICAL: Failed to load fallback chunk. Transition aborted.");
                return; // Transition failed
            }
        }

        // Update WorldManager's state
        this.currentPlayerChunkId = newChunkId;

        // Update Player's state
        player.setMaze(newMaze); // Update player's internal maze reference
        player.setPosition(newPlayerPos); // Set new grid position
    }

    /**
     * [NEW] Helper method to get the chunk ID adjacent to the current one.
     * @param direction The direction of interest.
     * @return The adjacent chunk ID.
     */
    public GridPoint2 getAdjacentChunkId(Direction direction) {
        GridPoint2 adj = new GridPoint2(this.currentPlayerChunkId);
        switch (direction) {
            case NORTH: adj.y += 1; break;
            case SOUTH: adj.y -= 1; break;
            case EAST:  adj.x += 1; break;
            case WEST:  adj.x -= 1; break;
        }
        return adj;
    }
}
