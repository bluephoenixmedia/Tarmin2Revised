package com.bpm.minotaur.rendering;

import com.badlogic.gdx.graphics.Color;
import com.bpm.minotaur.weather.WeatherType;
import org.junit.Test;

import static org.junit.Assert.*;

public class TundraAtmosphereTest {

    private static final Color WHITE_FOG = new Color(Color.WHITE);
    private static final Color VOLCANIC_DAWN = new Color(0.95f, 0.45f, 0.20f, 1f);
    private static final Color VOLCANIC_NIGHT = new Color(0.08f, 0.05f, 0.12f, 1f);

    @Test
    public void clearWeatherRetainsPermafrostTwilightTone() {
        Color fog = TundraAtmosphere.fogColor(WeatherType.CLEAR, WHITE_FOG, Color.WHITE, new Color());
        assertTrue("Cold blue dominant", fog.b > fog.r);
        assertTrue("Twilight navy", fog.b > 0.25f);
    }

    @Test
    public void blizzardCausesWhiteoutFog() {
        Color blizzardFog = new Color(0.90f, 0.95f, 1.0f, 1f);
        Color fog = TundraAtmosphere.fogColor(WeatherType.BLIZZARD, blizzardFog, VOLCANIC_DAWN, new Color());
        assertTrue("Blizzard should be very bright whiteout", fog.r > 0.50f && fog.g > 0.50f && fog.b > 0.60f);
    }

    @Test
    public void fogDistanceScalesFromTrailToBasin() {
        assertEquals(TundraAtmosphere.TRAIL_FOG_DISTANCE, TundraAtmosphere.fogDistance(0f, 70f), 0.001f);
        assertEquals(TundraAtmosphere.BASIN_FOG_DISTANCE, TundraAtmosphere.fogDistance(1f, 70f), 0.001f);
        assertEquals("Severe weather limits visibility", 5f, TundraAtmosphere.fogDistance(1f, 5f), 0.001f);
    }

    @Test
    public void ambientHueIsCoolCrystalline() {
        Color ambient = TundraAtmosphere.ambientHue(Color.WHITE, new Color());
        assertTrue("Blue dominant cold ambient", ambient.b > ambient.r && ambient.b > ambient.g);
    }

    @Test
    public void auroraActiveDuringNightOnly() {
        assertTrue("Aurora active at midnight (0.0)", TundraAtmosphere.auroraIntensity(0.0f, WeatherType.CLEAR) > 0.8f);
        assertEquals("Aurora inactive at solar noon (0.5)", 0.0f, TundraAtmosphere.auroraIntensity(0.5f, WeatherType.CLEAR), 0.001f);
        assertEquals("Aurora obscured by blizzard", 0.0f, TundraAtmosphere.auroraIntensity(0.0f, WeatherType.BLIZZARD), 0.001f);
    }
}
