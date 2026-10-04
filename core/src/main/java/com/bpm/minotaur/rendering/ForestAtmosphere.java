package com.bpm.minotaur.rendering;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.MathUtils;
import com.bpm.minotaur.weather.WeatherType;

/**
 * Light and fog under the surface forest's canopy.
 *
 * The world is lit by a burning mountain (see DayNightManager), and out in the
 * open that red lights everything. Under the trees the canopy filters it: the
 * forest is green-black and moss-teal, and the volcanic red is only glimpsed
 * through gaps. The forest takes {@link #SKY_TINT_SHARE} of the sky tint, so
 * dawn and dusk still move it a little without repainting it.
 *
 * Weather blends over the canopy fog rather than replacing it, so a blizzard
 * still whitens the forest and a tornado keeps its sickly green.
 */
public final class ForestAtmosphere {

    /** Fully fogged at this many tiles on a trail under the canopy. */
    public static final float TRAIL_FOG_DISTANCE = 8f;
    /** And at this many in a glade, where the canopy opens. */
    public static final float GLADE_FOG_DISTANCE = 14f;

    /** How much of the volcanic sky tint reaches under the canopy. */
    public static final float SKY_TINT_SHARE = 0.15f;

    /** Moss-black with a teal cast. */
    private static final Color CANOPY_FOG = new Color(0.030f, 0.070f, 0.060f, 1f);
    /** Hue of the light that filters through the needles; intensity comes from the day/night cycle. */
    private static final Color CANOPY_AMBIENT = new Color(0.55f, 0.78f, 0.70f, 1f);
    /** Matches the supercell tint in Skybox3DRenderer. */
    private static final Color TORNADO_FOG = new Color(0.24f, 0.30f, 0.20f, 1f);

    /** Ambient, sun/moon and sky-rim light under a closed canopy, as a share of the open-sky value. */
    public static final float CANOPY_AMBIENT_SHARE = 0.75f;
    public static final float CANOPY_SUN_SHARE = 0.25f;
    public static final float CANOPY_RIM_SHARE = 0.30f;
    /** How much of the rain's slate-blue overcast reaches the canopy ambient. */
    public static final float CANOPY_OVERCAST_SHARE = 0.40f;

    private ForestAtmosphere() {
    }

    /**
     * Fog colour under the canopy.
     *
     * @param weather    current weather
     * @param weatherFog the weather system's own fog colour, already easing between states
     * @param skyTint    the full volcanic sky tint
     */
    public static Color fogColor(WeatherType weather, Color weatherFog, Color skyTint, Color out) {
        out.set(CANOPY_FOG);
        if (weather == WeatherType.TORNADO) {
            out.lerp(TORNADO_FOG, 0.75f);
        } else {
            out.lerp(weatherFog, weatherShare(weather));
        }
        return applySkyShare(out, skyTint);
    }

    /** How strongly each weather's fog shows through the canopy fog. */
    static float weatherShare(WeatherType weather) {
        if (weather == null) return 0f;
        switch (weather) {
            case RAIN:     return 0.20f;
            case STORM:    return 0.25f;
            case FOG:      return 0.55f;
            case SNOW:     return 0.60f;
            case BLIZZARD: return 0.90f;
            default:       return 0f;
        }
    }

    /**
     * Distance at which the world is fully fogged. Opens from the trail value to
     * the glade value as {@code glade} goes from 0 to 1. Weather can close it
     * further, never open it.
     */
    public static float fogDistance(float glade, float weatherFogDistance) {
        float canopy = MathUtils.lerp(TRAIL_FOG_DISTANCE, GLADE_FOG_DISTANCE, MathUtils.clamp(glade, 0f, 1f));
        return Math.min(canopy, weatherFogDistance);
    }

    /** Hue of the ambient light under the canopy, before day/night intensity is applied. */
    public static Color ambientHue(Color skyTint, Color out) {
        return applySkyShare(out.set(CANOPY_AMBIENT), skyTint);
    }

    /** Scales a value that is {@code share} of itself under a closed canopy and whole in a glade. */
    public static float canopyScale(float value, float share, float glade) {
        return value * MathUtils.lerp(share, 1f, MathUtils.clamp(glade, 0f, 1f));
    }

    private static Color applySkyShare(Color c, Color skyTint) {
        c.r *= MathUtils.lerp(1f, skyTint.r, SKY_TINT_SHARE);
        c.g *= MathUtils.lerp(1f, skyTint.g, SKY_TINT_SHARE);
        c.b *= MathUtils.lerp(1f, skyTint.b, SKY_TINT_SHARE);
        return c;
    }
}
