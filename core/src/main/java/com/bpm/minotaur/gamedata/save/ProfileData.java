package com.bpm.minotaur.gamedata.save;

/**
 * Global profile data shared across all save slots.
 * Persisted to saves/profile.json.
 */
public class ProfileData {
    /**
     * @deprecated Moved to {@link SlotMetadata#classicModeUnlocked}. Still read
     *             so a player who earned it before the move keeps it.
     */
    @Deprecated
    public boolean classicModeUnlocked = false;
    public int totalRunsAttempted = 0;
    public int lastPlayedSlot = 1;

    public ProfileData() {
    }
}
