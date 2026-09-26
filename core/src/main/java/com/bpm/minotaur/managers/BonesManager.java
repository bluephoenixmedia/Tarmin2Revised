package com.bpm.minotaur.managers;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonWriter;
import com.bpm.minotaur.gamedata.GameMode;
import com.bpm.minotaur.gamedata.bones.BonesData;
import com.bpm.minotaur.gamedata.player.Player;
import com.bpm.minotaur.gamedata.save.PlayerSaveData;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;

/**
 * Manages the NetHack-style Bones system for Tarmin 2.
 * Serializes player death snapshots to global bones files, handles strata-bracket
 * procedural chunk allocations, tracks active floor bones, and unlinks/consumes
 * bones upon ghost defeat.
 */
public class BonesManager {

    /** Legacy shared location; retained only so old files can be cleaned up. */
    private static final String LEGACY_BONES_DIR = "saves/bones/";

    /**
     * Bones belong to the character who died, not to the installation.
     *
     * <p>They lived in one shared directory keyed only by strata depth, and
     * neither deleteSlot nor startNewGame ever pruned them, so the ghosts of
     * one slot's dead heroes kept spawning in every other slot's expeditions.
     */
    private static String bonesDir() {
        try {
            return SaveManager.getInstance().getActiveSlotFilePath("bones/");
        } catch (Exception e) {
            return LEGACY_BONES_DIR;
        }
    }
    private static final int MAX_BONES_PER_STRATA = 5;
    private static final float DEFAULT_SPAWN_CHANCE = 0.30f; // 30% NetHack-style roll per floor

    private static BonesManager instance;

    private final Json json;
    private final Random random;

    // Runtime state on active dungeon floor
    private BonesData activeFloorBones = null;
    private boolean notificationTriggered = false;

    private BonesManager() {
        this.json = new Json();
        this.json.setOutputType(JsonWriter.OutputType.json);
        this.json.setIgnoreUnknownFields(true);
        this.random = new Random();
        ensureDirectory();
    }

    public static synchronized BonesManager getInstance() {
        if (instance == null) {
            instance = new BonesManager();
        }
        return instance;
    }

    private void ensureDirectory() {
        try {
            FileHandle dir = getFileHandle(bonesDir());
            if (!dir.exists()) {
                dir.mkdirs();
            }
        } catch (Exception e) {
            logError("Failed to initialize bones directory: " + bonesDir(), e);
        }
    }

    /**
     * Records a new bones file when the player dies in GameMode.ADVANCED.
     * Strictly skipped in GameMode.CLASSIC to maintain retro mode isolation.
     */
    public BonesData recordBonesOnDeath(Player player, int floorLevel, String epitaph, GameMode gameMode) {
        if (gameMode == GameMode.CLASSIC) {
            log("Retro Classic mode active: skipping bones file generation.");
            return null;
        }

        if (player == null) {
            logError("Cannot record bones: Player is null", null);
            return null;
        }

        ensureDirectory();

        int clampedFloor = Math.max(1, floorLevel);
        int strataDepth = Math.max(1, (clampedFloor - 1) / 3 + 1);
        long timestamp = System.currentTimeMillis();
        String id = UUID.randomUUID().toString();

        String charName = "Fallen Hero";
        try {
            SaveManager sm = SaveManager.getInstance();
            if (sm != null) {
                int slot = sm.getActiveSlotIndex();
                if (sm.getSlotMetadata(slot) != null && sm.getSlotMetadata(slot).characterName != null) {
                    charName = sm.getSlotMetadata(slot).characterName;
                }
            }
        } catch (Exception ignored) {
        }

        PlayerSaveData playerData = new PlayerSaveData(player);
        BonesData bones = new BonesData(id, charName, epitaph, clampedFloor, strataDepth, playerData);

        String fileName = "bones_strata_" + strataDepth + "_" + timestamp + ".json";
        String filePath = bonesDir() + fileName;
        bones.filePath = filePath;

        try {
            FileHandle fileHandle = getFileHandle(filePath);
            atomicWriteJson(fileHandle, bones);
            log("Recorded bones file on death: " + filePath + " for hero " + charName + " at Strata " + strataDepth);

            pruneOldBones(strataDepth);
            return bones;
        } catch (Exception e) {
            logError("Failed to save bones file: " + filePath, e);
            return null;
        }
    }

