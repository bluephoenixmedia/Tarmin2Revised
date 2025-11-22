package com.bpm.minotaur.managers;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.AudioDevice;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.files.FileHandle;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.monster.Monster;
import com.bpm.minotaur.gamedata.item.ItemCategory;
import com.bpm.minotaur.weather.WeatherType; // NEW IMPORT
import com.bpm.minotaur.weather.WeatherIntensity; // NEW IMPORT

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class SoundManager {

    private final DebugManager debugManager;
    private final Map<String, Sound> modernSounds = new HashMap<>();
    private final AudioDevice retroAudioDevice;
    private static final int SAMPLE_RATE = 44100;

    // --- NEW: Weather Loop Tracking ---
    private long currentRainId = -1;
    private long currentWindId = -1;
    private WeatherType lastWeatherType = null;

    public SoundManager(DebugManager debugManager) {
        this.debugManager = debugManager;
        this.retroAudioDevice = Gdx.audio.newAudioDevice(SAMPLE_RATE, true);
        loadModernSounds();
    }

    private void loadModernSounds() {
        // Sounds for player actions
        loadSound("player_attack", "sounds/player_attack.wav");
        loadSound("player_bow_attack", "sounds/player_bow_attack.wav");
        loadSound("player_spiritual_attack", "sounds/player_spiritual_attack.wav");
        loadSound("pickup_item", "sounds/pickup_item.wav");
        loadSound("door_open", "sounds/door_open.wav");
        loadSound("player_death", "sounds/music/tarmin_enter_fx.ogg");

        // Sounds for monster attacks
        loadSound("monster_attack", "sounds/monster_attack.wav");
        loadSound("tarmin_roar", "sounds/tarmin_roar.mp3");
        loadSound("monster_roar", "sounds/monster_roar.wav");
        loadSound("player_level_up", "sounds/level_up.mp3");
        loadSound("attack", "sounds/attack.mp3");

        // --- NEW: Weather Sounds (Ensure these exist in assets!) ---
        loadSound("rain_loop", "sounds/rain.wav");   // Add this file
        loadSound("wind_loop", "sounds/wind.wav");   // Add this file
        loadSound("thunder", "sounds/thunder.ogg");  // Add this file
    }

    // ... (Existing methods: loadSound, playPlayerAttackSound, etc. KEEP THEM) ...
    // [COPY PREVIOUS METHODS HERE]

    private void loadSound(String name, String path) {
        FileHandle file = Gdx.files.internal(path);
        if (file.exists()) {
            modernSounds.put(name, Gdx.audio.newSound(file));
        } else {
            // Only warn, don't crash if missing
            // Gdx.app.log("SoundManager", "Sound file not found: " + path);
        }
    }



    // ... (Existing playPlayerAttackSound, playMonsterAttackSound, etc.) ...

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
        if (debugManager.getRenderMode() == DebugManager.RenderMode.MODERN) {
            playSound("pickup_item");
        } else {
            playSound("pickup_item");
        }
    }

    public void playPlayerLevelUpSound() {
        playSound("player_level_up");
    }

    public void playPlayerDeathSound() {
        playSound("player_death");
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

    // --- NEW: Weather Audio Logic ---

    public void updateWeatherAudio(WeatherType type, WeatherIntensity intensity) {
        if (type == lastWeatherType) return;
        lastWeatherType = type;

        // Stop existing loops
        if (currentRainId != -1 && modernSounds.containsKey("rain_loop")) modernSounds.get("rain_loop").stop(currentRainId);
        if (currentWindId != -1 && modernSounds.containsKey("wind_loop")) modernSounds.get("wind_loop").stop(currentWindId);

        float vol = 0.5f;
        if (intensity == WeatherIntensity.HEAVY) vol = 0.8f;
        if (intensity == WeatherIntensity.EXTREME) vol = 1.0f;

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
                    // THE ELDRITCH ROAR:
                    // Volume 1.0 (Max)
                    // Pitch 0.6 (Deep, slow, massive)
                    // Pan 0.0 (Center)
                    currentWindId = modernSounds.get("wind_loop").loop(1.0f, 0.6f, 0.0f);
                }
                break;
            default:
                break;
        }
    }

    public void playThunder() {
        playSound("thunder");
    }

    // ... (Rest of the existing Retro sound methods) ...

    private void playRetroClick(float duration, float volume) { /* ... */ }
    private void playRetroSwoosh(int startFreq, int endFreq, float duration, float volume) { /* ... */ }

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

    private void playRetroSlide(int startFreq, int endFreq, float duration) { /* ... */ }
    private void playRetroArpeggio(int[] frequencies, float noteDuration) {
        for (int freq : frequencies) {
            playRetroSound(freq, noteDuration, 0.7f);
        }
    }
    private void playRetroGrowl(float duration) { /* ... */ }

    public void dispose() {
        for (Sound sound : modernSounds.values()) {
            sound.dispose();
        }
        retroAudioDevice.dispose();
    }
}
