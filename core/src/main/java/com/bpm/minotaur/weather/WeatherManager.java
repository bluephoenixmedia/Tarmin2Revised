package com.bpm.minotaur.weather;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.GridPoint2;
import com.badlogic.gdx.math.MathUtils;
import com.bpm.minotaur.generation.Biome;
import com.bpm.minotaur.managers.WorldManager;

public class WeatherManager {

    private final WorldManager worldManager;
    private WeatherType currentWeather;
    private WeatherIntensity currentIntensity;
    private float weatherTimer;

    // Fog Rendering Targets
    private float targetFogDistance;
    private final Color targetFogColor;
    private float currentFogDistance;
    private final Color currentFogColor;

    // Lightning & Atmosphere
    private float lightningTimer;
    private float flashIntensity; // 0.0 = normal, 1.0 = full white flash
    private float globalLightDimmer; // 1.0 = bright, 0.5 = dark storm

    // Constants
    private static final float MIN_WEATHER_DURATION = 30f;
    private static final float MAX_WEATHER_DURATION = 120f;
    private static final float FOG_LERP_SPEED = 0.5f; // Time to transition fog

    public WeatherManager(WorldManager worldManager) {
        this.worldManager = worldManager;
        this.currentWeather = WeatherType.CLEAR;
        this.currentIntensity = WeatherIntensity.LIGHT;
        this.weatherTimer = 10f;

        // Init defaults
        this.targetFogDistance = 60f; // Default clear view
        this.targetFogColor = new Color(Color.WHITE);
        this.currentFogDistance = targetFogDistance;
        this.currentFogColor = new Color(Color.WHITE);

        this.lightningTimer = 0f;
        this.flashIntensity = 0f;
        this.globalLightDimmer = 1.0f;
    }

    public void update(float delta) {
        weatherTimer -= delta;

        // 1. Handle Weather Cycle
        if (weatherTimer <= 0) {
            changeWeather();
            weatherTimer = MathUtils.random(MIN_WEATHER_DURATION, MAX_WEATHER_DURATION);
        }

        // 2. Interpolate Fog for smooth transitions
        currentFogDistance = MathUtils.lerp(currentFogDistance, targetFogDistance, FOG_LERP_SPEED * delta);
        currentFogColor.lerp(targetFogColor, FOG_LERP_SPEED * delta);

        // 3. Handle Lightning (Only during Storms)
        if (currentWeather == WeatherType.STORM || currentWeather == WeatherType.TORNADO) {
            updateLightning(delta);
        } else {
            flashIntensity = 0f;
        }

        // 4. Decay Flash
        if (flashIntensity > 0) {
            flashIntensity -= delta * 2.0f; // Flash fades out quickly
            if (flashIntensity < 0) flashIntensity = 0;
        }
    }

    private void updateLightning(float delta) {
        lightningTimer -= delta;
        if (lightningTimer <= 0) {
            // Trigger Flash
            triggerLightning();
            // Reset timer (random frequency based on intensity)
            float minTime = (currentIntensity == WeatherIntensity.EXTREME) ? 2f : 5f;
            float maxTime = (currentIntensity == WeatherIntensity.EXTREME) ? 8f : 20f;
            lightningTimer = MathUtils.random(minTime, maxTime);
        }
    }

    private void triggerLightning() {
        this.flashIntensity = 1.0f;
    }

    private void changeWeather() {
        GridPoint2 chunkId = worldManager.getCurrentPlayerChunkId();
        Biome currentBiome = worldManager.getBiomeManager().getBiome(chunkId);

        WeatherType nextWeather = pickWeatherForBiome(currentBiome);
        WeatherIntensity nextIntensity = pickIntensityForWeather(nextWeather);

        if (this.currentWeather != nextWeather || this.currentIntensity != nextIntensity) {
            this.currentWeather = nextWeather;
            this.currentIntensity = nextIntensity;
            Gdx.app.log("WeatherManager", "Weather changing to: " + currentIntensity + " " + currentWeather);
            updateAtmosphereTargets();
        }
    }

