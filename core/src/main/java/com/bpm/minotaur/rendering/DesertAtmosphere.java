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
    /** Dark enough that a storm still reads as thick once the sky's brightness is applied. */
    private static final Color DUST_STORM = new Color(0.28f, 0.18f, 0.10f, 1f);
    private static final float DUST_STORM_SHARE = 0.85f;

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
        if (weather == WeatherType.TORNADO) {
            out.set(DUST).lerp(DUST_STORM, DUST_STORM_SHARE);
        } else {
            out.set(DUST).lerp(weatherFog, WeatherFog.share(weather));
        }
        // Whatever hangs in the air, it is lit by the sky: bright and red at dawn, dim at night.
        float lum = skyTint.r * 0.299f + skyTint.g * 0.587f + skyTint.b * 0.114f;
        out.r *= MathUtils.lerp(lum, skyTint.r, SKY_TINT_SHARE) * DUST_GAIN;
        out.g *= MathUtils.lerp(lum, skyTint.g, SKY_TINT_SHARE) * DUST_GAIN;
        out.b *= MathUtils.lerp(lum, skyTint.b, SKY_TINT_SHARE) * DUST_GAIN;
        return out.clamp();
    }

    /** Time of day (0 midnight, 0.5 noon) the heat shimmer starts, peaks, starts to fade, and ends: 11:00-15:00. */
    private static final float SHIMMER_START = 11f / 24f;
    private static final float SHIMMER_FULL = 12f / 24f;
    private static final float SHIMMER_FADE = 14f / 24f;
    private static final float SHIMMER_END = 15f / 24f;

    /**
     * How strongly the air above the sand shimmers, 0 to 1: building from eleven,
     * strongest from noon to two, gone by three, and only under a clear sky.
     */
    public static float heatShimmer(float timeOfDay, WeatherType weather) {
        if (weather != null && weather != WeatherType.CLEAR) return 0f;
        float rise = smoothstep(SHIMMER_START, SHIMMER_FULL, timeOfDay);
        float fall = 1f - smoothstep(SHIMMER_FADE, SHIMMER_END, timeOfDay);
        return rise * fall;
    }

    private static float smoothstep(float edge0, float edge1, float x) {
        float t = MathUtils.clamp((x - edge0) / (edge1 - edge0), 0f, 1f);
        return t * t * (3f - 2f * t);
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
