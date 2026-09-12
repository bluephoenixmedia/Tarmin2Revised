package com.bpm.minotaur.managers;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Json;

import java.util.HashSet;
import java.util.Set;

/**
 * Tracks the player's Divinities: a permanent-progression currency that is banked
 * automatically as it is earned and is never lost, including on death. Divinities are
 * spent at the Shelter on permanent upgrades (see the Loot Retention upgrade below);
 * only a full Apocalypse wipe (see {@link SaveManager#wipeActiveSlotOnApocalypse()})
 * ever resets them.
 */
public class DivinityManager {

    public static final String DIVINITY_NAME = "Divinities";

    /** Loot Retention: a permanent upgrade letting the player keep a capped amount of
     *  unequipped backpack/quickslot items through death instead of losing everything. */
    private static final int MAX_LOOT_RETENTION_LEVEL = 5;
    private static final int LOOT_RETENTION_ITEMS_PER_LEVEL = 2;
    private static final int LOOT_RETENTION_BASE_COST = 20;

    private String getSaveFilePath() {
        return SaveManager.getInstance().getActiveSlotFilePath("divinities.json");
    }

    private static DivinityManager instance;

    // Persistent, safe-from-death balance.
    private int currentDivinities = 0;
    private int lootRetentionUpgradeLevel = 0;

    // Per-world-generation tracking (reset whenever the explored world is wiped).
    private final Set<String> visitedChunksThisRun = new HashSet<>();

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
        save();
        return amount;
    }

    /**
     * Awards divinities for killing an enemy. Scales with monster tier and dungeon depth.
     */
    public int awardKillDivinities(int monsterBaseLevel, int dungeonLevel) {
        int amount = Math.max(1, (monsterBaseLevel + dungeonLevel) / 2);
        currentDivinities += amount;
        save();
        return amount;
    }

    /**
     * Called when the explored world is wiped (currently: on every player death).
     * Divinities themselves are never lost; only the per-world "new chunk" bonus
     * tracking resets, since the wiped world's chunks no longer exist to revisit.
     */
    public void onWorldReset() {
        visitedChunksThisRun.clear();
    }

    // ---- Loot Retention upgrade ----

    public int getLootRetentionUpgradeLevel() {
        return lootRetentionUpgradeLevel;
    }

    /** @return how many unequipped backpack/quickslot items survive death. */
    public int getLootRetentionCap() {
        return lootRetentionUpgradeLevel * LOOT_RETENTION_ITEMS_PER_LEVEL;
    }

    public boolean isLootRetentionMaxed() {
        return lootRetentionUpgradeLevel >= MAX_LOOT_RETENTION_LEVEL;
    }

    /** @return the Divinity cost of the next Loot Retention upgrade, or -1 if maxed. */
    public int getNextLootRetentionUpgradeCost() {
        if (isLootRetentionMaxed()) return -1;
        return LOOT_RETENTION_BASE_COST * (lootRetentionUpgradeLevel + 1);
    }

    public boolean purchaseLootRetentionUpgrade() {
        if (isLootRetentionMaxed()) return false;
        int cost = getNextLootRetentionUpgradeCost();
        if (currentDivinities < cost) return false;
        currentDivinities -= cost;
        lootRetentionUpgradeLevel++;
        save();
        return true;
    }

    // ---- Accessors ----

    public int getCurrentDivinities() {
        return currentDivinities;
    }

    // ---- Persistence ----

    public void save() {
        try {
            FileHandle dir = Gdx.files.local("saves/");
            if (!dir.exists()) dir.mkdirs();
            FileHandle file = Gdx.files.local(getSaveFilePath());
            SaveData data = new SaveData();
            data.currentDivinities = currentDivinities;
            data.lootRetentionUpgradeLevel = lootRetentionUpgradeLevel;
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
                    currentDivinities = data.currentDivinities;
                    lootRetentionUpgradeLevel = data.lootRetentionUpgradeLevel;
                }
            }
        } catch (Exception e) {
            Gdx.app.error("DivinityManager", "Failed to load: " + e.getMessage());
        }
    }

    public static class SaveData {
        public int currentDivinities = 0;
        public int lootRetentionUpgradeLevel = 0;
    }
}
