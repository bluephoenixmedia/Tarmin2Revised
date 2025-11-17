package com.bpm.minotaur.gamedata.item;

/**
 * A data class representing a "variant" of an item, typically a color,
 * that can spawn within a specific level range.
 * This class is loaded from the "variants" list in items.json.
 */
public class ItemVariant {
    public ItemColor color;
    public int minLevel;
    public int maxLevel;
    public int weight; // Used for weighted random selection

    public ItemVariant() {
        // Default constructor for Json parsing
    }
}
