package com.bpm.minotaur.managers;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Json;
import com.bpm.minotaur.gamedata.UnlockData;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class UnlockManager {
    private static final UnlockManager INSTANCE = new UnlockManager();
    public static final String DEFAULT_SAVE_FILE = "saves/unlocks.json";
    public static final String LEGACY_PROFILE_FILE = "saves/profile.json";
    public static final int UNLOCK_SCORE_THRESHOLD = 310;

    private String saveFile = DEFAULT_SAVE_FILE;
    private UnlockData data;
    private final Json json;
    private com.bpm.minotaur.gamedata.item.ItemDataManager itemDataManager;

    // Session tracking
    private final List<String> sessionUnlocks = new ArrayList<>();

    private UnlockManager() {
        json = new Json();
        json.setUsePrototypes(false);
        json.setIgnoreUnknownFields(true);
        load();
    }

    public static UnlockManager getInstance() {
        return INSTANCE;
    }

    public void setSaveFile(String path) {
        this.saveFile = path;
        load();
    }

    public void load() {
        FileHandle file = getFileHandle(saveFile);
        if (file.exists()) {
            try {
                data = json.fromJson(UnlockData.class, file);
                if (data == null) {
                    data = new UnlockData();
                }
                if (Gdx.app != null) {
                    Gdx.app.log("UnlockManager", "Unlocks loaded successfully from " + saveFile);
                }
            } catch (Exception e) {
                if (Gdx.app != null) {
                    Gdx.app.error("UnlockManager", "Failed to load unlocks from " + saveFile + ", creating new.", e);
                }
                data = new UnlockData();
            }
        } else {
            data = new UnlockData();
            checkAndMigrateLegacyProfile();
            save();
        }
    }

    private void checkAndMigrateLegacyProfile() {
        try {
            FileHandle legacyFile = getFileHandle(LEGACY_PROFILE_FILE);
            if (legacyFile.exists()) {
                String content = legacyFile.readString("UTF-8");
                if (content.contains("unlockedContent")) {
                    UnlockData legacyData = json.fromJson(UnlockData.class, legacyFile);
                    if (legacyData != null && legacyData.unlockedContent != null && !legacyData.unlockedContent.isEmpty()) {
                        data.unlockedContent.addAll(legacyData.unlockedContent);
                        if (Gdx.app != null) {
                            Gdx.app.log("UnlockManager", "Migrated " + legacyData.unlockedContent.size() + " unlocks from legacy profile.json");
                        }
                    }
                }
            }
        } catch (Exception e) {
            if (Gdx.app != null) {
                Gdx.app.error("UnlockManager", "Legacy migration check failed", e);
            }
        }
    }

    public void save() {
        if (data == null) {
            return;
        }
        try {
            FileHandle file = getFileHandle(saveFile);
            SaveManager.getInstance().atomicWriteJson(file, data);
        } catch (Exception e) {
            if (Gdx.app != null) {
                Gdx.app.error("UnlockManager", "Failed to save unlocks to " + saveFile, e);
            }
        }
    }

    private FileHandle getFileHandle(String path) {
        if (Gdx.files != null) {
            return Gdx.files.local(path);
        }
        return new FileHandle(new File(path));
    }

    public void setItemDataManager(com.bpm.minotaur.gamedata.item.ItemDataManager manager) {
        this.itemDataManager = manager;
    }

    public List<String> getSessionUnlocks() {
        return Collections.unmodifiableList(sessionUnlocks);
    }

    public static int calculateItemScore(com.bpm.minotaur.gamedata.item.ItemTemplate t) {
        if (t == null) return 0;
        int score = t.baseValue;
        if (t.isArmor)
            score += t.armorClassBonus * 100;
        if (t.isWeapon) {
            if (t.damageDice != null && t.damageDice.contains("d")) {
                String[] parts = t.damageDice.split("d");
                try {
                    int d = Integer.parseInt(parts[1]);
                    int n = Integer.parseInt(parts[0]);
                    score += n * d * 50;
                } catch (Exception ignored) {
                }
            }
        }
        return score;
    }

    public void incrementStat(String statName, int amount) {
        if (data == null) return;
        switch (statName) {
            case "steps":
                data.totalSteps += amount;
                break;
            case "doors":
                data.totalDoorsOpened += amount;
                break;
            default:
                break;
        }
    }

    public void recordKill(String monsterType) {
        if (data == null) return;
        int current = data.monsterKills.getOrDefault(monsterType, 0);
        data.monsterKills.put(monsterType, current + 1);
    }

    public void updateDeepestLevel(int level) {
        if (data == null) return;
        if (level > data.deepestLevelReached) {
            data.deepestLevelReached = level;
            save();
        }
    }

    public boolean isUnlocked(String contentId) {
        if (contentId == null || contentId.isEmpty()) {
            return false;
        }
        if (data != null && data.unlockedContent.contains(contentId)) {
            return true;
        }
        if (itemDataManager != null) {
            try {
                com.bpm.minotaur.gamedata.item.Item.ItemType type =
                        com.bpm.minotaur.gamedata.item.Item.ItemType.valueOf(contentId);
                com.bpm.minotaur.gamedata.item.ItemTemplate template = itemDataManager.getTemplate(type);
                if (template != null && !template.unlockGated) {
                    return true;
                }
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    public boolean isItemGated(com.bpm.minotaur.gamedata.item.Item.ItemType type) {
        if (type == null || itemDataManager == null) {
            return false;
        }
        com.bpm.minotaur.gamedata.item.ItemTemplate template = itemDataManager.getTemplate(type);
        return template != null && template.unlockGated;
    }

    public void resetForTesting() {
        data = new UnlockData();
        sessionUnlocks.clear();
    }

    public void unlockContent(String contentId) {
        if (data == null) return;
        if (!data.unlockedContent.contains(contentId)) {
            data.unlockedContent.add(contentId);
            sessionUnlocks.add(contentId);
            if (Gdx.app != null) {
                Gdx.app.log("UnlockManager", "!!! UNLOCKED NEW CONTENT: " + contentId + " !!!");
            }
            save();
        }
    }

    public List<String> rollRunUnlocks(com.bpm.minotaur.telemetry.TelemetryManager telemetry, int maxFloorReached) {
        List<String> newlyUnlocked = new ArrayList<>();
        if (itemDataManager == null || data == null) {
            return newlyUnlocked;
        }

        int strataReached = Math.max(1, (maxFloorReached - 1) / 3 + 1);
        if (telemetry != null && telemetry.getStrataReached() > strataReached) {
            strataReached = telemetry.getStrataReached();
        }

        int milestoneCount = 0;
        if (telemetry != null) {
            if (maxFloorReached > 1 && (strataReached > 1 || maxFloorReached > data.deepestLevelReached)) {
                milestoneCount++;
            }
            if (telemetry.getTotalMonstersKilled() >= 25) {
                milestoneCount++;
            }
            if (telemetry.getDivinitiesEarned() >= 50) {
                milestoneCount++;
            }
            if (telemetry.getTurnsLived() >= 300) {
                milestoneCount++;
            }
        }
        int unlocksToGrant = Math.min(3, 1 + milestoneCount);

        int maxEligibleScore = UNLOCK_SCORE_THRESHOLD + (strataReached * 250);
        List<com.bpm.minotaur.gamedata.item.Item.ItemType> boundedPool = new ArrayList<>();
        List<com.bpm.minotaur.gamedata.item.Item.ItemType> fallbackPool = new ArrayList<>();

        for (com.bpm.minotaur.gamedata.item.Item.ItemType type : com.bpm.minotaur.gamedata.item.Item.ItemType.values()) {
            try {
                com.bpm.minotaur.gamedata.item.ItemTemplate t = itemDataManager.getTemplate(type);
                if (t != null && t.unlockGated && !data.unlockedContent.contains(type.name())) {
                    int score = calculateItemScore(t);
                    fallbackPool.add(type);
                    if (score <= maxEligibleScore) {
                        boundedPool.add(type);
                    }
                }
            } catch (Exception ignored) {
            }
        }

        List<com.bpm.minotaur.gamedata.item.Item.ItemType> eligible =
                (!boundedPool.isEmpty()) ? boundedPool : fallbackPool;

        if (eligible.isEmpty()) {
            return newlyUnlocked;
        }

        Collections.shuffle(eligible);
        int grantCount = Math.min(unlocksToGrant, eligible.size());
        for (int i = 0; i < grantCount; i++) {
            com.bpm.minotaur.gamedata.item.Item.ItemType unlockedType = eligible.get(i);
            unlockContent(unlockedType.name());
            com.bpm.minotaur.gamedata.item.ItemTemplate t = itemDataManager.getTemplate(unlockedType);
            String displayName = (t != null && t.friendlyName != null) ? t.friendlyName : unlockedType.name();
            newlyUnlocked.add(displayName);
        }

        return newlyUnlocked;
    }

    public UnlockData getData() {
        return data;
    }
}
