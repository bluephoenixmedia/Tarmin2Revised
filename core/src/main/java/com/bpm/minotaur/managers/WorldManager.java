package com.bpm.minotaur.managers;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.math.GridPoint2;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Json;
import com.bpm.minotaur.gamedata.*;
import com.bpm.minotaur.gamedata.gore.GoreManager;
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
import com.bpm.minotaur.gamedata.monster.FactionMatrix;
import com.bpm.minotaur.gamedata.bones.BonesData;
import com.bpm.minotaur.managers.BonesManager;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WorldManager {

    private boolean savingEnabled = true;
    private final GameMode gameMode;
    private final Difficulty difficulty;
    private int currentLevel;
    private GridPoint2 currentPlayerChunkId;
    private final Json json;

    private String getChunkSaveDir() {
        return SaveManager.getInstance().getActiveChunkSaveDirectory();
    }

    private static String getChunkFileName(int level, int x, int y) {
        return "chunk_L" + level + "_" + x + "_" + y + ".json";
    }

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
    private final GoreManager goreManager;

    // --- NEW: Master Seed ---
    private long worldSeed;
    private FactionMatrix factionMatrix;

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

        // Initialize Global Gore Simulation
        this.goreManager = new GoreManager();
        if (assetManager != null) {
            String goreAtlasPath = "packed/gore.atlas";
            if (!assetManager.isLoaded(goreAtlasPath)) {
                assetManager.load(goreAtlasPath, TextureAtlas.class);
                assetManager.finishLoading();
            }
            if (assetManager.isLoaded(goreAtlasPath)) {
                TextureAtlas goreAtlas = assetManager.get(goreAtlasPath, TextureAtlas.class);
                this.goreManager.setTextures(goreAtlas);
                com.bpm.minotaur.gamedata.gore.WoundDecalRegistry.getInstance().loadFromAtlas(goreAtlas);
            }
        }

        // Initialize Seed
        this.worldSeed = new java.util.Random().nextLong();
        this.factionMatrix = new FactionMatrix(this.worldSeed);
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
        if (gameMode == GameMode.ADVANCED && BonesManager.getInstance().getActiveFloorBones() == null) {
            BonesManager.getInstance().rollBonesForFloor(currentLevel, gameMode);
        }
        Maze maze = loadChunk(currentPlayerChunkId);
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
     * Calculates difficulty based on NetHack EL & EDL formulas:
     * EL = depth + floor(D_xy / 2)
     * EDL = floor((EL + playerLevel) / 2)
     * Sanctuary Buffer: Any chunk within D_xy <= 1 on Z = 1 capped at EDL = 2
     */
    public int calculateEffectiveDifficulty(GridPoint2 chunkId, int depth) {
        int playerLevel = (playerReference != null) ? playerReference.getLevel() : 1;
        return calculateEffectiveDifficulty(chunkId, depth, playerLevel);
    }

    public int calculateEffectiveDifficulty(GridPoint2 chunkId, int depth, int playerLevel) {
        int horizontalDistance = (chunkId != null) ? (Math.abs(chunkId.x) + Math.abs(chunkId.y)) : 0;
        int el = depth + (horizontalDistance / 2);
        int edl = (el + playerLevel) / 2;

        // Sanctuary Buffer: Any chunk within D_xy <= 1 on Z = 1 capped at EDL = 2
        if (chunkId != null && Math.abs(chunkId.x) <= 1 && Math.abs(chunkId.y) <= 1 && depth <= 1) {
            edl = Math.min(edl, 2);
        }

        return Math.max(1, edl + difficultyOffset);
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
        FileHandle saveDir = Gdx.files.local(getChunkSaveDir());
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
        // Retain currentPlayerChunkId so player stays in the same coordinate column

        // Update Deepest Level Tracking
        UnlockManager.getInstance().updateDeepestLevel(this.currentLevel);

        // Rearm Shelter Altar commune on venturing into deeper strata
        com.bpm.minotaur.gamedata.progression.ShelterAltar.getInstance().rearmCommune();

        Gdx.app.log("WorldManager",
                "Descending to Level " + currentLevel + " at chunk " + currentPlayerChunkId + ". Pending UP Ladder at " + pendingUpLadderPos);

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
        // Retain currentPlayerChunkId so player returns to the same coordinate column

        Gdx.app.log("WorldManager", "Ascending to Level " + currentLevel + " at chunk " + currentPlayerChunkId);
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

        Biome biome;
        if (this.currentLevel > 1) {
            biome = Biome.MAZE;
        } else {
            biome = biomeManager.getBiome(chunkId);
            if (biome == Biome.OCEAN || biome == Biome.MOUNTAINS) {
                return null;
            }
        }

        String fileName = getChunkFileName(this.currentLevel, chunkId.x, chunkId.y);
        FileHandle file = Gdx.files.local(getChunkSaveDir() + fileName);
        if (file.exists()) {
            try {
                ChunkData data = json.fromJson(ChunkData.class, file);
                if (data.level != this.currentLevel) {
                    Gdx.app.error("WorldManager", "Chunk file " + fileName + " has level " + data.level
                            + " but current level is " + this.currentLevel + "! Discarding corrupted chunk.");
                    file.delete();
                } else if (this.currentLevel == 1 && chunkId.x == 0 && chunkId.y == 0
                        && (data.homeTiles == null || data.homeTiles.isEmpty())) {
                    Gdx.app.error("WorldManager", "Chunk file " + fileName
                            + " is missing Home Shelter zone! Discarding and regenerating starting shelter.");
                    file.delete();
                } else {
                    Maze maze = data.buildMaze(this.dataManager, this.itemDataManager, this.assetManager);
                    maze.setGoreManager(this.goreManager);
                    if (this.goreManager != null) {
                        this.goreManager.importChunkGore(chunkId, data);
                    }
                    // Ensure paired UP ladder exists if player is descending into previously visited chunk
                    if (pendingUpLadderPos != null && chunkId.equals(currentPlayerChunkId)) {
                        if (!maze.getLadders().containsKey(pendingUpLadderPos)) {
                            maze.addLadder(new Ladder(pendingUpLadderPos.x, pendingUpLadderPos.y, Ladder.LadderType.UP, Ladder.EntranceStyle.ROPE));
                        }
                        pendingUpLadderPos = null;
                    }
                    loadedChunks.put(chunkId, maze);
                    return maze;
                }
            } catch (Exception e) {
                Gdx.app.error("WorldManager", "Failed to load/parse chunk: " + chunkId, e);
            }
        }

        IChunkGenerator generator = generators.get(biome);
        if (generator == null) {
            generator = generators.get(Biome.FOREST);
        }

        // --- NEW: Inject Forced Ladder Pos if applicable ---
        if (pendingUpLadderPos != null && chunkId.equals(currentPlayerChunkId)) {
            if (generator instanceof MazeChunkGenerator) {
                ((MazeChunkGenerator) generator).setForcedUpLadderPos(pendingUpLadderPos);
                pendingUpLadderPos = null; // Consume the request
            } else if (generator instanceof ForestChunkGenerator) {
                ((ForestChunkGenerator) generator).setForcedUpLadderPos(pendingUpLadderPos);
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

        newMaze.setGoreManager(this.goreManager);
        newMaze.setChunkId(chunkId);

        // Themed Chunk Decoration
        com.bpm.minotaur.generation.theme.ChunkTheme theme = getChunkTheme(chunkId, currentLevel);
        if (theme != null) {
            newMaze.setChunkTheme(theme);
            com.bpm.minotaur.generation.theme.ChunkThemeDecorator.decorate(
                    newMaze, theme, chunkSeed, this.dataManager, this.itemDataManager, this.assetManager);
        }

        // Stamp target chunk themes on boundary gates so glowing runes reveal the theme ahead
        for (Gate gate : newMaze.getGates().values()) {
            if (gate.getTargetChunkId() != null) {
                com.bpm.minotaur.generation.theme.ChunkTheme targetTheme = getChunkTheme(gate.getTargetChunkId(), currentLevel);
                if (targetTheme != null) {
                    gate.setTheme(targetTheme);
                }
            }
        }

        // Place Decomposing Corpse if an active bones encounter was rolled for this floor
        // Never spawn in starting shelter sanctuary chunk (0, 0) on Level 1
        if (gameMode == GameMode.ADVANCED) {
            BonesData activeBones = BonesManager.getInstance().getActiveFloorBones();
            boolean isStartingShelterChunk = (currentLevel == 1 && chunkId.x == 0 && chunkId.y == 0);
            if (activeBones != null && activeBones.chunkX == -1 && !isStartingShelterChunk) {
                GridPoint2 corpseTile = findBonesPlacementTile(newMaze);
                if (corpseTile != null) {
                    activeBones.chunkX = chunkId.x;
                    activeBones.chunkY = chunkId.y;
                    activeBones.tileX = corpseTile.x;
                    activeBones.tileY = corpseTile.y;

                    String corpseTex = "images/scenery/decomposing_corpse.png";
                    if (Gdx.files == null || !Gdx.files.internal(corpseTex).exists()) {
                        if (Gdx.files != null && Gdx.files.internal("images/debris/bones.png").exists()) {
                            corpseTex = "images/debris/bones.png";
                        } else {
                            corpseTex = "images/debris/broken_column.png";
                        }
                    }

                    Scenery corpse = new Scenery(Scenery.SceneryType.DECOMPOSING_CORPSE, corpseTile.x, corpseTile.y, corpseTex);
                    corpse.setBonesData(activeBones);
                    if (assetManager != null && Gdx.files != null && Gdx.files.internal(corpseTex).exists()) {
                        if (!assetManager.isLoaded(corpseTex)) {
                            assetManager.load(corpseTex, com.badlogic.gdx.graphics.Texture.class);
                            assetManager.finishLoadingAsset(corpseTex);
                        }
                        corpse.setTexture(assetManager.get(corpseTex, com.badlogic.gdx.graphics.Texture.class));
                    }
                    newMaze.addScenery(corpse);
                    Gdx.app.log("WorldManager", "Placed decomposing corpse for " + activeBones.playerName
                            + " at chunk " + chunkId + " tile " + corpseTile);
                }
            }
        }

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

    private static final Pattern CHUNK_FILE_PATTERN = Pattern.compile("chunk_L(-?\\d+)_(-?\\d+)_(-?\\d+)\\.json");

    /**
     * A FileHandle rooted at a plain java.io.File, bypassing the Gdx.files backend so chunk-file
     * queries also work in headless unit tests where no LibGDX application backend is running.
     */
    private static FileHandle localFile(String path) {
        return new FileHandle(new File(path));
    }

    /** Visits every saved chunk file's (level, chunkId), regardless of which level. */
    private void forEachSavedChunk(BiConsumer<Integer, GridPoint2> visitor) {
        FileHandle dir = localFile(getChunkSaveDir());
        if (!dir.exists()) return;
        for (FileHandle f : dir.list()) {
            Matcher m = CHUNK_FILE_PATTERN.matcher(f.name());
            if (m.matches()) {
                int level = Integer.parseInt(m.group(1));
                GridPoint2 chunkId = new GridPoint2(Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)));
                visitor.accept(level, chunkId);
            }
        }
    }

    /**
     * Chunk coordinates for a given level that have a save file on disk, i.e. have actually
     * been entered by the player at some point. Read-only; does not touch {@link #loadedChunks}.
     */
    public Set<GridPoint2> getVisitedChunkIds(int level) {
        Set<GridPoint2> result = new HashSet<>();
        forEachSavedChunk((fileLevel, chunkId) -> {
            if (fileLevel == level) result.add(chunkId);
        });
        return result;
    }

    /**
     * Deepest level for which any chunk save file exists, or 1 if the player has never
     * descended below the surface.
     */
    public int getMaxVisitedLevel() {
        int[] max = {1};
        forEachSavedChunk((fileLevel, chunkId) -> max[0] = Math.max(max[0], fileLevel));
        return max[0];
    }

    /**
     * Reads a chunk's saved data directly from disk for inspection (e.g. the map screen),
     * without generating, caching, or otherwise affecting live gameplay state.
     * Returns null if the chunk has never been saved.
     */
    public ChunkData loadChunkDataReadOnly(int level, GridPoint2 chunkId) {
        FileHandle file = localFile(getChunkSaveDir() + getChunkFileName(level, chunkId.x, chunkId.y));
        if (!file.exists()) return null;
        try {
            return json.fromJson(ChunkData.class, file);
        } catch (Exception e) {
            if (Gdx.app != null) {
                Gdx.app.error("WorldManager", "Failed to read chunk data for map view: " + chunkId, e);
            }
            return null;
        }
    }

    public void setCurrentChunk(GridPoint2 chunkId) {
        this.currentPlayerChunkId = chunkId;
    }

    public int getCurrentLevel() {
        return currentLevel;
    }

    public void setCurrentLevel(int level) {
        if (this.currentLevel != level) {
            saveAllChunks();
            loadedChunks.clear();
            syncLightsForChunk(null);
            this.currentLevel = level;
            if (gameMode == GameMode.ADVANCED) {
                BonesManager.getInstance().rollBonesForFloor(level, gameMode);
            }
        }
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
            if (this.goreManager != null) {
                this.goreManager.exportChunkGore(chunkId, data);
            }
            String fileName = getChunkFileName(this.currentLevel, chunkId.x, chunkId.y);
            FileHandle file = Gdx.files.local(getChunkSaveDir() + fileName);
            SaveManager.getInstance().atomicWriteJson(file, data);
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

        if (goreManager != null) {
            goreManager.update(delta, currentMaze, this);
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

    /**
     * Fully wipes the explored world following a death: every generated chunk is deleted
     * (including chunk 0,0), and a new world seed is rolled so the next expedition generates
     * an entirely fresh maze and world. The starting shelter room itself is procedurally
     * generated inside the fresh chunk (0,0), while persistent shelter chest items survive
     * in shelter_chest.json.
     */
    public void wipeExploredWorldOnDeath() {
        loadedChunks.clear();
        levelThemes.clear();
        FileHandle dir = Gdx.files.local(getChunkSaveDir());
        if (dir.exists()) {
            for (FileHandle f : dir.list()) {
                if (!f.isDirectory() && f.name().endsWith(".json")) {
                    f.delete();
                }
            }
        }
        this.worldSeed = new java.util.Random().nextLong();
        Gdx.app.log("WorldManager", "Explored world wiped on death. New world seed: " + this.worldSeed);
    }

    public BiomeManager getBiomeManager() {
        return biomeManager;
    }

    public GameMode getGameMode() {
        return gameMode;
    }

    public Maze getCurrentMaze() {
        return loadedChunks.get(currentPlayerChunkId);
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
        if (Math.abs(newChunkId.x) >= 2 || Math.abs(newChunkId.y) >= 2 || currentLevel >= 2) {
            com.bpm.minotaur.gamedata.progression.ShelterAltar.getInstance().rearmCommune();
        }
        player.setMaze(newMaze);
        player.setPosition(newPlayerPos);
        setPlayerReference(player);
        syncLightsForChunk(newMaze);
        playThemeStinger(newMaze);
    }

    /**
     * Sounds the theme's entry stinger as the player crosses the threshold.
     *
     * <p>Contract slot (g): a themed chunk should announce itself at the moment
     * of maximum impact, so the player knows where they are without reading a
     * word.
     */
    private void playThemeStinger(Maze maze) {
        if (maze == null || maze.getChunkTheme() == null) return;

        com.bpm.minotaur.generation.theme.ThemeDefinition def =
                com.bpm.minotaur.generation.theme.ThemeDataManager.getInstance().get(maze.getChunkTheme());
        if (def == null) return;

        if (soundManager != null && def.getStinger() != null) {
            // SoundManager keys registered sounds by bare filename.
            String path = def.getStinger();
            int slash = path.lastIndexOf('/');
            int dot = path.lastIndexOf('.');
            String key = path.substring(slash + 1, dot > slash ? dot : path.length());
            soundManager.playSound(key);
        }

        announceSeal(maze, def);
    }

    /**
     * Tells the player the chunk has sealed behind them and what opens it.
     *
     * <p>Without this the seal is silent: the player walks in, finds every gate
     * refusing to open, and has no way to know it is a rule rather than a bug.
     */
    private void announceSeal(Maze maze, com.bpm.minotaur.generation.theme.ThemeDefinition def) {
        com.bpm.minotaur.generation.theme.ThemeObjectiveState state = maze.getThemeObjective();
        if (state == null || !state.isViable() || state.isResolved()) return;

        boolean sealed = false;
        for (Gate gate : maze.getGates().values()) {
            if (gate.isLocked()) {
                sealed = true;
                break;
            }
        }
        if (!sealed || pendingSealAnnouncement == null) return;

        String goal = state.getKind() != null
                ? state.getKind().getDescription()
                : "Find a way out";
        if (state.getRequired() > 1) {
            goal += " (0/" + state.getRequired() + ")";
        }

        pendingSealAnnouncement.accept(
                maze.getChunkTheme().getDisplayName() + ": the gates grind shut behind you.",
                goal + ", or channel the Rune of Surrender on any gate to forfeit and escape.");
    }

    /**
     * Sink for seal announcements. WorldManager has no GameEventManager of its
     * own, so the screen supplies one rather than this reaching for a singleton.
     */
    private BiConsumer<String, String> pendingSealAnnouncement;

    public void setSealAnnouncer(BiConsumer<String, String> announcer) {
        this.pendingSealAnnouncement = announcer;
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

        DoomManager doom = DoomManager.getInstance();
        doom.advanceExpeditionTurn();
        int interval = doom.getSpawnInterval();

        // Periodic Spawn Check using dynamic Doom Clock interval
        if (turnCount > 0 && turnCount % interval == 0) {
            int edlWithDoom = calculateEffectiveDifficulty(currentPlayerChunkId, currentLevel) + doom.getDoomEDLBonus();
            SpawnManager sm = new SpawnManager(dataManager, itemDataManager, assetManager,
                    loadedChunks.get(currentPlayerChunkId), difficulty, edlWithDoom, player.getLevel(),
                    player.getLuck(), null, spawnTableData, System.nanoTime(), null); // null = no reachability filter for periodic respawns

            sm.spawnPeriodicMonster(player);
            Gdx.app.log("WorldManager", "Periodic Spawn Triggered at Turn " + turnCount + " (Doom Stage " + doom.getDoomStage() + ")");
        }

        // Ambient Audio Escalation for Doom Clock & Themed Chunks
        if (Gdx.app != null && Gdx.app.getType() != com.badlogic.gdx.Application.ApplicationType.HeadlessDesktop) {
            if (doom.getDoomStage() >= 4) {
                // Stage 4 (Tarmin's Wrath / >= 75%): duck background music and play ominous sub-bass drone
                MusicManager.getInstance().duckMusic(0.25f);
                MusicManager.getInstance().playAmbientLoop("sounds/amb_doom_subbass.wav", 0.75f);
            } else if (doom.getDoomStage() == 3) {
                // Stage 3 (Corrupted): slight duck and play space groan
                MusicManager.getInstance().duckMusic(0.6f);
                MusicManager.getInstance().playAmbientLoop("sounds/amb_void_groan.wav", 0.5f);
            } else {
                Maze currentMaze = loadedChunks.get(currentPlayerChunkId);
                if (currentMaze != null && currentMaze.getChunkTheme() != null && 
                    (currentMaze.getChunkTheme().name().contains("VOID") || currentMaze.getChunkTheme().name().contains("FLOODED") || currentMaze.getChunkTheme().name().contains("NECROPOLIS"))) {
                    MusicManager.getInstance().duckMusic(0.5f);
                    MusicManager.getInstance().playAmbientLoop("sounds/amb_void_groan.wav", 0.6f);
                } else {
                    MusicManager.getInstance().restoreMusicVolume();
                    MusicManager.getInstance().stopAmbientLoop();
                }
            }
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

    public GoreManager getGoreManager() {
        return goreManager;
    }

    public FactionMatrix getFactionMatrix() {
        return factionMatrix;
    }

    public void setFactionMatrix(FactionMatrix factionMatrix) {
        if (factionMatrix != null) {
            this.factionMatrix = factionMatrix;
        }
    }

    public long getWorldSeed() {
        return worldSeed;
    }

    public void setWorldSeed(long worldSeed) {
        this.worldSeed = worldSeed;
        if (this.factionMatrix == null) {
            this.factionMatrix = new FactionMatrix(worldSeed);
        }
    }

    /**
     * Debug-only forced themes, keyed by chunk. Consulted before the normal
     * cluster roll so {@link #debugWarpToTheme} can drop the player into a
     * chosen theme without hunting the world for one.
     */
    private final java.util.Map<GridPoint2, com.bpm.minotaur.generation.theme.ChunkTheme> themeOverrides =
            new java.util.HashMap<>();

    private int debugThemeCursor = 0;

    /**
     * Warps the player into a freshly generated chunk of the next theme.
     *
     * <p>Six themes are too many to verify by reading a diff, and the wade cost
     * and the seal are judgement calls no test settles. Cycles on each call.
     */
    public com.bpm.minotaur.generation.theme.ChunkTheme debugWarpToTheme(Player player) {
        com.bpm.minotaur.generation.theme.ChunkTheme[] themes =
                com.bpm.minotaur.generation.theme.ChunkTheme.values();
        com.bpm.minotaur.generation.theme.ChunkTheme theme = themes[debugThemeCursor % themes.length];
        debugThemeCursor++;

        // Park debug chunks far from anywhere the player would organically walk.
        GridPoint2 target = new GridPoint2(500 + debugThemeCursor, 500);
        themeOverrides.put(new GridPoint2(target), theme);

        // Drop any cached copy so the chunk regenerates under the override.
        loadedChunks.remove(target);

        Maze warped = loadChunk(target);
        if (warped == null) return null;

        GridPoint2 spawn = new GridPoint2(warped.getWidth() / 2, warped.getHeight() / 2);
        if (warped.isWall(spawn.x, spawn.y)) {
            outer:
            for (int y = 1; y < warped.getHeight() - 1; y++) {
                for (int x = 1; x < warped.getWidth() - 1; x++) {
                    if (!warped.isWall(x, y)) {
                        spawn.set(x, y);
                        break outer;
                    }
                }
            }
        }

        transitionPlayerToChunk(player, target, spawn);
        return theme;
    }

    /**
     * Resolves an interaction with whatever stands on a tile, if it is a portal.
     *
     * <p>Lives here rather than on Player because Player is a state holder:
     * AGENT.md forbids business logic there unless it is a pure accessor.
     *
     * @return true when a portal consumed the interaction.
     */
    public boolean tryUsePortalAt(Maze maze, GridPoint2 tile, GameEventManager eventManager) {
        if (maze == null || tile == null) return false;

        Item target = maze.getItems().get(tile);
        if (target == null) return false;

        com.bpm.minotaur.gamedata.progression.BiomePortal portal =
                com.bpm.minotaur.gamedata.progression.BiomePortal.forItem(target.getType());

        PortalWarp warp;
        String message;
        if (portal != null) {
            warp = prepareBiomeWarp(portal);
            message = "You step through the " + portal.getDisplayName() + "...";
        } else if (target.getType() == Item.ItemType.BIOME_RETURN_PORTAL) {
            warp = prepareReturnWarp();
            message = "The rift folds shut behind you. You are back at the shelter.";
        } else {
            return false;
        }

        if (warp == null || warp.chunkId == null || warp.playerPos == null) {
            if (eventManager != null) {
                eventManager.addEvent(new GameEvent("The rift gutters and fails to open.", 2.5f));
            }
            return true;
        }

        if (eventManager != null) {
            eventManager.addEvent(new GameEvent(message, 2.5f));
            eventManager.addEvent(new GameEvent(GameEvent.EventType.BIOME_PORTAL_WARP, warp));
        }
        return true;
    }

    /** Where a portal is sending the player: a chunk and a tile inside it. */
    public static class PortalWarp {
        public final GridPoint2 chunkId;
        public final GridPoint2 playerPos;

        public PortalWarp(GridPoint2 chunkId, GridPoint2 playerPos) {
            this.chunkId = chunkId;
            this.playerPos = playerPos;
        }
    }

    /**
     * Resolves a shelter portal into a destination, leaving a permanent return
     * portal beside where the player lands.
     *
     * <p>The return portal is what keeps this a travel convenience rather than a
     * one-way trip: a portal you can only use outbound just relocates the long
     * walk to the other end.
     *
     * @return the warp, or null if the biome could not be found or entered.
     */
    public PortalWarp prepareBiomeWarp(com.bpm.minotaur.gamedata.progression.BiomePortal portal) {
        if (portal == null) return null;

        GridPoint2 target = findNearestChunkOfBiome(portal.getDestination());
        if (target == null) {
            log("No chunk of biome " + portal.getDestination() + " found within scan range.");
            return null;
        }

        Maze destination = loadChunk(target);
        if (destination == null) return null;

        GridPoint2 arrival = findSafeArrivalTile(destination,
                destination.getWidth() / 2, destination.getHeight() / 2);
        if (arrival == null) return null;

        placeReturnPortalNear(destination, arrival);
        return new PortalWarp(target, arrival);
    }

    /** Sends the player from a return portal back to the shelter. */
    public PortalWarp prepareReturnWarp() {
        GridPoint2 shelter = new GridPoint2(0, 0);
        Maze shelterMaze = loadChunk(shelter);
        if (shelterMaze == null) return null;

        GridPoint2 spawn = getInitialPlayerStartPos();
        GridPoint2 safe = findSafeArrivalTile(shelterMaze,
                spawn != null ? spawn.x : shelterMaze.getWidth() / 2,
                spawn != null ? spawn.y : shelterMaze.getHeight() / 2);
        return new PortalWarp(shelter, safe != null ? safe : spawn);
    }

    /**
     * Stands a return portal on a tile next to the arrival point, so the player
     * steps out of the rift rather than on top of it.
     */
    private void placeReturnPortalNear(Maze destination, GridPoint2 arrival) {
        if (itemDataManager == null) return;

        // One rift home per chunk. Without this, every trip drops another
        // portal beside the last, and the arrival clearing fills with them.
        for (Item existing : destination.getItems().values()) {
            if (existing != null && existing.getType() == Item.ItemType.BIOME_RETURN_PORTAL) {
                return;
            }
        }

        int[][] offsets = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] off : offsets) {
            int px = arrival.x + off[0];
            int py = arrival.y + off[1];
            GridPoint2 pt = new GridPoint2(px, py);

            if (px < 1 || py < 1 || px >= destination.getWidth() - 1 || py >= destination.getHeight() - 1) continue;
            if (destination.isWall(px, py)) continue;
            if (destination.getItems().containsKey(pt)) continue;
            if (destination.getMonsters().containsKey(pt)) continue;

            Item portalItem = itemDataManager.createItem(
                    Item.ItemType.BIOME_RETURN_PORTAL, px, py, com.bpm.minotaur.gamedata.item.ItemColor.BLUE,
                    assetManager);
            if (portalItem != null) {
                destination.addItem(portalItem);
                destination.addLight(new LightSource("return_portal_" + px + "_" + py, px + 0.5f, py + 0.5f,
                        com.bpm.minotaur.gamedata.progression.BiomePortal.RETURN_PORTAL_TINT, 3.5f, 1.0f,
                        LightSource.FlickerProfile.LANTERN_BREATH));
            }
            return;
        }
    }

    private static void log(String message) {
        if (Gdx.app != null) {
            Gdx.app.log("WorldManager", message);
        }
    }

    /**
     * The closest chunk of a given biome to the shelter.
     *
     * <p>Biome bands are square rings around the origin, so "closest" is scanned
     * by Chebyshev radius and tie-broken by Euclidean distance then by lowest
     * coordinate. That makes the destination deterministic for a given world --
     * the same portal always lands in the same place, so the arrival becomes a
     * landmark the player learns -- while still differing between worlds.
     *
     * @return the chunk id, or null if no such biome exists within the scan.
     */
    public GridPoint2 findNearestChunkOfBiome(Biome biome) {
        if (biome == null || biomeManager == null) return null;

        final int maxRadius = 60;
        for (int r = 1; r <= maxRadius; r++) {
            GridPoint2 best = null;
            double bestDist = Double.MAX_VALUE;

            for (int x = -r; x <= r; x++) {
                for (int y = -r; y <= r; y++) {
                    // Only the ring itself; the interior was covered by smaller r.
                    if (Math.max(Math.abs(x), Math.abs(y)) != r) continue;

                    GridPoint2 candidate = new GridPoint2(x, y);
                    if (biomeManager.getBiome(candidate) != biome) continue;

                    double dist = Math.sqrt((double) x * x + (double) y * y);
                    if (dist < bestDist
                            || (dist == bestDist && best != null
                                && (x < best.x || (x == best.x && y < best.y)))) {
                        bestDist = dist;
                        best = candidate;
                    }
                }
            }
            if (best != null) return best;
        }
        return null;
    }

    /**
     * An open, unoccupied tile in a chunk, spiralling out from a preferred spot.
     *
     * <p>Arriving inside a wall or on top of a monster would be a worse failure
     * than a slightly off-centre landing.
     */
    public static GridPoint2 findSafeArrivalTile(Maze maze, int preferX, int preferY) {
        if (maze == null) return null;

        int maxSpan = Math.max(maze.getWidth(), maze.getHeight());
        for (int radius = 0; radius < maxSpan; radius++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    if (radius > 0 && Math.abs(dx) != radius && Math.abs(dy) != radius) continue;

                    int x = preferX + dx;
                    int y = preferY + dy;
                    if (x < 1 || y < 1 || x >= maze.getWidth() - 1 || y >= maze.getHeight() - 1) continue;
                    if (!maze.isPassable(x, y)) continue;

                    GridPoint2 pt = new GridPoint2(x, y);
                    if (maze.getItems().containsKey(pt)) continue;
                    if (maze.getMonsters().containsKey(pt)) continue;
                    if (maze.getGates().containsKey(pt)) continue;
                    return pt;
                }
            }
        }
        return null;
    }

    public com.bpm.minotaur.generation.theme.ChunkTheme getChunkTheme(GridPoint2 chunkId, int level) {
        if (chunkId == null) return null;

        com.bpm.minotaur.generation.theme.ChunkTheme forced = themeOverrides.get(chunkId);
        if (forced != null) return forced;

        if (chunkId.x == 0 && chunkId.y == 0 && level == 1) {
            return null; // Shelter chunk is never themed
        }

        // Themed chunks are a dungeon construct: they carve arenas, keeps and
        // crypts out of a tile maze, and their props are stonework and bone.
        // Stamping one onto open wilderness reads as a bug, and its impassable
        // props land on forest trails that are only one tile wide.
        if (biomeManager != null && biomeManager.getBiome(chunkId) != Biome.MAZE) {
            return null;
        }

        // 3x3 Cluster Coordinates
        int clusterX = Math.floorDiv(chunkId.x, 3);
        int clusterY = Math.floorDiv(chunkId.y, 3);

        long clusterSeed = (worldSeed ^ (clusterX * 73856093L) ^ (clusterY * 19349663L) ^ (level * 83492791L));
        java.util.Random clusterRng = new java.util.Random(clusterSeed);

        int targetLocalX = clusterRng.nextInt(3);
        int targetLocalY = clusterRng.nextInt(3);

        // Ensure cluster (0,0) on Level 1 keeps the shelter sanctuary buffer (x <= 1 && y <= 1) un-themed
        if (clusterX == 0 && clusterY == 0 && level == 1) {
            while (targetLocalX <= 1 && targetLocalY <= 1) {
                targetLocalX = clusterRng.nextInt(3);
                targetLocalY = clusterRng.nextInt(3);
            }
        }

        int localX = ((chunkId.x % 3) + 3) % 3;
        int localY = ((chunkId.y % 3) + 3) % 3;

        if (localX != targetLocalX || localY != targetLocalY) {
            return null;
        }

        // Filter available themes by minLevel <= level
        java.util.List<com.bpm.minotaur.generation.theme.ChunkTheme> available = new java.util.ArrayList<>();
        for (com.bpm.minotaur.generation.theme.ChunkTheme t : com.bpm.minotaur.generation.theme.ChunkTheme.values()) {
            if (t.getMinLevel() <= level) {
                available.add(t);
            }
        }
        if (available.isEmpty()) {
            return com.bpm.minotaur.generation.theme.ChunkTheme.OVERGROWN_THICKET;
        }
        return available.get(clusterRng.nextInt(available.size()));
    }

    /**
     * Drives the themed chunk objective to completion each turn.
     *
     * <p>Replaces the old colosseum-only clear check: every theme now has an
     * objective and a Crest reward, so the completion predicate lives with the
     * objective rather than being hardcoded here.
     */
    public void checkThemeObjective(Maze maze, GameEventManager eventManager) {
        if (maze == null || maze.getChunkTheme() == null) return;

        com.bpm.minotaur.generation.theme.ThemeObjectiveState state = maze.getThemeObjective();
        if (state == null || state.isResolved() || !state.isViable()) return;

        // Kill-everything objectives can only be evaluated by sweeping the chunk.
        if (state.getKind() == com.bpm.minotaur.generation.theme.ThemeObjectiveKind.LAST_COMBATANT_STANDING) {
            com.bpm.minotaur.generation.theme.ThemeObjectiveManager.onMonsterKilled(maze, null, eventManager);
            return;
        }

        // Champion objectives resolve when the champion is gone. Checking here as
        // well as on the kill hook covers deaths the hook never sees, such as a
        // champion killed by faction infighting or an environmental hazard.
        if (state.getKind() == com.bpm.minotaur.generation.theme.ThemeObjectiveKind.SLAY_CHAMPION) {
            boolean championAlive = false;
            for (com.bpm.minotaur.gamedata.monster.Monster m : maze.getMonsters().values()) {
                if (m != null && m.isAlive() && m.isThemeChampion()) {
                    championAlive = true;
                    break;
                }
            }
            if (!championAlive) {
                state.complete();
                com.bpm.minotaur.generation.theme.ThemeObjectiveManager.resolveIfComplete(maze, eventManager);
            }
        }
    }

    public static GridPoint2 findBonesPlacementTile(Maze maze) {
        if (maze == null) return null;
        int width = maze.getWidth();
        int height = maze.getHeight();
        int midX = width / 2;
        int midY = height / 2;

        for (int r = 1; r < Math.max(width, height); r++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dx = -r; dx <= r; dx++) {
                    if (Math.abs(dx) != r && Math.abs(dy) != r) continue;
                    int x = midX + dx;
                    int y = midY + dy;
                    if (x > 1 && x < width - 2 && y > 1 && y < height - 2) {
                        GridPoint2 pt = new GridPoint2(x, y);
                        if (!maze.isWall(x, y)
                                && !maze.isHomeTile(x, y)
                                && (maze.getScenery() == null || !maze.getScenery().containsKey(pt))
                                && (maze.getItems() == null || !maze.getItems().containsKey(pt))
                                && (maze.getMonsters() == null || !maze.getMonsters().containsKey(pt))
                                && (maze.getLadders() == null || !maze.getLadders().containsKey(pt))
                                && (maze.getGates() == null || !maze.getGates().containsKey(pt))
                                && (maze.getEventAt(x, y) == null)) {
                            return pt;
                        }
                    }
                }
            }
        }
        return null;
    }
}
