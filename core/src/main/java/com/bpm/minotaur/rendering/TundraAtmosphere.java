package com.bpm.minotaur.rendering;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.MathUtils;
import com.bpm.minotaur.weather.WeatherType;

/**
 * Light, fog, and polar atmosphere in the surface Siberian Tundra.
 *
 * <p>The Siberian Tundra lies under a freezing polar twilight. Air is crystalline and
 * sub-zero, carrying a deep twilight navy-slate fog that opens up across vast frozen
 * lake basins and contracts tightly within dark snow-laden pine groves.
 *
 * <p>During blizzards, howling winds whip permafrost snow into an impenetrable whiteout.
 * Under clear night skies, the polar atmosphere glows with dancing northern auroras.
 */
public final class TundraAtmosphere {

    /** Fully fogged at this many tiles inside dense pine valleys and moraines. */
    public static final float TRAIL_FOG_DISTANCE = 10f;
    /** Fully fogged at this many tiles across the wide glassy ice tarn basin. */
    public static final float BASIN_FOG_DISTANCE = 22f;
    /** Maximum visibility during a severe howling blizzard. */
    public static final float BLIZZARD_FOG_DISTANCE = 5f;

    /** Share of the sky tint that filters into polar fog. */
    public static final float SKY_TINT_SHARE = 0.30f;

    /** Polar twilight navy-slate. */
    private static final Color PERMAFROST_FOG = new Color(0.14f, 0.22f, 0.32f, 1f);
    /** Ambient hue: cool crystalline arctic twilight. */
    private static final Color TUNDRA_AMBIENT = new Color(0.65f, 0.80f, 0.95f, 1f);
    /** Blinding whiteout snow squall during blizzards. */
    private static final Color BLIZZARD_FOG = new Color(0.85f, 0.92f, 0.98f, 1f);
    private static final float BLIZZARD_SHARE = 0.90f;

    private TundraAtmosphere() {
    }

    /**
     * Computes the atmospheric fog colour over the Siberian Tundra.
     *
     * @param weather    current weather
     * @param weatherFog the weather system's own fog colour
     * @param skyTint    the sky tint from the day/night cycle
     * @param out        color receiving the computed fog
     * @return the computed fog color
     */
    public static Color fogColor(WeatherType weather, Color weatherFog, Color skyTint, Color out) {
        if (weather == WeatherType.BLIZZARD) {
            out.set(BLIZZARD_FOG);
        } else {
            out.set(PERMAFROST_FOG).lerp(weatherFog, WeatherFog.share(weather));
        }

        if (skyTint != null) {
            float share = (weather == WeatherType.BLIZZARD) ? 0.15f : SKY_TINT_SHARE;
            out.lerp(skyTint, share);
            float lum = skyTint.r * 0.299f + skyTint.g * 0.587f + skyTint.b * 0.114f;
            out.mul(MathUtils.clamp(lum * 1.5f, 0.35f, 1.15f));
        }
        return out.clamp();
    }

    /**
     * Distance at which the world is fully fogged: from trail distance in
     * dense pine groves to basin distance across the open frozen lake.
     */
    public static float fogDistance(float openness, float weatherFogDistance) {
        float basin = MathUtils.lerp(TRAIL_FOG_DISTANCE, BASIN_FOG_DISTANCE, MathUtils.clamp(openness, 0f, 1f));
        return Math.min(basin, weatherFogDistance);
    }

    /**
     * Hue of the ambient light across the tundra.
     */
    public static Color ambientHue(Color skyTint, Color out) {
        out.set(TUNDRA_AMBIENT);
        if (skyTint != null) {
            out.r *= MathUtils.lerp(1f, skyTint.r, SKY_TINT_SHARE);
            out.g *= MathUtils.lerp(1f, skyTint.g, SKY_TINT_SHARE);
            out.b *= MathUtils.lerp(1f, skyTint.b, SKY_TINT_SHARE);
        }
        return out;
    }

    /**
     * Computes aurora intensity (0.0 to 1.0) under night skies in clear or cold weather.
     */
    public static float auroraIntensity(float timeOfDay, WeatherType weather) {
        if (weather == WeatherType.BLIZZARD || weather == WeatherType.RAIN || weather == WeatherType.STORM) {
            return 0.0f;
        }
        // Peaks around midnight (0.0 / 1.0)
        float night = 0.0f;
        if (timeOfDay < 0.22f) {
            night = 1.0f - (timeOfDay / 0.22f);
        } else if (timeOfDay > 0.78f) {
            night = (timeOfDay - 0.78f) / 0.22f;
        }
        return MathUtils.clamp(night, 0.0f, 1.0f);
    }
}
