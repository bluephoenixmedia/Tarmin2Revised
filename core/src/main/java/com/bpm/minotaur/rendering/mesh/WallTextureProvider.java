package com.bpm.minotaur.rendering.mesh;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.utils.Disposable;

import java.util.HashMap;
import java.util.Map;

/**
 * Owns the twelve wall textures -- two palettes of six -- and hands them out by
 * variant.
 *
 * <p>Loading is lazy and cached, so a run that only ever sees green chunks never
 * pays for the grey set. All twelve at 1024x640 would be 30MB of VRAM if every
 * one were touched.
 *
 * <p>A missing file falls back to the base wall texture rather than throwing.
 * Wall art is not worth a crash, and the renderer has no sensible way to recover
 * from a null texture mid-frame.
 */
public class WallTextureProvider implements Disposable {

    private final Map<String, Texture> cache = new HashMap<>();
    private final Texture fallback;

    /**
     * @param fallback used when a variant file is missing; typically the base
     *                 {@code images/wall.png} the renderer already holds. Not
     *                 disposed by this class, since the caller owns it.
     */
    public WallTextureProvider(Texture fallback) {
        this.fallback = fallback;
    }

    /** The texture for one face, or the fallback if that variant is missing. */
    public Texture get(WallVariants.Palette palette, int variant) {
        String path = WallVariants.texturePath(palette, variant);

        Texture cached = cache.get(path);
        if (cached != null) return cached;
        if (cache.containsKey(path)) return fallback; // known-missing, already logged

        if (!Gdx.files.internal(path).exists()) {
            Gdx.app.error("WallTextureProvider", "Missing wall texture, using fallback: " + path);
            cache.put(path, null);
            return fallback;
        }

        try {
            Texture tex = new Texture(Gdx.files.internal(path));
            // The shelter pushes wall V below zero to tile masonry into its taller
            // ceiling, so repeat is required rather than merely tidy.
            tex.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat);
            tex.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
            cache.put(path, tex);
            return tex;
        } catch (Exception e) {
            Gdx.app.error("WallTextureProvider", "Could not load wall texture: " + path, e);
            cache.put(path, null);
            return fallback;
        }
    }

    @Override
    public void dispose() {
        for (Texture t : cache.values()) {
            if (t != null) t.dispose();
        }
        cache.clear();
    }
}
