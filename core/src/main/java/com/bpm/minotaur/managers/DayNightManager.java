package com.bpm.minotaur.managers;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.MathUtils;

/**
 * Manages the in-game day/night cycle.
 *
 * <p>timeOfDay runs from 0.0 (midnight) to 1.0 (next midnight) in real time.
 * Phases:
 *   NIGHT  0.00 – 0.22   (midnight to pre-dawn)
 *   DAWN   0.22 – 0.30   (sunrise)
 *   DAY    0.30 – 0.68   (full daylight)
 *   DUSK   0.68 – 0.78   (sunset)
 *   NIGHT  0.78 – 1.00   (evening to midnight)
 *
 * <p>CYCLE_DURATION controls how many real seconds equal one full game day.
 */
public class DayNightManager {

    public enum Phase { NIGHT, DAWN, DAY, DUSK }

    // 10 real minutes = one full game day
    public static final float CYCLE_DURATION = 600f;

    // Phase boundaries (as fractions of the cycle)
    private static final float DAWN_START  = 0.22f;
    private static final float DAY_START   = 0.30f;
    private static final float DUSK_START  = 0.68f;
    private static final float NIGHT_START = 0.78f;

    // Minimum brightness during full night
    private static final float MIN_BRIGHTNESS = 0.18f;

    private float timeOfDay; // 0 – 1
    private final Color skyTint = new Color(Color.WHITE);

    // Reusable colours (avoid allocation per frame)
    private static final Color COL_NIGHT = new Color(0.10f, 0.12f, 0.35f, 1f);
    private static final Color COL_DAWN  = new Color(1.00f, 0.55f, 0.15f, 1f);
    private static final Color COL_DAY   = new Color(1.00f, 1.00f, 1.00f, 1f);
    private static final Color COL_DUSK  = new Color(1.00f, 0.35f, 0.10f, 1f);

    public DayNightManager() {
        // Start at early morning so the first thing the player sees is a sunrise
        this.timeOfDay = DAWN_START + 0.01f;
    }

    public DayNightManager(float startTimeOfDay) {
        this.timeOfDay = MathUtils.clamp(startTimeOfDay, 0f, 1f);
    }

    public void update(float delta) {
        timeOfDay += delta / CYCLE_DURATION;
        if (timeOfDay >= 1f) {
            timeOfDay -= 1f;
        }
        recomputeSkyTint();
    }

    /** 0.0 – 1.0 representing position in the day cycle (0 = midnight). */
    public float getTimeOfDay() {
        return timeOfDay;
    }

    /** Set the time of day directly (e.g., when loading a save). */
    public void setTimeOfDay(float t) {
        this.timeOfDay = MathUtils.clamp(t, 0f, 1f);
        recomputeSkyTint();
    }

    /**
     * Brightness multiplier to apply to outdoor light (0.18 at night, 1.0 at noon).
     * Indoors / dungeon levels should ignore this.
     */
    public float getBrightness() {
        if (timeOfDay < DAWN_START) {
            return MIN_BRIGHTNESS;
        } else if (timeOfDay < DAY_START) {
            float t = (timeOfDay - DAWN_START) / (DAY_START - DAWN_START);
            return MathUtils.lerp(MIN_BRIGHTNESS, 1.0f, smoothstep(t));
        } else if (timeOfDay < DUSK_START) {
            return 1.0f;
        } else if (timeOfDay < NIGHT_START) {
            float t = (timeOfDay - DUSK_START) / (NIGHT_START - DUSK_START);
            return MathUtils.lerp(1.0f, MIN_BRIGHTNESS, smoothstep(t));
        } else {
            return MIN_BRIGHTNESS;
        }
    }

    /**
     * Sky tint color for the skybox and ceiling (warm at dawn/dusk, white at day,
     * dark blue at night). Multiply your existing skybox color by this value.
     */
    public Color getSkyTint() {
        return skyTint;
    }

    public Phase getPhase() {
        if (timeOfDay >= DAY_START && timeOfDay < DUSK_START) return Phase.DAY;
        if (timeOfDay >= DAWN_START && timeOfDay < DAY_START)  return Phase.DAWN;
        if (timeOfDay >= DUSK_START && timeOfDay < NIGHT_START) return Phase.DUSK;
        return Phase.NIGHT;
    }

    /**
     * In-game time as a "HH:MM" string (24-hour clock).
     * Midnight = 00:00, Noon = 12:00.
     */
    public String getTimeString() {
        float totalMinutes = timeOfDay * 24f * 60f;
        int hours   = (int) (totalMinutes / 60f) % 24;
        int minutes = (int) (totalMinutes % 60f);
        return String.format("%02d:%02d", hours, minutes);
    }

    /** Returns a short label like "DAY" or a moon/sun glyph string. */
    public String getPhaseLabel() {
        switch (getPhase()) {
            case DAWN:  return "DAWN";
            case DAY:   return "DAY";
            case DUSK:  return "DUSK";
            default:    return "NIGHT";
        }
    }

    // --- Private helpers ---

    private void recomputeSkyTint() {
        if (timeOfDay < DAWN_START) {
            skyTint.set(COL_NIGHT);
        } else if (timeOfDay < DAY_START) {
            float t = (timeOfDay - DAWN_START) / (DAY_START - DAWN_START);
            // Night -> Dawn half, then Dawn -> Day
            if (t < 0.5f) {
                lerpColor(skyTint, COL_NIGHT, COL_DAWN, t * 2f);
            } else {
                lerpColor(skyTint, COL_DAWN, COL_DAY, (t - 0.5f) * 2f);
            }
        } else if (timeOfDay < DUSK_START) {
            skyTint.set(COL_DAY);
        } else if (timeOfDay < NIGHT_START) {
            float t = (timeOfDay - DUSK_START) / (NIGHT_START - DUSK_START);
            if (t < 0.5f) {
                lerpColor(skyTint, COL_DAY, COL_DUSK, t * 2f);
            } else {
                lerpColor(skyTint, COL_DUSK, COL_NIGHT, (t - 0.5f) * 2f);
            }
        } else {
            skyTint.set(COL_NIGHT);
        }
    }

    private static void lerpColor(Color out, Color a, Color b, float t) {
        out.r = MathUtils.lerp(a.r, b.r, t);
        out.g = MathUtils.lerp(a.g, b.g, t);
        out.b = MathUtils.lerp(a.b, b.b, t);
        out.a = 1f;
    }

    /** Smooth hermite interpolation (ease-in / ease-out). */
    private static float smoothstep(float t) {
        t = MathUtils.clamp(t, 0f, 1f);
        return t * t * (3f - 2f * t);
    }
}
