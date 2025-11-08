package com.bpm.minotaur.gamedata;

/**
 * A simple data container representing a single modifier applied to an item.
 * Designed for easy serialization with ChunkData.
 */
public class ItemModifier {

    public ModifierType type;
    public int value;
    public String displayName; // e.g., "Fiery", "+2", "of Ice Warding"

    /**
     * No-arg constructor required for JSON deserialization.
     */
    public ItemModifier() {}

    public ItemModifier(ModifierType type, int value, String displayName) {
        this.type = type;
        this.value = value;
        this.displayName = displayName;
    }
}
