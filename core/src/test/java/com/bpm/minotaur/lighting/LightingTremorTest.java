package com.bpm.minotaur.lighting;

import org.junit.Test;

import static org.junit.Assert.*;

/** Living War W9: a battle overhead shakes the stone, and every flame gutters for a moment. */
public class LightingTremorTest {

    @Test
    public void theLightsGutterWhileTheStoneShakesAndSteadyAfter() {
        LightingManager lights = new LightingManager();
        assertEquals(1f, lights.tremorFactor(), 0f);
        lights.tremble(0.9f);
        float lowest = 1f;
        for (int i = 0; i < 20; i++) {
            lights.update(0.02f, null, null);
            lowest = Math.min(lowest, lights.tremorFactor());
        }
        assertTrue("they gutter: " + lowest, lowest < 0.8f);
        for (int i = 0; i < 60; i++) lights.update(0.02f, null, null);
        assertEquals("and steady again", 1f, lights.tremorFactor(), 0f);
    }
}
