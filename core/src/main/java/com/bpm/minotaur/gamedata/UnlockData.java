package com.bpm.minotaur.gamedata;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class UnlockData {
    // --- Statistics ---
    public long totalSteps = 0;
    public long totalDoorsOpened = 0;
    public int deepestLevelReached = 0;

    // Map of MonsterType (String) -> Count
    public Map<String, Integer> monsterKills = new HashMap<>();

    // --- Unlocks ---
    // A set of IDs for items/features the player has unlocked.
    // Example IDs: "weapon_longsword", "class_ranger", "item_magic_lamp"
    public Set<String> unlockedContent = new HashSet<>();

    public UnlockData() {
        // Default constructor for Json serialization
    }
}
