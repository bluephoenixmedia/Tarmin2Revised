package com.bpm.minotaur.rendering;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.MathUtils;
import com.bpm.minotaur.weather.WeatherType;

/**
 * Light and fog over the Blighted Marches.
 *
 * <p>The Marches lie under Castle Tarmin's shadow: a permanent overcast lit from
 * below by the volcanic sky, so the air is a crimson-ochre murk that never
 * clears. Ashfall greys it and closes it in. Ambient light is low and warm, as
 * from embers rather than sun, and the castle's braziers are the brightest thing
 * in it.
 */
public final class BlightAtmosphere {

    /** Fully fogged at this many tiles among the ridges and charred timber. */
    public static final float TRAIL_FOG_DISTANCE = 11f;
    /** Fully fogged at this many tiles across open burnt ground. */
    public static final float OPEN_FOG_DISTANCE = 20f;
    /** Ashfall closes visibility to this. */
    public static final float ASHFALL_FOG_DISTANCE = 9f;

    /** Share of the sky tint that filters into the murk. */
    public static final float SKY_TINT_SHARE = 0.25f;

    /** Crimson-ochre murk. */
    private static final Color MURK = new Color(0.30f, 0.13f, 0.09f, 1f);
    /** Grey ash curtain during ashfall. */
    private static final Color ASH = new Color(0.36f, 0.31f, 0.29f, 1f);
    private static final float ASH_SHARE = 0.65f;
    /** Ember-lit ambient. */
    private static final Color BLIGHT_AMBIENT = new Color(0.95f, 0.66f, 0.55f, 1f);

    private BlightAtmosphere() {
    }

    public static Color fogColor(WeatherType weather, Color weatherFog, Color skyTint, Color out) {
        out.set(MURK);
        if (weather == WeatherType.ASHFALL) {
            out.lerp(ASH, ASH_SHARE);
        } else {
            out.lerp(weatherFog, WeatherFog.share(weather) * 0.5f);
        }
        if (skyTint != null) {
            out.lerp(skyTint, SKY_TINT_SHARE);
            float lum = skyTint.r * 0.299f + skyTint.g * 0.587f + skyTint.b * 0.114f;
            out.mul(MathUtils.clamp(lum * 1.4f, 0.40f, 1.05f));
        }
        return out.clamp();
    }

    public static float fogDistance(float openness, WeatherType weather, float weatherFogDistance) {
        float d = MathUtils.lerp(TRAIL_FOG_DISTANCE, OPEN_FOG_DISTANCE, MathUtils.clamp(openness, 0f, 1f));
        if (weather == WeatherType.ASHFALL) d = Math.min(d, ASHFALL_FOG_DISTANCE + openness * 4f);
        return Math.min(d, weatherFogDistance);
    }

    public static Color ambientHue(Color skyTint, Color out) {
        out.set(BLIGHT_AMBIENT);
        if (skyTint != null) {
            out.r *= MathUtils.lerp(1f, skyTint.r, SKY_TINT_SHARE);
            out.g *= MathUtils.lerp(1f, skyTint.g, SKY_TINT_SHARE);
            out.b *= MathUtils.lerp(1f, skyTint.b, SKY_TINT_SHARE);
        }
        return out;
    }
}
