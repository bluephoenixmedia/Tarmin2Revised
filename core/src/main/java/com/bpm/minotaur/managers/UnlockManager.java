package com.bpm.minotaur.managers;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Json;
import com.bpm.minotaur.gamedata.UnlockData;

public class UnlockManager {
    private static final UnlockManager INSTANCE = new UnlockManager();
    private static final String SAVE_FILE = "saves/profile.json";

    private UnlockData data;
    private final Json json;

    private UnlockManager() {
        json = new Json();
        json.setUsePrototypes(false);
        load();
    }

    public static UnlockManager getInstance() {
        return INSTANCE;
    }

    public void load() {
        FileHandle file = Gdx.files.local(SAVE_FILE);
        if (file.exists()) {
            try {
                data = json.fromJson(UnlockData.class, file);
                Gdx.app.log("UnlockManager", "Profile loaded successfully.");
            } catch (Exception e) {
                Gdx.app.error("UnlockManager", "Failed to load profile, creating new.", e);
                data = new UnlockData();
            }
        } else {
            data = new UnlockData();
            save();
        }
    }

    public void save() {
        try {
            FileHandle file = Gdx.files.local(SAVE_FILE);
            file.writeString(json.prettyPrint(data), false);
        } catch (Exception e) {
            Gdx.app.error("UnlockManager", "Failed to save profile.", e);
        }
    }

    // --- Data Access & Rules ---

    public void incrementStat(String statName, int amount) {
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
        checkUnlocks(); // Check for new unlocks after stat update
    }

    public void recordKill(String monsterType) {
        int current = data.monsterKills.getOrDefault(monsterType, 0);
        data.monsterKills.put(monsterType, current + 1);
        save();
        checkUnlocks();
    }

    public void updateDeepestLevel(int level) {
        if (level > data.deepestLevelReached) {
            data.deepestLevelReached = level;
            save();
            checkUnlocks();
        }
    }

    public boolean isUnlocked(String contentId) {
        if (contentId == null || contentId.isEmpty()) return true;
        return data.unlockedContent.contains(contentId);
    }

    public void unlockContent(String contentId) {
        if (!data.unlockedContent.contains(contentId)) {
            data.unlockedContent.add(contentId);
            Gdx.app.log("UnlockManager", "!!! UNLOCKED NEW CONTENT: " + contentId + " !!!");
            save();
        }
    }

    // --- META-GAME RULES ---
    private void checkUnlocks() {
        // Rule 1: Open 2 Doors -> Unlock the BOW
        if (data.totalDoorsOpened >= 2) {
            unlockContent("item_bow");
        }

        // Rule 2: Walk 50 Steps -> Unlock SPEED_BOOTS (Example)
        if (data.totalSteps >= 50) {
            unlockContent("item_speed_boots");
        }
    }

    public UnlockData getData() {
        return data;
    }
}
