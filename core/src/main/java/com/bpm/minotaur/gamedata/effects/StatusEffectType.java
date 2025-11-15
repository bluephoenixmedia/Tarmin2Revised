package com.bpm.minotaur.gamedata.effects;

public enum StatusEffectType {
    // Negative Effects (Debuffs)
    POISONED,
    SICK,
    OVERBURDENED,
    HUNGRY,
    STARVING,
    THIRSTY,
    DEHYDRATED,
    CURSED,
    FEVER,
    VIRUS,
    CONFUSED,
    HALLUCINATING,
    SLOW,
    EXHAUSTED,
    COLD,
    HOT,
    FREEZING,
    OVERHEATING,
    WET,
    SOAKED,
    SEIZURE,
    BLEEDING,

    // Positive Effects (Buffs)
    FOCUSED,
    ADRENALINE_BOOST,
    HEALTHY,
    IMMUNE_BOOSTED,
    TEMP_STRENGTH,
    TEMP_SPEED,
    TEMP_HEALTH,
    // ... other stat boosts
    PSYCHIC,
    FLOATING,
    FLYING,
    SUPER_SPEED,
    HARDENED,
    SUPER_INTELLIGENT, // Identify items
    OMNISCIENT;       // See map

    // We can add fields here later, e.g.,
    // private final boolean isDebuff;
    // StatusEffectType(boolean isDebuff) { this.isDebuff = isDebuff; }
}
