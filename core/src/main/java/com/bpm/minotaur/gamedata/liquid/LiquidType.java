package com.bpm.minotaur.gamedata.liquid;

import com.badlogic.gdx.graphics.Color;

/**
 * Classifies liquids present in Flooded Chunks and environmental pools.
 */
public enum LiquidType {
    NONE((byte) 0, "Dry Ground", "Solid dry stone or earthen ground.", Color.CLEAR),
    WATER((byte) 1, "Murky Shallows", "Stagnant, frigid water that slows movement and corrodes unprotected metal.", new Color(0.18f, 0.54f, 0.58f, 0.90f)),
    BLOOD((byte) 2, "Sanguine Deluge", "Thick coppery blood pooling from ancient battles, driving beasts into a frenzy.", new Color(0.75f, 0.05f, 0.05f, 0.7f)),
    BLACK_MUCK((byte) 3, "Necrotic Sludge", "Bubbling tar-like viscous muck radiating toxic rot.", new Color(0.20f, 0.08f, 0.28f, 0.92f)),
    QUICKSAND((byte) 4, "Sinking Sand", "Collapsing desert dunes that slow movement and drag down heavy armor.", new Color(0.82f, 0.71f, 0.45f, 0.85f)),
    FREEZING_SLUSH((byte) 5, "Freezing Slush", "Partially frozen icy mire that hampers footsteps and rapidly drains body warmth.", new Color(0.60f, 0.78f, 0.85f, 0.88f)),
    PACKED_ICE((byte) 6, "Glacial Ice", "Glassy, low-friction permafrost ice that accelerates sliding and offers no purchase.", new Color(0.40f, 0.70f, 0.90f, 0.75f)),
    /** The chasm under the Bridge of Souls: nothing living or dead can wade into it. */
    MOLTEN_FIRE((byte) 7, "Hellfire Chasm", "A gulf of molten fire with no bottom anyone has found.", new Color(1.0f, 0.36f, 0.06f, 0.95f));

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

    /** Whether nothing can step into it at all: a chasm, not a puddle. */
    public boolean isImpassable() {
        return this == MOLTEN_FIRE;
    }

    public static LiquidType fromId(byte id) {
        for (LiquidType t : values()) {
            if (t.id == id) return t;
        }
        return NONE;
    }
}
