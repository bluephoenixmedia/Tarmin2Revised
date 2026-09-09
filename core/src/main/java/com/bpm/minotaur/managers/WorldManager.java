package com.bpm.minotaur.managers;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.math.GridPoint2;
import com.badlogic.gdx.math.Vector2;
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
import com.bpm.minotaur.lighting.LightSource;
import com.bpm.minotaur.lighting.LightingManager;
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
    private final DayNightManager dayNightManager;
    private final LightingManager lightingManager;
    private final SpawnTableData spawnTableData;
    private final com.bpm.minotaur.gamedata.encounters.EncounterManager encounterManager;
    private final CookingManager cookingManager;

    private final SoundManager soundManager;

    private final Map<Integer, RetroTheme.Theme> levelThemes = new HashMap<>();
    private RetroTheme.Theme currentLevelTheme = RetroTheme.STANDARD_THEME;

    private final BiomeManager biomeManager;
    private final Map<Biome, IChunkGenerator> generators = new HashMap<>();
    private final Map<GridPoint2, Maze> loadedChunks = new HashMap<>();

    // --- NEW: Master Seed ---
    private long worldSeed;

    // --- NEW: Track where to place the return ladder ---
    private GridPoint2 pendingUpLadderPos = null;

    // Keep reference to player to check location for Audio dampening
    private Player playerReference;

    public WorldManager(GameMode gameMode, Difficulty difficulty, int initialLevel,
            MonsterDataManager dataManager,
            ItemDataManager itemDataManager,
            AssetManager assetManager,
            com.bpm.minotaur.gamedata.encounters.EncounterManager encounterManager,
            SpawnTableData spawnTableData,
            SoundManager soundManager) {
        this.gameMode = gameMode;
        this.difficulty = difficulty;
        this.currentLevel = initialLevel;
        DoomManager.getInstance().setCurrentLevel(initialLevel);
        this.json = new Json();
        this.json.setUsePrototypes(false);
        this.currentPlayerChunkId = new GridPoint2(0, 0);
        this.savingEnabled = true;

        // Initialize Seed
        this.worldSeed = new java.util.Random().nextLong();
        if (Gdx.app != null) {
            Gdx.app.log("WorldManager", "World Initialized with Seed: " + this.worldSeed);
        }

        this.biomeManager = new BiomeManager();
        this.dataManager = dataManager;
        this.itemDataManager = itemDataManager;
        this.assetManager = assetManager;
        this.encounterManager = encounterManager;
        this.spawnTableData = spawnTableData;
        this.soundManager = soundManager;
        this.cookingManager = new CookingManager();

        this.weatherManager = new WeatherManager(this);
        this.dayNightManager = new DayNightManager();
        this.lightingManager = new LightingManager();

        // Boot weather audio immediately: Heavy Storm raging outside, dampened inside shelter
        if (this.soundManager != null && this.weatherManager != null) {
            this.soundManager.updateWeatherAudio(this.weatherManager.getCurrentWeather(), this.weatherManager.getCurrentIntensity());
            this.soundManager.setDampenedImmediate(true);
        }

        MazeChunkGenerator mazeGen = new MazeChunkGenerator();
        ForestChunkGenerator forestGen = new ForestChunkGenerator();

        this.generators.put(Biome.MAZE, mazeGen);
        this.generators.put(Biome.FOREST, forestGen);
        this.generators.put(Biome.PLAINS, forestGen);
        this.currentLevelTheme = getThemeForLevel(initialLevel);
    }

    /**
     * Calculates a deterministic seed for a specific chunk/level combination.
     */
    public long getChunkSeed(int level, int x, int y) {
        long seed = worldSeed;
        seed = 31 * seed + level;
        seed = 31 * seed + x;
        seed = 31 * seed + y;
        return seed;
    }

    public void setPlayerReference(Player player) {
        this.playerReference = player;
    }

    public CookingManager getCookingManager() {
        return cookingManager;
    }

    public SoundManager getSoundManager() {
        return soundManager;
    }

    public Maze getInitialMaze() {
        Maze maze = loadChunk(new GridPoint2(0, 0));
        syncLightsForChunk(maze);
        return maze;
    }

    public void disableSaving() {
        this.savingEnabled = false;
        Gdx.app.log("WorldManager", "Saving has been disabled.");
    }

    public void enableSaving() {
        this.savingEnabled = true;
        Gdx.app.log("WorldManager", "Saving has been enabled.");
    }

    // --- NEW: Difficulty Persistence ---
    private int difficultyOffset = 0;

    /**
     * Calculates difficulty based on Depth + Horizontal Distance + Offset.
     * Moving 2 chunks away is roughly equivalent to descending 1 floor.
     */
    private int calculateEffectiveDifficulty(GridPoint2 chunkId, int depth) {
        int horizontalDistance = Math.abs(chunkId.x) + Math.abs(chunkId.y);
        int distancePenalty = (int) (horizontalDistance * 0.5f);
        return depth + distancePenalty + difficultyOffset;
    }

    public void resetWorldKeepDifficulty() {
        Gdx.app.log("WorldManager", "RESETTING WORLD - PRESERVING DIFFICULTY");

        // 1. Increase Difficulty Offset
        // If we are at Level 5, we want next run's Level 1 to feel like Level 6 (or
        // similar).
        // effectiveLevel = 1 + offset.
        // We want 1 + offset = currentLevel + 1?
        // Let's simply add currentLevel to offset.
        this.difficultyOffset += this.currentLevel;
        Gdx.app.log("WorldManager", "Difficulty Offset increased to: " + difficultyOffset);

        // 2. Clear Loaded State
        loadedChunks.clear();
        levelThemes.clear();
        currentPlayerChunkId.set(0, 0);
        this.currentLevel = 1;
        this.currentLevelTheme = getThemeForLevel(1);
        this.pendingUpLadderPos = null;

        // 3. Delete Chunk Save Files (Keep Discovery, Keep Player meta if stored
        // separately)
        FileHandle saveDir = Gdx.files.local(SAVE_DIRECTORY);
        if (saveDir.exists()) {
            for (FileHandle file : saveDir.list()) {
                if (file.name().startsWith("chunk_")) {
                    file.delete();
                }
            }
        }
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
        syncLightsForChunk(null);
        this.currentPlayerChunkId = new GridPoint2(0, 0);

        // Update Deepest Level Tracking
        UnlockManager.getInstance().updateDeepestLevel(this.currentLevel);

        Gdx.app.log("WorldManager",
                "Descending to Level " + currentLevel + ". Pending UP Ladder at " + pendingUpLadderPos);

        // --- BALANCE LOGGING ---
        BalanceLogger.getInstance().log("NAVIGATION", "Descending to Depth " + currentLevel);
        if (playerReference != null) {
            BalanceLogger.getInstance().logPlayerState(playerReference);
        }
    }

    // --- NEW: Ascent Logic ---
    public boolean ascendLevel() {
        if (currentLevel <= 1)
            return false;

        saveAllChunks();

        this.currentLevel--;
        this.currentLevelTheme = getThemeForLevel(currentLevel);
        this.pendingUpLadderPos = null; // No forced generation needed, we load previous state

        loadedChunks.clear();
        syncLightsForChunk(null);
        this.currentPlayerChunkId = new GridPoint2(0, 0);

        Gdx.app.log("WorldManager", "Ascending to Level " + currentLevel);
        return true;
    }

    private void saveAllChunks() {
        if (!savingEnabled || gameMode == GameMode.CLASSIC)
            return;
        for (Map.Entry<GridPoint2, Maze> entry : loadedChunks.entrySet()) {
            saveChunk(entry.getValue(), entry.getKey());
        }
    }

    public Maze loadChunk(GridPoint2 chunkId) {
        // --- Calculate Dynamic Difficulty ---
        int effectiveLevel = calculateEffectiveDifficulty(chunkId, currentLevel);

        if (gameMode == GameMode.CLASSIC) {
            Gdx.app.log("WorldManager", "CLASSIC mode: Generating new chunk.");
            long chunkSeed = getChunkSeed(effectiveLevel, chunkId.x, chunkId.y);
            return generators.get(Biome.MAZE).generateChunk(chunkId, effectiveLevel, effectiveLevel, difficulty,
                    gameMode,
                    RetroTheme.STANDARD_THEME, RetroTheme.STANDARD_THEME,
                    this.dataManager, this.itemDataManager, this.assetManager, this.encounterManager,
                    this.spawnTableData, chunkSeed,
                    playerReference != null ? playerReference.getLuck() : 0);
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

        // Pass currentLevel as layoutLevel (for visuals) and effectiveLevel for
        // difficulty (spawns)
        long chunkSeed = getChunkSeed(effectiveLevel, chunkId.x, chunkId.y);
        Maze newMaze = generator.generateChunk(chunkId, currentLevel, effectiveLevel, difficulty, gameMode,
                themeToGenerate, this.currentLevelTheme,
                this.dataManager, this.itemDataManager, this.assetManager, this.encounterManager, this.spawnTableData,
                chunkSeed,
                playerReference != null ? playerReference.getLuck() : 0);

        loadedChunks.put(chunkId, newMaze);
        saveChunk(newMaze, chunkId);

        Gdx.app.log("WorldManager", "Generated Chunk " + chunkId + " with Effective Difficulty: " + effectiveLevel);

        // --- BALANCE LOGGING ---
        BalanceLogger.getInstance().log("CHUNK_GEN",
                String.format("Generated Chunk %s (Biome: %s) | Eff. Difficulty: %d", chunkId.toString(), biome.name(),
                        effectiveLevel));

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

    public int getCurrentLevel() {
        return currentLevel;
    }

    public void setCurrentLevel(int level) {
        this.currentLevel = level;
        DoomManager.getInstance().setCurrentLevel(level);
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
        if (!savingEnabled || gameMode == GameMode.CLASSIC)
            return;
        saveChunk(maze, this.currentPlayerChunkId);
    }

    private void saveChunk(Maze maze, GridPoint2 chunkId) {
        if (!savingEnabled || gameMode == GameMode.CLASSIC)
            return;
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

    public DayNightManager getDayNightManager() {
        return dayNightManager;
    }

    public void update(float delta) {
        if (dayNightManager != null) {
            dayNightManager.update(delta);
        }

        Maze currentMaze = loadedChunks.get(currentPlayerChunkId);
        if (lightingManager != null) {
            if (currentMaze != null && (lightingManager.getWorldLights().size != currentMaze.getLights().size)) {
                syncLightsForChunk(currentMaze);
            }
            lightingManager.update(delta, playerReference, currentMaze);
        }

        if (currentLevel == 1) {
            weatherManager.update(delta);

            // Audio Dampening Check
            if (playerReference != null) {
                if (currentMaze != null) {
                    boolean isIndoors = currentMaze.isIndoors((int) playerReference.getPosition().x,
                            (int) playerReference.getPosition().y);
                    soundManager.setDampened(isIndoors);
                }
            }
        } else {
            soundManager.stopWeatherEffects();
        }

        if (soundManager != null) {
            soundManager.update(delta);
        }
    }

    /**
     * Decoupled exploration update: reveals ambient 3x3 tiles around the player
     * plus a forward line-of-sight cone up to 8 tiles (stopped by closed walls/doors or biome fog).
     * Works uniformly across all render engines (3D, Raycaster, etc.).
     */
    public void updateExploration(Player player, Maze maze) {
        if (player == null || maze == null) return;

        int px = (int) Math.floor(player.getPosition().x);
        int py = (int) Math.floor(player.getPosition().y);

        // 1. Ambient 3x3 reveal around player
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                int nx = px + dx;
                int ny = py + dy;
                if (nx >= 0 && nx < maze.getWidth() && ny >= 0 && ny < maze.getHeight()) {
                    maze.markVisited(nx, ny);
                }
            }
        }

        // 2. Directional sight cone
        GridPoint2 chunkId = getCurrentPlayerChunkId();
        Biome biome = (biomeManager != null && chunkId != null) ? biomeManager.getBiome(chunkId) : null;
        float maxDist = (biome != null && biome.hasFogOfWar()) ? Math.min(8.0f, biome.getFogDistance()) : 8.0f;

        Vector2 baseDir = player.getDirectionVector();
        Vector2 cameraPlane = player.getCameraPlane();

        float startX = player.getPosition().x;
        float startY = player.getPosition().y;

        int numRays = 30;
        for (int r = 0; r < numRays; r++) {
            float t = (numRays == 1) ? 0f : -1.0f + 2.0f * r / (numRays - 1);
            float rdx = baseDir.x + (cameraPlane != null ? cameraPlane.x * t : 0f);
            float rdy = baseDir.y + (cameraPlane != null ? cameraPlane.y * t : 0f);
            float len = (float) Math.sqrt(rdx * rdx + rdy * rdy);
            if (len > 0) {
                rdx /= len;
                rdy /= len;
            }

            float stepSize = 0.2f;
            int maxSteps = (int) (maxDist / stepSize);
            float curX = startX;
            float curY = startY;

            int prevTileX = px;
            int prevTileY = py;

            for (int s = 1; s <= maxSteps; s++) {
                curX += rdx * stepSize;
                curY += rdy * stepSize;

                int tileX = (int) Math.floor(curX);
                int tileY = (int) Math.floor(curY);

                if (tileX < 0 || tileX >= maze.getWidth() || tileY < 0 || tileY >= maze.getHeight()) {
                    break;
                }

                if (tileX != prevTileX || tileY != prevTileY) {
                    boolean blocked = false;
                    if (tileX != prevTileX) {
                        Direction dX = (tileX > prevTileX) ? Direction.EAST : Direction.WEST;
                        if (maze.isWallBlocking(prevTileX, prevTileY, dX) || maze.isWallBlocking(tileX, prevTileY, dX.getOpposite())) {
                            blocked = true;
                        }
                    }
                    if (!blocked && tileY != prevTileY) {
                        Direction dY = (tileY > prevTileY) ? Direction.NORTH : Direction.SOUTH;
                        if (maze.isWallBlocking(prevTileX, prevTileY, dY) || maze.isWallBlocking(prevTileX, tileY, dY.getOpposite())) {
                            blocked = true;
                        }
                    }

                    if (blocked) {
                        maze.markVisited(tileX, tileY);
                        break;
                    }

                    maze.markVisited(tileX, tileY);
                    prevTileX = tileX;
                    prevTileY = tileY;
                }
            }
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

    public Maze getLoadedChunk(GridPoint2 chunkId) {
        return loadedChunks.get(chunkId);
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
            if (newMaze == null)
                return;
        }
        this.currentPlayerChunkId = newChunkId;
        player.setMaze(newMaze);
        player.setPosition(newPlayerPos);
        setPlayerReference(player);
        syncLightsForChunk(newMaze);
    }

    public GridPoint2 getAdjacentChunkId(Direction direction) {
        GridPoint2 adj = new GridPoint2(this.currentPlayerChunkId);
        switch (direction) {
            case NORTH:
                adj.y += 1;
                break;
            case SOUTH:
                adj.y -= 1;
                break;
            case EAST:
                adj.x += 1;
                break;
            case WEST:
                adj.x -= 1;
                break;
        }
        return adj;
    }

    public void processTurn(Player player, int turnCount) {
        // No periodic spawning on Level 1 (Home Base)
        if (currentLevel <= 1)
            return;

        // Periodic Spawn Check (e.g., every 300 turns)
        if (turnCount > 0 && turnCount % 300 == 0) {
            SpawnManager sm = new SpawnManager(dataManager, itemDataManager, assetManager,
                    loadedChunks.get(currentPlayerChunkId), difficulty, currentLevel, player.getLevel(),
                    player.getLuck(), null, spawnTableData, System.nanoTime(), null); // null = no reachability filter for periodic respawns

            sm.spawnPeriodicMonster(player);
            Gdx.app.log("WorldManager", "Periodic Spawn Triggered at Turn " + turnCount);
        }
    }

    public void syncLightsForChunk(Maze maze) {
        if (lightingManager != null) {
            lightingManager.setWorldLights(maze != null ? maze.getLights() : null);
        }
    }

    public LightingManager getLightingManager() {
        return lightingManager;
    }
}
