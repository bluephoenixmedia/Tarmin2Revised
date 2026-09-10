package com.bpm.minotaur.managers;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.math.GridPoint2;
import com.badlogic.gdx.utils.Json;

import java.util.HashSet;
import java.util.Set;

public class DivinityManager {

    public static final String DIVINITY_NAME = "Divinities";
    private String getSaveFilePath() {
        return SaveManager.getInstance().getActiveSlotFilePath("divinities.json");
    }

    private static DivinityManager instance;

    // Current run state
    private int currentDivinities = 0;
    private final Set<String> visitedChunksThisRun = new HashSet<>();

    // Lost divinity state — persists across runs until retrieved or lost again
    private int lostDivinityAmount = 0;
    private String lostDivinityChunkKey = null;

    // Set when the player enters the matching chunk; cleared on collection or next death
    private GridPoint2 lostDivinityTile = null;

    private DivinityManager() {
        load();
    }

    public static DivinityManager getInstance() {
        if (instance == null) {
            instance = new DivinityManager();
        }
        return instance;
    }

    /** Canonical chunk key: "<level>_<chunkX>_<chunkY>" */
    public static String buildChunkKey(int level, int chunkX, int chunkY) {
        return level + "_" + chunkX + "_" + chunkY;
    }

    /**
     * Awards divinities for entering a new chunk. Returns 0 if already visited this run.
     */
    public int tryAwardChunkDivinities(String chunkKey, int dungeonLevel) {
        if (visitedChunksThisRun.contains(chunkKey)) return 0;
        visitedChunksThisRun.add(chunkKey);
        int amount = Math.max(1, dungeonLevel);
        currentDivinities += amount;
        return amount;
    }

    /**
     * Awards divinities for killing an enemy. Scales with monster tier and dungeon depth.
     */
    public int awardKillDivinities(int monsterBaseLevel, int dungeonLevel) {
        int amount = Math.max(1, (monsterBaseLevel + dungeonLevel) / 2);
        currentDivinities += amount;
        return amount;
    }

    /**
     * Returns the pending lost-divinity amount if the given chunk key matches the death location.
     * Returns 0 otherwise.
     */
    public int checkForLostDivinities(String chunkKey) {
        if (lostDivinityAmount > 0 && lostDivinityChunkKey != null
                && lostDivinityChunkKey.equals(chunkKey)) {
            return lostDivinityAmount;
        }
        return 0;
    }

    /**
     * Called when the player physically reaches the lost-divinity tile and collects them.
     * Returns the amount collected.
     */
    public int collectLostDivinities() {
        int amount = lostDivinityAmount;
        currentDivinities += amount;
        lostDivinityAmount = 0;
        lostDivinityChunkKey = null;
        lostDivinityTile = null;
        save();
        return amount;
    }

    /**
     * Called on player death. Stores current divinities as lost at the death chunk.
     * Any previously uncollected lost divinities are overwritten (lost forever).
     */
    public void onPlayerDeath(String deathChunkKey) {
        onPlayerDeath(deathChunkKey, null);
    }

    public void onPlayerDeath(String deathChunkKey, GridPoint2 deathTile) {
        lostDivinityAmount = currentDivinities;
        lostDivinityChunkKey = deathChunkKey;
        this.lostDivinityTile = deathTile;
        currentDivinities = 0;
        visitedChunksThisRun.clear();
        save();
        Gdx.app.log("DivinityManager", "Player died with " + lostDivinityAmount
                + " Divinities. Stored at chunk: " + deathChunkKey + " at tile: " + deathTile);
    }

    /**
     * Called on portal reset. Clears run-progress state; lost divinities persist.
     */
    public void onRunReset() {
        currentDivinities = 0;
        visitedChunksThisRun.clear();
    }

    // ---- Accessors ----

    public void setLostDivinityTile(GridPoint2 tile) {
        this.lostDivinityTile = tile;
    }

    public GridPoint2 getLostDivinityTile() {
        return lostDivinityTile;
    }

    public int getCurrentDivinities() {
        return currentDivinities;
    }

    public boolean hasLostDivinities() {
        return lostDivinityAmount > 0;
    }

    public int getLostDivinityAmount() {
        return lostDivinityAmount;
    }

    public String getLostDivinityChunkKey() {
        return lostDivinityChunkKey;
    }

    // ---- Persistence ----

    public void save() {
        try {
            FileHandle dir = Gdx.files.local("saves/");
            if (!dir.exists()) dir.mkdirs();
            FileHandle file = Gdx.files.local(getSaveFilePath());
            SaveData data = new SaveData();
            data.lostDivinityAmount = lostDivinityAmount;
            data.lostDivinityChunkKey = lostDivinityChunkKey;
            data.lostDivinityTile = lostDivinityTile;
            SaveManager.getInstance().atomicWriteJson(file, data);
        } catch (Exception e) {
            Gdx.app.error("DivinityManager", "Failed to save: " + e.getMessage());
        }
    }

    private void load() {
        try {
            FileHandle file = Gdx.files.local(getSaveFilePath());
            if (file.exists()) {
                Json json = new Json();
                json.setUsePrototypes(false);
                SaveData data = json.fromJson(SaveData.class, file.readString());
                if (data != null) {
                    lostDivinityAmount = data.lostDivinityAmount;
                    lostDivinityChunkKey = data.lostDivinityChunkKey;
                    lostDivinityTile = data.lostDivinityTile;
                    if (lostDivinityAmount > 0) {
                        Gdx.app.log("DivinityManager", "Loaded lost Divinities: "
                                + lostDivinityAmount + " at " + lostDivinityChunkKey
                                + " tile " + lostDivinityTile);
                    }
                }
            }
        } catch (Exception e) {
            Gdx.app.error("DivinityManager", "Failed to load: " + e.getMessage());
        }
    }

    public static class SaveData {
        public int lostDivinityAmount = 0;
        public String lostDivinityChunkKey = null;
        public GridPoint2 lostDivinityTile = null;
    }
}
