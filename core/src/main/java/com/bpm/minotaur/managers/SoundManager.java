package com.bpm.minotaur.managers;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.AudioDevice;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.math.MathUtils;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.monster.Monster;
import com.bpm.minotaur.gamedata.item.ItemCategory;
import com.bpm.minotaur.weather.WeatherType;
import com.bpm.minotaur.weather.WeatherIntensity;

import java.util.HashMap;
import java.util.Map;

public class SoundManager {

    private final DebugManager debugManager;
    private final Map<String, Sound> modernSounds = new HashMap<>();
    private final AudioDevice retroAudioDevice;
    private static final int SAMPLE_RATE = 44100;

    // --- Sound Layer Tracking ---
    private long currentRainId = -1;
    private long currentWindId = -1;
    private WeatherType lastWeatherType = null;

    // --- NEW: Dampening State ---
    private boolean isDampened = false;
    private float currentBaseVol = 0.5f;

    public SoundManager(DebugManager debugManager) {
        this.debugManager = debugManager;
        this.retroAudioDevice = Gdx.audio.newAudioDevice(SAMPLE_RATE, true);
        loadModernSounds();
    }

    private void loadModernSounds() {
        loadSound("player_attack", "sounds/player_attack.wav");
        loadSound("player_bow_attack", "sounds/player_bow_attack.wav");
        loadSound("player_spiritual_attack", "sounds/player_spiritual_attack.wav");
        loadSound("pickup_item", "sounds/pickup_item.wav");
        loadSound("door_open", "sounds/door_open.wav");
        loadSound("player_death", "sounds/music/tarmin_enter_fx.ogg");
        loadSound("monster_attack", "sounds/monster_attack.wav");
        loadSound("tarmin_roar", "sounds/tarmin_roar.mp3");
        loadSound("monster_roar", "sounds/monster_roar.wav");
        loadSound("player_level_up", "sounds/level_up.mp3");
        loadSound("attack", "sounds/attack.mp3");
        loadSound("tarmin_laugh", "sounds/tarmin_laugh.ogg");
        loadSound("rain_loop", "sounds/rain.ogg");
        loadSound("wind_loop", "sounds/wind.ogg");
        loadSound("thunder_1", "sounds/thunder_1.ogg");
        loadSound("thunder_2", "sounds/thunder_2.ogg");
        loadSound("thunder_3", "sounds/thunder_3.ogg");
        loadSound("lightning_crash_1", "sounds/lightning_crash_1.ogg");
        loadSound("lightning_crash_2", "sounds/lightning_crash_2.ogg");
        loadSound("lightning_crash_3", "sounds/lightning_crash_3.ogg");
    }

    private void loadSound(String name, String path) {
        FileHandle file = Gdx.files.internal(path);
        if (file.exists()) {
            modernSounds.put(name, Gdx.audio.newSound(file));
        }
    }

    public void stopAllSounds() {
        for (Sound sound : modernSounds.values()) {
            sound.stop();
        }
        currentRainId = -1;
        currentWindId = -1;
        lastWeatherType = null;
    }

    public void stopWeatherEffects() {
        if (currentRainId != -1 && modernSounds.containsKey("rain_loop")) {
            modernSounds.get("rain_loop").stop(currentRainId);
            currentRainId = -1;
        }
        if (currentWindId != -1 && modernSounds.containsKey("wind_loop")) {
            modernSounds.get("wind_loop").stop(currentWindId);
            currentWindId = -1;
        }
        lastWeatherType = null;
    }

    public void playPlayerDeathSound() {
        stopAllSounds();
        MusicManager.getInstance().stop();
        playSound("player_death");
        playSound("tarmin_laugh");
    }

    // --- NEW: Volume Dampening for Interiors ---
    public void setDampened(boolean dampened) {
        if (this.isDampened == dampened) return;
        this.isDampened = dampened;

        // Update currently playing loops immediately
        float modifier = isDampened ? 0.25f : 1.0f;
        float targetVol = currentBaseVol * modifier;

        if (currentRainId != -1 && modernSounds.containsKey("rain_loop")) {
            modernSounds.get("rain_loop").setVolume(currentRainId, targetVol);
        }
        if (currentWindId != -1 && modernSounds.containsKey("wind_loop")) {
            float windVol = (lastWeatherType == WeatherType.STORM) ? targetVol * 0.8f : targetVol;
            modernSounds.get("wind_loop").setVolume(currentWindId, windVol);
        }
    }

