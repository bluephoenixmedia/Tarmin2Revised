package com.bpm.minotaur.rendering;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.MathUtils;
import com.bpm.minotaur.weather.WeatherType;

/**
 * Light and dust in the surface desert.
 *
 * The forest filters the burning mountain's light through its canopy; the
 * desert has nothing overhead, so it takes that light at full force. The air is
 * an amber dust haze carrying {@link #SKY_TINT_SHARE} of the sky's hue at the
 * sky's own brightness: ember-red at dawn, dim brown at night. The sand is lit
 * {@link #BLEACH} times brighter than the maze, bleached by the glare.
 *
 * Visibility closes in the canyons and opens across the basins. Weather blends
 * over the dust, so a blizzard still whitens the dunes, and a tornado out here
 * is a thick brown dust storm.
 */
public final class DesertAtmosphere {

    /** Fully fogged at this many tiles in a canyon, and across a basin. */
    public static final float CANYON_FOG_DISTANCE = 10f;
    public static final float BASIN_FOG_DISTANCE = 20f;

    /** How much of the sky's hue the dust carries; its brightness is carried whole. */
    public static final float SKY_TINT_SHARE = 0.6f;
    /** Ambient and sun on the open sand, against the maze. */
    public static final float BLEACH = 1.3f;

    private static final Color DUST = new Color(0.75f, 0.55f, 0.38f, 1f);
    /** Brings the dust back up to the sky's brightness after multiplying by it. */
    private static final float DUST_GAIN = 1.6f;
    private static final Color DUST_STORM = new Color(0.45f, 0.30f, 0.18f, 1f);
    private static final float DUST_STORM_SHARE = 0.8f;

    private DesertAtmosphere() {
    }

    /**
     * Fog colour over the sand.
     *
     * @param weather    current weather
     * @param weatherFog the weather system's own fog colour, already easing between states
     * @param skyTint    the full volcanic sky tint
     */
    public static Color fogColor(WeatherType weather, Color weatherFog, Color skyTint, Color out) {
        float lum = skyTint.r * 0.299f + skyTint.g * 0.587f + skyTint.b * 0.114f;
        out.set(
                DUST.r * MathUtils.lerp(lum, skyTint.r, SKY_TINT_SHARE) * DUST_GAIN,
                DUST.g * MathUtils.lerp(lum, skyTint.g, SKY_TINT_SHARE) * DUST_GAIN,
                DUST.b * MathUtils.lerp(lum, skyTint.b, SKY_TINT_SHARE) * DUST_GAIN,
                1f).clamp();
        if (weather == WeatherType.TORNADO) {
            return out.lerp(DUST_STORM, DUST_STORM_SHARE);
        }
        return out.lerp(weatherFog, ForestAtmosphere.weatherShare(weather));
    }

    /**
     * Distance at which the world is fully fogged: the canyon value in a
     * corridor, opening to the basin value as {@code openness} reaches 1.
     * Weather can close it further, never open it.
     */
    public static float fogDistance(float openness, float weatherFogDistance) {
        float dust = MathUtils.lerp(CANYON_FOG_DISTANCE, BASIN_FOG_DISTANCE, MathUtils.clamp(openness, 0f, 1f));
        return Math.min(dust, weatherFogDistance);
    }
}
