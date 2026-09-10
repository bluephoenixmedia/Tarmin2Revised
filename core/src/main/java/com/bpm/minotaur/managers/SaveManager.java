package com.bpm.minotaur.managers;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonWriter;
import com.bpm.minotaur.gamedata.item.ShelterChest;
import com.bpm.minotaur.gamedata.player.Player;
import com.bpm.minotaur.gamedata.save.ItemSaveData;
import com.bpm.minotaur.gamedata.save.PlayerSaveData;
import com.bpm.minotaur.gamedata.save.ProfileData;
import com.bpm.minotaur.gamedata.save.SlotMetadata;
import com.bpm.minotaur.gamedata.save.WorldSaveData;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * Coordinates multi-slot saving, loading, atomic disk persistence,
 * backup snapshots, and global profile progression.
 */
public class SaveManager {

    private static SaveManager instance;

    public static final int MAX_SLOTS = 3;
    private static final String ROOT_SAVES_DIR = "saves/";
    private static final String PROFILE_FILE = ROOT_SAVES_DIR + "profile.json";

    private int activeSlotIndex = 1; // 1, 2, or 3
    private ProfileData profile;
    private final Json json;

    private SaveManager() {
        this.json = new Json();
        this.json.setOutputType(JsonWriter.OutputType.json);
        this.json.setIgnoreUnknownFields(true);
        loadProfile();
        this.activeSlotIndex = Math.max(1, Math.min(MAX_SLOTS, profile.lastPlayedSlot));
    }

    public static synchronized SaveManager getInstance() {
        if (instance == null) {
            instance = new SaveManager();
        }
        return instance;
    }

    // --- Profile Management ---

    public ProfileData getProfile() {
        if (profile == null) {
            loadProfile();
        }
        return profile;
    }

    public void loadProfile() {
        try {
            FileHandle file = getFileHandle(PROFILE_FILE);
            if (file.exists()) {
                profile = json.fromJson(ProfileData.class, file);
            }
        } catch (Exception e) {
            logError("Failed to load profile.json", e);
        }
        if (profile == null) {
            profile = new ProfileData();
            saveProfile();
        }
    }

    public void saveProfile() {
        if (profile == null) {
            profile = new ProfileData();
        }
        atomicWriteJson(getFileHandle(PROFILE_FILE), profile);
    }

    public void unlockClassicMode() {
        getProfile().classicModeUnlocked = true;
        saveProfile();
        log("Classic Mode permanently unlocked in profile.json!");
    }

    public boolean isClassicModeUnlocked() {
        return getProfile().classicModeUnlocked;
    }

    // --- Slot Path Helpers ---

    public int getActiveSlotIndex() {
        return activeSlotIndex;
    }

    public void setActiveSlotIndex(int slotIndex) {
        this.activeSlotIndex = Math.max(1, Math.min(MAX_SLOTS, slotIndex));
        getProfile().lastPlayedSlot = this.activeSlotIndex;
        saveProfile();
    }

    public String getSlotDirectoryPath(int slotIndex) {
        return ROOT_SAVES_DIR + "slot_" + slotIndex + "/";
    }

    public String getActiveSlotDirectoryPath() {
        return getSlotDirectoryPath(activeSlotIndex);
    }

    public String getChunkSaveDirectory(int slotIndex) {
        return getSlotDirectoryPath(slotIndex) + "chunks/";
    }

    public String getActiveChunkSaveDirectory() {
        return getChunkSaveDirectory(activeSlotIndex);
    }

    public String getSlotFilePath(int slotIndex, String fileName) {
        return getSlotDirectoryPath(slotIndex) + fileName;
    }

    public String getActiveSlotFilePath(String fileName) {
        return getSlotFilePath(activeSlotIndex, fileName);
    }

    // --- Slot Lifecycle & Metadata ---

    public boolean hasAnySave() {
        for (int i = 1; i <= MAX_SLOTS; i++) {
            if (getSlotMetadata(i).isOccupied) {
                return true;
            }
        }
        return false;
    }

    public int getMostRecentOccupiedSlot() {
        int last = getProfile().lastPlayedSlot;
        if (last >= 1 && last <= MAX_SLOTS && getSlotMetadata(last).isOccupied) {
            return last;
        }
        for (int i = 1; i <= MAX_SLOTS; i++) {
            if (getSlotMetadata(i).isOccupied) {
                return i;
            }
        }
        return -1;
    }

