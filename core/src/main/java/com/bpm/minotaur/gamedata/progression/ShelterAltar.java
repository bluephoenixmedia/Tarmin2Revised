package com.bpm.minotaur.gamedata.progression;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Json;
import com.bpm.minotaur.managers.DivinityManager;
import com.bpm.minotaur.managers.SaveManager;

/**
 * Persistent Shelter Altar meta-progression: three upgrade trees purchased with
 * banked Divinities. Like Divinities themselves, altar tiers survive death and
 * world resets, and are only cleared by a full Apocalypse wipe.
 */
public class ShelterAltar {

    public enum Tree { PROVISIONS, REPERTOIRE, MONUMENT }

    public static final int MAX_TIER = 3;
    private static final int BASE_COST = 30;

    private static final float STATUE_FREQUENCY_BASE = 0.15f;
    private static final float STATUE_FREQUENCY_MAX = 0.35f;

    private static ShelterAltar instance;

    private int provisionsTier = 0;
    private int repertoireTier = 0;
    private int monumentTier = 0;

    private ShelterAltar() {
        load();
    }

    public static ShelterAltar getInstance() {
        if (instance == null) {
            instance = new ShelterAltar();
        }
        return instance;
    }

    public int getProvisionsTier() {
        return provisionsTier;
    }

    public int getRepertoireTier() {
        return repertoireTier;
    }

    public int getMonumentTier() {
        return monumentTier;
    }

    public int getTier(Tree tree) {
        switch (tree) {
            case PROVISIONS: return provisionsTier;
            case REPERTOIRE: return repertoireTier;
            case MONUMENT: return monumentTier;
            default: return 0;
        }
    }

    public boolean isMaxed(Tree tree) {
        return getTier(tree) >= MAX_TIER;
    }

    /** @return the Divinity cost of the next tier in this tree, or -1 if maxed. */
    public int getNextUpgradeCost(Tree tree) {
        if (isMaxed(tree)) {
            return -1;
        }
        return BASE_COST * (getTier(tree) + 1);
    }

    public boolean purchaseUpgrade(Tree tree) {
        int cost = getNextUpgradeCost(tree);
        if (cost < 0) {
            return false;
        }
        if (!DivinityManager.getInstance().spendDivinities(cost)) {
            return false;
        }
        switch (tree) {
            case PROVISIONS: provisionsTier++; break;
            case REPERTOIRE: repertoireTier++; break;
            case MONUMENT: monumentTier++; break;
        }
        save();
        return true;
    }

    /** Extra bread/waterskin/bandages granted at the start of each expedition. */
    public int getBonusProvisionCount() {
        return provisionsTier;
    }

    /** Per-chunk chance of a statue encounter event, scaling from a 15% baseline to 35% at max tier. */
    public float getStatueEventFrequency() {
        return STATUE_FREQUENCY_BASE + (STATUE_FREQUENCY_MAX - STATUE_FREQUENCY_BASE) * ((float) monumentTier / MAX_TIER);
    }

    // ---- Persistence ----

    private String getSaveFilePath() {
        return SaveManager.getInstance().getActiveSlotFilePath("altar_progression.json");
    }

    public void save() {
        try {
            FileHandle dir = Gdx.files.local("saves/");
            if (!dir.exists()) {
                dir.mkdirs();
            }
            FileHandle file = Gdx.files.local(getSaveFilePath());
            SaveData data = new SaveData();
            data.provisionsTier = provisionsTier;
            data.repertoireTier = repertoireTier;
            data.monumentTier = monumentTier;
            SaveManager.getInstance().atomicWriteJson(file, data);
        } catch (Exception e) {
            if (Gdx.app != null) {
                Gdx.app.error("ShelterAltar", "Failed to save: " + e.getMessage());
            }
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
                    provisionsTier = data.provisionsTier;
                    repertoireTier = data.repertoireTier;
                    monumentTier = data.monumentTier;
                }
            }
        } catch (Exception e) {
            if (Gdx.app != null) {
                Gdx.app.error("ShelterAltar", "Failed to load: " + e.getMessage());
            }
        }
    }

    public static class SaveData {
        public int provisionsTier = 0;
        public int repertoireTier = 0;
        public int monumentTier = 0;
    }
}
