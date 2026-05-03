package com.bpm.minotaur.gamedata.item;

/**
 * Defines the true effects of rings.
 */
public enum RingEffectType {

    // --- Existing ---
    REGENERATION("Regeneration"),
    PROTECTION("Protection"),
    STRENGTH("Strength"),
    INVISIBILITY("Invisibility"),
    LEVITATION("Levitation"),
    SEARCHING("Searching"),

    // --- New: Build-defining rings ---
    /** +5 to effective DEX, feeds to-hit and crit chance. */
    DEXTERITY("Dexterity"),
    /** +5 to effective CON, feeds stamina and HP-per-level. */
    CONSTITUTION("Constitution"),
    /** +5 to effective INT, feeds spell power and MP-per-level. */
    INTELLIGENCE("Intelligence"),
    /** +5 to effective WIS, feeds spiritual defense and MP regen. */
    WISDOM("Wisdom"),
    /** +5 to effective AGI, feeds dodge and movement speed. */
    AGILITY("Agility"),
    /** +10% crit chance (stacks with DEX and BONUS_CRIT_CHANCE). */
    CRITICAL_EDGE("Critical Edge"),
    /** +12% dodge chance (stacks with AGI and BONUS_DODGE). */
    EVASION("Evasion"),
    /** +6 flat spell power (stacks with INT and BONUS_SPELL_POWER). */
    SPELL_MASTERY("Spell Mastery"),
    /** Shifts toxicity tier thresholds up by +15, delaying penalties. */
    FORTITUDE("Fortitude");

    private final String baseName;

    RingEffectType(String baseName) {
        this.baseName = baseName;
    }

    public String getBaseName() {
        return baseName;
    }
}
