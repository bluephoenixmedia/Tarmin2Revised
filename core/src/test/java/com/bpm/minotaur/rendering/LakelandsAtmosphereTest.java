package com.bpm.minotaur.rendering;

import com.badlogic.gdx.graphics.Color;
import com.bpm.minotaur.weather.WeatherType;
import org.junit.Test;

import static org.junit.Assert.*;

public class LakelandsAtmosphereTest {

    private static final Color VOLCANIC_DAWN = new Color(0.95f, 0.33f, 0.12f, 1f);
    private static final Color VOLCANIC_NIGHT = new Color(0.16f, 0.07f, 0.13f, 1f);
    private static final Color WHITE_FOG = new Color(Color.WHITE);

    @Test
    public void wetlandFogIsMistySlateTeal() {
        Color fog = LakelandsAtmosphere.fogColor(WeatherType.CLEAR, WHITE_FOG, Color.WHITE, new Color());
        // Green and blue channels dominate red for slate-teal
        assertTrue("wetland fog has cool teal hue: " + fog, fog.b > fog.r && fog.g > fog.r);
    }

    @Test
    public void dawnLightFiltersIntoMist() {
        Color fog = LakelandsAtmosphere.fogColor(WeatherType.CLEAR, WHITE_FOG, VOLCANIC_DAWN, new Color());
        assertTrue("dawn warms the mist: " + fog, fog.r > 0.08f);
    }

    @Test
    public void nightMistDimsDeeply() {
        Color fog = LakelandsAtmosphere.fogColor(WeatherType.CLEAR, WHITE_FOG, VOLCANIC_NIGHT, new Color());
        assertTrue("night mist is dark: " + fog, fog.r + fog.g + fog.b < 0.45f);
    }

    @Test
    public void poolsSeeFarAndChannelsClose() {
        assertEquals(8f, LakelandsAtmosphere.fogDistance(0f, 70f), 0.001f);
        assertEquals(16f, LakelandsAtmosphere.fogDistance(1f, 70f), 0.001f);
        assertEquals("weather can close it further", 5f, LakelandsAtmosphere.fogDistance(1f, 5f), 0.001f);
    }

    @Test
    public void waterMistPeaksAtDawnDuskAndRain() {
        // Fog & Rain hold thick mist
        assertEquals(1.0f, LakelandsAtmosphere.waterMist(0.5f, WeatherType.FOG), 0.001f);
        assertEquals(0.85f, LakelandsAtmosphere.waterMist(0.5f, WeatherType.RAIN), 0.001f);

        // Clear day: dawn and dusk peaks
        float dawn = LakelandsAtmosphere.waterMist(0.25f, WeatherType.CLEAR);
        float midday = LakelandsAtmosphere.waterMist(0.50f, WeatherType.CLEAR);
        float dusk = LakelandsAtmosphere.waterMist(0.75f, WeatherType.CLEAR);

        assertTrue("dawn mist is thicker than midday: dawn=" + dawn + " midday=" + midday, dawn > midday);
        assertTrue("dusk mist is thicker than midday: dusk=" + dusk + " midday=" + midday, dusk > midday);
        assertTrue("midday still has base wetland mist", midday >= 0.25f);
    }
}
