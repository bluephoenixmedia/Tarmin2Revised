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
    private static final String SAVE_FILE = "saves/doom_state.json";

    private DoomManager() {
        // Private constructor for singleton
    }

    public static DoomManager getInstance() {
        if (instance == null) {
            instance = new DoomManager();
        }
        return instance;
    }

    // --- Core Logic ---

    public void incrementDeaths() {
        this.deathCount++;
        save();
        Gdx.app.log("DoomManager", "Death Count increased to: " + deathCount);
        BalanceLogger.getInstance().log("DOOM_UPDATE",
                "Deaths: " + deathCount + " | Bridge: " + getBridgeIntegrity() + "%");
    }

    public int getDeathCount() {
        return deathCount;
    }

    /**
     * @return Multiplier for Enemy Stats (HP/Damage). Base is 1.0.
     *         Formula: 1 + (0.05 * Deaths)
     */
    public float getEnemyScalingMultiplier() {
        return 1.0f + (DAMAGE_SCALE_PER_DEATH * deathCount);
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
        Gdx.app.log("DoomManager", "DOOM RESET. The cycle begins anew.");
    }

    // --- Persistence ---

    public void save() {
        try {
            Json json = new Json();
            json.setOutputType(OutputType.json);

            // Simple wrapper object or just the int? Let's verify directory first.
            FileHandle file = Gdx.files.local(SAVE_FILE);
            if (!file.parent().exists()) {
                file.parent().mkdirs();
            }

            DoomState state = new DoomState();
            state.deathCount = this.deathCount;

            file.writeString(json.prettyPrint(state), false);
            Gdx.app.log("DoomManager", "Saved Doom State.");
        } catch (Exception e) {
            Gdx.app.error("DoomManager", "Failed to save Doom State", e);
        }
    }

    public void load() {
        try {
            FileHandle file = Gdx.files.local(SAVE_FILE);
            if (file.exists()) {
                Json json = new Json();
                DoomState state = json.fromJson(DoomState.class, file);
                if (state != null) {
                    this.deathCount = state.deathCount;
                    Gdx.app.log("DoomManager", "Loaded Doom State. Deaths: " + deathCount);
                }
            } else {
                Gdx.app.log("DoomManager", "No Doom State found. Starting fresh.");
            }
        } catch (Exception e) {
            Gdx.app.error("DoomManager", "Failed to load Doom State", e);
        }
    }

    // --- Data Class for JSON ---
    public static class DoomState {
        public int deathCount = 0;
    }
}
