// Path: core/src/main/java/com/bpm/minotaur/managers/SettingsManager.java
package com.bpm.minotaur.managers;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Preferences;
import com.bpm.minotaur.gamedata.Difficulty;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A singleton manager to handle loading, saving, and providing
 * all game settings. Uses LibGDX Preferences to persist data.
 */
public class SettingsManager {
    private static final SettingsManager INSTANCE = new SettingsManager();
    private static final String PREFS_NAME = "Tarmin2Settings";

    // --- Settings ---
    private Difficulty currentDifficulty;
    private boolean isAdvancedMode;
    private final Map<String, Integer> keyBindings = new LinkedHashMap<>();
    private final Map<String, String> keyBindingDescriptions = new LinkedHashMap<>();

    // --- Private constructor for singleton ---
    private SettingsManager() {
        // Define all game actions and their user-friendly descriptions
        keyBindingDescriptions.put("MOVE_FORWARD", "Move Forward");
        keyBindingDescriptions.put("MOVE_BACK", "Move Backward");
        keyBindingDescriptions.put("TURN_LEFT", "Turn Left");
        keyBindingDescriptions.put("TURN_RIGHT", "Turn Right");
        keyBindingDescriptions.put("INTERACT", "Open/Interact");
        keyBindingDescriptions.put("PICKUP_DROP", "Pickup/Drop Item");
        keyBindingDescriptions.put("USE_ITEM", "Use Item");
        keyBindingDescriptions.put("DESCEND", "Descend Ladder");
        keyBindingDescriptions.put("REST", "Rest");
        keyBindingDescriptions.put("SWAP_HANDS", "Swap Hands");
        keyBindingDescriptions.put("SWAP_PACK", "Swap with Pack");
        keyBindingDescriptions.put("ROTATE_PACK", "Rotate Pack");
        keyBindingDescriptions.put("ATTACK", "Attack (Combat)");
        keyBindingDescriptions.put("MAP", "Castle Map");
    }

    public static SettingsManager getInstance() {
        return INSTANCE;
    }

    /**
     * Loads all settings from Preferences.
     * If a setting is not found, it sets and saves a default.
     */
    public void load() {
        Preferences prefs = Gdx.app.getPreferences(PREFS_NAME);

        // Load Difficulty
        String diffName = prefs.getString("difficulty", Difficulty.MEDIUM.name());
        try {
            currentDifficulty = Difficulty.valueOf(diffName);
        } catch (IllegalArgumentException e) {
            currentDifficulty = Difficulty.MEDIUM;
        }

        // Load Game Mode
        isAdvancedMode = prefs.getBoolean("isAdvancedMode", false);

        // Load Key Bindings
        keyBindings.clear();
        for (String action : keyBindingDescriptions.keySet()) {
            int defaultKey = getDefaultKeyForAction(action);
            keyBindings.put(action, prefs.getInteger(action, defaultKey));
        }

        // Save defaults if any were missing
        prefs.flush();
    }

    private Preferences getPrefs() {
        return Gdx.app.getPreferences(PREFS_NAME);
    }

    // --- Difficulty ---
    public Difficulty getDifficulty() {
        return currentDifficulty;
    }

    public void setDifficulty(Difficulty difficulty) {
        this.currentDifficulty = difficulty;
        getPrefs().putString("difficulty", difficulty.name()).flush();
    }

    // --- Game Mode ---
    public boolean isAdvancedMode() {
        return isAdvancedMode;
    }

    public void setAdvancedMode(boolean isAdvanced) {
        this.isAdvancedMode = isAdvanced;
        getPrefs().putBoolean("isAdvancedMode", isAdvanced).flush();
    }

    // --- Key Bindings ---
    public int getKey(String action) {
        return keyBindings.getOrDefault(action, Input.Keys.UNKNOWN);
    }

    public void setKey(String action, int keycode) {
        keyBindings.put(action, keycode);
        getPrefs().putInteger(action, keycode).flush();
    }

    public Map<String, String> getKeyBindingDescriptions() {
        return keyBindingDescriptions;
    }

    private int getDefaultKeyForAction(String action) {
        switch (action) {
            case "MOVE_FORWARD": return Input.Keys.UP;
            case "MOVE_BACK": return Input.Keys.DOWN;
            case "TURN_LEFT": return Input.Keys.LEFT;
            case "TURN_RIGHT": return Input.Keys.RIGHT;
            case "INTERACT": return Input.Keys.O;
            case "PICKUP_DROP": return Input.Keys.P;
            case "USE_ITEM": return Input.Keys.U;
            case "DESCEND": return Input.Keys.D;
            case "REST": return Input.Keys.R;
            case "SWAP_HANDS": return Input.Keys.S;
            case "SWAP_PACK": return Input.Keys.E;
            case "ROTATE_PACK": return Input.Keys.T;
            case "ATTACK": return Input.Keys.A;
            case "MAP": return Input.Keys.M;
            default: return Input.Keys.UNKNOWN;
        }
    }
}
