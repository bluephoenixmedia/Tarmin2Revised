package com.bpm.minotaur.gamedata.spawntables;

import com.bpm.minotaur.gamedata.item.ItemColor;

/**
 * A generic data class for a weighted, level-gated spawn table entry.
 */
public class SpawnTableEntry {
    public String type; // The enum name (e.g., "GIANT_ANT" or "BOW")
    public int minLevel;
    public int maxLevel;
    public int weight;
    public ItemColor keyColor; // Only used by containerSpawnTable

    public SpawnTableEntry() {}
}