    public void updateWeatherAudio(WeatherType type, WeatherIntensity intensity) {
        if (type == lastWeatherType) return;
        lastWeatherType = type;

        if (currentRainId != -1 && modernSounds.containsKey("rain_loop")) modernSounds.get("rain_loop").stop(currentRainId);
        if (currentWindId != -1 && modernSounds.containsKey("wind_loop")) modernSounds.get("wind_loop").stop(currentWindId);

        currentRainId = -1;
        currentWindId = -1;

        currentBaseVol = 0.5f;
        if (intensity == WeatherIntensity.HEAVY) currentBaseVol = 0.8f;
        if (intensity == WeatherIntensity.EXTREME) currentBaseVol = 1.0f;

        float modifier = isDampened ? 0.25f : 1.0f;
        float vol = currentBaseVol * modifier;

        switch (type) {
            case RAIN:
            case STORM:
                if (modernSounds.containsKey("rain_loop")) {
                    currentRainId = modernSounds.get("rain_loop").loop(vol);
                }
                if (type == WeatherType.STORM && modernSounds.containsKey("wind_loop")) {
                    currentWindId = modernSounds.get("wind_loop").loop(vol * 0.8f);
                }
                break;
            case SNOW:
            case BLIZZARD:
                if (modernSounds.containsKey("wind_loop")) {
                    currentWindId = modernSounds.get("wind_loop").loop(vol);
                }
                break;
            case TORNADO:
                if (modernSounds.containsKey("wind_loop")) {
                    currentWindId = modernSounds.get("wind_loop").loop(1.0f * modifier, 0.6f, 0.0f);
                }
                break;
            default:
                break;
        }
    }

    public void playThunder() {
        int variant = MathUtils.random(1, 3);
        // Thunder is loud, but still slightly dampened if indoors
        float vol = isDampened ? 0.5f : 1.0f;
        if (modernSounds.containsKey("thunder_" + variant)) {
            modernSounds.get("thunder_" + variant).play(vol);
        }
    }

    public void playLightningCrash() {
        int variant = MathUtils.random(1, 3);
        float vol = isDampened ? 0.5f : 1.0f;
        if (modernSounds.containsKey("lightning_crash_" + variant)) {
            modernSounds.get("lightning_crash_" + variant).play(vol);
        }
    }

    public void playPlayerAttackSound(Item weapon) {
        if (debugManager.getRenderMode() == DebugManager.RenderMode.MODERN) {
            if (weapon != null) {
                if (weapon.getType() == Item.ItemType.BOW) {
                    playSound("player_bow_attack");
                } else if (weapon.getCategory() == ItemCategory.SPIRITUAL_WEAPON) {
                    playSound("player_spiritual_attack");
                } else {
                    playSound("player_attack");
                }
            } else {
                playSound("player_attack");
            }
        } else {
            if (weapon != null && weapon.getCategory() == ItemCategory.SPIRITUAL_WEAPON) {
                playRetroArpeggio(new int[]{523, 659, 784}, 0.04f);
            } else {
                playRetroSound(110, 0.15f, 0.8f);
            }
        }
    }

    public void playMonsterAttackSound(Monster monster) {
        if (debugManager.getRenderMode() == DebugManager.RenderMode.MODERN) {
            playSound("monster_attack");
        } else {
            playSound("attack");
        }
    }

    public void playPickupItemSound() {
        playSound("pickup_item");
    }

    public void playPlayerLevelUpSound() {
        playSound("player_level_up");
    }

    public void playDoorOpenSound() {
        playSound("door_open");
    }

    public void playCombatStartSound() {
        if (debugManager.getRenderMode() == DebugManager.RenderMode.MODERN) {
            playSound("monster_roar");
        } else {
            playSound("tarmin_roar");
        }
    }

    private void playSound(String name) {
        if (modernSounds.containsKey(name)) {
            modernSounds.get(name).play();
        }
    }

    private void playRetroSound(int frequency, float duration, float volume) {
        int numSamples = (int) (duration * SAMPLE_RATE);
        short[] samples = new short[numSamples];
        int wavelength = SAMPLE_RATE / frequency;
        for (int i = 0; i < numSamples; i++) {
            samples[i] = (short) ((i % wavelength < wavelength / 2) ? (short)(Short.MAX_VALUE * volume) : (short)(-Short.MAX_VALUE * volume));
        }
        retroAudioDevice.writeSamples(samples, 0, numSamples);
    }

    private void playRetroArpeggio(int[] frequencies, float noteDuration) {
        for (int freq : frequencies) {
            playRetroSound(freq, noteDuration, 0.7f);
        }
    }

    public void dispose() {
        stopAllSounds();
        for (Sound sound : modernSounds.values()) {
            sound.dispose();
        }
        retroAudioDevice.dispose();
    }
}