    /**
     * Finds all eligible bones files for the specified strata bracket.
     */
    public List<BonesData> getEligibleBonesForStrata(int strataDepth) {
        List<BonesData> eligible = new ArrayList<>();
        ensureDirectory();

        try {
            FileHandle dir = getFileHandle(bonesDir());
            if (!dir.exists() || !dir.isDirectory()) {
                return eligible;
            }

            FileHandle[] files = dir.list(".json");
            if (files == null) return eligible;

            for (FileHandle f : files) {
                try {
                    BonesData data = json.fromJson(BonesData.class, f);
                    if (data != null && data.strataDepth == strataDepth) {
                        data.filePath = f.path();
                        eligible.add(data);
                    }
                } catch (Exception e) {
                    logError("Failed to parse bones file: " + f.name(), e);
                }
            }
        } catch (Exception e) {
            logError("Failed to query bones for strata: " + strataDepth, e);
        }

        return eligible;
    }

    /**
     * Rolls whether a bones file should spawn on the newly entered floor.
     * If successful, selects an eligible bones file and sets it as the active floor bones.
     */
    public BonesData rollBonesForFloor(int floorLevel, GameMode gameMode) {
        if (gameMode == GameMode.CLASSIC) {
            clearActiveFloorBones();
            return null;
        }

        clearActiveFloorBones();

        int strataDepth = Math.max(1, (floorLevel - 1) / 3 + 1);
        List<BonesData> eligible = getEligibleBonesForStrata(strataDepth);

        if (eligible.isEmpty()) {
            return null;
        }

        if (random.nextFloat() < DEFAULT_SPAWN_CHANCE) {
            BonesData chosen = eligible.get(random.nextInt(eligible.size()));
            this.activeFloorBones = chosen;
            this.notificationTriggered = false;
            log("Bones rolled successfully for floor " + floorLevel + " (Strata " + strataDepth + "): " + chosen.playerName);
            return chosen;
        }

        return null;
    }

    /**
     * Unlinks/deletes the bones file from disk upon defeating the ghost.
     */
    public boolean consumeBones(BonesData bones) {
        if (bones == null || bones.filePath == null) {
            return false;
        }

        try {
            FileHandle fh = getFileHandle(bones.filePath);
            if (fh.exists()) {
                boolean deleted = fh.delete();
                log("Consumed bones file: " + bones.filePath + " (deleted=" + deleted + ")");
                return deleted;
            }
        } catch (Exception e) {
            logError("Failed to consume bones file: " + bones.filePath, e);
        }
        return false;
    }

    public boolean bonesFileExists(BonesData bones) {
        if (bones == null || bones.filePath == null) return false;
        try {
            return getFileHandle(bones.filePath).exists();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Enforces FIFO limit of max 5 bones files per strata bracket.
     */
    private void pruneOldBones(int strataDepth) {
        List<BonesData> list = getEligibleBonesForStrata(strataDepth);
        if (list.size() <= MAX_BONES_PER_STRATA) {
            return;
        }

        // Sort by timestamp ascending (oldest first)
        list.sort(Comparator.comparingLong(b -> b.timestamp));

        int toRemove = list.size() - MAX_BONES_PER_STRATA;
        for (int i = 0; i < toRemove; i++) {
            consumeBones(list.get(i));
        }
    }

    public BonesData getActiveFloorBones() {
        return activeFloorBones;
    }

    public void setActiveFloorBones(BonesData bones) {
        this.activeFloorBones = bones;
        this.notificationTriggered = false;
    }

    public void clearActiveFloorBones() {
        this.activeFloorBones = null;
        this.notificationTriggered = false;
    }

    public boolean isNotificationTriggered() {
        return notificationTriggered;
    }

    public void setNotificationTriggered(boolean triggered) {
        this.notificationTriggered = triggered;
    }

    // --- Helpers ---

    private void atomicWriteJson(FileHandle targetFile, Object object) throws Exception {
        targetFile.parent().mkdirs();
        String jsonText = json.prettyPrint(object);

        File target = targetFile.file();
        File tempFile = new File(target.getParentFile(), target.getName() + ".tmp");

        Files.write(tempFile.toPath(), jsonText.getBytes("UTF-8"));

        try {
            Files.move(tempFile.toPath(), target.toPath(),
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            Files.move(tempFile.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private FileHandle getFileHandle(String path) {
        // Bones files are runtime local saves in saves/bones/, always resolve to direct file
        return new FileHandle(new File(path));
    }

    private void log(String msg) {
        if (Gdx.app != null) {
            Gdx.app.log("BonesManager", msg);
        } else {
            System.out.println("[BonesManager] " + msg);
        }
    }

    private void logError(String msg, Throwable t) {
        if (Gdx.app != null) {
            Gdx.app.error("BonesManager", msg, t);
        } else {
            System.err.println("[BonesManager ERROR] " + msg);
            if (t != null) t.printStackTrace();
        }
    }
}
