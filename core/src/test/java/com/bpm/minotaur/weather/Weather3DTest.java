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
}
