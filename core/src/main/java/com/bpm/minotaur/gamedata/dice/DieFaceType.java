package com.bpm.minotaur.gamedata.dice;

/**
 * Represents the type of symbol on a die face.
 * This determines the basic effect when solved.
 */
public enum DieFaceType {
    BLANK, // No effect
    SWORD, // Deal Physical Damage
    SHIELD, // Block Physical Damage
    SKULL, // Risky: Might hurt player or trigger execution
    FIRE, // Fire Damage (Elemental)
    ICE, // Ice Damage / Freeze
    LIGHTNING, // Lightning Damage / Chain
    HEART, // Heal Player
    MOON, // Spiritual/Mana restore or Magic Damage
    CRIT, // Multiplier for other dice
    POISON, // Damage over time
    ASH, // Failed fire roll / clutter
    GOLD, // Economy gain
    BULLSEYE, // Guaranteed hit / accuracy bonus
    GLANCING, // Reduced damage hit
    PARRY, // Counter-attack block
    CURSE, // Debuff or risky trade
    BONE // Necromancy resource
}
