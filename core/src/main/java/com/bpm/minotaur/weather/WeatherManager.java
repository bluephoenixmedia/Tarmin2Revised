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
    private float flashIntensity;
    private float globalLightDimmer;

    // Thunder Timing Logic
    private float thunderDelayTimer;
    private boolean isThunderPending;

    // Constants
    private static final float MIN_WEATHER_DURATION = 30f;
    private static final float MAX_WEATHER_DURATION = 120f;
    private static final float FOG_LERP_SPEED = 0.5f;

    public WeatherManager(WorldManager worldManager) {
        this.worldManager = worldManager;

        // --- [CHANGED] Default to High Intensity Storm ---
        this.currentWeather = WeatherType.STORM;
        this.currentIntensity = WeatherIntensity.HEAVY;
        // ------------------------------------------------

        this.weatherTimer = 10f;

        // Init defaults (These will be overwritten immediately by updateAtmosphereTargets, but good to have)
        this.targetFogDistance = 60f;
        this.targetFogColor = new Color(Color.WHITE);
        this.currentFogDistance = targetFogDistance;
        this.currentFogColor = new Color(Color.WHITE);

        this.lightningTimer = 0f;
        this.flashIntensity = 0f;
        this.globalLightDimmer = 1.0f;

        this.thunderDelayTimer = 0f;
        this.isThunderPending = false;

        // Ensure visual targets are set correctly for the starting storm
        updateAtmosphereTargets();
    }

    public void update(float delta) {
        weatherTimer -= delta;

        // 1. Handle Weather Cycle
        if (weatherTimer <= 0) {
            changeWeather();
            weatherTimer = MathUtils.random(MIN_WEATHER_DURATION, MAX_WEATHER_DURATION);
        }

        // 2. Interpolate Fog
        currentFogDistance = MathUtils.lerp(currentFogDistance, targetFogDistance, FOG_LERP_SPEED * delta);
        currentFogColor.lerp(targetFogColor, FOG_LERP_SPEED * delta);

        // 3. Handle Lightning
        if (currentWeather == WeatherType.STORM || currentWeather == WeatherType.TORNADO) {
            updateLightning(delta);
        } else {
            flashIntensity = 0f;
        }

        // 4. Decay Flash
        if (flashIntensity > 0) {
            flashIntensity -= delta * 2.0f;
            if (flashIntensity < 0) flashIntensity = 0;
        }

        // 5. Handle Thunder Delay
        if (isThunderPending) {
            thunderDelayTimer -= delta;
            if (thunderDelayTimer <= 0) {
                // Time to play thunder
                if (worldManager.getSoundManager() != null) {
                    worldManager.getSoundManager().playThunder(); // Plays random variant
                }
                isThunderPending = false;
            }
        }
    }

    private void updateLightning(float delta) {
        lightningTimer -= delta;
        if (lightningTimer <= 0) {
            triggerLightning();
            float minTime = (currentIntensity == WeatherIntensity.EXTREME) ? 2f : 5f;
            float maxTime = (currentIntensity == WeatherIntensity.EXTREME) ? 8f : 20f;
            lightningTimer = MathUtils.random(minTime, maxTime);
        }
    }

    private void triggerLightning() {
        this.flashIntensity = 1.0f;

        // Trigger Sound Logic
        if (worldManager.getSoundManager() != null) {
            if (currentIntensity == WeatherIntensity.EXTREME) {
                // IMMEDIATE SOUND (Random Crash)
                worldManager.getSoundManager().playLightningCrash();
                // Cancel pending thunder if any, to avoid noise clutter
                isThunderPending = false;
            } else {
                // DELAYED THUNDER
                thunderDelayTimer = MathUtils.random(1.0f, 4.0f);
                isThunderPending = true;
            }
        }
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

            // Notify SoundManager of weather change for loops (rain/wind)
            if (worldManager.getSoundManager() != null) {
                worldManager.getSoundManager().updateWeatherAudio(nextWeather, nextIntensity);
            }

            updateAtmosphereTargets();
        }
    }

    private WeatherType pickWeatherForBiome(Biome biome) {
        float roll = MathUtils.random();
        switch (biome) {
            case MAZE:
            case FOREST:
            case PLAINS:
                if (roll < 0.20f) return WeatherType.CLEAR;
                if (roll < 0.30f) return WeatherType.FOG;
                if (roll < 0.45f) return WeatherType.RAIN;
                if (roll < 0.90f) return WeatherType.STORM;
                return WeatherType.TORNADO;
            case MOUNTAINS:
                if (roll < 0.20f) return WeatherType.CLEAR;
                if (roll < 0.40f) return WeatherType.SNOW;
                return WeatherType.BLIZZARD;
            case OCEAN:
                if (roll < 0.10f) return WeatherType.CLEAR;
                if (roll < 0.30f) return WeatherType.RAIN;
                return WeatherType.STORM;
            case DESERT:
                if (roll < 0.80f) return WeatherType.CLEAR;
                return WeatherType.TORNADO;
            default: return WeatherType.CLEAR;
        }
    }

    private WeatherIntensity pickIntensityForWeather(WeatherType type) {
        if (type == WeatherType.CLEAR) return WeatherIntensity.LIGHT;
        float roll = MathUtils.random();
        if (roll < 0.10f) return WeatherIntensity.LIGHT;
        if (roll < 0.30f) return WeatherIntensity.MEDIUM;
        if (roll < 0.60f) return WeatherIntensity.HEAVY;
        return WeatherIntensity.EXTREME;
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
                targetFogColor.set(0.6f, 0.6f, 0.7f, 1f);
                globalLightDimmer = 0.8f;
                break;
            case STORM:
                targetFogDistance = 15f;
                targetFogColor.set(0.3f, 0.3f, 0.35f, 1f);
                globalLightDimmer = 0.5f;
                break;
            case SNOW:
                targetFogDistance = 30f;
                targetFogColor.set(0.9f, 0.9f, 0.95f, 1f);
                globalLightDimmer = 0.9f;
                break;
            case BLIZZARD:
                targetFogDistance = 8f;
                targetFogColor.set(0.95f, 0.95f, 1.0f, 1f);
                globalLightDimmer = 0.7f;
                break;
            case FOG:
                targetFogDistance = 12f;
                targetFogColor.set(0.5f, 0.5f, 0.5f, 1f);
                globalLightDimmer = 0.6f;
                break;
            case TORNADO:
                targetFogDistance = 30f;
                targetFogColor.set(0.4f, 0.35f, 0.2f, 1f);
                globalLightDimmer = 0.6f;
                break;
        }
    }

    public boolean isStormy() {
        return currentWeather != WeatherType.CLEAR && currentWeather != WeatherType.FOG;
    }

    public WeatherType getCurrentWeather() { return currentWeather; }
    public WeatherIntensity getCurrentIntensity() { return currentIntensity; }
    public float getFogDistance() { return currentFogDistance; }
    public Color getFogColor() { return currentFogColor; }

    public float getLightIntensity() {
        return Math.min(1.5f, globalLightDimmer + flashIntensity);
    }

    public boolean isLightningFlashing() { return flashIntensity > 0.1f; }

    public void debugCycleWeather() {
        int nextOrdinal = (currentWeather.ordinal() + 1) % WeatherType.values().length;
        this.currentWeather = WeatherType.values()[nextOrdinal];
        Gdx.app.log("WeatherManager", "Debug: Forced weather to " + currentWeather);

        if (worldManager.getSoundManager() != null) {
            worldManager.getSoundManager().updateWeatherAudio(currentWeather, currentIntensity);
        }

        updateAtmosphereTargets();
        weatherTimer = 30f;
    }

    public void debugCycleIntensity() {
        int nextOrdinal = (currentIntensity.ordinal() + 1) % WeatherIntensity.values().length;
        this.currentIntensity = WeatherIntensity.values()[nextOrdinal];
        Gdx.app.log("WeatherManager", "Debug: Forced intensity to " + currentIntensity);

        if (worldManager.getSoundManager() != null) {
            worldManager.getSoundManager().updateWeatherAudio(currentWeather, currentIntensity);
        }

        updateAtmosphereTargets();
        weatherTimer = 30f;
    }

    public float getTraumaLevel() {
        if (currentWeather == WeatherType.TORNADO) {
            return 1.0f;
        }
        return 0.0f;
    }
}
