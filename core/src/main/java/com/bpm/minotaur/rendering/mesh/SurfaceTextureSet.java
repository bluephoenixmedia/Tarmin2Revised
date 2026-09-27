package com.bpm.minotaur.rendering.mesh;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.utils.Disposable;

import java.util.ArrayList;
import java.util.List;

/**
 * An ordered set of interchangeable textures for one surface, default first.
 *
 * <p>Index 0 is the default and is what {@link WeightedVariant} favours. Files
 * that do not exist are skipped at construction rather than failing, so a set
 * shrinks gracefully: ceilings currently have no authored default, and the set
 * simply becomes the eight variants until one is added.
 */
public class SurfaceTextureSet implements Disposable {

    private final List<Texture> textures = new ArrayList<>();

    /**
     * @param paths internal asset paths, the default first. Missing files are
     *              skipped, so callers may list a default that does not exist yet.
     */
    public SurfaceTextureSet(String... paths) {
        if (paths == null) return;
        for (String path : paths) {
            if (path == null) continue;
            try {
                if (!Gdx.files.internal(path).exists()) continue;
                Texture tex = new Texture(Gdx.files.internal(path));
                tex.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat);
                tex.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
                textures.add(tex);
            } catch (Exception e) {
                if (Gdx.app != null) {
                    Gdx.app.error("SurfaceTextureSet", "Could not load " + path, e);
                }
            }
        }
    }

    /** How many textures are actually available; 0 when none loaded. */
    public int size() {
        return textures.size();
    }

    public boolean isEmpty() {
        return textures.isEmpty();
    }

    /** The texture for a variant index, or null when the set is empty. */
    public Texture get(int variant) {
        if (textures.isEmpty()) return null;
        return textures.get(Math.floorMod(variant, textures.size()));
    }

    @Override
    public void dispose() {
        for (Texture t : textures) {
            if (t != null) t.dispose();
        }
        textures.clear();
    }
}
