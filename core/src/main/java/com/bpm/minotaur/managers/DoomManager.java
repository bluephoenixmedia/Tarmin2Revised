package com.bpm.minotaur.managers;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Json;

import com.badlogic.gdx.utils.JsonWriter.OutputType;

/**
 * Manages the "Tarmin's Hunger" mechanic:
 * - Tracks total deaths (DoomCounter).
 * - Calculates enemy scaling (Meat Grinder).
 * - Calculates resource scarcity (Starvation Curve).
 * - Tracks Bridge Integrity (Apocalypse condition).
 * 
 * Persists data separately from world saves to allow "Resurrection" mechanics
 * where the world resets but the meta-difficulty increases.
 */
public class DoomManager {
    private static DoomManager instance;

    // --- Core Variable ---
    private int deathCount = 0;

    // --- Constants ---
    private static final int MAX_DEATHS_ALLOWED = 50; // The Hard Cap
    private static final float DAMAGE_SCALE_PER_DEATH = 0.025f; // +2.5% per death (Reduced from 5%)
    private static final float LOOT_DECAY_RATE = 0.02f; // -2% loot chance per death
    private String getSaveFilePath() {
        return SaveManager.getInstance().getActiveSlotFilePath("doom_state.json");
    }

    private DoomManager() {
        // Private constructor for singleton
    }

    public static DoomManager getInstance() {
        if (instance == null) {
            instance = new DoomManager();
        }
        return instance;
    }

    private String lastDeathRunId = null;

    public boolean recordDeath(String runId) {
        if (runId != null && runId.equals(lastDeathRunId)) {
            if (Gdx.app != null) {
                Gdx.app.log("DoomManager", "Suppressed duplicate death record for run: " + runId);
            }
            return false;
        }
        this.lastDeathRunId = runId;
        incrementDeaths();
        return true;
    }

    public void incrementDeaths() {
        this.deathCount++;
        save();
        if (Gdx.app != null) {
            Gdx.app.log("DoomManager", "Death Count increased to: " + deathCount);
        }
        if (Gdx.files != null) {
            BalanceLogger.getInstance().log("DOOM_UPDATE",
                    "Deaths: " + deathCount + " | Bridge: " + getBridgeIntegrity() + "%");
        }
        if (this.deathCount >= MAX_DEATHS_ALLOWED) {
            SaveManager.getInstance().wipeActiveSlotOnApocalypse();
        }
    }

    public void resetDeaths() {
        this.deathCount = 0;
        save();
    }

    public int getDeathCount() {
        return deathCount;
    }

    // --- 4-Stage Expedition Escalation Clock ("Tarmin's Hunger") ---
    private int expeditionTurns = 0;

    public void advanceExpeditionTurn() {
        expeditionTurns++;
    }

    public void resetExpeditionTurns() {
        expeditionTurns = 0;
    }

    public int getExpeditionTurns() {
        return expeditionTurns;
    }

    /**
     * Returns the current Doom stage (1..4) based on both expedition turn count and total deaths.
     * - Stage 1: Quiescent (Turns 0–400, 0 deaths)
     * - Stage 2: Restless (Turns 401–800, or 1st Death)
     * - Stage 3: Corrupted (Turns 801–1200, or 2nd Death)
     * - Stage 4: Tarmin's Wrath (Turns 1201+, or 3+ Deaths)
     */
    public int getDoomStage() {
        int deathStage = 1;
        if (deathCount >= 3) {
            deathStage = 4;
        } else if (deathCount == 2) {
            deathStage = 3;
        } else if (deathCount == 1) {
            deathStage = 2;
        }

        int turnStage = 1;
        if (expeditionTurns > 1200) {
            turnStage = 4;
        } else if (expeditionTurns > 800) {
            turnStage = 3;
        } else if (expeditionTurns > 400) {
            turnStage = 2;
        }

        return Math.max(deathStage, turnStage);
    }

    /**
     * Periodic spawn interval in turns for the current Doom Stage.
     */
    public int getSpawnInterval() {
        switch (getDoomStage()) {
            case 2: return 200;
            case 3: return 120;
            case 4: return 80;
            case 1:
            default: return 300;
        }
    }

