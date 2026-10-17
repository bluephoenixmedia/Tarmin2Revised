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
            new String[]{"brazier", "stalagmite", "skull_pile"}, false),

    // Gash interiors (plan T1.13, D43): the strata under a seal site, one to each doctrine's
    // interiorTheme in doctrines.json, in that doctrine's colours. Never in a regional band.
    // Props are passable ones only, as the decorator scatters through open floor.
    FLAYED_CATHEDRAL("The Flayed Cathedral", new Color(0.24f, 0.06f, 0.06f, 1f), 5f, new Color(1.0f, 0.35f, 0.30f, 1f),
            new String[]{"chandelier", "brazier", "skull_pile", "hanging_vine"}, "flayed_cathedral"),
    HOOK_FOUNDRY("The Hook Foundry", new Color(0.12f, 0.13f, 0.15f, 1f), 5f, new Color(1.0f, 0.55f, 0.20f, 1f),
            new String[]{"arena_chain", "brazier", "helmet_pile"}, "hook_foundry"),
    SILENT_GALLERIES("The Silent Galleries", new Color(0.26f, 0.25f, 0.23f, 1f), 7f, new Color(0.85f, 0.88f, 1.0f, 1f),
            new String[]{"chandelier", "skull_pile", "brazier"}, "silent_galleries"),
    GILDED_CHARNEL("The Gilded Charnel", new Color(0.20f, 0.16f, 0.05f, 1f), 6f, new Color(1.0f, 0.82f, 0.35f, 1f),
            new String[]{"bone_pile", "helmet_pile", "chandelier"}, "gilded_charnel"),
    PYRE_CLOISTERS("The Pyre Cloisters", new Color(0.14f, 0.12f, 0.11f, 1f), 5f, new Color(1.0f, 0.45f, 0.15f, 1f),
            new String[]{"brazier", "campfire", "skull_pile"}, "pyre_cloisters"),
    BONE_ARCHITECTURE("The Bone Architecture", new Color(0.24f, 0.22f, 0.18f, 1f), 6f, new Color(0.95f, 0.90f, 0.70f, 1f),
            new String[]{"bone_pile", "skull_pile", "brazier"}, "bone_architecture"),
    GLASS_GULLET("The Glass Gullet", new Color(0.10f, 0.18f, 0.20f, 1f), 7f, new Color(0.55f, 0.95f, 1.0f, 1f),
            new String[]{"algae_mat", "drowned_cache", "skull_pile"}, "glass_gullet"),
    OBSIDIAN_RIFT("The Obsidian Rift", new Color(0.12f, 0.04f, 0.04f, 1f), 5f, new Color(0.90f, 0.20f, 0.15f, 1f),
            new String[]{"war_banner", "brazier", "skull_pile"}, "obsidian_rift");

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
    /** The doctrine interiorTheme this gash interior is, or null for a regional stratum. */
    public final String interiorTheme;

    Stratum(String displayName, Color fogColor, float fogDistance, Color glow, String[] props, boolean allowsTowns) {
        this(displayName, fogColor, fogDistance, glow, props, allowsTowns, null);
    }

    Stratum(String displayName, Color fogColor, float fogDistance, Color glow, String[] props, String interiorTheme) {
        this(displayName, fogColor, fogDistance, glow, props, false, interiorTheme);
    }

    Stratum(String displayName, Color fogColor, float fogDistance, Color glow, String[] props, boolean allowsTowns,
            String interiorTheme) {
        this.displayName = displayName;
        this.fogColor = fogColor;
        this.fogDistance = fogDistance;
        this.glow = glow;
        this.props = props;
        this.allowsTowns = allowsTowns;
        this.interiorTheme = interiorTheme;
    }

    /** The gash interior for a doctrine's {@code interiorTheme}, or null if none is authored. */
    public static Stratum interior(String theme) {
        if (theme == null) return null;
        for (Stratum s : values()) if (theme.equals(s.interiorTheme)) return s;
        return null;
    }
}
