package com.bpm.minotaur.rendering;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.utils.Disposable;

/**
 * Holds the baked 2D textures produced by {@link CreatureBaker} for one
 * assembled creature.  Four facing angles are stored so the renderer can
 * show the correct view based on the player-to-monster angle, matching the
 * existing directional texture system already used by Monster.
 *
 * Texture orientation matches the convention used by EntityRenderer
 * (drawMonsterTexture): V=0 at image bottom (feet), V=1 at image top (head).
 */
public class BakedMonsterSprite implements Disposable {

    /** Facing index constants — matches getTextureForPlayerAngle quadrants. */
    public static final int FACING_NORTH = 0;
    public static final int FACING_EAST  = 1;
    public static final int FACING_SOUTH = 2;
    public static final int FACING_WEST  = 3;

    private final Texture[] facingTextures;   // [N, E, S, W]
    private final Texture[][] facingFrames;   // [facing][frame] — null when single-frame

    /** Single-frame constructor (one texture per facing direction). */
    public BakedMonsterSprite(Texture north, Texture east, Texture south, Texture west) {
        this.facingTextures = new Texture[]{ north, east, south, west };
        this.facingFrames   = null;
    }

    /** Multi-frame constructor for animated bakes. */
    public BakedMonsterSprite(Texture[][] facingFrames) {
        this.facingFrames   = facingFrames;
        this.facingTextures = new Texture[4];
        for (int i = 0; i < 4; i++) {
            facingTextures[i] = (facingFrames[i] != null && facingFrames[i].length > 0)
                    ? facingFrames[i][0] : null;
        }
    }

    /**
     * Returns the texture for the given facing index [0..3].
     * Falls back to the first non-null facing if the requested one is missing.
     */
    public Texture getTexture(int facingIndex) {
        Texture t = facingTextures[facingIndex & 3];
        if (t != null) return t;
        for (Texture fallback : facingTextures) {
            if (fallback != null) return fallback;
        }
        return null;
    }

    /** Returns the animated frame texture for a given facing and frame index. */
    public Texture getFrame(int facingIndex, int frameIndex) {
        if (facingFrames == null) return getTexture(facingIndex);
        Texture[] frames = facingFrames[facingIndex & 3];
        if (frames == null || frames.length == 0) return getTexture(facingIndex);
        return frames[frameIndex % frames.length];
    }

    public int getFrameCount(int facingIndex) {
        if (facingFrames == null) return 1;
        Texture[] frames = facingFrames[facingIndex & 3];
        return (frames != null) ? frames.length : 1;
    }

    /** North-facing texture — convenience for setBakedDirectionalTextures(). */
    public Texture north() { return facingTextures[FACING_NORTH]; }
    /** East-facing texture. */
    public Texture east()  { return facingTextures[FACING_EAST]; }
    /** South-facing texture. */
    public Texture south() { return facingTextures[FACING_SOUTH]; }
    /** West-facing texture. */
    public Texture west()  { return facingTextures[FACING_WEST]; }
    /** South frames array — for setBakedDirectionalTextures() southFrames param. */
    public Texture[] southFrames() {
        if (facingFrames != null) return facingFrames[FACING_SOUTH];
        Texture s = facingTextures[FACING_SOUTH];
        return s != null ? new Texture[]{ s } : null;
    }

    @Override
    public void dispose() {
        if (facingFrames != null) {
            for (Texture[] frames : facingFrames) {
                if (frames == null) continue;
                for (Texture t : frames) {
                    if (t != null) t.dispose();
                }
            }
        } else {
            for (Texture t : facingTextures) {
                if (t != null) t.dispose();
            }
        }
    }
}
