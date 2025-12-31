package com.bpm.minotaur.managers;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Json;
import com.bpm.minotaur.gamedata.UnlockData;
import java.util.Map;

public class UnlockManager {
    private static final UnlockManager INSTANCE = new UnlockManager();
    private static final String SAVE_FILE = "saves/profile.json";

    private UnlockData data;
    private final Json json;
    private com.bpm.minotaur.gamedata.item.ItemDataManager itemDataManager;

    // Session tracking
    private java.util.List<String> sessionUnlocks = new java.util.ArrayList<>();

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

    public void setItemDataManager(com.bpm.minotaur.gamedata.item.ItemDataManager manager) {
        this.itemDataManager = manager;
        // Generate plan if missing OR if content was wiped/invalid.
        // Assuming empty map means no plan.
        if (data.unlockPlan.isEmpty()) {
            generateUnlockPlan();
        }
    }

    public java.util.List<String> getSessionUnlocks() {
        return sessionUnlocks;
    }

    private void generateUnlockPlan() {
        if (itemDataManager == null)
            return;
        Gdx.app.log("UnlockManager", "Generating progressive unlock plan...");

        java.util.List<com.bpm.minotaur.gamedata.item.ItemTemplate> lockedItems = new java.util.ArrayList<>();

        // 1. Gather all locked items
        for (com.bpm.minotaur.gamedata.item.Item.ItemType type : com.bpm.minotaur.gamedata.item.Item.ItemType
                .values()) {
            try {
                com.bpm.minotaur.gamedata.item.ItemTemplate t = itemDataManager.getTemplate(type);
                // Check if actually locked in template AND not yet unlocked in profile
                if (t.locked && !data.unlockedContent.contains(type.name())) {
                    lockedItems.add(t);
                }
            } catch (Exception e) {
                // Ignore missing templates
            }
        }

        // 2. Sort by Value/Power
        lockedItems.sort((a, b) -> Integer.compare(calculateItemScore(a), calculateItemScore(b)));

        if (lockedItems.isEmpty()) {
            Gdx.app.log("UnlockManager", "No locked items found to plan for.");
            return;
        }

        // 3. Assign Requirements progressively
        java.util.Random rng = new java.util.Random();

        long currentSteps = Math.max(data.totalSteps, 100);
        long currentDoors = Math.max(data.totalDoorsOpened, 10);
        long currentKills = 10; // Start low
        int currentLevel = 1;

        for (int i = 0; i < lockedItems.size(); i++) {
            com.bpm.minotaur.gamedata.item.ItemTemplate item = lockedItems.get(i);

            // Determine Gate Type
            UnlockData.UnlockRequirement.GateType gate = UnlockData.UnlockRequirement.GateType.values()[rng.nextInt(4)];
            long target = 0;

            // Progressive scaling factor (0.0 to 1.0)
            float progress = (float) i / lockedItems.size();

            switch (gate) {
                case TOTAL_STEPS:
                    currentSteps += 250 + (long) (progress * 2500); // Reduced halves
                    target = currentSteps;
                    break;
                case TOTAL_DOORS:
                    currentDoors += 3 + (long) (progress * 25);
                    target = currentDoors;
                    break;
                case MONSTER_KILLS:
                    currentKills += 5 + (long) (progress * 50);
                    target = currentKills;
                    break;
                case DEEPEST_LEVEL:
                    if (progress < 0.2f)
                        currentLevel = Math.max(currentLevel, 2);
                    else if (progress < 0.5f)
                        currentLevel = Math.max(currentLevel, 4); // Lowered
                    else
                        currentLevel = Math.max(currentLevel, 8); // Lowered

                    // Add slight random variance
                    target = currentLevel + rng.nextInt(2);
                    break;
            }

            // Map ItemType Name (ID) to Requirement
            String id = findIdForTemplate(item);
            if (id != null) {
                data.unlockPlan.put(id, new UnlockData.UnlockRequirement(gate, target));
            }
        }
        save();
    }

    private int calculateItemScore(com.bpm.minotaur.gamedata.item.ItemTemplate t) {
        int score = t.baseValue;
        if (t.isArmor)
            score += t.armorClassBonus * 100;
        if (t.isWeapon) {
            // Rough damage est: "1d6" -> 3.5
            if (t.damageDice != null && t.damageDice.contains("d")) {
                String[] parts = t.damageDice.split("d");
                try {
                    int d = Integer.parseInt(parts[1]);
                    int n = Integer.parseInt(parts[0]);
                    score += n * d * 50;
                } catch (Exception e) {
                }
            }
        }
        return score;
    }

    private String findIdForTemplate(com.bpm.minotaur.gamedata.item.ItemTemplate t) {
        // This is slow, but runs once per profile gen
        try {
            for (com.bpm.minotaur.gamedata.item.Item.ItemType type : com.bpm.minotaur.gamedata.item.Item.ItemType
                    .values()) {
                try {
                    if (itemDataManager.getTemplate(type) == t)
                        return type.name();
                } catch (Exception e) {
                }
            }
        } catch (Exception e) {
        }
        return null;
    }

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
        if (contentId == null || contentId.isEmpty())
            return true;
        return data.unlockedContent.contains(contentId);
    }

    private void checkUnlocks() {
        if (data.unlockPlan.isEmpty())
            return;

        java.util.Iterator<Map.Entry<String, UnlockData.UnlockRequirement>> it = data.unlockPlan.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, UnlockData.UnlockRequirement> entry = it.next();
            String id = entry.getKey();
            UnlockData.UnlockRequirement req = entry.getValue();
            boolean met = false;

            switch (req.type) {
                case TOTAL_STEPS:
                    met = data.totalSteps >= req.targetValue;
                    break;
                case TOTAL_DOORS:
                    met = data.totalDoorsOpened >= req.targetValue;
                    break;
                case MONSTER_KILLS:
                    long totalKills = data.monsterKills.values().stream().mapToInt(Integer::intValue).sum();
                    met = totalKills >= req.targetValue;
                    break;
                case DEEPEST_LEVEL:
                    met = data.deepestLevelReached >= req.targetValue;
                    break;
            }

            if (met) {
                // Must call internal unlock to avoid recursion/checks issues if any
                unlockContent(id);
                it.remove(); // Remove from plan (moved to unlockedContent)
            }
        }
    }

    public void unlockContent(String contentId) {
        if (!data.unlockedContent.contains(contentId)) {
            data.unlockedContent.add(contentId);
            sessionUnlocks.add(contentId); // Track for this session
            Gdx.app.log("UnlockManager", "!!! UNLOCKED NEW CONTENT: " + contentId + " !!!");
            save();
        }
    }

    public UnlockData getData() {
        return data;
    }
}
