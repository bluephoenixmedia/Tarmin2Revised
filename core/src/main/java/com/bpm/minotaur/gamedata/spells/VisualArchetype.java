package com.bpm.minotaur.gamedata.spells;

import com.badlogic.gdx.graphics.Color;

/**
 * Visual archetype taxonomy for all spells in Tarmin2.
 * Categorizes spells into 12 distinct aesthetic and physical presentation families.
 */
public enum VisualArchetype {
    FLAME_BOLT(
            new Color(1.0f, 0.45f, 0.1f, 1.0f),    // Fiery orange
            new Color(1.0f, 0.9f, 0.2f, 1.0f),     // Bright yellow
            "spell_flame",
            false,
            true,   // Heat shimmer vignette
            false,
            new Color(0.12f, 0.12f, 0.12f, 0.85f), // Charred scorch mark
            "*"
    ),
    FROST_RAY(
            new Color(0.3f, 0.85f, 1.0f, 1.0f),    // Crystalline cyan
            new Color(0.9f, 0.95f, 1.0f, 1.0f),    // Ice white
            "spell_frost",
            false,
            true,   // Frost edge vignette
            false,
            new Color(0.7f, 0.9f, 1.0f, 0.75f),    // Frost decal
            "+"
    ),
    LIGHTNING_ARC(
            new Color(1.0f, 1.0f, 0.3f, 1.0f),     // Electric yellow
            new Color(0.8f, 0.95f, 1.0f, 1.0f),    // Blue-white
            "spell_lightning",
            true,   // Electric pulse shockwave
            false,
            false,
            new Color(0.2f, 0.2f, 0.25f, 0.7f),    // Fulgurite mark
            "/"
    ),
    FORCE_MISSILE(
            new Color(0.35f, 0.75f, 1.0f, 1.0f),   // Radiant arcane cyan
            new Color(0.95f, 0.4f, 1.0f, 1.0f),    // Magenta spiral
            "spell_force",
            false,
            false,
            false,
            new Color(0.3f, 0.4f, 0.6f, 0.5f),     // Arcane residue
            "o"
    ),
    EXPLOSIVE_BURST(
            new Color(1.0f, 0.3f, 0.05f, 1.0f),    // Blazing crimson
            new Color(1.0f, 0.8f, 0.1f, 1.0f),     // Orange-gold core
            "spell_explosion",
            true,   // Radial heat shockwave
            true,   // Fire vignette
            false,
            new Color(0.08f, 0.08f, 0.08f, 0.9f),  // Deep scorched crater
            "#"
    ),
    HOLY_RADIANCE(
            new Color(1.0f, 0.88f, 0.4f, 1.0f),    // Golden sunlight
            new Color(1.0f, 1.0f, 0.9f, 1.0f),     // Pure white halo
            "spell_holy",
            false,
            true,   // Golden vignette
            false,
            new Color(1.0f, 0.92f, 0.5f, 0.6f),    // Hallowed rune circle
            "^"
    ),
    NECROTIC_DRAIN(
            new Color(0.55f, 0.1f, 0.75f, 1.0f),   // Dark purple
            new Color(0.15f, 0.85f, 0.3f, 1.0f),   // Toxic bile green
            "spell_necrotic",
            false,
            true,   // Dark shadowy vignette
            false,
            new Color(0.18f, 0.05f, 0.22f, 0.8f),  // Blighted rot mark
            "x"
    ),
    TOXIC_CLOUD(
            new Color(0.4f, 0.9f, 0.15f, 1.0f),    // Chartreuse green
            new Color(0.2f, 0.6f, 0.1f, 1.0f),     // Poison fog
            "spell_toxic",
            false,
            true,   // Green toxic haze
            false,
            new Color(0.25f, 0.75f, 0.15f, 0.7f),  // Corrosive acid pool
            "%"
    ),
    SPATIAL_WARP(
            new Color(0.7f, 0.2f, 0.95f, 1.0f),    // Violet void
            new Color(0.2f, 1.0f, 0.9f, 1.0f),     // Dimensional turquoise
            "spell_warp",
            true,   // Chromatic distortion wave
            false,
            true,   // Spatial glitch tear
            new Color(0.4f, 0.15f, 0.6f, 0.6f),    // Dimensional tear mark
            "@"
    ),
    ARCANE_WARD(
            new Color(0.25f, 0.65f, 1.0f, 1.0f),   // Aegis sapphire
            new Color(0.8f, 0.9f, 1.0f, 1.0f),     // Barrier white
            "spell_ward",
            false,
            true,   // Protective blue vignette
            false,
            new Color(0.2f, 0.5f, 0.8f, 0.5f),     // Warded circle
            "&"
    ),
    PSYCHIC_SHOCK(
            new Color(1.0f, 0.15f, 0.6f, 1.0f),    // Neon magenta
            new Color(0.6f, 0.1f, 0.9f, 1.0f),     // Deep purple
            "spell_psychic",
            true,   // Disorienting distortion ripple
            false,
            true,   // Minor glitch
            new Color(0.5f, 0.1f, 0.4f, 0.5f),     // Psychic resonance
            "?"
    ),
    THUNDER_CONCUSSION(
            new Color(0.75f, 0.85f, 0.95f, 1.0f),  // Storm grey-white
            new Color(0.4f, 0.6f, 0.9f, 1.0f),     // Concussive blue
            "spell_thunder",
            true,   // Strong concussive shockwave
            false,
            false,
            new Color(0.3f, 0.35f, 0.45f, 0.7f),   // Shockwave floor crack
            "!"
    );

    private final Color primaryColor;
    private final Color secondaryColor;
    private final String soundKey;
    private final boolean hasShockwave;
    private final boolean hasVignette;
    private final boolean hasGlitch;
    private final Color decalColor;
    private final String particleAscii;

    VisualArchetype(Color primaryColor, Color secondaryColor, String soundKey,
                    boolean hasShockwave, boolean hasVignette, boolean hasGlitch,
                    Color decalColor, String particleAscii) {
        this.primaryColor = primaryColor;
        this.secondaryColor = secondaryColor;
        this.soundKey = soundKey;
        this.hasShockwave = hasShockwave;
        this.hasVignette = hasVignette;
        this.hasGlitch = hasGlitch;
        this.decalColor = decalColor;
        this.particleAscii = particleAscii;
    }

    public Color getPrimaryColor() {
        return primaryColor;
    }

    public Color getSecondaryColor() {
        return secondaryColor;
    }

    public String getSoundKey() {
        return soundKey;
    }

    public boolean hasShockwave() {
        return hasShockwave;
    }

    public boolean hasVignette() {
        return hasVignette;
    }

    public boolean hasGlitch() {
        return hasGlitch;
    }

    public Color getDecalColor() {
        return decalColor;
    }

    public String getParticleAscii() {
        return particleAscii;
    }

    public static VisualArchetype fromString(String name) {
        if (name == null || name.isEmpty()) {
            return FORCE_MISSILE;
        }
        try {
            return VisualArchetype.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return FORCE_MISSILE;
        }
    }
}
