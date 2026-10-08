package com.bpm.minotaur.video;

import org.junit.Test;

import static org.junit.Assert.*;

/** The loading backdrop's vignette shows the centre of the video and fades it to black toward its borders. */
public class LoadingBackdropTest {

    @Test
    public void theCentreIsClearAndTheBordersAreBlack() {
        assertEquals(0f, LoadingBackdrop.darknessAt(0f), 1e-4f);
        assertEquals(1f, LoadingBackdrop.darknessAt(LoadingBackdrop.BLACK_RADIUS), 1e-4f);
        assertEquals("the video's edges", 1f, LoadingBackdrop.darknessAt(1f), 1e-4f);
        assertEquals("corners", 1f, LoadingBackdrop.darknessAt((float) Math.sqrt(2)), 1e-4f);
    }

    @Test
    public void itDarkensSteadilyOutward() {
        float prev = -1f;
        for (float r = 0f; r <= 1.42f; r += 0.01f) {
            float d = LoadingBackdrop.darknessAt(r);
            assertTrue("lighter further out at r=" + r, d >= prev);
            prev = d;
        }
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
