package com.bpm.minotaur.weather;

import com.badlogic.gdx.math.Vector3;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class Weather3DTest {

    private WeatherManager weatherManager;

    @Before
    public void setUp() {
        weatherManager = new WeatherManager(null);
    }

    @Test
    public void testPrecipitationDetection() {
        assertTrue("Rain must be precipitation", weatherManager.isPrecipitation(WeatherType.RAIN));
        assertTrue("Storm must be precipitation", weatherManager.isPrecipitation(WeatherType.STORM));
        assertTrue("Snow must be precipitation", weatherManager.isPrecipitation(WeatherType.SNOW));
        assertTrue("Blizzard must be precipitation", weatherManager.isPrecipitation(WeatherType.BLIZZARD));
        assertTrue("Tornado must be precipitation", weatherManager.isPrecipitation(WeatherType.TORNADO));
        assertFalse("Clear must not be precipitation", weatherManager.isPrecipitation(WeatherType.CLEAR));
        assertFalse("Fog must not be precipitation", weatherManager.isPrecipitation(WeatherType.FOG));
    }

    @Test
    public void testWetnessAccumulationAndDrying() {
        float initialWetness = weatherManager.getWetness();
        assertTrue("Starting storm must have initial wetness", initialWetness > 0f);

        // Update in stormy weather -> wetness should accumulate toward 1.0
        for (int i = 0; i < 30; i++) {
            weatherManager.update(0.1f);
        }
        assertTrue("Wetness must stay bounded in [0, 1]", weatherManager.getWetness() <= 1.0f);
        assertTrue("Wetness should be high during storm", weatherManager.getWetness() >= initialWetness);
    }

    @Test
    public void testWindVectorGeneration() {
        Vector3 wind = new Vector3();
        weatherManager.getWindVector(wind);

        // Storm wind should have non-zero lateral velocity
        assertTrue("Storm wind must have lateral component", Math.abs(wind.x) > 0.5f);
        assertEquals("Wind Y (up/down in world) should be zero for horizontal wind", 0f, wind.y, 0.001f);
    }

    @Test
    public void testSplashDropletLifecycle() {
        WeatherRenderer.SplashDroplet droplet = new WeatherRenderer.SplashDroplet(5f, 5f, 0.5f, 0.5f, 1.5f, 0.15f);
        assertFalse("Splash droplet must start alive", droplet.isDead);
        assertEquals("Initial Z should be slightly above ground", 0.02f, droplet.z, 0.001f);

        // Update partially
        droplet.update(0.05f);
        assertTrue("Droplet must have moved horizontally", droplet.x > 5f);
        assertTrue("Droplet must have popped upward", droplet.z > 0.02f);
        assertFalse("Droplet should still be alive", droplet.isDead);

        // Update past max life (0.15s)
        droplet.update(0.15f);
        assertTrue("Droplet must die after maxLife", droplet.isDead);
    }

    @Test
    public void testWeatherParticlePhysics() {
        // Rain particle
        WeatherRenderer.WeatherParticle rain = new WeatherRenderer.WeatherParticle(
                10f, 10f, 8f, -2.0f, 0.5f, -16.0f, 0.55f, WeatherType.RAIN
        );
        assertEquals("Altitude must match spawn", 8f, rain.z, 0.001f);

        rain.update(0.1f);
        assertEquals("Rain altitude must decrease with downward velocity", 6.4f, rain.z, 0.01f);
        assertTrue("Rain X must move with wind velocity", rain.x < 10f);

        // Snow particle (wobble oscillation)
        WeatherRenderer.WeatherParticle snow = new WeatherRenderer.WeatherParticle(
                10f, 10f, 8f, -0.5f, 0.2f, -2.5f, 0.12f, WeatherType.SNOW
        );
        snow.update(0.1f);
        assertTrue("Snow altitude must decrease gently", snow.z < 8f && snow.z > 7.5f);
    }

    @Test
    public void testBootWeatherIsHeavyStorm() {
        assertEquals("Boot weather must be STORM", WeatherType.STORM, weatherManager.getCurrentWeather());
        assertEquals("Boot weather intensity must be HEAVY", WeatherIntensity.HEAVY, weatherManager.getCurrentIntensity());
    }

    @Test
    public void testSoundManagerDampenCrossfade() {
        com.bpm.minotaur.managers.SoundManager sm = new com.bpm.minotaur.managers.SoundManager() {};

        // Default state: 1.0f
        assertEquals(1.0f, sm.getCurrentDampenFactor(), 0.001f);
        assertEquals(1.0f, sm.getTargetDampenFactor(), 0.001f);

        // Immediate dampening on boot inside shelter
        sm.setDampenedImmediate(true);
        assertEquals(0.25f, sm.getCurrentDampenFactor(), 0.001f);
        assertEquals(0.25f, sm.getTargetDampenFactor(), 0.001f);

        // Player walks outdoors: smooth crossfade to 1.0f
        sm.setDampened(false);
        assertEquals("Target should be 1.0f", 1.0f, sm.getTargetDampenFactor(), 0.001f);
        assertEquals("Current should start at 0.25f before updates", 0.25f, sm.getCurrentDampenFactor(), 0.001f);

        // Simulate 0.15s elapsed
        sm.update(0.15f);
        assertTrue("Current dampen factor must increase towards 1.0", sm.getCurrentDampenFactor() > 0.25f);
        assertTrue("Current dampen factor must not exceed 1.0", sm.getCurrentDampenFactor() <= 1.0f);

        // Simulate further time (~1.0s total) to reach near target
        for (int i = 0; i < 20; i++) {
            sm.update(0.1f);
        }
        assertEquals("Current dampen factor should reach target after time", 1.0f, sm.getCurrentDampenFactor(), 0.01f);
    }

    @Test
    public void testIndoorDetectionForWeather() {
        com.bpm.minotaur.gamedata.Maze mazeL1 = new com.bpm.minotaur.gamedata.Maze(1, new int[16][16]);
        mazeL1.setHomeTiles(java.util.Collections.singletonList(new com.badlogic.gdx.math.GridPoint2(8, 8)));

        assertTrue("Level 1 home tile (8,8) must be indoors", mazeL1.isIndoors(8, 8));
        assertFalse("Level 1 wilderness tile (0,0) must not be indoors", mazeL1.isIndoors(0, 0));

        com.bpm.minotaur.gamedata.Maze mazeL2 = new com.bpm.minotaur.gamedata.Maze(2, new int[16][16]);
        assertTrue("Level 2 tile must always be indoors", mazeL2.isIndoors(0, 0));
    }
}
