package com.bpm.minotaur.gamedata.gore;

import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Array;

import java.util.EnumMap;
import java.util.Map;

/**
 * Manages the pool of 40 rich combat wound decals extracted from gore spritesheets,
 * categorizing them by weapon wound archetype (SLASH, SLICE, STAB, PUNCTURE, CRUSH)
 * with multi-category support and graceful headless fallbacks.
 */
public class WoundDecalRegistry {

    private static WoundDecalRegistry instance;

    // Categorized decal indices (1-indexed matching decals/wound in gore.atlas)
    public static final int[] SLASH_INDICES = {6, 10, 11, 14, 17, 18, 19, 26, 30, 31, 37, 38, 39};
    public static final int[] SLICE_INDICES = {2, 6, 10, 11, 17, 18, 22, 30, 37, 38};
    public static final int[] STAB_INDICES = {1, 2, 4, 5, 8, 13, 21, 22, 24, 25, 28, 33};
    public static final int[] PUNCTURE_INDICES = {1, 3, 4, 5, 13, 21, 23, 24, 25, 33, 34};
    public static final int[] CRUSH_INDICES = {7, 9, 12, 14, 15, 16, 20, 24, 27, 28, 29, 32, 34, 35, 36, 40};

    private final Map<WoundDecal.WoundType, Array<TextureRegion>> categorizedPools = new EnumMap<>(WoundDecal.WoundType.class);
    private final Array<TextureRegion> allRegions = new Array<>();
    private boolean loaded = false;

    public static synchronized WoundDecalRegistry getInstance() {
        if (instance == null) {
            instance = new WoundDecalRegistry();
        }
        return instance;
    }

    public WoundDecalRegistry() {
        for (WoundDecal.WoundType type : WoundDecal.WoundType.values()) {
            categorizedPools.put(type, new Array<>());
        }
    }

    /**
     * Initializes decal pools from the provided TextureAtlas (typically gore.atlas).
     */
    public void loadFromAtlas(TextureAtlas atlas) {
        if (atlas == null) return;

        for (Array<TextureRegion> pool : categorizedPools.values()) {
            pool.clear();
        }
        allRegions.clear();

        // 1. Load all 40 decals by index
        for (int i = 1; i <= 40; i++) {
            TextureRegion r = atlas.findRegion("decals/wound", i);
            if (r != null) {
                allRegions.add(r);
            }
        }

        // 2. Populate categorized pools
        populateCategory(atlas, WoundDecal.WoundType.SLASH, SLASH_INDICES);
        populateCategory(atlas, WoundDecal.WoundType.SLICE, SLICE_INDICES);
        populateCategory(atlas, WoundDecal.WoundType.STAB, STAB_INDICES);
        populateCategory(atlas, WoundDecal.WoundType.PUNCTURE, PUNCTURE_INDICES);
        populateCategory(atlas, WoundDecal.WoundType.CRUSH, CRUSH_INDICES);

        loaded = !allRegions.isEmpty();
    }

    private void populateCategory(TextureAtlas atlas, WoundDecal.WoundType type, int[] indices) {
        Array<TextureRegion> pool = categorizedPools.get(type);
        for (int idx : indices) {
            TextureRegion r = atlas.findRegion("decals/wound", idx);
            if (r != null) {
                pool.add(r);
            }
        }
    }

    /**
     * Retrieves a random decal region appropriate for the given wound type.
     * Falls back to ProceduralDecalGenerator if no atlas textures are loaded.
     */
    public TextureRegion getRandomRegion(WoundDecal.WoundType type) {
        if (type == null) type = WoundDecal.WoundType.SLASH;

        Array<TextureRegion> pool = categorizedPools.get(type);
        if (pool != null && pool.size > 0) {
            return pool.random();
        }

        // Fallback to any loaded decal
        if (allRegions.size > 0) {
            return allRegions.random();
        }

        // Headless / fallback to procedural brush if GL context is active
        if (com.badlogic.gdx.Gdx.gl != null) {
            try {
                return ProceduralDecalGenerator.getInstance().getRegionForType(type);
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    public Array<TextureRegion> getPool(WoundDecal.WoundType type) {
        return categorizedPools.get(type);
    }

    public Array<TextureRegion> getAllRegions() {
        return allRegions;
    }

    public boolean isLoaded() {
        return loaded;
    }

    /**
     * Returns the aspect ratio (width / height) of the given texture region, or a sensible default.
     */
    public static float getAspectRatio(TextureRegion region, WoundDecal.WoundType type) {
        if (region != null && region.getRegionHeight() > 0) {
            return (float) region.getRegionWidth() / (float) region.getRegionHeight();
        }
        if (type != null) {
            switch (type) {
                case SLASH: return 4.0f;
                case SLICE: return 4.5f;
                case STAB: return 1.5f;
                case PUNCTURE: return 1.0f;
                case CRUSH: return 1.0f;
                case SCORCH: return 1.0f;
            }
        }
        return 1.0f;
    }
}
