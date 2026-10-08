package com.bpm.minotaur.video;

import org.junit.Test;

import static org.junit.Assert.*;

/** The loading backdrop's vignette swallows the centre, lets the face show around it, and closes the corners. */
public class LoadingBackdropTest {

    @Test
    public void theCentreIsDarkerThanTheRingAndTheCornersAreBlack() {
        float centre = LoadingBackdrop.darknessAt(0f);
        float ring = LoadingBackdrop.darknessAt(0.65f);
        float corner = LoadingBackdrop.darknessAt((float) Math.sqrt(2));
        assertTrue("centre " + centre + " vs ring " + ring, centre > ring);
        assertEquals(1f, corner, 1e-4f);
    }

    @Test
    public void darknessChangesSmoothlyWithNoJumps() {
        float prev = LoadingBackdrop.darknessAt(0f);
        for (float r = 0.01f; r <= 1.42f; r += 0.01f) {
            float d = LoadingBackdrop.darknessAt(r);
            assertTrue("jump at r=" + r, Math.abs(d - prev) < 0.05f);
            assertTrue(d >= 0f && d <= 1f);
            prev = d;
        }
    }
}
