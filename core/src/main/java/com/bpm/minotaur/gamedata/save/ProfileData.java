package com.bpm.minotaur.gamedata.save;

/**
 * Global profile data shared across all save slots.
 * Persisted to saves/profile.json.
 */
public class ProfileData {
    public boolean classicModeUnlocked = false;
    public int totalRunsWon = 0;
    public int totalRunsAttempted = 0;
    public int totalMinotaursSlain = 0;
    public int highestTormentCleared = 0;
    public int lastPlayedSlot = 1;

    public ProfileData() {
    }
}
