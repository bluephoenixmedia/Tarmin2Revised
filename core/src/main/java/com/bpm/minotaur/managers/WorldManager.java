package com.bpm.minotaur.managers;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.math.GridPoint2;
import com.badlogic.gdx.utils.Json;
import com.bpm.minotaur.gamedata.*;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.ItemDataManager;
import com.bpm.minotaur.gamedata.monster.MonsterDataManager;
import com.bpm.minotaur.gamedata.player.Player;
import com.bpm.minotaur.gamedata.spawntables.SpawnTableData;
import com.bpm.minotaur.generation.Biome;
import com.bpm.minotaur.generation.ForestChunkGenerator;
import com.bpm.minotaur.generation.IChunkGenerator;
import com.bpm.minotaur.generation.MazeChunkGenerator;
import com.bpm.minotaur.rendering.RetroTheme;
import com.bpm.minotaur.weather.WeatherManager;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class WorldManager {

    private boolean savingEnabled = true;
    private final GameMode gameMode;
    private final Difficulty difficulty;
    private int currentLevel;
    private GridPoint2 currentPlayerChunkId;
    private final Json json;
    private static final String SAVE_DIRECTORY = "saves/world/";

    private final MonsterDataManager dataManager;
    private final ItemDataManager itemDataManager;
    private final AssetManager assetManager;

    private final WeatherManager weatherManager;
    private final SpawnTableData spawnTableData;

    private final SoundManager soundManager;

    private final Map<Integer, RetroTheme.Theme> levelThemes = new HashMap<>();
    private RetroTheme.Theme currentLevelTheme = RetroTheme.STANDARD_THEME;

    private final BiomeManager biomeManager;
    private final Map<Biome, IChunkGenerator> generators = new HashMap<>();
    private final Map<GridPoint2, Maze> loadedChunks = new HashMap<>();

    // --- NEW: Track where to place the return ladder ---
    private GridPoint2 pendingUpLadderPos = null;

    // Keep reference to player to check location for Audio dampening
    private Player playerReference;

    public WorldManager(GameMode gameMode, Difficulty difficulty, int initialLevel,
                        MonsterDataManager dataManager,
                        ItemDataManager itemDataManager,
                        AssetManager assetManager,
                        SpawnTableData spawnTableData,
                        SoundManager soundManager) {
        this.gameMode = gameMode;
        this.difficulty = difficulty;
        this.currentLevel = initialLevel;
        this.json = new Json();
        this.json.setUsePrototypes(false);
        this.currentPlayerChunkId = new GridPoint2(0, 0);
        this.savingEnabled = true;

        this.biomeManager = new BiomeManager();
        this.dataManager = dataManager;
        this.itemDataManager = itemDataManager;
        this.assetManager = assetManager;
        this.spawnTableData = spawnTableData;
        this.soundManager = soundManager;

        this.weatherManager = new WeatherManager(this);

        MazeChunkGenerator mazeGen = new MazeChunkGenerator();
        ForestChunkGenerator forestGen = new ForestChunkGenerator();

        this.generators.put(Biome.MAZE, mazeGen);
        this.generators.put(Biome.FOREST, forestGen);
        this.generators.put(Biome.PLAINS, forestGen);
        this.currentLevelTheme = getThemeForLevel(initialLevel);
    }

    public void setPlayerReference(Player player) {
        this.playerReference = player;
    }

    public SoundManager getSoundManager() {
        return soundManager;
    }

    public Maze getInitialMaze() {
        return loadChunk(new GridPoint2(0, 0));
    }

    public void disableSaving() {
        this.savingEnabled = false;
        Gdx.app.log("WorldManager", "Saving has been disabled.");
    }

    public void enableSaving() {
        this.savingEnabled = true;
        Gdx.app.log("WorldManager", "Saving has been enabled.");
    }

    /**
     * Calculates difficulty based on Depth + Horizontal Distance.
     * Moving 2 chunks away is roughly equivalent to descending 1 floor.
     */
    private int calculateEffectiveDifficulty(GridPoint2 chunkId, int depth) {
        int horizontalDistance = Math.abs(chunkId.x) + Math.abs(chunkId.y);
        int distancePenalty = (int) (horizontalDistance * 0.5f);
        return depth + distancePenalty;
    }

    // --- NEW: Descent Logic ---
    public void descendLevel(GridPoint2 playerPos) {
        // Save current level state before leaving
        saveAllChunks();

        this.currentLevel++;
        this.currentLevelTheme = getThemeForLevel(currentLevel);

        // We want an UP ladder at this specific position on the next floor
        this.pendingUpLadderPos = new GridPoint2(playerPos.x, playerPos.y);

        // Clear cache so we don't see old level chunks
        loadedChunks.clear();
        this.currentPlayerChunkId = new GridPoint2(0, 0);

        // Update Deepest Level Tracking
        UnlockManager.getInstance().updateDeepestLevel(this.currentLevel);

        Gdx.app.log("WorldManager", "Descending to Level " + currentLevel + ". Pending UP Ladder at " + pendingUpLadderPos);

        // --- BALANCE LOGGING ---
        BalanceLogger.getInstance().log("NAVIGATION", "Descending to Depth " + currentLevel);
        if (playerReference != null) {
            BalanceLogger.getInstance().logPlayerState(playerReference);
        }
    }

    // --- NEW: Ascent Logic ---
    public boolean ascendLevel() {
        if (currentLevel <= 1) return false;

        saveAllChunks();

        this.currentLevel--;
        this.currentLevelTheme = getThemeForLevel(currentLevel);
        this.pendingUpLadderPos = null; // No forced generation needed, we load previous state

        loadedChunks.clear();
        this.currentPlayerChunkId = new GridPoint2(0, 0);

        Gdx.app.log("WorldManager", "Ascending to Level " + currentLevel);
        return true;
    }

    private void saveAllChunks() {
        if (!savingEnabled || gameMode == GameMode.CLASSIC) return;
        for(Map.Entry<GridPoint2, Maze> entry : loadedChunks.entrySet()) {
            saveChunk(entry.getValue(), entry.getKey());
        }
    }

    public Maze loadChunk(GridPoint2 chunkId) {
        // --- Calculate Dynamic Difficulty ---
        int effectiveLevel = calculateEffectiveDifficulty(chunkId, currentLevel);

        if (gameMode == GameMode.CLASSIC) {
            Gdx.app.log("WorldManager", "CLASSIC mode: Generating new chunk.");
            return generators.get(Biome.MAZE).generateChunk(chunkId, effectiveLevel, difficulty, gameMode, RetroTheme.STANDARD_THEME,
                this.dataManager, this.itemDataManager, this.assetManager, this.spawnTableData);
        }

        if (loadedChunks.containsKey(chunkId)) {
            Gdx.app.log("WorldManager", "Loading chunk from cache: " + chunkId);
            return loadedChunks.get(chunkId);
        }

        Biome biome = biomeManager.getBiome(chunkId);

        if (biome == Biome.OCEAN || biome == Biome.MOUNTAINS) {
            return null;
        }

        String fileName = "chunk_L" + this.currentLevel + "_" + chunkId.x + "_" + chunkId.y + ".json";
        FileHandle file = Gdx.files.local(SAVE_DIRECTORY + fileName);
        if (file.exists()) {
            try {
                ChunkData data = json.fromJson(ChunkData.class, file);
                Maze maze = data.buildMaze(this.dataManager, this.itemDataManager, this.assetManager);
                loadedChunks.put(chunkId, maze);
                return maze;
            } catch (Exception e) {
                Gdx.app.error("WorldManager", "Failed to load/parse chunk: " + chunkId, e);
            }
        }

        IChunkGenerator generator = generators.get(biome);
        if (generator == null) {
            generator = generators.get(Biome.FOREST);
        }

        // --- NEW: Inject Forced Ladder Pos if applicable ---
        if (generator instanceof MazeChunkGenerator && pendingUpLadderPos != null) {
            // Only force it for the initial chunk (0,0) where player enters
            if (chunkId.x == 0 && chunkId.y == 0) {
                ((MazeChunkGenerator) generator).setForcedUpLadderPos(pendingUpLadderPos);
                pendingUpLadderPos = null; // Consume the request
            }
        }
        // ---------------------------------------------------

        RetroTheme.Theme themeToGenerate;
        switch (biome) {
            case MAZE:
                themeToGenerate = this.currentLevelTheme;
                break;
            case FOREST:
            case PLAINS:
                themeToGenerate = RetroTheme.FOREST_THEME;
                break;
            default:
                themeToGenerate = this.currentLevelTheme;
                break;
        }

        // Pass effectiveLevel instead of currentLevel
        Maze newMaze = generator.generateChunk(chunkId, effectiveLevel, difficulty, gameMode, themeToGenerate,
            this.dataManager, this.itemDataManager, this.assetManager, this.spawnTableData);

        loadedChunks.put(chunkId, newMaze);
        saveChunk(newMaze, chunkId);

        Gdx.app.log("WorldManager", "Generated Chunk " + chunkId + " with Effective Difficulty: " + effectiveLevel);

        // --- BALANCE LOGGING ---
        BalanceLogger.getInstance().log("CHUNK_GEN",
            String.format("Generated Chunk %s (Biome: %s) | Eff. Difficulty: %d", chunkId.toString(), biome.name(), effectiveLevel));

        // Log all items spawned in this chunk to analyze distribution
        for (Item item : newMaze.getItems().values()) {
            BalanceLogger.getInstance().logItemSpawn(item, effectiveLevel);
        }
        // -----------------------

        return newMaze;
    }

    public Maze getChunk(GridPoint2 chunkId) {
        return loadedChunks.get(chunkId);
    }

    public void setCurrentChunk(GridPoint2 chunkId) {
        this.currentPlayerChunkId = chunkId;
    }

    public int getCurrentLevel() { return currentLevel; }

    public void setCurrentLevel(int level) {
        this.currentLevel = level;
        UnlockManager.getInstance().updateDeepestLevel(level);
        this.currentLevelTheme = getThemeForLevel(level);
        Gdx.app.log("WorldManager", "Set current level to: " + level);
    }

    public RetroTheme.Theme getThemeForLevel(int level) {
        if (levelThemes.containsKey(level)) {
            return levelThemes.get(level);
        } else {
            RetroTheme.Theme newTheme = RetroTheme.getRandomTheme();
            levelThemes.put(level, newTheme);
            return newTheme;
        }
    }

    public void saveCurrentChunk(Maze maze) {
        if (!savingEnabled || gameMode == GameMode.CLASSIC) return;
        saveChunk(maze, this.currentPlayerChunkId);
    }

    private void saveChunk(Maze maze, GridPoint2 chunkId) {
        if (!savingEnabled || gameMode == GameMode.CLASSIC) return;
        try {
            ChunkData data = new ChunkData(maze);
            String jsonData = json.prettyPrint(data);
            String fileName = "chunk_L" + this.currentLevel + "_" + chunkId.x + "_" + chunkId.y + ".json";
            FileHandle file = Gdx.files.local(SAVE_DIRECTORY + fileName);
            file.writeString(jsonData, false);
            Gdx.app.log("WorldManager", "Saved chunk state to " + file.path());
        } catch (Exception e) {
            Gdx.app.error("WorldManager", "Failed to save chunk: " + chunkId, e);
        }
    }

    public WeatherManager getWeatherManager() {
        return weatherManager;
    }

    public void update(float delta) {
        if (currentLevel == 1) {
            weatherManager.update(delta);

            // Audio Dampening Check
            if (playerReference != null) {
                Maze currentMaze = loadedChunks.get(currentPlayerChunkId);
                if (currentMaze != null) {
                    boolean insideHome = currentMaze.isHomeTile((int)playerReference.getPosition().x, (int)playerReference.getPosition().y);
                    soundManager.setDampened(insideHome);
                }
            }
        } else {
            soundManager.stopWeatherEffects();
        }
    }

    public GridPoint2 getInitialPlayerStartPos() {
        return generators.get(Biome.MAZE).getInitialPlayerStartPos();
    }

    public void clearLoadedChunks() {
        loadedChunks.clear();
    }

    public BiomeManager getBiomeManager() {
        return biomeManager;
    }

    public GridPoint2 getCurrentPlayerChunkId() {
        return currentPlayerChunkId;
    }

    public Set<GridPoint2> getLoadedChunkIds() {
        return loadedChunks.keySet();
    }

    public Maze requestLoadChunk(GridPoint2 chunkId) {
        if (loadedChunks.containsKey(chunkId)) {
            return loadedChunks.get(chunkId);
        }
        Biome biome = biomeManager.getBiome(chunkId);
        if (biome == Biome.OCEAN || biome == Biome.MOUNTAINS) {
            return null;
        }
        return loadChunk(chunkId);
    }

    public void transitionPlayerToChunk(Player player, GridPoint2 newChunkId, GridPoint2 newPlayerPos) {
        Maze newMaze = loadedChunks.get(newChunkId);
        if (newMaze == null) {
            newMaze = this.loadChunk(newChunkId);
            if (newMaze == null) return;
        }
        this.currentPlayerChunkId = newChunkId;
        player.setMaze(newMaze);
        player.setPosition(newPlayerPos);
    }

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
