package com.bpm.minotaur.gamedata.save;

/**
 * Lightweight DTO representing a summary of a save slot for the Load Game UI.
 * This can be loaded quickly without parsing full chunk, inventory, or world models.
 */
public class SlotMetadata {
    public int slotIndex = 1;
    public boolean isOccupied = false;
    public String characterName = "Hero";
    public String characterClass = "Warrior";
    public int level = 1;
    public int currentHP = 20;
    public int maxHP = 20;
    public int currentMP = 10;
    public int maxMP = 10;
    public int depthLevel = 1;
    public String locationName = "Shelter (Floor 1)";
    public String gameMode = "MODERN"; // "MODERN" or "CLASSIC"
    public int tormentLevel = 0;
    public int deathCount = 0;
    public int maxDeaths = 50;
    public long playTimeSeconds = 0;
    public long lastPlayedTimestamp = 0;

    public SlotMetadata() {
    }

    public SlotMetadata(int slotIndex) {
        this.slotIndex = slotIndex;
        this.isOccupied = false;
    }
}
