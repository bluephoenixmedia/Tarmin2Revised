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

    // Subterranean forest chunks (dungeon levels 2+ tagged FOREST): deep cavern
    // moss green / near-black twilight, not the blinding torch-lit gray this used
    // to compute (a missing-parens bug in calculateTorchBrightness previously
    // pushed the multiplier well past 1.0, washing this out to near-white).
    FOREST(true, true, 8, new Color(0x0a / 255f, 0x18 / 255f, 0x0f / 255f, 1.0f), "images/skybox/skybox_forest.png",
            "images/floor_forest.png", "images/forest_cliff.png"),
    DESERT(true, true, 20, new Color(0.7f, 0.6f, 0.4f, 1.0f)),
    PLAINS(true, true, 25, new Color(0.3f, 0.5f, 0.2f, 1.0f)),
    MOUNTAINS(false, false, 100, null), // Not seamless, impassable
    LAKELANDS(true, true, 15, new Color(0.4f, 0.4f, 0.7f, 1.0f)),
    OCEAN(false, false, 100, null); // Not seamless, impassable

    // --- New Properties ---
    private final boolean isSeamless;
    private final boolean hasFogOfWar;
    private final int fogDistance;
    private final Color fogColor;

    // --- Modern Rendering Properties ---
    private final String skyboxTexturePath;
    private final String floorTexturePath;
    private final String wallTexturePath;

    Biome(boolean isSeamless, boolean hasFog, int fogDistance, Color fogColor) {
        this(isSeamless, hasFog, fogDistance, fogColor, null, null, null);
    }

    Biome(boolean isSeamless, boolean hasFog, int fogDistance, Color fogColor, String skyboxPath, String floorPath) {
        this(isSeamless, hasFog, fogDistance, fogColor, skyboxPath, floorPath, null);
    }

    Biome(boolean isSeamless, boolean hasFog, int fogDistance, Color fogColor, String skyboxPath, String floorPath, String wallPath) {
        this.isSeamless = isSeamless;
        this.hasFogOfWar = hasFog;
        this.fogDistance = fogDistance;
        this.fogColor = fogColor;
        this.skyboxTexturePath = skyboxPath;
        this.floorTexturePath = floorPath;
        this.wallTexturePath = wallPath;
    }

    public boolean isSeamless() {
        return isSeamless;
    }

    public boolean hasFogOfWar() {
        return hasFogOfWar;
    }

    public int getFogDistance() {
        return fogDistance;
    }

    public Color getFogColor() {
        return fogColor;
    }

    public String getSkyboxTexturePath() {
        return skyboxTexturePath;
    }

    public String getFloorTexturePath() {
        return floorTexturePath;
    }

    public String getWallTexturePath() {
        return wallTexturePath;
    }
}
