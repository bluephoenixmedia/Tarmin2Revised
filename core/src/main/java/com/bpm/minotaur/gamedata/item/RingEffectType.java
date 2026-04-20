package com.bpm.minotaur.gamedata.item;

/**
 * Defines the true effects of rings.
 */
public enum RingEffectType {

    REGENERATION("Regeneration"),
    PROTECTION("Protection"),
    STRENGTH("Strength"),
    INVISIBILITY("Invisibility"),
    LEVITATION("Levitation"),
    SEARCHING("Searching");

    private final String baseName;

    RingEffectType(String baseName) {
        this.baseName = baseName;
    }

    public String getBaseName() {
        return baseName;
    }
}
