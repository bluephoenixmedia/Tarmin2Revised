package com.bpm.minotaur.rendering;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.MathUtils;
import com.bpm.minotaur.weather.WeatherType;

/**
 * Light, fog, and low ground mist in the surface Lakelands.
 *
 * The wetlands lie under cool damp air where water vapor rises from the shallows.
 * The fog is deep misty slate-teal, absorbing the volcanic sky tint across its
 * pools and canals. Visibility closes to tight channels within the thickets
 * and opens across broad marsh pools.
 *
 * Ground mist lingers over the water, thickening at dawn, dusk, and during storms.
 */
public final class LakelandsAtmosphere {

    /** Fully fogged at this many tiles in a narrow thicket channel. */
    public static final float CHANNEL_FOG_DISTANCE = 8f;
    /** Fully fogged at this many tiles across an open marsh pool or glade. */
    public static final float POOL_FOG_DISTANCE = 16f;

    /** Share of the sky tint that filters into the mist and water reflections. */
    public static final float SKY_TINT_SHARE = 0.35f;

    /** Deep misty slate-teal. */
    private static final Color WETLAND_FOG = new Color(0.10f, 0.22f, 0.26f, 1f);
    /** Ambient hue: cool cyan-slate with damp chill. */
    private static final Color WETLAND_AMBIENT = new Color(0.60f, 0.75f, 0.82f, 1f);
    /** Thick dark squall fog during a tornado/tempest. */
    private static final Color TEMPEST_FOG = new Color(0.15f, 0.20f, 0.22f, 1f);
    private static final float TEMPEST_SHARE = 0.80f;

    private LakelandsAtmosphere() {
    }

    /**
     * Fog colour over the water and marshland.
     *
     * @param weather    current weather
     * @param weatherFog the weather system's own fog colour, easing between states
     * @param skyTint    the volcanic sky tint
     * @param out        color receiving the computed fog
     */
    public static Color fogColor(WeatherType weather, Color weatherFog, Color skyTint, Color out) {
        if (weather == WeatherType.TORNADO) {
            out.set(WETLAND_FOG).lerp(TEMPEST_FOG, TEMPEST_SHARE);
        } else {
            out.set(WETLAND_FOG).lerp(weatherFog, WeatherFog.share(weather));
        }

        if (skyTint != null) {
            out.r *= MathUtils.lerp(1f, skyTint.r, SKY_TINT_SHARE);
            out.g *= MathUtils.lerp(1f, skyTint.g, SKY_TINT_SHARE);
            out.b *= MathUtils.lerp(1f, skyTint.b, SKY_TINT_SHARE);
        }
        return out.clamp();
    }

    /**
     * Distance at which the world is fully fogged: from channel distance in
     * narrow waterways to pool distance across open water.
     */
    public static float fogDistance(float openness, float weatherFogDistance) {
        float mist = MathUtils.lerp(CHANNEL_FOG_DISTANCE, POOL_FOG_DISTANCE, MathUtils.clamp(openness, 0f, 1f));
        return Math.min(mist, weatherFogDistance);
    }

    /** Hue of the ambient light across the wetlands. */
    public static Color ambientHue(Color skyTint, Color out) {
        out.set(WETLAND_AMBIENT);
        if (skyTint != null) {
            out.r *= MathUtils.lerp(1f, skyTint.r, SKY_TINT_SHARE);
            out.g *= MathUtils.lerp(1f, skyTint.g, SKY_TINT_SHARE);
            out.b *= MathUtils.lerp(1f, skyTint.b, SKY_TINT_SHARE);
        }
        return out;
    }

    /**
     * Intensity of the low rolling water mist (0 to 1).
     * Peaks at dawn (0.20-0.35) and dusk (0.70-0.85), remains thick during
     * fog or rain, and settles to a faint base level under clear midday sun.
     */
    public static float waterMist(float timeOfDay, WeatherType weather) {
        if (weather == WeatherType.FOG) return 1.0f;
        if (weather == WeatherType.RAIN || weather == WeatherType.STORM) return 0.85f;
        if (weather == WeatherType.TORNADO || weather == WeatherType.BLIZZARD) return 0.70f;

        // Dawn peak around 0.25 (6am)
        float dawn = 1f - Math.min(1f, Math.abs(timeOfDay - 0.25f) / 0.12f);
        // Dusk peak around 0.75 (6pm)
        float dusk = 1f - Math.min(1f, Math.abs(timeOfDay - 0.75f) / 0.12f);
        float diurnal = Math.max(0f, Math.max(dawn, dusk));

        // Base residual mist over wetlands is at least 0.25 even at midday
        return MathUtils.clamp(0.25f + 0.75f * diurnal, 0f, 1f);
    }
}