    public SlotMetadata getSlotMetadata(int slotIndex) {
        String metaPath = getSlotFilePath(slotIndex, "meta.json");
        FileHandle file = getFileHandle(metaPath);
        if (file.exists()) {
            try {
                SlotMetadata meta = json.fromJson(SlotMetadata.class, file);
                if (meta != null) {
                    meta.slotIndex = slotIndex;
                    return meta;
                }
            } catch (Exception e) {
                logError("Failed to read meta.json for slot " + slotIndex, e);
            }
        }

        SlotMetadata empty = new SlotMetadata(slotIndex);
        FileHandle slotDir = getFileHandle(getSlotDirectoryPath(slotIndex));
        if (slotDir.exists() && slotDir.list().length > 0) {
            empty.isOccupied = true;
            empty.locationName = "Unknown Ruins";
        }
        return empty;
    }

    public void startNewGame(int slotIndex, String gameMode, String characterName, String characterClass) {
        setActiveSlotIndex(slotIndex);

        // Clear existing slot directory
        deleteSlot(slotIndex);

        // Ensure directories exist
        FileHandle slotDir = getFileHandle(getSlotDirectoryPath(slotIndex));
        slotDir.mkdirs();
        FileHandle chunkDir = getFileHandle(getChunkSaveDirectory(slotIndex));
        chunkDir.mkdirs();

        // Create initial metadata
        SlotMetadata meta = new SlotMetadata(slotIndex);
        meta.isOccupied = true;
        meta.characterName = (characterName != null && !characterName.trim().isEmpty()) ? characterName : "Hero";
        meta.characterClass = (characterClass != null && !characterClass.trim().isEmpty()) ? characterClass : "Warrior";
        meta.gameMode = (gameMode != null) ? gameMode : "MODERN";
        meta.level = 1;
        meta.currentHP = 20;
        meta.maxHP = 20;
        meta.currentMP = 10;
        meta.maxMP = 10;
        meta.depthLevel = 1;
        meta.locationName = "Shelter (Floor 1)";
        meta.deathCount = 0;
        meta.maxDeaths = 50;
        meta.lastPlayedTimestamp = System.currentTimeMillis();

        atomicWriteJson(getFileHandle(getSlotFilePath(slotIndex, "meta.json")), meta);

        // Clear in-memory singletons to start clean
        ShelterChest.getInstance().clear();
        DoomManager.getInstance().resetDeaths();

        log("Started New Game in slot " + slotIndex + " [" + meta.gameMode + "] as " + meta.characterName);
    }

    public void deleteSlot(int slotIndex) {
        try {
            FileHandle slotDir = getFileHandle(getSlotDirectoryPath(slotIndex));
            if (slotDir.exists()) {
                deleteDirectoryRecursively(slotDir.file());
                log("Deleted save slot " + slotIndex);
            }
        } catch (Exception e) {
            logError("Failed to delete slot " + slotIndex, e);
        }
    }

    public void wipeActiveSlotOnApocalypse() {
        log("APOCALYPSE: Wiping active slot " + activeSlotIndex);
        getProfile().totalRunsAttempted++;
        saveProfile();
        deleteSlot(activeSlotIndex);
    }

    // --- Save Orchestration ---

    public void saveActiveSlot(Player player, WorldManager worldManager) {
        if (player == null) {
            return;
        }

        try {
            // 1. Ensure directory
            FileHandle slotDir = getFileHandle(getActiveSlotDirectoryPath());
            slotDir.mkdirs();

            // 2. Save Player
            PlayerSaveData playerData = new PlayerSaveData(player);
            atomicWriteJson(getFileHandle(getActiveSlotFilePath("player.json")), playerData);

            // 3. Save World
            WorldSaveData worldData = new WorldSaveData();
            if (worldManager != null) {
                worldData.currentLevel = worldManager.getCurrentLevel();
                if (worldManager.getCurrentPlayerChunkId() != null) {
                    worldData.playerChunkX = worldManager.getCurrentPlayerChunkId().x;
                    worldData.playerChunkY = worldManager.getCurrentPlayerChunkId().y;
                }
                worldData.gameMode = worldManager.getGameMode() != null ? worldManager.getGameMode().name() : "MODERN";
                // Save current chunk
                if (worldManager.getCurrentMaze() != null) {
                    worldManager.saveCurrentChunk(worldManager.getCurrentMaze());
                }
            }
            atomicWriteJson(getFileHandle(getActiveSlotFilePath("world.json")), worldData);

            // 4. Save Chest, Doom, Divinities
            ShelterChest.getInstance().save();
            DoomManager.getInstance().save();
            DivinityManager.getInstance().save();

            // 5. Update and save Metadata
            SlotMetadata meta = getSlotMetadata(activeSlotIndex);
            meta.isOccupied = true;
            meta.level = player.getStats() != null ? player.getStats().getLevel() : 1;
            meta.currentHP = player.getStats() != null ? player.getStats().getCurrentHP() : 20;
            meta.maxHP = player.getStats() != null ? player.getStats().getMaxHP() : 20;
            meta.currentMP = player.getStats() != null ? player.getStats().getCurrentMP() : 10;
            meta.maxMP = player.getStats() != null ? player.getStats().getMaxMP() : 10;
            meta.depthLevel = worldManager != null ? worldManager.getCurrentLevel() : 1;
            meta.locationName = (meta.depthLevel == 1) ? "Shelter (Floor 1)" : "Dungeon (Floor " + meta.depthLevel + ")";
            meta.deathCount = DoomManager.getInstance().getDeathCount();
            meta.lastPlayedTimestamp = System.currentTimeMillis();

            atomicWriteJson(getFileHandle(getActiveSlotFilePath("meta.json")), meta);
            log("Saved game successfully for slot " + activeSlotIndex);

        } catch (Exception e) {
            logError("Failed to save active slot " + activeSlotIndex, e);
        }
    }

