package com.bpm.minotaur.gamedata.liquid;

import com.badlogic.gdx.graphics.Color;

/**
 * Classifies liquids present in Flooded Chunks and environmental pools.
 */
public enum LiquidType {
    NONE((byte) 0, "Dry Ground", "Solid dry stone or earthen ground.", Color.CLEAR),
    WATER((byte) 1, "Murky Shallows", "Stagnant, frigid water that slows movement and corrodes unprotected metal.", new Color(0.2f, 0.5f, 0.8f, 0.6f)),
    BLOOD((byte) 2, "Sanguine Deluge", "Thick coppery blood pooling from ancient battles, driving beasts into a frenzy.", new Color(0.75f, 0.05f, 0.05f, 0.7f)),
    BLACK_MUCK((byte) 3, "Necrotic Sludge", "Bubbling tar-like viscous muck radiating toxic rot.", new Color(0.12f, 0.05f, 0.18f, 0.85f));

    private final byte id;
    private final String displayName;
    private final String description;
    private final Color color;

    LiquidType(byte id, String displayName, String description, Color color) {
        this.id = id;
        this.displayName = displayName;
        this.description = description;
        this.color = color;
    }

    public byte getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public Color getColor() {
        return color;
    }

    public static LiquidType fromId(byte id) {
        for (LiquidType t : values()) {
            if (t.id == id) return t;
        }
        return NONE;
    }
}
