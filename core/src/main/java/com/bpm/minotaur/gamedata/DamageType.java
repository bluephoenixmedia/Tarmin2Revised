package com.bpm.minotaur.gamedata;

/**
 * Enumerates all possible damage types for attacks and effects.
 * Used for calculating resistances.
 */
public enum DamageType {
    PHYSICAL,       // Standard War Weapon damage
    SPIRITUAL,      // Standard Spiritual Weapon damage
    FIRE,
    ICE,
    POISON,
    BLEED,
    DISEASE,
    DARK,
    LIGHT,
    SORCERY,
    MAGICAL         // Added to support new monsters (Lich, Vampire, etc.)
}
