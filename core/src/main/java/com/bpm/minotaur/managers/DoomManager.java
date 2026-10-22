package com.bpm.minotaur.managers;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Json;

import com.badlogic.gdx.utils.JsonWriter.OutputType;

/**
 * Manages the "Tarmin's Hunger" mechanic:
 * - Tracks total deaths (DoomCounter), which raise a slow stage baseline.
 * - Escalates the stage with turns spent inside one Expedition.
 * - Calculates resource scarcity (Starvation Curve).
 * - Tracks Bridge Integrity (Apocalypse condition).
 * 
 * Persists data separately from world saves to allow "Resurrection" mechanics
 * where the world resets but the meta-difficulty increases.
 */
public class DoomManager implements SlotScopedState {
    private static DoomManager instance;

    // --- Core Variables ---
    private int deathCount = 0;
    private boolean bridgeBossActive = false;
    private boolean apocalypseTriggered = false;
    /**
     * How many times the bridge boss has been summoned in this run.
     *
     * <p>Load-bearing, because killing the boss resets the death count to zero.
     * With a fixed-difficulty boss that would permanently remove the 50-death
     * cap for any player who can beat it once, and the apocalypse would become
     * unreachable. Each summon makes the next fight harder instead.
     */
    private int bridgeBossSummons = 0;

    // --- Constants ---
    private static final int MAX_DEATHS_ALLOWED = 50; // The Hard Cap
    /**
     * Deaths raise a slow stage baseline across all 50 (#176 / #188): stage 2 from 10, stage 3
     * from 25, stage 4 from 40. Deaths no longer scale monster damage; unlocks and altar
     * stations are what offset the growing Hunger.
     */
    public static final int DEATHS_FOR_STAGE_2 = 10;
    public static final int DEATHS_FOR_STAGE_3 = 25;
    public static final int DEATHS_FOR_STAGE_4 = 40;
    private static final float LOOT_DECAY_RATE = 0.02f; // -2% loot chance per death
    private String getSaveFilePath() {
        return SaveManager.getInstance().getActiveSlotFilePath("doom_state.json");
    }

    private DoomManager() {
        // Follow the active slot; see SlotScopedState.
        SaveManager.register(this);
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
        if (Gdx.app != null) {
            Gdx.app.log("DoomManager", "Death Count increased to: " + deathCount);
        }
        if (Gdx.files != null) {
            BalanceLogger.getInstance().log("DOOM_UPDATE",
                    "Deaths: " + deathCount + " | Bridge: " + getBridgeIntegrity() + "%");
        }
        if (this.deathCount >= MAX_DEATHS_ALLOWED) {
            if (!this.bridgeBossActive) {
                // First time reaching 100% integrity (50 deaths): the Bridge reaches the far
                // shore, and its Warden (the Iron Golem) comes to guard the last stone (#267).
                this.bridgeBossActive = true;
                this.bridgeBossSummons++;
                if (Gdx.app != null) {
                    Gdx.app.log("DoomManager", "Bridge integrity reached 100%! Summoning Bridge Boss!");
                }
            } else {
                // Next death while the boss is active wipes the slot!
                this.apocalypseTriggered = true;
                SaveManager.getInstance().wipeActiveSlotOnApocalypse();
                // The slot is gone. Saving now would write doom_state.json back into it.
                return;
            }
        }
        save();
    }

    public void resetDeaths() {
        this.deathCount = 0;
        this.bridgeBossActive = false;
        this.apocalypseTriggered = false;
        this.bridgeBossSummons = 0;
        save();
    }

    /** The death count at which the Bridge reaches the far shore and its Warden comes. */
    public static int maxDeaths() {
        return MAX_DEATHS_ALLOWED;
    }

