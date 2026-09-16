package com.bpm.minotaur.generation.theme;

import com.badlogic.gdx.graphics.Color;

/**
 * Procedural themes that can be applied to world chunks, signaled by glowing Runes above border gates.
 */
public enum ChunkTheme {
    BLOOD_COLOSSEUM(
            "Blood Colosseum",
            "A blood-soaked open arena where monsters frenzy and slay each other. Victory awards a Crest of Valor.",
            "Blood Sigil (High Danger)",
            "ᚦ",
            new Color(0.95f, 0.1f, 0.1f, 1f),
            1),

    FLOODED_CAVERNS(
            "Flooded Caverns",
            "Submerged corridors pooling with murky waters, stagnant blood, and toxic black sludge.",
            "Aquatic Glyph (Hazards)",
            "ᛚ",
            new Color(0.1f, 0.75f, 0.95f, 1f),
            1),

    WANDERING_BATTALION(
            "Wandering Battalion",
            "An organized legion of Tarmin's guards marching under an elite Commander.",
            "Martial Crest (Patrol)",
            "ᛏ",
            new Color(0.95f, 0.8f, 0.2f, 1f),
            2),

    MAKESHIFT_GRAVEYARD(
            "Makeshift Graveyard",
            "An eerie potter's field tended by a solitary Gravedigger. Desecrating the crypts stirs the dead.",
            "Pale Crypt Mark (Undead)",
            "ᚱ",
            new Color(0.85f, 0.85f, 0.9f, 1f),
            1),

    RUINED_CASTLE(
            "Ruined Castle",
            "A fortified bastion compound housing an inner courtyard, armory, and the Castle Warden.",
            "Citadel Crown (Megastructure)",
            "ᚲ",
            new Color(0.8f, 0.3f, 0.95f, 1f),
            3),

    OVERGROWN_THICKET(
            "Overgrown Thicket",
            "Choked with bioluminescent fungi, strangling vines, and thorny bramble barriers.",
            "Verdant Sprout (Foliage)",
            "ᛒ",
            new Color(0.2f, 0.9f, 0.3f, 1f),
            1);

    private final String displayName;
    private final String description;
    private final String crypticOmen;
    private final String runeGlyph;
    private final Color runeColor;
    private final int minLevel;

    ChunkTheme(String displayName, String description, String crypticOmen, String runeGlyph, Color runeColor, int minLevel) {
        this.displayName = displayName;
        this.description = description;
        this.crypticOmen = crypticOmen;
        this.runeGlyph = runeGlyph;
        this.runeColor = runeColor;
        this.minLevel = minLevel;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public String getCrypticOmen() {
        return crypticOmen;
    }

    public String getRuneGlyph() {
        return runeGlyph;
    }

    public Color getRuneColor() {
        return runeColor;
    }

    public int getMinLevel() {
        return minLevel;
    }
}
