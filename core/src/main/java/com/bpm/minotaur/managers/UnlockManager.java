package com.bpm.minotaur.managers;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Json;
import com.bpm.minotaur.gamedata.UnlockData;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class UnlockManager implements SlotScopedState {
    private static final UnlockManager INSTANCE = new UnlockManager();
    /**
     * Only a fallback for when no slot is resolvable, e.g. in tests.
     *
     * <p>This used to be the real path: one global file shared by all three
     * slots, holding unlocked content, deepest level reached and lifetime kill
     * counts, with no production code path that ever reset it. Slot 1's depth
     * also gates Altar station visibility, so it revealed stations in a
     * brand-new slot 3.
     */
    public static final String DEFAULT_SAVE_FILE = "saves/unlocks.json";

    /** Per-slot file name; resolved against the active slot at load time. */
    public static final String SLOT_SAVE_FILE = "unlocks.json";
    /**
     * Items scoring at or above this are locked behind meta-progression.
     *
     * <p>At 310 -- above the median item score of 210 -- a brand-new save could
     * already see 379 of 544 templates, so the unlock system gated almost
     * nothing and a first run looked much like a tenth. At 50 that drops to
     * roughly 134.
     */
    public static final int UNLOCK_SCORE_THRESHOLD = 50;

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
        this.saveFile = resolveSlotPath();
        load();
        // Follow the active slot; see SlotScopedState.
        SaveManager.register(this);
    }

    /**
     * The active slot's unlocks file, or the legacy global path when no slot can
     * be resolved (tests, or very early startup).
     */
    private String resolveSlotPath() {
        try {
            return SaveManager.getInstance().getActiveSlotFilePath(SLOT_SAVE_FILE);
        } catch (Exception e) {
            return DEFAULT_SAVE_FILE;
        }
    }

    @Override
    public void reloadForActiveSlot() {
        this.saveFile = resolveSlotPath();
        sessionUnlocks.clear();
        load();
    }

    @Override
    public void resetForNewGame() {
        this.saveFile = resolveSlotPath();
        data = new UnlockData();
        sessionUnlocks.clear();
        save();
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
            // Deliberately does NOT migrate the old global saves/unlocks.json.
            // Unlocks are per-character now, so inheriting a shared pool would
            // reintroduce exactly the leak this change removes.
            data = new UnlockData();
            save();
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

    /**
     * Display names of this run's unlocks.
     *
     * <p>Kept for callers that only render text. Anything that needs an icon should use
     * {@link #rollRunUnlockTypes} instead, since the item type is what the atlas is keyed on.
     */
    public List<String> rollRunUnlocks(com.bpm.minotaur.telemetry.TelemetryManager telemetry, int maxFloorReached) {
        List<String> names = new ArrayList<>();
        for (com.bpm.minotaur.gamedata.item.Item.ItemType type : rollRunUnlockTypes(telemetry, maxFloorReached)) {
            names.add(displayNameFor(type));
        }
        return names;
    }

    /** The item data the unlock roll is resolved against, for callers that need templates. */
    public com.bpm.minotaur.gamedata.item.ItemDataManager getItemDataManager() {
        return itemDataManager;
    }

    /** The friendly name an unlocked type should be shown under, falling back to its enum name. */
    public String displayNameFor(com.bpm.minotaur.gamedata.item.Item.ItemType type) {
        if (type == null) return "";
        com.bpm.minotaur.gamedata.item.ItemTemplate t =
                (itemDataManager != null) ? itemDataManager.getTemplate(type) : null;
        return (t != null && t.friendlyName != null)
                ? com.bpm.minotaur.gamedata.item.ItemName.natural(t.friendlyName)
                : type.name();
    }

    /**
     * Rolls this run's unlocks and returns the item types granted.
     *
     * <p>Returns types rather than names because the death screen shows each discovery as a card
     * with its icon, and icons are looked up from the packed atlases by item type.
     */
    public List<com.bpm.minotaur.gamedata.item.Item.ItemType> rollRunUnlockTypes(
            com.bpm.minotaur.telemetry.TelemetryManager telemetry, int maxFloorReached) {
        List<com.bpm.minotaur.gamedata.item.Item.ItemType> newlyUnlocked = new ArrayList<>();
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
            if (telemetry.getTotalMonstersKilled() >= 100) {
                milestoneCount++;
            }
            if (telemetry.getDivinitiesEarned() >= 150) {
                milestoneCount++;
            }
            if (telemetry.getTurnsLived() >= 1000) {
                milestoneCount++;
            }
        }
        // Capped at 2, down from 3, and the milestones above were raised from
        // 25 / 50 / 300. Those bars were reachable in an ordinary run, so almost
        // every run hit the cap; lowering the cap alone would have left a strong
        // run indistinguishable from an average one.
        int unlocksToGrant = Math.min(2, 1 + milestoneCount);

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
            newlyUnlocked.add(unlockedType);
        }

        return newlyUnlocked;
    }

    public UnlockData getData() {
        return data;
    }
}
