package com.bpm.minotaur.generation;

import com.badlogic.gdx.graphics.Color;

/**
 * What the strata are made of, chunk by chunk (Houses of the Maze plan D36, T4.1). Every stratum
 * keeps the maze's layout; a stratum gives it its air, its light and what grows or lies in it.
 * Depth decides which strata a level can hold, and regional noise decides where.
 */
public enum Stratum {
    MAZE("The Maze", null, 0f, null, new String[0], false),
    FUNGAL_FOREST("Fungal Forest", new Color(0.10f, 0.22f, 0.12f, 1f), 6f, new Color(0.35f, 0.95f, 0.45f, 1f),
            new String[]{"glowing_mushroom", "mushroom_cluster", "twisted_root"}, true),
    FLOODED_HALLS("Flooded Halls", new Color(0.08f, 0.16f, 0.22f, 1f), 7f, new Color(0.35f, 0.65f, 0.95f, 1f),
            new String[]{"stalagmite", "twisted_root", "skull_pile"}, true),
    OSSUARY("Ossuary", new Color(0.22f, 0.20f, 0.17f, 1f), 6f, new Color(0.95f, 0.85f, 0.60f, 1f),
            new String[]{"bone_pile", "skull_pile", "sealed_tomb", "brazier"}, true),
    MAGMA_DEEPS("Magma Deeps", new Color(0.30f, 0.10f, 0.05f, 1f), 5f, new Color(1.0f, 0.42f, 0.12f, 1f),
            new String[]{"brazier", "stalagmite", "skull_pile"}, false);

    public final String displayName;
    /** Fog this stratum's air takes, or null to leave the strata's own darkness alone. */
    public final Color fogColor;
    public final float fogDistance;
    /** The colour of the glow its scattered lights give, or null for none. */
    public final Color glow;
    /** Props from props.json scattered through it. */
    public final String[] props;
    /** Whether a town can stand here (plan D36, D37). */
    public final boolean allowsTowns;

    Stratum(String displayName, Color fogColor, float fogDistance, Color glow, String[] props, boolean allowsTowns) {
        this.displayName = displayName;
        this.fogColor = fogColor;
        this.fogDistance = fogDistance;
        this.glow = glow;
        this.props = props;
        this.allowsTowns = allowsTowns;
    }
}
