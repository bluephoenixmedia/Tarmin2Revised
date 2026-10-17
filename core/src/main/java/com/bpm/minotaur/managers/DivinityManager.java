package com.bpm.minotaur.managers;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Json;

import java.util.HashSet;
import java.util.Set;

/**
 * Tracks the player's Divinities: a permanent-progression currency that is banked
 * automatically as it is earned and is never lost, including on death. Divinities are
 * spent at the Shelter altar on permanent upgrades; only a full Apocalypse wipe (see {@link SaveManager#wipeActiveSlotOnApocalypse()})
 * ever resets them.
 *
 * <p><b>Income target (#197, decided in #160):</b> about 15-25 per depth-1 expedition, so
 * the first station (Lantern 10, Bed 15) comes after the first or second expedition and a
 * mid-tier one after about five. The per-kill and per-chunk amounts below already land
 * there; the arithmetic is pinned in {@code DivinityIncomeTest}.
 */
public class DivinityManager implements SlotScopedState {

    public static final String DIVINITY_NAME = "Divinities";

    /**
     * The retired Loot Retention upgrade (#197) cost 20 x the level bought, up to level 5.
     * Death is permanent for the Incarnation now, so a save that bought levels is refunded
     * what it paid. The cost is kept only to compute that refund.
     */
    private static final int RETIRED_LOOT_RETENTION_BASE_COST = 20;

    private String getSaveFilePath() {
        return SaveManager.getInstance().getActiveSlotFilePath("divinities.json");
    }

    private static DivinityManager instance;

    // Persistent, safe-from-death balance.
    private int currentDivinities = 0;

    // Per-world-generation tracking (reset whenever the explored world is wiped).
    private final Set<String> visitedChunksThisRun = new HashSet<>();

    private DivinityManager() {
        load();
        // Follow the active slot; see SlotScopedState.
        SaveManager.register(this);
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
        int amount = Math.max(1, Math.round(baseChunkAward(dungeonLevel) * com.bpm.minotaur.gamedata.trait.TraitEffects.mult("divinityMult")));
        currentDivinities += amount;
        com.bpm.minotaur.telemetry.TelemetryManager.getInstance().recordDivinitiesEarned(amount);
        save();
        return amount;
    }

    /** Divinity for entering a new chunk, before traits: {@code max(1, depth)}. */
    public static int baseChunkAward(int dungeonLevel) {
        return Math.max(1, dungeonLevel);
    }

    /** Divinity for a kill, before traits: {@code max(1, (baseLevel + depth) / 2)}. */
    public static int baseKillAward(int monsterBaseLevel, int dungeonLevel) {
        return Math.max(1, (monsterBaseLevel + dungeonLevel) / 2);
    }

    /**
     * Awards divinities for killing an enemy. Scales with monster tier and dungeon depth.
     */
    public int awardKillDivinities(int monsterBaseLevel, int dungeonLevel) {
        int amount = baseKillAward(monsterBaseLevel, dungeonLevel);
        amount = Math.max(0, Math.round(amount * com.bpm.minotaur.gamedata.trait.TraitEffects.mult("divinityMult")) + Math.round(com.bpm.minotaur.gamedata.trait.TraitEffects.add("divinityKillAdd")));
        currentDivinities += amount;
        // Every kill path is counted here, the remote (spell and missile) kills included.
        com.bpm.minotaur.telemetry.TelemetryManager.getInstance().recordDivinitiesEarned(amount);
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

    // ---- Retired Loot Retention upgrade (#197) ----

    /** What a save that bought {@code level} Loot Retention levels paid: 20 x (1 + ... + level). */
    public static int retiredLootRetentionRefund(int level) {
        int lvl = Math.max(0, level);
        return RETIRED_LOOT_RETENTION_BASE_COST * lvl * (lvl + 1) / 2;
    }

    /**
     * Turns any Loot Retention levels in loaded save data back into the Divinities paid for
     * them, and clears the level. Returns the refund.
     */
    static int refundRetiredLootRetention(SaveData data) {
        if (data == null || data.lootRetentionUpgradeLevel <= 0) return 0;
        int refund = retiredLootRetentionRefund(data.lootRetentionUpgradeLevel);
        data.currentDivinities += refund;
        data.lootRetentionUpgradeLevel = 0;
        return refund;
    }

    // ---- Accessors ----

    public int getCurrentDivinities() {
        return currentDivinities;
    }

    public int getDivinities() {
        return currentDivinities;
    }

    public boolean spendDivinities(int amount) {
        if (currentDivinities < amount) return false;
        currentDivinities -= amount;
        com.bpm.minotaur.telemetry.TelemetryManager.getInstance().recordDivinitiesSpent(amount);
        save();
        return true;
    }

    public void addDivinities(int amount) {
        int gained = Math.max(0, Math.round(Math.max(0, amount) * com.bpm.minotaur.gamedata.trait.TraitEffects.mult("divinityMult")));
        currentDivinities += gained;
        com.bpm.minotaur.telemetry.TelemetryManager.getInstance().recordDivinitiesEarned(gained);
        save();
    }

    // ---- Persistence ----

    public void save() {
        try {
            FileHandle dir = SaveManager.getInstance().getFileHandle("saves/");
            if (!dir.exists()) dir.mkdirs();
            FileHandle file = SaveManager.getInstance().getFileHandle(getSaveFilePath());
            SaveData data = new SaveData();
            data.currentDivinities = currentDivinities;
            SaveManager.getInstance().atomicWriteJson(file, data);
        } catch (Exception e) {
            if (Gdx.app != null) {
                Gdx.app.error("DivinityManager", "Failed to save: " + e.getMessage());
            }
        }
    }

    private void load() {
        try {
            FileHandle file = SaveManager.getInstance().getFileHandle(getSaveFilePath());
            if (file.exists()) {
                Json json = new Json();
                json.setUsePrototypes(false);
                SaveData data = json.fromJson(SaveData.class, file.readString());
                if (data != null) {
                    int refund = refundRetiredLootRetention(data);
                    currentDivinities = data.currentDivinities;
                    if (refund > 0) {
                        if (Gdx.app != null) {
                            Gdx.app.log("DivinityManager", "Refunded " + refund
                                    + " Divinities for the retired Loot Retention upgrade");
                        }
                        save();
                    }
                }
            }
        } catch (Exception e) {
            if (Gdx.app != null) {
                Gdx.app.error("DivinityManager", "Failed to load: " + e.getMessage());
            }
        }
    }

    public static class SaveData {
        public int currentDivinities = 0;
        /** Retired (#197). Read from old saves only, to refund it; always written as 0. */
        public int lootRetentionUpgradeLevel = 0;
    }

    @Override
    public void reloadForActiveSlot() {
        clearBankedProgress();
        load();
    }

    @Override
    public void resetForNewGame() {
        clearBankedProgress();
        save();
    }

    /**
     * Banked divinities are earned by one character. There was no reset, so they
     * followed the player into every other slot and every new game.
     */
    private void clearBankedProgress() {
        this.currentDivinities = 0;
    }
}
