package com.bpm.minotaur.weather;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.GridPoint2;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
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
    private static final float MIN_WEATHER_DURATION = 300f;
    private static final float MAX_WEATHER_DURATION = 6000f;
    private static final float FOG_LERP_SPEED = 0.5f;

    public WeatherManager(WorldManager worldManager) {
        this.worldManager = worldManager;

        // Init defaults
        this.targetFogDistance = 60f;
        this.targetFogColor = new Color(Color.WHITE);
        this.currentFogDistance = targetFogDistance;
        this.currentFogColor = new Color(Color.WHITE);

        this.lightningTimer = 0f;
        this.flashIntensity = 0f;
        this.globalLightDimmer = 1.0f;

        this.thunderDelayTimer = 0f;
        this.isThunderPending = false;
        this.isRollingThunderPlayed = false;

        // Randomize starting weather for each run based on the starting biome
        randomizeStartingWeather();
    }

    /**
     * Randomizes starting weather for each run based on the starting biome,
     * setting appropriate initial wetness, snow accumulation, and atmosphere targets.
     */
    public void randomizeStartingWeather() {
        Biome startingBiome = Biome.FOREST;
        if (worldManager != null && worldManager.getBiomeManager() != null) {
            GridPoint2 chunkId = worldManager.getCurrentPlayerChunkId();
            if (chunkId != null) {
                Biome b = worldManager.getBiomeManager().getBiome(chunkId);
                if (b != null) {
                    startingBiome = b;
                }
            }
        }

        this.currentWeather = pickWeatherForBiome(startingBiome);
        this.currentIntensity = pickIntensityForWeather(this.currentWeather);
        this.weatherTimer = MathUtils.random(MIN_WEATHER_DURATION, MAX_WEATHER_DURATION);

        // Calibrate initial wetness and snow accumulation based on starting weather
        if (this.currentWeather == WeatherType.RAIN || this.currentWeather == WeatherType.STORM || this.currentWeather == WeatherType.TORNADO) {
            this.wetness = (this.currentIntensity == WeatherIntensity.EXTREME) ? 0.85f
                    : (this.currentIntensity == WeatherIntensity.HEAVY) ? 0.65f
                    : (this.currentIntensity == WeatherIntensity.MEDIUM) ? 0.45f : 0.25f;
            this.snowAccumulation = 0.0f;
        } else if (this.currentWeather == WeatherType.SNOW || this.currentWeather == WeatherType.BLIZZARD) {
            this.wetness = 0.1f;
            this.snowAccumulation = (this.currentWeather == WeatherType.BLIZZARD) ? 0.5f : 0.25f;
        } else {
            this.wetness = 0.0f;
            this.snowAccumulation = 0.0f;
        }

        this.lightningTimer = 0f;
        this.flashIntensity = 0f;
        this.thunderDelayTimer = 0f;
        this.isThunderPending = false;
        this.isRollingThunderPlayed = false;

        if (Gdx.app != null) {
            Gdx.app.log("WeatherManager", "Starting run with randomized weather: " + currentIntensity + " " + currentWeather);
        }

        updateAtmosphereTargets();
        this.currentFogDistance = this.targetFogDistance;
        this.currentFogColor.set(this.targetFogColor);
    }

    private float wetness = 0.0f;
    private float snowAccumulation = 0.0f;
    private boolean isRollingThunderPlayed = false;

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

        // 2b. Track Ground Surface Wetness and Snow Accumulation
        if (currentWeather == WeatherType.RAIN || currentWeather == WeatherType.STORM || currentWeather == WeatherType.TORNADO) {
            float wetRate = (currentIntensity == WeatherIntensity.EXTREME) ? 0.12f
                    : (currentIntensity == WeatherIntensity.HEAVY) ? 0.08f
                    : (currentIntensity == WeatherIntensity.MEDIUM) ? 0.05f : 0.025f;
            wetness = Math.min(1.0f, wetness + delta * wetRate);
            // Rain and storm rapidly wash away snow accumulation
            snowAccumulation = Math.max(0.0f, snowAccumulation - delta * 0.12f);
        } else {
            // Gradual drying during clear or non-rain weather (~45-60s for full dry)
            float dryRate = (currentWeather == WeatherType.CLEAR) ? 0.020f : 0.008f;
            wetness = Math.max(0.0f, wetness - delta * dryRate);
        }

        if (currentWeather == WeatherType.SNOW || currentWeather == WeatherType.BLIZZARD) {
            float snowRate = (currentWeather == WeatherType.BLIZZARD) ? 0.08f
                    : (currentIntensity == WeatherIntensity.HEAVY) ? 0.045f
                    : (currentIntensity == WeatherIntensity.MEDIUM) ? 0.025f : 0.012f;
            snowAccumulation = Math.min(1.0f, snowAccumulation + delta * snowRate);
        } else if (currentWeather != WeatherType.RAIN && currentWeather != WeatherType.STORM) {
            // Gradual melting in clear weather or fog
            float meltRate = (currentWeather == WeatherType.CLEAR) ? 0.018f : 0.006f;
            snowAccumulation = Math.max(0.0f, snowAccumulation - delta * meltRate);
        }

        // 3. Handle Lightning
        if (currentWeather == WeatherType.STORM || currentWeather == WeatherType.TORNADO) {
            updateLightning(delta);
        } else {
            flashIntensity = 0f;
        }

        // 4. Decay Flash
        if (flashIntensity > 0) {
            flashIntensity -= delta * 2.0f;
            if (flashIntensity < 0)
                flashIntensity = 0;
        }

        // 5. Handle Thunder Delay
        if (isThunderPending) {
            thunderDelayTimer -= delta;
            if (thunderDelayTimer <= 0) {
                // Time to play thunder
                if (worldManager != null && worldManager.getSoundManager() != null) {
                    worldManager.getSoundManager().playThunder(); // Plays random variant
                }
                isThunderPending = false;
            }
        }
    }

    private void updateLightning(float delta) {
        lightningTimer -= delta;
        // Rolling thunder prelude ~0.7s before the lightning flash
        if (!isRollingThunderPlayed && lightningTimer <= 0.75f && lightningTimer > 0f) {
            if (worldManager != null && worldManager.getSoundManager() != null) {
                worldManager.getSoundManager().playRollingThunder();
            }
            isRollingThunderPlayed = true;
        }
        if (lightningTimer <= 0) {
            triggerLightning();
            float minTime = (currentIntensity == WeatherIntensity.EXTREME) ? 2f : 5f;
            float maxTime = (currentIntensity == WeatherIntensity.EXTREME) ? 8f : 20f;
            lightningTimer = MathUtils.random(minTime, maxTime);
            isRollingThunderPlayed = false;
        }
    }

    private void triggerLightning() {
        this.flashIntensity = 1.0f;

        // Trigger Sound Logic
        if (worldManager != null && worldManager.getSoundManager() != null) {
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
        if (worldManager == null) return;
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
                if (roll < 0.35f)
                    return WeatherType.CLEAR;
                if (roll < 0.55f)
                    return WeatherType.FOG;
                if (roll < 0.80f)
                    return WeatherType.RAIN;
                if (roll < 0.98f)
                    return WeatherType.STORM;
                return WeatherType.TORNADO;
            case MOUNTAINS:
                if (roll < 0.35f)
                    return WeatherType.CLEAR;
                if (roll < 0.70f)
                    return WeatherType.SNOW;
                return WeatherType.BLIZZARD;
            case OCEAN:
                if (roll < 0.10f)
                    return WeatherType.CLEAR;
                if (roll < 0.30f)
                    return WeatherType.RAIN;
                return WeatherType.STORM;
            case DESERT:
                if (roll < 0.80f)
                    return WeatherType.CLEAR;
                return WeatherType.TORNADO;
            default:
                return WeatherType.CLEAR;
        }
    }

    private WeatherIntensity pickIntensityForWeather(WeatherType type) {
        if (type == WeatherType.CLEAR)
            return WeatherIntensity.LIGHT;
        float roll = MathUtils.random();
        if (roll < 0.10f)
            return WeatherIntensity.LIGHT;
        if (roll < 0.30f)
            return WeatherIntensity.MEDIUM;
        if (roll < 0.60f)
            return WeatherIntensity.HEAVY;
        return WeatherIntensity.EXTREME;
    }

    private void updateAtmosphereTargets() {
        switch (currentWeather) {
            case CLEAR:
                targetFogDistance = 70f;
                targetFogColor.set(Color.WHITE);
                globalLightDimmer = 1.0f;
                break;
            case RAIN:
                switch (currentIntensity) {
                    case LIGHT:
                        targetFogDistance = 45f;
                        targetFogColor.set(0.68f, 0.72f, 0.80f, 1f);
                        globalLightDimmer = 0.88f;
                        break;
                    case MEDIUM:
                        targetFogDistance = 34f;
                        targetFogColor.set(0.58f, 0.62f, 0.72f, 1f);
                        globalLightDimmer = 0.78f;
                        break;
                    case HEAVY:
                        targetFogDistance = 24f;
                        targetFogColor.set(0.48f, 0.52f, 0.64f, 1f);
                        globalLightDimmer = 0.68f;
                        break;
                    case EXTREME:
                        targetFogDistance = 18f;
                        targetFogColor.set(0.38f, 0.42f, 0.55f, 1f);
                        globalLightDimmer = 0.58f;
                        break;
                }
                break;
            case STORM:
                switch (currentIntensity) {
                    case LIGHT:
                        targetFogDistance = 22f;
                        targetFogColor.set(0.35f, 0.35f, 0.42f, 1f);
                        globalLightDimmer = 0.58f;
                        break;
                    case MEDIUM:
                        targetFogDistance = 18f;
                        targetFogColor.set(0.30f, 0.30f, 0.38f, 1f);
                        globalLightDimmer = 0.50f;
                        break;
                    case HEAVY:
                        targetFogDistance = 14f;
                        targetFogColor.set(0.24f, 0.24f, 0.32f, 1f);
                        globalLightDimmer = 0.45f;
                        break;
                    case EXTREME:
                        targetFogDistance = 10f;
                        targetFogColor.set(0.18f, 0.18f, 0.26f, 1f);
                        globalLightDimmer = 0.38f;
                        break;
                }
                break;
            case SNOW:
                switch (currentIntensity) {
                    case LIGHT:
                        targetFogDistance = 40f;
                        targetFogColor.set(0.92f, 0.94f, 0.98f, 1f);
                        globalLightDimmer = 0.95f;
                        break;
                    case MEDIUM:
                        targetFogDistance = 28f;
                        targetFogColor.set(0.88f, 0.90f, 0.95f, 1f);
                        globalLightDimmer = 0.90f;
                        break;
                    case HEAVY:
                        targetFogDistance = 18f;
                        targetFogColor.set(0.82f, 0.85f, 0.92f, 1f);
                        globalLightDimmer = 0.82f;
                        break;
                    case EXTREME:
                        targetFogDistance = 12f;
                        targetFogColor.set(0.78f, 0.82f, 0.90f, 1f);
                        globalLightDimmer = 0.75f;
                        break;
                }
                break;
            case BLIZZARD:
                // Stark, disorienting whiteout
                switch (currentIntensity) {
                    case LIGHT:
                        targetFogDistance = 14f;
                        targetFogColor.set(0.92f, 0.94f, 0.98f, 1f);
                        globalLightDimmer = 0.75f;
                        break;
                    case MEDIUM:
                        targetFogDistance = 11f;
                        targetFogColor.set(0.95f, 0.96f, 1.0f, 1f);
                        globalLightDimmer = 0.70f;
                        break;
                    case HEAVY:
                        targetFogDistance = 8.5f;
                        targetFogColor.set(0.96f, 0.97f, 1.0f, 1f);
                        globalLightDimmer = 0.65f;
                        break;
                    case EXTREME:
                        targetFogDistance = 6.5f;
                        targetFogColor.set(0.98f, 0.99f, 1.0f, 1f);
                        globalLightDimmer = 0.60f;
                        break;
                }
                break;
            case FOG:
                switch (currentIntensity) {
                    case LIGHT:
                        targetFogDistance = 25f;
                        targetFogColor.set(0.65f, 0.65f, 0.68f, 1f);
                        globalLightDimmer = 0.75f;
                        break;
                    case MEDIUM:
                        targetFogDistance = 16f;
                        targetFogColor.set(0.55f, 0.55f, 0.58f, 1f);
                        globalLightDimmer = 0.65f;
                        break;
                    case HEAVY:
                        targetFogDistance = 10f;
                        targetFogColor.set(0.45f, 0.45f, 0.48f, 1f);
                        globalLightDimmer = 0.55f;
                        break;
                    case EXTREME:
                        targetFogDistance = 6f;
                        targetFogColor.set(0.38f, 0.38f, 0.40f, 1f);
                        globalLightDimmer = 0.45f;
                        break;
                }
                break;
            case TORNADO:
                // Ominous sickly green-black atmospheric supercell
                targetFogDistance = 22f;
                targetFogColor.set(0.28f, 0.35f, 0.24f, 1f);
                globalLightDimmer = 0.48f;
                break;
        }
    }

    public boolean isStormy() {
        return currentWeather != WeatherType.CLEAR && currentWeather != WeatherType.FOG;
    }

    public WeatherType getCurrentWeather() {
        return currentWeather;
    }

    public WeatherIntensity getCurrentIntensity() {
        return currentIntensity;
    }

    public float getFogDistance() {
        return currentFogDistance;
    }

    public Color getFogColor() {
        return currentFogColor;
    }

    public float getLightIntensity() {
        return Math.min(1.5f, globalLightDimmer + flashIntensity);
    }

    public float getGlobalLightDimmer() {
        return globalLightDimmer;
    }

    public float getFlashIntensity() {
        return flashIntensity;
    }

    public boolean isLightningFlashing() {
        return flashIntensity > 0.1f;
    }

    public void setCurrentWeather(WeatherType weather) {
        if (weather != null && this.currentWeather != weather) {
            this.currentWeather = weather;
            if (worldManager != null && worldManager.getSoundManager() != null) {
                worldManager.getSoundManager().updateWeatherAudio(currentWeather, currentIntensity);
            }
            updateAtmosphereTargets();
        }
    }

    public void setCurrentIntensity(WeatherIntensity intensity) {
        if (intensity != null && this.currentIntensity != intensity) {
            this.currentIntensity = intensity;
            if (worldManager != null && worldManager.getSoundManager() != null) {
                worldManager.getSoundManager().updateWeatherAudio(currentWeather, currentIntensity);
            }
            updateAtmosphereTargets();
        }
    }

    public void debugCycleWeather() {
        int nextOrdinal = (currentWeather.ordinal() + 1) % WeatherType.values().length;
        this.currentWeather = WeatherType.values()[nextOrdinal];
        if (Gdx.app != null) {
            Gdx.app.log("WeatherManager", "Debug: Forced weather to " + currentWeather);
        }

        if (worldManager != null && worldManager.getSoundManager() != null) {
            worldManager.getSoundManager().updateWeatherAudio(currentWeather, currentIntensity);
        }

        updateAtmosphereTargets();
        weatherTimer = 30f;
    }

    public void debugCycleIntensity() {
        int nextOrdinal = (currentIntensity.ordinal() + 1) % WeatherIntensity.values().length;
        this.currentIntensity = WeatherIntensity.values()[nextOrdinal];
        if (Gdx.app != null) {
            Gdx.app.log("WeatherManager", "Debug: Forced intensity to " + currentIntensity);
        }

        if (worldManager != null && worldManager.getSoundManager() != null) {
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

    public float getAmbientTemperature(Biome biome) {
        if (biome == Biome.MAZE) {
            // Subterranean dungeon / shelter: stable, insulated cave climate
            return 20.0f; // 68°F - temperate baseline
        }

        float baseTemp = 20.0f; // Default temperate baseline

        switch (biome) {
            case PLAINS:
                baseTemp = 22.0f; // 71.6°F
                break;
            case FOREST:
                baseTemp = 20.0f; // 68°F
                break;
            case MOUNTAINS:
                baseTemp = 15.0f; // 59°F - crisp alpine elevation
                break;
            case DESERT:
                baseTemp = 28.0f; // 82.4°F - warm arid climate
                break;
            case OCEAN:
                baseTemp = 19.0f; // 66.2°F - maritime breeze
                break;
            default:
                baseTemp = 20.0f;
                break;
        }

        // Weather modifiers - temperatures do NOT become extreme unless weather supports it
        switch (currentWeather) {
            case CLEAR:
                if (biome == Biome.DESERT) {
                    baseTemp += (currentIntensity == WeatherIntensity.EXTREME) ? 10.0f : 4.0f;
                } else {
                    baseTemp += 2.0f; // Pleasant sunny warmth
                }
                break;
            case FOG:
                baseTemp -= 1.0f; // Slight damp chill
                break;
            case RAIN:
                baseTemp -= 2.0f; // Mild cooling rain (e.g. 18°C)
                break;
            case STORM:
                baseTemp -= 3.0f; // Cool thunderstorm (e.g. 17°C)
                break;
            case TORNADO:
                baseTemp -= 3.0f; // Strong wind chill
                break;
            case SNOW:
                // Winter weather: drops near freezing
                baseTemp = (biome == Biome.MOUNTAINS) ? -2.0f : 1.0f;
                break;
            case BLIZZARD:
                // Severe winter weather: extreme sub-zero freezing
                float blizzardBase = (biome == Biome.MOUNTAINS) ? -16.0f : -10.0f;
                if (currentIntensity == WeatherIntensity.HEAVY) {
                    blizzardBase -= 3.0f;
                } else if (currentIntensity == WeatherIntensity.EXTREME) {
                    blizzardBase -= 6.0f;
                }
                baseTemp = blizzardBase;
                break;
        }

        // Subtle intensity adjustments for rain/storm
        if (currentWeather == WeatherType.RAIN || currentWeather == WeatherType.STORM) {
            if (currentIntensity == WeatherIntensity.HEAVY) {
                baseTemp -= 1.0f;
            } else if (currentIntensity == WeatherIntensity.EXTREME) {
                baseTemp -= 2.0f;
            }
        }

        return baseTemp;
    }

    public boolean isPrecipitation() {
        return isPrecipitation(currentWeather);
    }

    public boolean isPrecipitation(WeatherType type) {
        return type == WeatherType.RAIN || type == WeatherType.STORM ||
                type == WeatherType.SNOW || type == WeatherType.BLIZZARD ||
                type == WeatherType.TORNADO;
    }

    public float getWetness() {
        return wetness;
    }

    public void setWetness(float wetness) {
        this.wetness = MathUtils.clamp(wetness, 0f, 1f);
    }

    public float getSnowAccumulation() {
        return snowAccumulation;
    }

    public void setSnowAccumulation(float snowAccumulation) {
        this.snowAccumulation = MathUtils.clamp(snowAccumulation, 0f, 1f);
    }

    /**
     * Returns cloud coverage factor (0.0 = completely clear skies, 1.0 = heavy overcast/storm canopy).
     */
    public float getCloudCover() {
        switch (currentWeather) {
            case CLEAR:
                return 0.0f;
            case FOG:
                return 0.45f;
            case RAIN:
                switch (currentIntensity) {
                    case LIGHT: return 0.35f;
                    case MEDIUM: return 0.55f;
                    case HEAVY: return 0.75f;
                    case EXTREME: default: return 0.90f;
                }
            case STORM:
                return (currentIntensity == WeatherIntensity.LIGHT) ? 0.85f : 1.0f;
            case SNOW:
                switch (currentIntensity) {
                    case LIGHT: return 0.40f;
                    case MEDIUM: return 0.60f;
                    case HEAVY: return 0.80f;
                    case EXTREME: default: return 0.92f;
                }
            case BLIZZARD:
            case TORNADO:
            default:
                return 1.0f;
        }
    }

    /**
     * Calculates the 3D world-space wind velocity vector.
     */
    public void getWindVector(Vector3 out) {
        if (out == null) return;
        float intensityMod = (currentIntensity == WeatherIntensity.EXTREME) ? 1.8f
                : (currentIntensity == WeatherIntensity.HEAVY) ? 1.35f
                : (currentIntensity == WeatherIntensity.MEDIUM) ? 1.0f : 0.7f;
        switch (currentWeather) {
            case STORM:
                out.set(-4.5f * intensityMod, 0f, 1.8f * intensityMod);
                break;
            case BLIZZARD:
                out.set(-9.5f * intensityMod, 0f, 3.8f * intensityMod);
                break;
            case TORNADO:
                out.set(-15.0f, 0f, 6.0f);
                break;
            case RAIN:
                out.set(-1.8f * intensityMod, 0f, 0.7f * intensityMod);
                break;
            case SNOW:
                out.set(-0.6f * intensityMod, 0f, 0.25f * intensityMod); // gentle drift
                break;
            default:
                out.set(-0.2f, 0f, 0.1f);
                break;
        }
    }
}
