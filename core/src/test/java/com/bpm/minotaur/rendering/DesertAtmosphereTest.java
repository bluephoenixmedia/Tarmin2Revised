package com.bpm.minotaur.rendering;

import com.badlogic.gdx.graphics.Color;
import com.bpm.minotaur.weather.WeatherType;
import org.junit.Test;

import static org.junit.Assert.*;

public class DesertAtmosphereTest {

    private static final Color VOLCANIC_DAWN = new Color(0.95f, 0.33f, 0.12f, 1f);
    private static final Color VOLCANIC_NIGHT = new Color(0.16f, 0.07f, 0.13f, 1f);
    private static final Color WHITE_FOG = new Color(Color.WHITE);

    @Test
    public void dawnDustBurnsEmberRed() {
        Color fog = DesertAtmosphere.fogColor(WeatherType.CLEAR, WHITE_FOG, VOLCANIC_DAWN, new Color());

        assertTrue("the desert keeps the red: " + fog, fog.r > fog.g && fog.g > fog.b);
        assertTrue("and it is a bright haze, not a dark one: " + fog, fog.r > 0.6f);
    }

    @Test
    public void nightDustGoesDark() {
        Color fog = DesertAtmosphere.fogColor(WeatherType.CLEAR, WHITE_FOG, VOLCANIC_NIGHT, new Color());

        assertTrue("night haze is dim: " + fog, fog.r + fog.g + fog.b < 0.5f);
    }

    @Test
    public void aBlizzardStillWhitensTheDesert() {
        Color blizzardFog = new Color(0.96f, 0.97f, 1.0f, 1f);
        Color fog = DesertAtmosphere.fogColor(WeatherType.BLIZZARD, blizzardFog, Color.WHITE, new Color());

        assertTrue("a blizzard whitens the dunes: " + fog, fog.b > 0.7f && Math.abs(fog.r - fog.b) < 0.25f);
    }

    @Test
    public void weatherHazeDimsAtNightToo() {
        Color blizzardFog = new Color(0.96f, 0.97f, 1.0f, 1f);
        Color blizzard = DesertAtmosphere.fogColor(WeatherType.BLIZZARD, blizzardFog, VOLCANIC_NIGHT, new Color());
        Color storm = DesertAtmosphere.fogColor(WeatherType.TORNADO, WHITE_FOG, VOLCANIC_NIGHT, new Color());

        assertTrue("a night blizzard is not daylight-bright: " + blizzard, blizzard.r + blizzard.g + blizzard.b < 0.8f);
        assertTrue("nor is a night dust storm: " + storm, storm.r + storm.g + storm.b < 0.5f);
    }

    @Test
    public void aTornadoIsADustStorm() {
        Color fog = DesertAtmosphere.fogColor(WeatherType.TORNADO, WHITE_FOG, Color.WHITE, new Color());

        assertTrue("a dust storm is brown: " + fog, fog.r > fog.g && fog.g > fog.b);
        assertTrue("and thick: " + fog, fog.r < 0.6f);
    }

    @Test
    public void basinsSeeFarAndCanyonsClose() {
        assertEquals(10f, DesertAtmosphere.fogDistance(0f, 70f), 0.001f);
        assertEquals(20f, DesertAtmosphere.fogDistance(1f, 70f), 0.001f);
        assertEquals("weather can close it further", 6f, DesertAtmosphere.fogDistance(1f, 6f), 0.001f);
    }

    @Test
    public void sandIsBrighterThanTheMaze() {
        assertTrue(DesertAtmosphere.BLEACH > 1f);
    }

    @Test
    public void theAirShimmersOnlyThroughTheHeatOfAClearDay() {
        assertEquals("before eleven the sand is not hot yet", 0f,
                DesertAtmosphere.heatShimmer(0.40f, WeatherType.CLEAR), 0.001f);
        assertEquals("at noon it is at its strongest", 1f,
                DesertAtmosphere.heatShimmer(0.54f, WeatherType.CLEAR), 0.001f);
        float rising = DesertAtmosphere.heatShimmer(0.48f, WeatherType.CLEAR);
        assertTrue("it builds through the late morning: " + rising, rising > 0f && rising < 1f);
        assertEquals("gone after three", 0f, DesertAtmosphere.heatShimmer(0.66f, WeatherType.CLEAR), 0.001f);
        assertEquals("rain kills it", 0f, DesertAtmosphere.heatShimmer(0.54f, WeatherType.RAIN), 0.001f);
        assertEquals("so does a dust storm", 0f, DesertAtmosphere.heatShimmer(0.54f, WeatherType.TORNADO), 0.001f);
    }
}