    public PlayerSaveData loadActivePlayerData() {
        FileHandle file = getFileHandle(getActiveSlotFilePath("player.json"));
        if (file.exists()) {
            try {
                return json.fromJson(PlayerSaveData.class, file);
            } catch (Exception e) {
                logError("Failed to parse player.json", e);
            }
        }
        return null;
    }

    public WorldSaveData loadActiveWorldData() {
        FileHandle file = getFileHandle(getActiveSlotFilePath("world.json"));
        if (file.exists()) {
            try {
                return json.fromJson(WorldSaveData.class, file);
            } catch (Exception e) {
                logError("Failed to parse world.json", e);
            }
        }
        return null;
    }

    // --- Rolling Shelter Backup ---

    public void backupActiveSlot() {
        try {
            FileHandle slotDir = getFileHandle(getActiveSlotDirectoryPath());
            if (!slotDir.exists()) return;

            FileHandle backupDir = getFileHandle(getActiveSlotDirectoryPath() + "backup/");
            backupDir.mkdirs();

            for (FileHandle file : slotDir.list()) {
                if (!file.isDirectory() && file.name().endsWith(".json")) {
                    file.copyTo(backupDir.child(file.name()));
                }
            }
            log("Shelter backup snapshot created for slot " + activeSlotIndex);
        } catch (Exception e) {
            logError("Failed to create shelter backup for slot " + activeSlotIndex, e);
        }
    }

    // --- Atomic File Writing ---

    public void atomicWriteJson(FileHandle targetFile, Object object) {
        if (targetFile == null || object == null) {
            return;
        }

        try {
            targetFile.parent().mkdirs();
            String jsonText = json.prettyPrint(object);

            File target = targetFile.file();
            File tempFile = new File(target.getParentFile(), target.getName() + ".tmp");

            // Write to .tmp file first
            Files.write(tempFile.toPath(), jsonText.getBytes("UTF-8"));

            // Atomic move/replace
            try {
                Files.move(tempFile.toPath(), target.toPath(),
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tempFile.toPath(), target.toPath(),
                        StandardCopyOption.REPLACE_EXISTING);
            }

        } catch (Exception e) {
            logError("Failed atomic write to " + targetFile.path(), e);
        }
    }

    // --- Helpers ---

    private FileHandle getFileHandle(String path) {
        if (Gdx.files != null) {
            return Gdx.files.local(path);
        }
        return new FileHandle(new File(path));
    }

    private void deleteDirectoryRecursively(File dir) {
        if (dir == null || !dir.exists()) return;
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) {
                    deleteDirectoryRecursively(f);
                } else {
                    f.delete();
                }
            }
        }
        dir.delete();
    }

    private void log(String msg) {
        if (Gdx.app != null) {
            Gdx.app.log("SaveManager", msg);
        } else {
            System.out.println("[SaveManager] " + msg);
        }
    }

    private void logError(String msg, Throwable t) {
        if (Gdx.app != null) {
            Gdx.app.error("SaveManager", msg, t);
        } else {
            System.err.println("[SaveManager ERROR] " + msg);
            if (t != null) t.printStackTrace();
        }
    }
}
