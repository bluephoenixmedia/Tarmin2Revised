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

    public Map<String, UnlockRequirement> unlockPlan = new HashMap<>(); // ItemId -> Requirement

    public transient java.util.List<String> sessionUnlocks = new java.util.ArrayList<>();

    public UnlockData() {
        // Default constructor for Json serialization
    }

    public static class UnlockRequirement {
        public enum GateType {
            TOTAL_STEPS, TOTAL_DOORS, MONSTER_KILLS, DEEPEST_LEVEL
        }

        public GateType type;
        public long targetValue;

        public UnlockRequirement() {
        }

        public UnlockRequirement(GateType type, long targetValue) {
            this.type = type;
            this.targetValue = targetValue;
        }
    }
}
