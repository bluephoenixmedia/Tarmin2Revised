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
    FOREST(true, true, 30, applyTorchLighting(Color.LIGHT_GRAY, 5.0f, new Color())),
    DESERT(true, true, 20, new Color(0.7f, 0.6f, 0.4f, 1.0f)),
    PLAINS(true, true, 25, new Color(0.3f, 0.5f, 0.2f, 1.0f)),
    MOUNTAINS(false, false, 100, null), // Not seamless, impassable
    LAKELANDS(true, true, 15, new Color(0.4f, 0.4f, 0.7f, 1.0f)),
    OCEAN(false, false, 100, null);  // Not seamless, impassable

    public static final float TORCH_FULL_BRIGHTNESS_RADIUS = 1.0f;  // Distance where lighting is at 100%
    public static final float TORCH_FADE_START = 2.0f;              // Distance where dimming starts
    public static final float TORCH_FADE_END = 9.0f;               // Distance where it's completely dark
    public static final float TORCH_MIN_BRIGHTNESS = 0.15f;         // Minimum brightness (never completely black)
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

    /**
     * Applies torch lighting to a color by darkening it based on distance.
     *
     * @param originalColor The original color to darken
     * @param distance The distance from the player
     * @param outputColor Reusable Color object to store the result
     * @return The darkened color
     */
    private static Color applyTorchLighting(Color originalColor, float distance, Color outputColor) {
        float brightness = calculateTorchBrightness(distance);
        outputColor.set(
            originalColor.r * brightness,
            originalColor.g * brightness,
            originalColor.b * brightness,
            originalColor.a
        );
        return outputColor;
    }

    /**
     * Calculates the brightness multiplier based on distance from player.
     * Creates a torch effect with full brightness close to player, fading to darkness at distance.
     *
     * @param distance The perpendicular distance from the player
     * @return A brightness value between TORCH_MIN_BRIGHTNESS and 1.0
     */
    private static float calculateTorchBrightness(float distance) {
        if (distance <= TORCH_FULL_BRIGHTNESS_RADIUS) {
            return 1.0f; // Full brightness close to player
        } else if (distance <= TORCH_FADE_START) {
            // Gradual fade from full brightness to dimming
            float fadeRatio = (distance - TORCH_FULL_BRIGHTNESS_RADIUS) / (TORCH_FADE_START -TORCH_FULL_BRIGHTNESS_RADIUS);
            return 1.0f - (fadeRatio * (1.0f - 0.8f)); // Fade from 100% to 80%
        } else if (distance <= TORCH_FADE_END) {
            // Main dimming zone
            float fadeRatio = (distance - TORCH_FADE_START) / TORCH_FADE_END - TORCH_FADE_START;
            return Math.max(TORCH_MIN_BRIGHTNESS, 0.8f - (fadeRatio * (0.8f - TORCH_MIN_BRIGHTNESS)));
        } else {
            return TORCH_MIN_BRIGHTNESS; // Minimum brightness at maximum distance
        }
    }
}
