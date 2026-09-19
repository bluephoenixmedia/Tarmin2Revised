package com.bpm.minotaur.gamedata.gore;

/**
 * One splash of blood on the paperdoll.
 *
 * Coordinates are pixels on a 1024x1536 layer canvas, +y down. While a stain is pending
 * that canvas is the doll's master canvas; once it lands on a layer it is that layer's
 * own texture, so the stain rides along with the item's calibration and moves with the
 * item if it is taken off and put back on.
 *
 * Public fields and a no-arg constructor so libGDX Json can save it directly.
 */
public class BloodStain {

    public float x;
    public float y;
    public float radius;
    /** How far the blood runs down below the stain, in radii. 0 for none. */
    public float drip;
    /** Picks the stain's irregular shape and satellite droplets, so it redraws identically. */
    public int seed;
    /** Colour when fresh, 0xRRGGBB. Dries toward near-black from there. */
    public int rgb;
    /** Game turns since it landed. */
    public int age;

    public BloodStain() {
    }

    public BloodStain(float x, float y, float radius, float drip, int seed, int rgb) {
        this.x = x;
        this.y = y;
        this.radius = radius;
        this.drip = drip;
        this.seed = seed;
        this.rgb = rgb;
    }

    public BloodStain copy() {
        BloodStain s = new BloodStain(x, y, radius, drip, seed, rgb);
        s.age = age;
        return s;
    }
}