    /** The death count at which the next death stage begins, or -1 past the last one. */
    public static int nextDeathStageAt(int deaths) {
        if (deaths < DEATHS_FOR_STAGE_2) return DEATHS_FOR_STAGE_2;
        if (deaths < DEATHS_FOR_STAGE_3) return DEATHS_FOR_STAGE_3;
        if (deaths < DEATHS_FOR_STAGE_4) return DEATHS_FOR_STAGE_4;
        return -1;
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
     * Returns the current Doom stage (1..4): the higher of the expedition turn stage and the
     * death baseline. Turns reset each expedition; deaths only ever climb.
     * - Stage 1: Quiescent (Turns 0-400, 0-9 deaths)
     * - Stage 2: Restless (Turns 401-800, or 10+ deaths)
     * - Stage 3: Corrupted (Turns 801-1200, or 25+ deaths)
     * - Stage 4: Tarmin's Wrath (Turns 1201+, or 40+ deaths)
     */
    public int getDoomStage() {
        int deathStage = 1;
        if (deathCount >= DEATHS_FOR_STAGE_4) {
            deathStage = 4;
        } else if (deathCount >= DEATHS_FOR_STAGE_3) {
            deathStage = 3;
        } else if (deathCount >= DEATHS_FOR_STAGE_2) {
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
     * @return True if the bridge is complete and apocalypse triggered (Game Over / Wipe).
     */
    public boolean isApocalypse() {
        return apocalypseTriggered;
    }

    public boolean isBridgeBossActive() {
        return bridgeBossActive;
    }

    public void setBridgeBossActive(boolean active) {
        this.bridgeBossActive = active;
        save();
    }

    /**
     * The boss is dead: bridge integrity returns to zero.
     *
     * <p>A full reset, not a partial cut. The counterweight is
     * {@link #getBridgeBossSummons()}: each victory makes the next summoning
     * harder, so the cap reasserts itself over a long campaign rather than
     * being removed by a single competent fight.
     */
    public void onBridgeBossDefeated() {
        this.bridgeBossActive = false;
        this.deathCount = 0;
        this.apocalypseTriggered = false;
        save();
        if (Gdx.app != null) {
            Gdx.app.log("DoomManager", "Bridge Boss defeated! Integrity reset to 0. Summons so far: "
                    + bridgeBossSummons);
        }
    }

    /** How many times the boss has been summoned; 1 during the first fight. */
    public int getBridgeBossSummons() {
        return bridgeBossSummons;
    }

    /**
     * Extra champion hit points for this summoning.
     *
     * <p>The first fight uses the authored value; each later one adds half
     * again, so a player who keeps resetting the bridge meets a boss that keeps
     * outgrowing them.
     */
    public int getBridgeBossHpBonus(int baseHpBonus) {
        int extraSummons = Math.max(0, bridgeBossSummons - 1);
        return baseHpBonus + Math.round(baseHpBonus * 0.5f * extraSummons);
    }

    /**
     * Resets the Doom counter. Used after a full wipe/apocalypse.
     */
    public void reset() {
        this.deathCount = 0;
        this.bridgeBossActive = false;
        this.apocalypseTriggered = false;
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
            state.bridgeBossActive = this.bridgeBossActive;
            state.apocalypseTriggered = this.apocalypseTriggered;
            state.bridgeBossSummons = this.bridgeBossSummons;
            state.expeditionTurns = this.expeditionTurns;

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
                    this.bridgeBossActive = state.bridgeBossActive;
                    this.apocalypseTriggered = state.apocalypseTriggered;
                    this.bridgeBossSummons = state.bridgeBossSummons;
                    this.expeditionTurns = state.expeditionTurns;
                    if (Gdx.app != null) {
                        Gdx.app.log("DoomManager", "Loaded Doom State. Deaths: " + deathCount + " | BridgeBoss: " + bridgeBossActive);
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
        public boolean bridgeBossActive = false;
        public boolean apocalypseTriggered = false;
        public int bridgeBossSummons = 0;
        /** Feeds the Doom stage; absent (0) in saves written before it was persisted. */
        public int expeditionTurns = 0;
    }

    @Override
    public void reloadForActiveSlot() {
        this.deathCount = 0;
        this.bridgeBossActive = false;
        this.apocalypseTriggered = false;
        this.bridgeBossSummons = 0;
        resetExpeditionTurns();
        load();
    }

    @Override
    public void resetForNewGame() {
        this.deathCount = 0;
        this.bridgeBossActive = false;
        this.apocalypseTriggered = false;
        this.bridgeBossSummons = 0;
        resetExpeditionTurns();
        save();
    }
}
