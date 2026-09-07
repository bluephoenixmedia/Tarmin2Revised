package com.bpm.minotaur.managers;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Vector3;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class DayNightManagerTest {

    private DayNightManager manager;

    @Before
    public void setUp() {
        manager = new DayNightManager(0.5f); // Start at Noon
    }

    @Test
    public void testPhaseProgression() {
        manager.setTimeOfDay(0.10f);
        assertEquals(DayNightManager.Phase.NIGHT, manager.getPhase());
        assertEquals("NIGHT", manager.getPhaseLabel());

        manager.setTimeOfDay(0.25f);
        assertEquals(DayNightManager.Phase.DAWN, manager.getPhase());
        assertEquals("DAWN", manager.getPhaseLabel());

        manager.setTimeOfDay(0.50f);
        assertEquals(DayNightManager.Phase.DAY, manager.getPhase());
        assertEquals("DAY", manager.getPhaseLabel());

        manager.setTimeOfDay(0.72f);
        assertEquals(DayNightManager.Phase.DUSK, manager.getPhase());
        assertEquals("DUSK", manager.getPhaseLabel());

        manager.setTimeOfDay(0.90f);
        assertEquals(DayNightManager.Phase.NIGHT, manager.getPhase());
    }

    @Test
    public void testTimeStringFormatting() {
        manager.setTimeOfDay(0.0f);
        assertEquals("00:00", manager.getTimeString());

        manager.setTimeOfDay(0.25f);
        assertEquals("06:00", manager.getTimeString());

        manager.setTimeOfDay(0.50f);
        assertEquals("12:00", manager.getTimeString());

        manager.setTimeOfDay(0.75f);
        assertEquals("18:00", manager.getTimeString());
    }

    @Test
    public void testBrightnessLevels() {
        manager.setTimeOfDay(0.50f); // Noon
        assertEquals(1.0f, manager.getBrightness(), 0.01f);

        manager.setTimeOfDay(0.05f); // Midnight
        assertEquals(0.18f, manager.getBrightness(), 0.01f);
    }

    @Test
    public void testCelestialVectors() {
        Vector3 sun = new Vector3();
        Vector3 moon = new Vector3();

        // At noon (0.50), Sun is high in the sky (Y > 0)
        manager.setTimeOfDay(0.50f);
        manager.getSunDirection(sun);
        assertTrue("Sun elevation at noon should be positive", sun.y > 0.5f);

        manager.getMoonDirection(moon);
        assertTrue("Moon elevation at noon should be negative", moon.y < -0.5f);

        // Sun and Moon vectors must be opposite
        float dot = sun.dot(moon);
        assertEquals("Sun and Moon should be antipodal", -1.0f, dot, 0.05f);

        // At midnight (0.00), Moon is high in the sky
        manager.setTimeOfDay(0.0f);
        manager.getSunDirection(sun);
        manager.getMoonDirection(moon);
        assertTrue("Sun elevation at midnight should be negative", sun.y < -0.5f);
        assertTrue("Moon elevation at midnight should be positive", moon.y > 0.5f);
    }

    @Test
    public void testStarfieldRotation() {
        manager.setTimeOfDay(0.0f);
        assertEquals(0f, manager.getStarfieldRotation(), 0.01f);

        manager.setTimeOfDay(0.5f);
        assertEquals(180f, manager.getStarfieldRotation(), 0.01f);

        manager.setTimeOfDay(0.75f);
        assertEquals(270f, manager.getStarfieldRotation(), 0.01f);
    }

    @Test
    public void testDirectionalLightColors() {
        Color lightColor = new Color();

        // Noon: warm white daylight
        manager.setTimeOfDay(0.50f);
        manager.getDirectionalLightColor(lightColor);
        assertTrue(lightColor.r > 0.8f);
        assertTrue(lightColor.g > 0.8f);

        // Midnight: cool moonlight
        manager.setTimeOfDay(0.0f);
        manager.getDirectionalLightColor(lightColor);
        assertTrue(lightColor.b > lightColor.r); // Blue dominates
    }

    @Test
    public void testUpdateCycleWrap() {
        manager.setTimeOfDay(0.999f);
        // Advance by 10 seconds of 600s cycle (~0.0167)
        manager.update(10f);
        assertTrue("Cycle should wrap smoothly past midnight", manager.getTimeOfDay() < 0.1f);
    }
}