    private WeatherType pickWeatherForBiome(Biome biome) {
        float roll = MathUtils.random(); // 0.0 to 1.0

        // [UPDATED PROBABILITIES]
        // Aggressively favor extreme weather.
        // Approx 20% Clear, 80% Weather.
        // Within Weather, favor Storms/Blizzards/Tornadoes.

        switch (biome) {
            case MAZE:
            case FOREST:
            case PLAINS:
                if (roll < 0.20f) return WeatherType.CLEAR;   // 20% Clear
                if (roll < 0.30f) return WeatherType.FOG;     // 10% Fog
                if (roll < 0.45f) return WeatherType.RAIN;    // 15% Rain
                if (roll < 0.90f) return WeatherType.STORM;   // 45% Storm (Dominant)
                return WeatherType.TORNADO;                   // 10% Tornado (Rare but possible)

            case MOUNTAINS:
                if (roll < 0.20f) return WeatherType.CLEAR;   // 20% Clear
                if (roll < 0.40f) return WeatherType.SNOW;    // 20% Snow
                return WeatherType.BLIZZARD;                  // 60% Blizzard (Dominant)

            case OCEAN: // If implemented
                if (roll < 0.10f) return WeatherType.CLEAR;
                if (roll < 0.30f) return WeatherType.RAIN;
                return WeatherType.STORM;

            case DESERT: // If implemented
                if (roll < 0.80f) return WeatherType.CLEAR;
                return WeatherType.TORNADO; // Dust devils

            default: return WeatherType.CLEAR;
        }
    }

    private WeatherIntensity pickIntensityForWeather(WeatherType type) {
        if (type == WeatherType.CLEAR) return WeatherIntensity.LIGHT;

        // [UPDATED PROBABILITIES]
        // Skew heavily towards Heavy/Extreme
        float roll = MathUtils.random();

        if (roll < 0.10f) return WeatherIntensity.LIGHT;    // 10%
        if (roll < 0.30f) return WeatherIntensity.MEDIUM;   // 20%
        if (roll < 0.60f) return WeatherIntensity.HEAVY;    // 30%
        return WeatherIntensity.EXTREME;                    // 40%
    }

    private void updateAtmosphereTargets() {
        switch (currentWeather) {
            case CLEAR:
                targetFogDistance = 60f;
                targetFogColor.set(Color.WHITE);
                globalLightDimmer = 1.0f;
                break;
            case RAIN:
                targetFogDistance = (currentIntensity == WeatherIntensity.HEAVY) ? 25f : 40f;
                targetFogColor.set(0.6f, 0.6f, 0.7f, 1f); // Blue-gray
                globalLightDimmer = 0.8f;
                break;
            case STORM:
                targetFogDistance = 15f;
                targetFogColor.set(0.3f, 0.3f, 0.35f, 1f); // Dark slate
                globalLightDimmer = 0.5f;
                break;
            case SNOW:
                targetFogDistance = 30f;
                targetFogColor.set(0.9f, 0.9f, 0.95f, 1f); // Cold white
                globalLightDimmer = 0.9f;
                break;
            case BLIZZARD:
                targetFogDistance = 8f;
                targetFogColor.set(0.95f, 0.95f, 1.0f, 1f); // Blind white
                globalLightDimmer = 0.7f;
                break;
            case FOG:
                targetFogDistance = 12f;
                targetFogColor.set(0.5f, 0.5f, 0.5f, 1f); // Gray
                globalLightDimmer = 0.6f;
                break;
            case TORNADO:
                targetFogDistance = 30f;
                targetFogColor.set(0.4f, 0.35f, 0.2f, 1f); // Dust
                globalLightDimmer = 0.6f;
                break;
        }
    }

    public WeatherType getCurrentWeather() { return currentWeather; }
    public WeatherIntensity getCurrentIntensity() { return currentIntensity; }
    public float getFogDistance() { return currentFogDistance; }
    public Color getFogColor() { return currentFogColor; }

    public float getLightIntensity() {
        return Math.min(1.5f, globalLightDimmer + flashIntensity);
    }

    public boolean isLightningFlashing() { return flashIntensity > 0.1f; }

    // --- DEBUG METHODS ---
    public void debugCycleWeather() {
        int nextOrdinal = (currentWeather.ordinal() + 1) % WeatherType.values().length;
        this.currentWeather = WeatherType.values()[nextOrdinal];
        Gdx.app.log("WeatherManager", "Debug: Forced weather to " + currentWeather);
        updateAtmosphereTargets();
        weatherTimer = 30f;
    }

    public void debugCycleIntensity() {
        int nextOrdinal = (currentIntensity.ordinal() + 1) % WeatherIntensity.values().length;
        this.currentIntensity = WeatherIntensity.values()[nextOrdinal];
        Gdx.app.log("WeatherManager", "Debug: Forced intensity to " + currentIntensity);
        updateAtmosphereTargets();
        weatherTimer = 30f;
    }


    /**
     * Returns a value from 0.0 to 1.0 representing how "scary" the current weather is.
     * Used to drive camera shake and audio distortion.
     * [UPDATED] Only TORNADO causes fear/shake effects.
     */
    public float getTraumaLevel() {
        if (currentWeather == WeatherType.TORNADO) {
            return 1.0f; // Maximum dread
        }
        // All other weather (Storms, Blizzards, etc.) is now calm regarding screen shake
        return 0.0f;
    }
}
