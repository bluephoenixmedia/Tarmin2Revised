package com.bpm.minotaur.rendering.weaponview;

import com.bpm.minotaur.paperdoll.calibration.LayerCalibration;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Tuning is applied as a post-process on the pose FirstPersonWeaponOverlay already
 * computes -- idle stance or attack arc, bob included. That makes the most important
 * property checkable: an untuned weapon draws exactly as it did before this existed.
 */
public class WeaponViewPlacementTest {

    // A 1920x1080 viewport and the main hand's existing sizing.
    private static final float W = 1920f;
    private static final float H = 1080f;
    private static final float BASE_H = H * 0.44f;
    private static final float ASPECT = 0.25f;
    private static final float ORIGIN_Y = 0.10f;

    private WeaponViewPlacement place(LayerCalibration cal, boolean mirror) {
        return WeaponViewPlacement.apply(1382f, 40f, -5f, BASE_H, ASPECT, ORIGIN_Y, W, H, cal, mirror);
    }

    @Test
    public void untunedWeaponDrawsExactlyAsBefore() {
        // The guarantee that makes this safe to ship: with nothing tuned, every one of
        // the 229 weapons renders identically to the hardcoded pose it had yesterday.
        WeaponViewPlacement p = place(new LayerCalibration(), false);
        assertEquals(1382f, p.x, 0.001f);
        assertEquals(40f, p.y, 0.001f);
        assertEquals(-5f, p.rotation, 0.001f);
        assertEquals(BASE_H, p.height, 0.001f);
        assertEquals(BASE_H * ASPECT, p.width, 0.001f);
        assertEquals(p.width * 0.5f, p.originX, 0.001f);
        assertEquals(p.height * ORIGIN_Y, p.originY, 0.001f);
        assertEquals(1f, p.scaleX, 0f);
    }

    @Test
    public void offsetsAreFractionsOfTheViewport() {
        // Fractions rather than pixels, so a pose tuned in a small window still holds at
        // full screen.
        LayerCalibration cal = new LayerCalibration();
        cal.offsetX = 0.05f;
        cal.offsetY = 0.10f;
        WeaponViewPlacement p = place(cal, false);
        assertEquals(1382f + W * 0.05f, p.x, 0.001f);
        assertEquals(40f + H * 0.10f, p.y, 0.001f);
    }

    @Test
    public void positiveOffsetYMovesTheWeaponUpTheScreen() {
        // Screen space, so +y is up: the up arrow and a slider dragged right both raise
        // the weapon. (The paperdoll stores +y downward because it is image space; the
        // keys behave the same way in both tools either way.)
        LayerCalibration cal = new LayerCalibration();
        cal.offsetY = 0.02f;
        assertEquals(true, place(cal, false).y > 40f);
    }

    @Test
    public void rotationIsAddedToTheComputedPose() {
        // Additive, so the attack arc's own rotation keeps working on top of the tuning --
        // that is what stops the weapon popping to a different angle when you swing.
        LayerCalibration cal = new LayerCalibration();
        cal.rotation = 12f;
        assertEquals(7f, place(cal, false).rotation, 0.001f);
    }

    @Test
    public void scaleResizesAboutTheSameGripPoint() {
        LayerCalibration cal = new LayerCalibration();
        cal.scaleX = 1.5f;
        cal.scaleY = 2f;
        WeaponViewPlacement p = place(cal, false);
        assertEquals(BASE_H * 2f, p.height, 0.001f);
        assertEquals(BASE_H * ASPECT * 1.5f, p.width, 0.001f);
        // The origin is the grip, so it has to move with the new size or the weapon
        // would pivot about a point that is no longer where the hand is.
        assertEquals(p.width * 0.5f, p.originX, 0.001f);
        assertEquals(p.height * ORIGIN_Y, p.originY, 0.001f);
    }

    @Test
    public void flipXMirrorsTheSprite() {
        LayerCalibration cal = new LayerCalibration();
        cal.flipX = true;
        assertEquals(-1f, place(cal, false).scaleX, 0f);
    }

    @Test
    public void offHandMirroringFlipsAWeaponDrawnForTheMainHand() {
        assertEquals(-1f, place(new LayerCalibration(), true).scaleX, 0f);
    }

    @Test
    public void flipXAndOffHandMirroringCancelOut() {
        // A weapon whose art is already drawn for the left hand must NOT be flipped a
        // second time just because it is being held in the left hand.
        LayerCalibration cal = new LayerCalibration();
        cal.flipX = true;
        assertEquals(1f, place(cal, true).scaleX, 0f);
    }

    @Test
    public void flippingDoesNotMoveTheWeapon() {
        // The mirror happens about the grip, so flipping a tuned weapon leaves it where it
        // was rather than jumping it sideways by its own width.
        LayerCalibration cal = new LayerCalibration();
        cal.offsetX = 0.03f;
        WeaponViewPlacement plain = place(cal, false);
        cal.flipX = true;
        WeaponViewPlacement flipped = place(cal, false);
        assertEquals(plain.x, flipped.x, 0.001f);
        assertEquals(plain.y, flipped.y, 0.001f);
        assertEquals(plain.originX, flipped.originX, 0.001f);
    }

    @Test
    public void theSameTuningShiftsIdleAndAttackIdentically() {
        // Idle and the attack arc produce different base poses; the tuning is a delta,
        // so it moves both by the same amount. That is the whole fix for the pop.
        LayerCalibration cal = new LayerCalibration();
        cal.offsetX = 0.04f;
        cal.rotation = 6f;

        WeaponViewPlacement idle = WeaponViewPlacement.apply(
                1382f, 40f, -5f, BASE_H, ASPECT, ORIGIN_Y, W, H, cal, false);
        WeaponViewPlacement swing = WeaponViewPlacement.apply(
                900f, 300f, 45f, BASE_H, ASPECT, ORIGIN_Y, W, H, cal, false);

        assertEquals(1382f + W * 0.04f, idle.x, 0.001f);
        assertEquals(900f + W * 0.04f, swing.x, 0.001f);
        assertEquals(1f, idle.rotation, 0.001f);
        assertEquals(51f, swing.rotation, 0.001f);
    }
}
