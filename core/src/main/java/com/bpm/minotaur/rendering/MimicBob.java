package com.bpm.minotaur.rendering;

/**
 * The breathing of a disguised mimic.
 *
 * <p>Shared by both render engines so the tell is identical in each. The retro
 * raycaster tints items by lighting alone, so a colour-based cue would be invisible
 * there -- a small vertical motion is the one tell that reads in both.
 *
 * <p>Deliberately tiny. It should be missable at a glance and obvious once a player
 * knows to look, which is the whole point of an always-on tell: it rewards learning the
 * game rather than rolling well.
 *
 * <p>Interim: this stands in for the dedicated mimic_chest.png silhouette variant. When
 * that asset lands, swap the texture at spawn and delete this.
 */
public final class MimicBob {

    /** Radians per second the shared phase clock advances. */
    public static final float PHASE_RATE = 2.4f;

    /** Amplitude in world units for the 3D engine. */
    private static final float WORLD_AMPLITUDE = 0.012f;
    /** Amplitude as a fraction of sprite height for the raycaster. */
    private static final float SPRITE_AMPLITUDE = 0.02f;

    /** Spreads neighbouring chests out of phase so they don't pulse in unison. */
    private static final float TILE_SPREAD = 1.7f;

    private MimicBob() {
    }

    /** Advances a renderer's phase clock by one frame. */
    public static float advance(float phase, float deltaSeconds) {
        return phase + deltaSeconds * PHASE_RATE;
    }

    /** Vertical offset in world units, for billboard rendering. */
    public static float worldOffset(float phase, float tileX, float tileY) {
        return wave(phase, tileX, tileY) * WORLD_AMPLITUDE;
    }

    /** Vertical offset as a fraction of sprite height, for the raycaster. */
    public static float spriteOffset(float phase, float tileX, float tileY) {
        return wave(phase, tileX, tileY) * SPRITE_AMPLITUDE;
    }

    private static float wave(float phase, float tileX, float tileY) {
        return (float) Math.sin(phase + (tileX + tileY) * TILE_SPREAD);
    }
}
