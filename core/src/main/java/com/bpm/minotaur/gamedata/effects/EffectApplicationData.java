// NEW FILE: core/src/main/java/com/bpm/minotaur/gamedata/effects/EffectApplicationData.java
package com.bpm.minotaur.gamedata.effects;

/**
 * A simple data class (POJO) used for loading effect application data
 * from JSON (e.g., from monsters.json or items.json).
 */
public class EffectApplicationData {
    public StatusEffectType type;
    public int duration;
    public int potency;
    public boolean stackable = false;
    public float chance = 1.0f; // Default to 100% chance

    // Public no-arg constructor for JSON serialization
    public EffectApplicationData() {}
}
