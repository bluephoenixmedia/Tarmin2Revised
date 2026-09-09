package com.bpm.minotaur.weather;

import com.badlogic.gdx.math.Vector3;
import com.bpm.minotaur.gamedata.player.Player;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class WeatherOverhaulTest {

    private WeatherManager weatherManager;

    @Before
    public void setUp() {
        weatherManager = new WeatherManager(null);
    }

    @Test
    public void testSnowAccumulationAndMelting() {
        weatherManager.setSnowAccumulation(0.0f);
        assertEquals(0.0f, weatherManager.getSnowAccumulation(), 0.001f);

        // Advance snow accumulation
        weatherManager.setCurrentWeather(WeatherType.SNOW);
        assertEquals(WeatherType.SNOW, weatherManager.getCurrentWeather());

        for (int i = 0; i < 50; i++) {
            weatherManager.update(0.1f);
        }
        assertTrue("Snow accumulation must build up during snowfall", weatherManager.getSnowAccumulation() > 0.0f);
        assertTrue("Snow accumulation must be <= 1.0", weatherManager.getSnowAccumulation() <= 1.0f);

        // Switch to CLEAR weather -> snow should begin gradual melting
        weatherManager.setCurrentWeather(WeatherType.CLEAR);
        assertEquals(WeatherType.CLEAR, weatherManager.getCurrentWeather());

        float accumulatedSnow = weatherManager.getSnowAccumulation();
        for (int i = 0; i < 30; i++) {
            weatherManager.update(0.1f);
        }
        assertTrue("Snow must melt during clear weather", weatherManager.getSnowAccumulation() < accumulatedSnow);
    }

    @Test
    public void testCloudCoverScaling() {
        weatherManager.setCurrentWeather(WeatherType.CLEAR);
        assertEquals("Clear weather must have zero cloud cover", 0.0f, weatherManager.getCloudCover(), 0.001f);

        weatherManager.setCurrentWeather(WeatherType.STORM);
        assertTrue("Storm weather must have heavy cloud cover >= 0.85", weatherManager.getCloudCover() >= 0.85f);

        weatherManager.setCurrentWeather(WeatherType.TORNADO);
        assertEquals("Tornado must have 100% overcast canopy", 1.0f, weatherManager.getCloudCover(), 0.001f);
    }

    @Test
    public void testFogDistanceIntensityCalibration() {
        // Verify CLEAR fog distance
        weatherManager.setCurrentWeather(WeatherType.CLEAR);
        for (int i = 0; i < 100; i++) {
            weatherManager.update(0.1f);
        }
        assertTrue("Clear weather fog distance should be expansive (>= 60m)", weatherManager.getFogDistance() >= 60f);

        // Verify BLIZZARD whiteout fog distance
        weatherManager.setCurrentWeather(WeatherType.BLIZZARD);
        for (int i = 0; i < 100; i++) {
            weatherManager.update(0.1f);
        }
        assertTrue("Blizzard fog distance should be dense whiteout (<= 15m)", weatherManager.getFogDistance() <= 15f);
    }

    @Test
    public void testWindVectorDifferential() {
        Vector3 snowWind = new Vector3();
        weatherManager.setCurrentWeather(WeatherType.SNOW);
        weatherManager.getWindVector(snowWind);

        Vector3 blizzardWind = new Vector3();
        weatherManager.setCurrentWeather(WeatherType.BLIZZARD);
        weatherManager.getWindVector(blizzardWind);

        Vector3 tornadoWind = new Vector3();
        weatherManager.setCurrentWeather(WeatherType.TORNADO);
        weatherManager.getWindVector(tornadoWind);

        assertTrue("Blizzard wind must be significantly stronger than snow wind",
                Math.abs(blizzardWind.x) > Math.abs(snowWind.x) * 3f);
        assertTrue("Tornado wind must be strongest velocity",
                Math.abs(tornadoWind.x) > Math.abs(blizzardWind.x));
    }

    @Test
    public void testWeatherParticleVariety() {
        WeatherRenderer.WeatherParticle snowParticle = new WeatherRenderer.WeatherParticle(
                1f, 1f, 3f, -0.5f, 0.2f, -1.5f, 0.02f, WeatherType.SNOW, true, false);
        assertTrue("Snow particle should be fluffy", snowParticle.isFluffy);
        assertFalse("Snow particle should not be debris", snowParticle.isDebris);

        snowParticle.update(0.1f);
        assertTrue("Snow particle must fall downwards", snowParticle.z < 3f);

        WeatherRenderer.WeatherParticle debrisParticle = new WeatherRenderer.WeatherParticle(
                2f, 2f, 1.5f, -15f, 4f, -0.5f, 0.03f, WeatherType.TORNADO, false, true);
        assertTrue("Tornado particle should be debris", debrisParticle.isDebris);
        debrisParticle.update(0.05f);
        assertTrue("Debris particle must travel fast horizontally", debrisParticle.x < 2f);
    }

    @Test
    public void testTornadoVortexStateAndPosition() {
        WeatherRenderer renderer = new WeatherRenderer(weatherManager);
        assertFalse("Tornado should not be active initially", renderer.isTornadoActive());

        weatherManager.setCurrentWeather(WeatherType.TORNADO);
        assertTrue("Tornado should be active when weather is TORNADO", renderer.isTornadoActive());

        Player player = new Player(10f, 10f);
        renderer.update(0.1f, player, null);

        Vector3 tornadoPos = new Vector3();
        renderer.getTornadoPosition(tornadoPos);

        // Verify distance from player (10, -10 in world coords)
        float dx = tornadoPos.x - player.getPosition().x;
        float dz = tornadoPos.z - (-player.getPosition().y);
        float dist = (float) Math.sqrt(dx * dx + dz * dz);

        assertTrue("Tornado should be positioned within visible range (15m - 22m), was " + dist,
                dist >= 15f && dist <= 22f);
    }
}
