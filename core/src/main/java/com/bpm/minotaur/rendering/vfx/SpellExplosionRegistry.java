package com.bpm.minotaur.rendering.vfx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Disposable;

import java.util.EnumMap;
import java.util.Map;

/**
 * Manages high-resolution (256x256 frame) animated explosion spritesheets from BearFX.
 * Provides frame-accurate slicing, caching, and playback for 3D in-world billboard bursts
 * and spell impacts (Fireball, Lightning Bolt, Summoning Rifts, Teleport Cyclones).
 */
public class SpellExplosionRegistry implements Disposable {

    public enum ExplosionType {
        FIRE("images/vfx/explosions/fire_explosion_256x256px.png", 8, 7, 56, 0.60f),
        ELECTRIC("images/vfx/explosions/electric_explosion_256x256px.png", 7, 7, 49, 0.55f),
        ICE("images/vfx/explosions/ice_explosion_256x256px.png", 8, 7, 56, 0.60f),
        TOXIC("images/vfx/explosions/toxic_explosion_256x256px.png", 9, 8, 72, 0.70f),
        VOID("images/vfx/explosions/void_explosion_256x256px.png", 9, 8, 72, 0.70f),
        WIND("images/vfx/explosions/wind_explosion_256x256px.png", 7, 7, 49, 0.55f),
        BLOOD("images/vfx/explosions/blood_explosion_256x256px.png", 7, 7, 49, 0.55f),
        CONCUSSIVE("images/vfx/explosions/concussive_explosion_256x256px.png", 6, 6, 36, 0.45f),
        FRAG("images/vfx/explosions/frag_explosion_256x256px.png", 7, 6, 42, 0.50f),
        HOLY_CROSS("images/vfx/explosions/laser_cross_explosion_256x256px.png", 8, 7, 56, 0.60f),
        STANDARD("images/vfx/explosions/standard_explosion_256x256px.png", 8, 7, 56, 0.60f);

        private final String assetPath;
        private final int cols;
        private final int rows;
        private final int frameCount;
        private final float defaultDuration;

        ExplosionType(String assetPath, int cols, int rows, int frameCount, float defaultDuration) {
            this.assetPath = assetPath;
            this.cols = cols;
            this.rows = rows;
            this.frameCount = frameCount;
            this.defaultDuration = defaultDuration;
        }

        public String getAssetPath() {
            return assetPath;
        }

        public int getCols() {
            return cols;
        }

        public int getRows() {
            return rows;
        }

        public int getFrameCount() {
            return frameCount;
        }

        public float getDefaultDuration() {
            return defaultDuration;
        }
    }

    private static SpellExplosionRegistry instance;

    private final Map<ExplosionType, TextureRegion[]> cachedAnimations = new EnumMap<>(ExplosionType.class);
    private final Map<ExplosionType, Texture> fallbackTextures = new EnumMap<>(ExplosionType.class);

    private SpellExplosionRegistry() {
    }

    public static synchronized SpellExplosionRegistry getInstance() {
        if (instance == null) {
            instance = new SpellExplosionRegistry();
        }
        return instance;
    }

    /** Pre-queues all explosion textures into the central AssetManager. */
    public void queueAssets(AssetManager assetManager) {
        if (assetManager == null) return;
        for (ExplosionType type : ExplosionType.values()) {
            if (!assetManager.isLoaded(type.getAssetPath(), Texture.class)) {
                assetManager.load(type.getAssetPath(), Texture.class);
            }
        }
    }

    /** Slices and caches the animation frames once assets are loaded. */
    public void init(AssetManager assetManager) {
        for (ExplosionType type : ExplosionType.values()) {
            if (cachedAnimations.containsKey(type)) continue;

            Texture texture = null;
            if (assetManager != null && assetManager.isLoaded(type.getAssetPath(), Texture.class)) {
                texture = assetManager.get(type.getAssetPath(), Texture.class);
            } else if (Gdx.files != null && Gdx.files.internal(type.getAssetPath()).exists()) {
                // Direct file fallback
                try {
                    texture = new Texture(Gdx.files.internal(type.getAssetPath()));
                    fallbackTextures.put(type, texture);
                } catch (Exception e) {
                    Gdx.app.error("SpellExplosionRegistry", "Failed loading explosion: " + type.getAssetPath(), e);
                }
            }

            if (texture != null) {
                TextureRegion[] frames = sliceFrames(texture, type.getCols(), type.getRows(), type.getFrameCount());
                cachedAnimations.put(type, frames);
            }
        }
    }

    /** Helper to slice a spritesheet into a 1D sequence of frames. */
    public static TextureRegion[] sliceFrames(Texture texture, int cols, int rows, int maxFrames) {
        if (texture == null || cols <= 0 || rows <= 0) return new TextureRegion[0];
        int frameW = texture.getWidth() / cols;
        int frameH = texture.getHeight() / rows;
        TextureRegion[][] grid = TextureRegion.split(texture, frameW, frameH);

        int total = Math.min(maxFrames, cols * rows);
        TextureRegion[] sequence = new TextureRegion[total];
        int idx = 0;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (idx < total) {
                    sequence[idx++] = grid[r][c];
                }
            }
        }
        return sequence;
    }

    /**
     * Retrieves the specific animation frame for an explosion type given a progress normalized 0.0f - 1.0f.
     */
    public TextureRegion getFrame(ExplosionType type, float progress) {
        TextureRegion[] frames = cachedAnimations.get(type);
        if (frames == null || frames.length == 0) {
            // Attempt lazy load if Gdx context is running
            if (Gdx.files != null && Gdx.files.internal(type.getAssetPath()).exists()) {
                try {
                    Texture tex = new Texture(Gdx.files.internal(type.getAssetPath()));
                    fallbackTextures.put(type, tex);
                    frames = sliceFrames(tex, type.getCols(), type.getRows(), type.getFrameCount());
                    cachedAnimations.put(type, frames);
                } catch (Exception ignored) {
                }
            }
        }

        if (frames == null || frames.length == 0) {
            return null;
        }

        int index = (int) (progress * frames.length);
        if (index < 0) index = 0;
        if (index >= frames.length) index = frames.length - 1;
        return frames[index];
    }

    public boolean hasAnimation(ExplosionType type) {
        return cachedAnimations.containsKey(type) && cachedAnimations.get(type).length > 0;
    }

    @Override
    public void dispose() {
        cachedAnimations.clear();
        for (Texture tex : fallbackTextures.values()) {
            if (tex != null) tex.dispose();
        }
        fallbackTextures.clear();
    }
}
