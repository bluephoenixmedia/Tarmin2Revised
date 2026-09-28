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
    CONFUSION,
    WEAKENED,
    SLOWED,
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
    BLIND,
    BERZERK,
    SLEEP,
    RECOVERING,
    PARALYZED,
    FROZEN,
    /**
     * Standing in obscuring fog: you cannot see out and nothing can see in.
     *
     * <p>Positional rather than inflicted -- GameScreen syncs it against the tile you are on
     * each world turn, so it appears and clears as you walk. It must stay above FOCUSED in
     * this enum, because everything below that ordinal is treated as a buff.
     */
    OBSCURED,

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
    OMNISCIENT, // See map

    // Intrinsics (Permanent/Long-term)
    RESIST_FIRE,
    RESIST_COLD,
    RESIST_LIGHTNING,
    RESIST_POISON,
    TELEPATHY,
    INVISIBLE,
    SEE_INVISIBLE,

    // Caves of Qud Style Metabolic & Conditional Boons
    METABOLIZING,
    BLOOD_SURGE,
    SPIRITUAL_WARD,
    CARAPACE_HARDENING,
    NIGHT_HUNTER,

    // Open5e Tactical Buffs & Intrinsics
    HASTED,
    HEROISM,
    GIANT_STRENGTH,
    INVULNERABILITY,
    DIMINUTIVE,
    ENLARGED,
    RESIST_ACID,
    RESIST_NECROTIC,
    FREE_ACTION,
    WARMTH,
    MOTE_OF_LIGHT,

    // Weather Survival Exposure (tiered body-temperature debuffs)
    CHILLED,
    HYPOTHERMIA,
    HEATSTROKE;

    // We can add fields here later, e.g.,
    // private final boolean isDebuff;
    // StatusEffectType(boolean isDebuff) { this.isDebuff = isDebuff; }
}
