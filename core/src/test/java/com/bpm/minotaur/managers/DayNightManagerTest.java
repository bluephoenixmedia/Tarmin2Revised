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

        // Noon: the key light is volcanic rather than solar -- warm and red-dominant, but damped
        // by WORLD_TINT_STRENGTH so world materials stay readable (issue #104).
        manager.setTimeOfDay(0.50f);
        manager.getDirectionalLightColor(lightColor);
        assertTrue("Volcanic daylight should be red-dominant", lightColor.r > lightColor.g);
        assertTrue("Firelight keeps more green than blue", lightColor.g > lightColor.b);
        assertTrue("Daylight should still read as bright", lightColor.r > 0.8f);
        assertTrue("World light must stay damped, not fully volcanic", lightColor.g > 0.5f);

        // Midnight: cool moonlight
        manager.setTimeOfDay(0.0f);
        manager.getDirectionalLightColor(lightColor);
        assertTrue(lightColor.b > lightColor.r); // Blue dominates
    }

    @Test
    public void testVolcanicSkyPaletteAndWorldTintClamp() {
        manager.setTimeOfDay(0.50f); // Noon

        // The sky itself burns: the palette is volcanic at every hour, never neutral white.
        Color sky = manager.getSkyTint();
        assertTrue("Noon sky must be red-dominant, not white", sky.r > sky.g + 0.3f);

        // The zenith stays dark so the fire reads as a band above the walls, not a flood.
        Color zenith = manager.getZenithTint();
        assertTrue("Zenith must be far darker than the sky tint", zenith.r < sky.r * 0.5f);
        assertTrue("Zenith leans purple rather than orange", zenith.b > zenith.g);

        // World lighting only leans toward the palette -- it never matches it.
        Color world = manager.getWorldTint(new Color());
        assertTrue("World tint must sit between neutral and the sky", world.g > sky.g);
        assertTrue("World tint must still follow the sky", world.g < 1.0f);
    }

    @Test
    public void testSkyTintIsVolcanicAtEveryPhase() {
        // No hour of the day may return to a neutral or cool-dominant sky.
        float[] hours = {0.00f, 0.26f, 0.50f, 0.72f, 0.90f};
        for (float t : hours) {
            manager.setTimeOfDay(t);
            Color sky = manager.getSkyTint();
            assertTrue("Sky must stay warm-dominant at t=" + t, sky.r > sky.b);
        }
    }

    @Test
    public void testUpdateCycleWrap() {
        manager.setTimeOfDay(0.999f);
        // Advance by 10 seconds of the 1500s cycle (~0.0067)
        manager.update(10f);
        assertTrue("Cycle should wrap smoothly past midnight", manager.getTimeOfDay() < 0.1f);
    }
}
