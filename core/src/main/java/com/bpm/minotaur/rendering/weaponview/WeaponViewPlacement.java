package com.bpm.minotaur.rendering.weaponview;

import com.bpm.minotaur.paperdoll.calibration.LayerCalibration;

/**
 * Where a first-person weapon is drawn once its tuning has been applied.
 *
 * Tuning is a post-process on the pose FirstPersonWeaponOverlay already computes -- the
 * idle stance or the attack arc, bob and sway included. Applying it after the fact rather
 * than threading it through each branch is what keeps two properties true at once:
 *
 *   - an untuned weapon draws exactly as it did before, because identity passes the pose
 *     straight through;
 *   - idle and attack move together, because the same delta lands on both, so a weapon
 *     tuned to sit further right does not snap back to the archetype's anchor on a swing.
 *
 * Offsets are fractions of the viewport, +y up (screen space), so a pose tuned in a small
 * window still holds at full screen.
 */
public final class WeaponViewPlacement {

    public float x;
    public float y;
    public float originX;
    public float originY;
    public float width;
    public float height;
    public float rotation;
    /** +1 or -1. Passed as the draw call's scaleX, which mirrors about the origin. */
    public float scaleX;

    /**
     * @param drawX       pose the overlay computed, world units
     * @param drawY       pose the overlay computed, world units
     * @param rotation    pose the overlay computed, degrees
     * @param baseHeight  the hand's untuned sprite height, world units
     * @param aspect      the texture's width / height
     * @param originYFrac where the grip sits up the sprite, as a fraction of its height
     * @param mirror      true when drawing in the off hand
     */
    public static WeaponViewPlacement apply(float drawX, float drawY, float rotation,
                                            float baseHeight, float aspect, float originYFrac,
                                            float worldW, float worldH,
                                            LayerCalibration cal, boolean mirror) {
        WeaponViewPlacement p = new WeaponViewPlacement();

        p.height = baseHeight * cal.scaleY;
        p.width = baseHeight * aspect * cal.scaleX;

        p.x = drawX + worldW * cal.offsetX;
        p.y = drawY + worldH * cal.offsetY;
        p.rotation = rotation + cal.rotation;

        // The origin is the grip. It has to follow the new size, or a rescaled weapon
        // would pivot about a point that is no longer where the hand is.
        p.originX = p.width * 0.5f;
        p.originY = p.height * originYFrac;

        // A weapon whose art is already drawn for the left hand must not be flipped a
        // second time just for being held in it, so the two mirrors cancel.
        boolean flip = cal.flipX ^ mirror;
        p.scaleX = flip ? -1f : 1f;
        return p;
    }
}
