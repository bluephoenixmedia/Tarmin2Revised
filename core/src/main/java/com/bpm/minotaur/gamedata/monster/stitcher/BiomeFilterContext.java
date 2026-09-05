package com.bpm.minotaur.gamedata.monster.stitcher;

/**
 * Snapshot of environmental conditions used to weight part selection.
 * Values are normalised floats [0..1] unless noted.
 */
public class BiomeFilterContext {
    /** 0 = freezing, 0.5 = temperate, 1 = scorching. */
    public final float temperature;
    /** 0 = arid desert, 1 = submerged / tropical. */
    public final float humidity;
    /** Active biome tag string, e.g. "forest", "desert", "swamp". */
    public final String biomeTag;

    public BiomeFilterContext(float temperature, float humidity, String biomeTag) {
        this.temperature = temperature;
        this.humidity    = humidity;
        this.biomeTag    = biomeTag != null ? biomeTag : "";
    }

    public static BiomeFilterContext defaults() {
        return new BiomeFilterContext(0.5f, 0.5f, "");
    }

    /** Returns true when temperature classifies as "cold" (< 0.35). */
    public boolean isCold()  { return temperature < 0.35f; }
    /** Returns true when temperature classifies as "hot"  (> 0.65). */
    public boolean isHot()   { return temperature > 0.65f; }
    /** Returns true when humidity classifies as "wet"     (> 0.65). */
    public boolean isWet()   { return humidity > 0.65f; }
    /** Returns true when humidity classifies as "arid"    (< 0.35). */
    public boolean isArid()  { return humidity < 0.35f; }
}
