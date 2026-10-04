package com.bpm.minotaur.rendering;

import com.bpm.minotaur.weather.WeatherType;

/** How strongly each weather's fog shows through a wilderness biome's own haze. */
final class WeatherFog {

    private WeatherFog() {
    }

    static float share(WeatherType weather) {
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
}