    /**
     * Bonus Effective Difficulty Level (EDL) applied by Doom escalation.
     */
    public int getDoomEDLBonus() {
        switch (getDoomStage()) {
            case 2: return 1;
            case 3: return 2;
            case 4: return 3;
            case 1:
            default: return 0;
        }
    }

    /**
     * Speed multiplier for monsters under restless/corrupted escalation.
     */
    public float getEscalationSpeedMultiplier() {
        return getDoomStage() >= 2 ? 1.05f : 1.0f;
    }

    /**
     * True if monsters should roll Shadow/Darkness affixes under Corrupted/Wrath stages.
     */
    public boolean shouldSpawnShadowAffixes() {
        return getDoomStage() >= 3;
    }

    private int currentLevel = 1;

    public void setCurrentLevel(int level) {
        this.currentLevel = level;
    }

    public int getCurrentLevel() {
        return currentLevel;
    }

    /**
     * @param level The dungeon depth or monster base level.
     * @return Multiplier for Enemy Stats (HP/Damage).
     *         Level 1 is strictly 1.0 (no doom penalty).
     *         Level 2+ scales dynamically with depth up to full scaling at Level 5+.
     */
    public float getEnemyScalingMultiplier(int level) {
        if (level <= 1) {
            return 1.0f;
        }
        float depthWeight = Math.min(1.0f, (level - 1) / 4.0f);
        return Math.min(1.5f, 1.0f + (DAMAGE_SCALE_PER_DEATH * deathCount * depthWeight));
    }

    /**
     * @return Multiplier for Enemy Stats using the current tracked dungeon level.
     */
    public float getEnemyScalingMultiplier() {
        return getEnemyScalingMultiplier(this.currentLevel);
    }

    /**
     * @return Multiplier for base loot chance. Base is 1.0.
     *         Formula: (1 - 0.02) ^ Deaths
     */
    public float getLootChanceMultiplier() {
        // PER USER REQUEST: Disabled loot scarcity impact for now.
        return 1.0f;
        // return (float) Math.pow(1.0f - LOOT_DECAY_RATE, deathCount);
    }

    /**
     * @return Bridge Integrity as a percentage (0-100).
     */
    public float getBridgeIntegrity() {
        float integrity = ((float) deathCount / MAX_DEATHS_ALLOWED) * 100f;
        return Math.min(100f, integrity);
    }

    /**
     * @return True if the bridge is complete (Game Over / Wipe).
     */
    public boolean isApocalypse() {
        return deathCount >= MAX_DEATHS_ALLOWED;
    }

    /**
     * Resets the Doom counter. Used after a full wipe/apocalypse.
     */
    public void reset() {
        this.deathCount = 0;
        save();
        if (Gdx.app != null) {
            Gdx.app.log("DoomManager", "DOOM RESET. The cycle begins anew.");
        }
    }

    // --- Persistence ---

    public void save() {
        try {
            Json json = new Json();
            json.setOutputType(OutputType.json);

            FileHandle file = SaveManager.getInstance().getFileHandle(getSaveFilePath());
            if (!file.parent().exists()) {
                file.parent().mkdirs();
            }

            DoomState state = new DoomState();
            state.deathCount = this.deathCount;

            file.writeString(json.prettyPrint(state), false);
            if (Gdx.app != null) {
                Gdx.app.log("DoomManager", "Saved Doom State to " + getSaveFilePath());
            }
        } catch (Exception e) {
            if (Gdx.app != null) {
                Gdx.app.error("DoomManager", "Failed to save Doom State", e);
            }
        }
    }

    public void load() {
        try {
            FileHandle file = SaveManager.getInstance().getFileHandle(getSaveFilePath());
            if (file.exists()) {
                Json json = new Json();
                DoomState state = json.fromJson(DoomState.class, file);
                if (state != null) {
                    this.deathCount = state.deathCount;
                    if (Gdx.app != null) {
                        Gdx.app.log("DoomManager", "Loaded Doom State. Deaths: " + deathCount);
                    }
                }
            } else {
                if (Gdx.app != null) {
                    Gdx.app.log("DoomManager", "No Doom State found. Starting fresh.");
                }
            }
        } catch (Exception e) {
            if (Gdx.app != null) {
                Gdx.app.error("DoomManager", "Failed to load Doom State", e);
            }
        }
    }

    // --- Data Class for JSON ---
    public static class DoomState {
        public int deathCount = 0;
    }
}
