package com.bpm.minotaur.generation;

import com.badlogic.gdx.graphics.Color;

/**
 * Defines all possible biome types for the world generator.
 * Now includes properties for seamless transitions and fog of war.
 */
public enum Biome {
    // Special static biomes
    MAZE(false, false, 100, null), // isSeamless, hasFog, fogDistance, fogColor

    // Wilderness biomes
    // --- [FIX] Changed fog color to BLACK for a true "torchlight" effect ---
    FOREST(true, true, 12, Color.BLACK),
    DESERT(true, true, 20, new Color(0.7f, 0.6f, 0.4f, 1.0f)),
    PLAINS(true, true, 25, new Color(0.3f, 0.5f, 0.2f, 1.0f)),
    MOUNTAINS(false, false, 100, null), // Not seamless, impassable
    LAKELANDS(true, true, 15, new Color(0.4f, 0.4f, 0.7f, 1.0f)),
    OCEAN(false, false, 100, null);  // Not seamless, impassable

    // --- New Properties ---
    private final boolean isSeamless;
    private final boolean hasFogOfWar;
    private final int fogDistance;
    private final Color fogColor;

    Biome(boolean isSeamless, boolean hasFog, int fogDistance, Color fogColor) {
        this.isSeamless = isSeamless;
        this.hasFogOfWar = hasFog;
        this.fogDistance = fogDistance;
        this.fogColor = fogColor;
    }

    public boolean isSeamless() { return isSeamless; }
    public boolean hasFogOfWar() { return hasFogOfWar; }
    public int getFogDistance() { return fogDistance; }
    public Color getFogColor() { return fogColor; }
}
