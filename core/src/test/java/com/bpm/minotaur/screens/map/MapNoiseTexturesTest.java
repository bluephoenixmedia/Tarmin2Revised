package com.bpm.minotaur.screens.map;

import org.junit.Test;

import static org.junit.Assert.*;

public class MapNoiseTexturesTest {

    @Test
    public void testSplitMix32DeterminismAndRange() {
        MapNoiseTextures.SplitMix32 prng1 = new MapNoiseTextures.SplitMix32(11);
        MapNoiseTextures.SplitMix32 prng2 = new MapNoiseTextures.SplitMix32(11);

        for (int i = 0; i < 100; i++) {
            float v1 = prng1.nextFloat();
            float v2 = prng2.nextFloat();
            assertEquals("PRNG should be deterministic for same seed", v1, v2, 0.00001f);
            assertTrue("PRNG output must be in [0, 1)", v1 >= 0f && v1 < 1.0f);
        }
    }

    @Test
    public void testSmoothstep() {
        assertEquals(0f, MapNoiseTextures.smoothstep(0.2f, 0.8f, 0.1f), 0.0001f);
        assertEquals(1f, MapNoiseTextures.smoothstep(0.2f, 0.8f, 0.9f), 0.0001f);
        assertEquals(0.5f, MapNoiseTextures.smoothstep(0.2f, 0.8f, 0.5f), 0.0001f);
    }

    @Test
    public void testNoiseNormalizationAndDimensions() {
        int size = 128;
        float[] noise = MapNoiseTextures.generateNoise(size, 11, 4);
        assertEquals(size * size, noise.length);

        float min = 1f;
        float max = 0f;
        for (float v : noise) {
            if (v < min) min = v;
            if (v > max) max = v;
            assertTrue("Noise value must be in [0, 1]", v >= 0f && v <= 1.0f);
        }
        assertEquals(0f, min, 0.0001f);
        assertEquals(1f, max, 0.0001f);
    }

    @Test
    public void testNoiseTransferFunctions() {
        float bodyAlpha = MapNoiseTextures.calcBodyAlpha(0.5f);
        assertTrue(bodyAlpha > 0.22f && bodyAlpha < 1.0f);

        float wispAlphaPeak = MapNoiseTextures.calcWispAlpha(0.74f);
        assertTrue("Wisp should peak in mid-high range", wispAlphaPeak > 0.8f);

        float wispAlphaCutoff = MapNoiseTextures.calcWispAlpha(0.98f);
        assertEquals("Wisp should fall off near 1.0", 0f, wispAlphaCutoff, 0.001f);
    }
}
