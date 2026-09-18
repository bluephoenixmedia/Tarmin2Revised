package com.bpm.minotaur.paperdoll.calibration;

/**
 * Where one paperdoll layer sits on the character, and what it covers up.
 *
 * Authored in master-canvas pixels (1024x1536) so the numbers survive the portrait
 * rectangle being moved or resized. Identity means "draw exactly as baked", which is
 * how every layer behaves until someone calibrates it.
 *
 * Offsets follow image-space intuition: +x is right, +y is DOWN. {@link LayerPlacement}
 * converts to LibGDX's y-up stage space.
 */
public class LayerCalibration {

    /** Horizontal nudge in canvas pixels; positive moves right. */
    public float offsetX = 0f;
    /** Vertical nudge in canvas pixels; positive moves DOWN the screen. */
    public float offsetY = 0f;
    /** Width multiplier about the layer's centre. Independent of {@link #scaleY}. */
    public float scaleX = 1f;
    /** Height multiplier about the layer's centre. Independent of {@link #scaleX}. */
    public float scaleY = 1f;
    /** Degrees, counter-clockwise, about the layer's centre. */
    public float rotation = 0f;

    /** Suppresses the base character's hair, for helmets that fully enclose the head. */
    public boolean hidesHair = false;
    /** Suppresses the base character's beard, for great helms and full visors. */
    public boolean hidesBeard = false;

    /**
     * Set when no calibration makes the art fit — the piece needs regenerating at the
     * correct perspective rather than nudging. Drives the art-redo triage list.
     */
    public boolean needsArtRedo = false;

    /**
     * Digest of the source image this calibration was authored against. When the baker
     * sees a different hash it flags the entry as stale instead of silently applying
     * numbers that were measured against different pixels.
     */
    public String sourceHash = null;

    private static final float EPSILON = 1e-4f;

    /** True when this layer draws exactly as baked, i.e. stretched to fill the rect. */
    public boolean isIdentity() {
        return Math.abs(offsetX) < EPSILON
                && Math.abs(offsetY) < EPSILON
                && Math.abs(scaleX - 1f) < EPSILON
                && Math.abs(scaleY - 1f) < EPSILON
                && Math.abs(rotation) < EPSILON;
    }

    public LayerCalibration copy() {
        LayerCalibration c = new LayerCalibration();
        c.offsetX = offsetX;
        c.offsetY = offsetY;
        c.scaleX = scaleX;
        c.scaleY = scaleY;
        c.rotation = rotation;
        c.hidesHair = hidesHair;
        c.hidesBeard = hidesBeard;
        c.needsArtRedo = needsArtRedo;
        c.sourceHash = sourceHash;
        return c;
    }
}
