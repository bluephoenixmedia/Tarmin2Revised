package com.bpm.minotaur.managers;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.AudioDevice;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.files.FileHandle;
import com.bpm.minotaur.gamedata.Item;
import com.bpm.minotaur.gamedata.Monster;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class SoundManager {

    private final DebugManager debugManager;
    private final Map<String, Sound> modernSounds = new HashMap<>();
    private final AudioDevice retroAudioDevice;
    private static final int SAMPLE_RATE = 44100;

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

    }

    private void loadSound(String name, String path) {
        FileHandle file = Gdx.files.internal(path);
        if (file.exists()) {
            modernSounds.put(name, Gdx.audio.newSound(file));
        } else {
            Gdx.app.log("SoundManager", "Sound file not found: " + path);
        }
    }

    public void playPlayerAttackSound(Item weapon) {
        if (debugManager.getRenderMode() == DebugManager.RenderMode.MODERN) {
            if (weapon != null) {
                if (weapon.getType() == Item.ItemType.BOW) {
                    playSound("player_bow_attack");
                } else if (weapon.getCategory() == Item.ItemCategory.SPIRITUAL_WEAPON) {
                    playSound("player_spiritual_attack");
                } else {
                    playSound("player_attack");
                }
            } else {
                playSound("player_attack");
            }
        } else {
            if (weapon != null && weapon.getCategory() == Item.ItemCategory.SPIRITUAL_WEAPON) {
                playRetroArpeggio(new int[]{523, 659, 784}, 0.04f); // C5-E5-G5 arpeggio
            } else {
                playRetroSound(110, 0.15f, 0.8f); // Low A note for melee/bow
            }
        }
    }

    public void playMonsterAttackSound(Monster monster) {
        if (debugManager.getRenderMode() == DebugManager.RenderMode.MODERN) {
            playSound("monster_attack");
        } else {
           // playRetroSound(73, 0.2f, 0.7f); // Lower D note for monster
            playSound("attack");
        }
    }

    public void playPickupItemSound() {
        if (debugManager.getRenderMode() == DebugManager.RenderMode.MODERN) {
            playSound("pickup_item");
        } else {
            //playRetroArpeggio(new int[]{1047, 1319}, 0.05f); // High C6-E6 arpeggio
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

/*        if (debugManager.getRenderMode() == DebugManager.RenderMode.MODERN) {
            playSound("door_open");
        } else {
            // A short, sharp click
            playRetroClick(0.03f, 0.5f);

            // A downward-sweeping "swoosh"
            playRetroSwoosh(800, 300, 0.15f, 0.6f);
        }
  */
    }

    public void playCombatStartSound() {
        if (debugManager.getRenderMode() == DebugManager.RenderMode.MODERN) {
            playSound("monster_roar");
        } else {
            //playRetroGrowl(0.5f);
            playSound("tarmin_roar");
        }
    }

    /**
     * Generates a short burst of white noise for a "click" effect.
     * @param duration The duration in seconds.
     * @param volume The volume (0.0 to 1.0).
     */
    private void playRetroClick(float duration, float volume) {
        int numSamples = (int) (duration * SAMPLE_RATE);
        short[] samples = new short[numSamples];
        Random random = new Random();

        // Apply a simple fade-out (linear)
        for (int i = 0; i < numSamples; i++) {
            float amplitude = (1.0f - (float) i / numSamples) * volume;
            samples[i] = (short) ((random.nextFloat() * 2.0f - 1.0f) * Short.MAX_VALUE * amplitude);
        }

        retroAudioDevice.writeSamples(samples, 0, numSamples);
    }

    /**
     * Generates a sine wave that sweeps from a start to an end frequency.
     * @param startFreq The starting frequency (e.g., 800 Hz).
     * @param endFreq The ending frequency (e.g., 300 Hz).
     * @param duration The duration in seconds.
     * @param volume The volume (0.0 to 1.0).
     */
    private void playRetroSwoosh(int startFreq, int endFreq, float duration, float volume) {
        int numSamples = (int) (duration * SAMPLE_RATE);
        short[] samples = new short[numSamples];
        double angle = 0.0;

        // Calculate how much to change the frequency per sample
        double freqStep = (double) (endFreq - startFreq) / numSamples;
        double currentFreq = startFreq;

        for (int i = 0; i < numSamples; i++) {
            // Apply a fade-in and fade-out to prevent popping
            float amplitude = volume;
            float fadeIn = (float) i / (numSamples / 10.0f); // 10% fade-in
            float fadeOut = (float) (numSamples - i) / (numSamples / 10.0f); // 10% fade-out

            if (fadeIn < 1.0f) amplitude *= fadeIn;
            if (fadeOut < 1.0f) amplitude *= fadeOut;

            samples[i] = (short) (Math.sin(angle) * Short.MAX_VALUE * amplitude);

            // Update angle based on the *current* frequency
            angle += 2 * Math.PI * currentFreq / SAMPLE_RATE;

            // Update the frequency for the next sample
            currentFreq += freqStep;
        }

        retroAudioDevice.writeSamples(samples, 0, numSamples);
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

    private void playRetroSlide(int startFreq, int endFreq, float duration) {
        int numSamples = (int) (duration * SAMPLE_RATE);
        short[] samples = new short[numSamples];
        float freq = startFreq;
        float freqStep = (endFreq - startFreq) / (float)numSamples;
        double angle = 0;
        for (int i = 0; i < numSamples; i++) {
            samples[i] = (short) (Math.signum(Math.sin(angle)) * Short.MAX_VALUE * 0.7f);
            angle += 2 * Math.PI * freq / SAMPLE_RATE;
            freq += freqStep;
        }
        retroAudioDevice.writeSamples(samples, 0, numSamples);
    }

    private void playRetroArpeggio(int[] frequencies, float noteDuration) {
        for (int freq : frequencies) {
            playRetroSound(freq, noteDuration, 0.7f);
        }
    }

    private void playRetroGrowl(float duration) {
        int numSamples = (int) (duration * SAMPLE_RATE);
        short[] samples = new short[numSamples];
        Random random = new Random();
        float freq = 60; // Start with a low frequency
        for (int i = 0; i < numSamples; i++) {
            // Mix a low-frequency square wave with some noise
            int wavelength = (int) (SAMPLE_RATE / freq);
            short waveSample = (short) ((i % wavelength < wavelength / 2) ? Short.MAX_VALUE : -Short.MAX_VALUE);
            short noiseSample = (short) (random.nextFloat() * Short.MAX_VALUE / 2); // Less intense noise
            samples[i] = (short) (waveSample * 0.6f + noiseSample * 0.4f);

            // Slowly decrease the frequency to make it sound more like a growl
            if (i % 100 == 0) {
                freq *= 0.995f;
            }
        }
        retroAudioDevice.writeSamples(samples, 0, numSamples);
    }

    public void dispose() {
        for (Sound sound : modernSounds.values()) {
            sound.dispose();
        }
        retroAudioDevice.dispose();
    }
}
