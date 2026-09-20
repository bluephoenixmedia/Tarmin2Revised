// Path: core/src/main/java/com/bpm/minotaur/managers/MusicManager.java
package com.bpm.minotaur.managers;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.math.MathUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * Robust dynamic music manager supporting state-driven transitions, smooth
 * crossfading between exploration and combat, atmospheric resting ducking,
 * paused exploration track continuity, and cached one-shot stingers.
 */
public class MusicManager {

    public enum MusicState {
        MENU,
        SHELTER,
        EXPLORATION,
        COMBAT,
        BOSS,
        PAUSED,
        MUTED
    }

    private static MusicManager instance;
    private final AssetManager assetManager;
    private final Map<String, Sound> stingerCache = new HashMap<>();

    // Track states
    private Music currentTrack;
    private String currentTrackPath;
    private MusicState currentState = MusicState.MENU;

    // Track Continuity / Resume Memory (combat interrupt)
    private Music pausedExplorationTrack;
    private String pausedExplorationPath;
    private float pausedExplorationPosition = 0f;

    // Crossfading State
    private Music outgoingTrack;
    private String outgoingTrackPath;
    private float outgoingTrackVolume = 0f;
    private float crossfadeTimer = 0f;
    private float crossfadeDuration = 0.6f;
    private boolean isCrossfading = false;
    private boolean pauseOutgoingOnFadeComplete = false;

    // Relative volume of current track during crossfade (0.0 to 1.0)
    private float currentTrackVolume = 1.0f;

    // Master Volume & Ducking
    private float masterVolume = 0.70f;
    private float customDuckFactor = 1.0f;
    private float currentDuckFactor = 1.0f;
    private boolean isResting = false;

    // Ambient loop (used for Doom / Void drone effects)
    private Music ambientTrack;
    private String ambientTrackPath;
    private float ambientVolume = 0.5f;

    private MusicManager() {
        assetManager = new AssetManager();
    }

    public static MusicManager getInstance() {
        if (instance == null) {
            instance = new MusicManager();
        }
        return instance;
    }

    public void loadMusic(String path) {
        if (path == null) return;
        try {
            assetManager.load(path, Music.class);
        } catch (Exception e) {
            if (Gdx.app != null) Gdx.app.error("MusicManager", "Error queueing music: " + path, e);
        }
    }

    public void finishLoading() {
        try {
            assetManager.finishLoading();
        } catch (Exception e) {
            if (Gdx.app != null) Gdx.app.error("MusicManager", "Error finishLoading music assets", e);
        }
    }

    public MusicState getCurrentState() {
        return currentState;
    }

    public String getCurrentTrackPath() {
        return currentTrackPath;
    }

    public boolean isPlaying() {
        return currentTrack != null && currentTrack.isPlaying();
    }

    public float getMasterVolume() {
        return masterVolume;
    }

    public float getVolume() {
        return masterVolume;
    }

    public void setMasterVolume(float volume) {
        this.masterVolume = Math.max(0.0f, Math.min(1.0f, volume));
        applyVolumes();
    }

    public void setVolume(float volume) {
        setMasterVolume(volume);
    }

    public void setResting(boolean resting) {
        this.isResting = resting;
    }

    public boolean isResting() {
        return isResting;
    }

    public void duckMusic(float factor) {
        this.customDuckFactor = Math.max(0.0f, Math.min(1.0f, factor));
    }

    public void restoreMusicVolume() {
        this.customDuckFactor = 1.0f;
    }

    private float computeVolume(float trackRelativeVol) {
        return Math.max(0.0f, Math.min(1.0f, masterVolume * currentDuckFactor * trackRelativeVol));
    }

    private void applyVolumes() {
        if (currentTrack != null) {
            try {
                currentTrack.setVolume(computeVolume(currentTrackVolume));
            } catch (Exception ignored) {}
        }
        if (outgoingTrack != null) {
            try {
                float outVol = isCrossfading && crossfadeDuration > 0f
                        ? (1.0f - Math.min(1.0f, crossfadeTimer / crossfadeDuration)) * outgoingTrackVolume
                        : 0f;
                outgoingTrack.setVolume(computeVolume(outVol));
            } catch (Exception ignored) {}
        }
    }

    public void update(float delta) {
        if (delta <= 0) return;

        // 1. Smooth ducking interpolation (resting ducks to 0.40f, else customDuckFactor)
        float targetDuck = isResting ? 0.40f : customDuckFactor;
        if (Math.abs(currentDuckFactor - targetDuck) > 0.005f) {
            currentDuckFactor = MathUtils.lerp(currentDuckFactor, targetDuck, Math.min(1.0f, 4.0f * delta));
            applyVolumes();
        }

        // 2. Crossfade interpolation
        if (isCrossfading) {
            crossfadeTimer += delta;
            float progress = Math.min(1.0f, crossfadeTimer / crossfadeDuration);

            if (outgoingTrack != null) {
                float outVol = (1.0f - progress) * outgoingTrackVolume;
                try {
                    outgoingTrack.setVolume(computeVolume(outVol));
                } catch (Exception ignored) {}
            }

            if (currentTrack != null) {
                currentTrackVolume = progress;
                try {
                    currentTrack.setVolume(computeVolume(currentTrackVolume));
                } catch (Exception ignored) {}
            }

            if (progress >= 1.0f) {
                isCrossfading = false;
                currentTrackVolume = 1.0f;
                stopOrPauseOutgoing();
                applyVolumes();
            }
        }
    }

    private void stopOrPauseOutgoing() {
        if (outgoingTrack != null) {
            try {
                if (pauseOutgoingOnFadeComplete) {
                    outgoingTrack.pause();
                } else {
                    outgoingTrack.stop();
                    if (outgoingTrackPath != null && !assetManager.isLoaded(outgoingTrackPath)) {
                        outgoingTrack.dispose();
                    }
                }
            } catch (Exception ignored) {}
            outgoingTrack = null;
            outgoingTrackPath = null;
        }
    }

    private Music loadOrGetMusic(String path) {
        if (path == null || Gdx.audio == null) return null;
        try {
            if (assetManager.isLoaded(path, Music.class)) {
                return assetManager.get(path, Music.class);
            }
            if (Gdx.files != null) {
                com.badlogic.gdx.files.FileHandle file = Gdx.files.internal(path);
                if (file.exists()) {
                    return Gdx.audio.newMusic(file);
                }
            }
            assetManager.load(path, Music.class);
            assetManager.finishLoading();
            if (assetManager.isLoaded(path, Music.class)) {
                return assetManager.get(path, Music.class);
            }
        } catch (Exception e) {
            if (Gdx.app != null) Gdx.app.error("MusicManager", "Failed to load music: " + path, e);
        }
        return null;
    }

    public void crossfadeTo(String path, MusicState newState, float duration, boolean loop, boolean pauseCurrent) {
        if (path == null) {
            stopWithFade(duration);
            return;
        }

        // If the same track is already playing, simply switch state if needed
        if (path.equals(currentTrackPath) && currentTrack != null && currentTrack.isPlaying()) {
            this.currentState = newState;
            return;
        }

        // Clean up any pending outgoing track from previous crossfade
        if (outgoingTrack != null) {
            stopOrPauseOutgoing();
        }

        // If entering combat/boss from exploration/shelter, remember the track
        if (pauseCurrent && (currentState == MusicState.EXPLORATION || currentState == MusicState.SHELTER)) {
            pausedExplorationTrack = currentTrack;
            pausedExplorationPath = currentTrackPath;
            if (currentTrack != null) {
                try {
                    pausedExplorationPosition = currentTrack.getPosition();
                } catch (Exception ignored) {
                    pausedExplorationPosition = 0f;
                }
            }
        }

        // Current track becomes outgoing track
        if (currentTrack != null) {
            outgoingTrack = currentTrack;
            outgoingTrackPath = currentTrackPath;
            outgoingTrackVolume = currentTrackVolume;
            pauseOutgoingOnFadeComplete = pauseCurrent;
        }

        currentTrack = null;
        currentTrackPath = path;
        currentState = newState;

        currentTrack = loadOrGetMusic(path);
        if (currentTrack != null) {
            try {
                currentTrack.setLooping(loop);
                currentTrackVolume = (outgoingTrack != null && duration > 0f) ? 0.0f : 1.0f;
                currentTrack.setVolume(computeVolume(currentTrackVolume));
                currentTrack.play();
            } catch (Exception e) {
                if (Gdx.app != null) Gdx.app.error("MusicManager", "Error playing track: " + path, e);
            }
        }

        if (outgoingTrack != null && duration > 0f) {
            isCrossfading = true;
            crossfadeDuration = duration;
            crossfadeTimer = 0f;
        } else {
            isCrossfading = false;
            currentTrackVolume = 1.0f;
            stopOrPauseOutgoing();
            applyVolumes();
        }
    }

    public void playTrack(String path) {
        playTrack(path, true);
    }

    public void playTrack(String path, boolean looping) {
        crossfadeTo(path, MusicState.MENU, 0.4f, looping, false);
    }

    public void playMenuMusic(String path) {
        crossfadeTo(path, MusicState.MENU, 0.5f, true, false);
    }

    public void playShelterMusic(String path) {
        crossfadeTo(path, MusicState.SHELTER, 0.8f, true, false);
    }

    public void playExplorationMusic(String path) {
        crossfadeTo(path, MusicState.EXPLORATION, 0.8f, true, false);
    }

    public void playCombatMusic(String path) {
        crossfadeTo(path, MusicState.COMBAT, 0.6f, true, true);
    }

    public void playBossCombat(String path) {
        crossfadeTo(path, MusicState.BOSS, 0.6f, true, true);
    }

    public void exitCombat() {
        if (currentState != MusicState.COMBAT && currentState != MusicState.BOSS) {
            return;
        }

        if (pausedExplorationPath != null) {
            // Smoothly resume paused exploration track
            if (outgoingTrack != null) {
                stopOrPauseOutgoing();
            }
            outgoingTrack = currentTrack;
            outgoingTrackPath = currentTrackPath;
            outgoingTrackVolume = currentTrackVolume;
            pauseOutgoingOnFadeComplete = false;

            currentTrack = (pausedExplorationTrack != null) ? pausedExplorationTrack : loadOrGetMusic(pausedExplorationPath);
            currentTrackPath = pausedExplorationPath;
            currentState = MusicState.EXPLORATION;
            pausedExplorationTrack = null;
            pausedExplorationPath = null;

            if (currentTrack != null) {
                try {
                    currentTrack.setLooping(true);
                    currentTrack.play();
                } catch (Exception ignored) {}
            }

            isCrossfading = true;
            crossfadeDuration = 1.0f;
            crossfadeTimer = 0f;
            currentTrackVolume = 0.0f;
            applyVolumes();
        } else {
            // Fade out combat track
            stopWithFade(1.0f);
        }
    }

    public void stopWithFade(float duration) {
        if (currentTrack != null) {
            if (duration <= 0.05f) {
                stop();
                return;
            }
            if (outgoingTrack != null) {
                stopOrPauseOutgoing();
            }
            outgoingTrack = currentTrack;
            outgoingTrackPath = currentTrackPath;
            outgoingTrackVolume = currentTrackVolume;
            pauseOutgoingOnFadeComplete = false;

            currentTrack = null;
            currentTrackPath = null;
            currentState = MusicState.PAUSED;

            isCrossfading = true;
            crossfadeDuration = duration;
            crossfadeTimer = 0f;
        } else {
            currentState = MusicState.PAUSED;
        }
    }

    public void playStinger(String path) {
        if (path == null) return;
        try {
            if (Gdx.audio == null || Gdx.files == null) return;
            Sound sound = stingerCache.get(path);
            if (sound == null) {
                com.badlogic.gdx.files.FileHandle file = Gdx.files.internal(path);
                if (file.exists()) {
                    sound = Gdx.audio.newSound(file);
                    stingerCache.put(path, sound);
                }
            }
            if (sound != null) {
                float sfxVol = SettingsManager.getInstance().getSfxVolume();
                sound.play(sfxVol);
                // Briefly duck music for stinger impact
                duckMusic(0.5f);
            }
        } catch (Exception e) {
            if (Gdx.app != null) Gdx.app.error("MusicManager", "Failed to play stinger: " + path, e);
        }
    }

    public void playAmbientLoop(String path, float targetVol) {
        if (path == null) {
            stopAmbientLoop();
            return;
        }
        if (path.equals(ambientTrackPath)) {
            if (ambientTrack != null) {
                ambientVolume = targetVol;
                ambientTrack.setVolume(targetVol * masterVolume);
            }
            return;
        }
        if (ambientTrack != null && ambientTrack.isPlaying()) {
            ambientTrack.stop();
        }
        try {
            ambientTrack = loadOrGetMusic(path);
            if (ambientTrack != null) {
                ambientTrack.setLooping(true);
                ambientVolume = targetVol;
                ambientTrack.setVolume(targetVol * masterVolume);
                ambientTrack.play();
                ambientTrackPath = path;
            }
        } catch (Exception e) {
            if (Gdx.app != null) Gdx.app.error("MusicManager", "Failed to play ambient loop: " + path, e);
        }
    }

    public void stopAmbientLoop() {
        if (ambientTrack != null) {
            try {
                ambientTrack.stop();
            } catch (Exception ignored) {}
            ambientTrack = null;
            ambientTrackPath = null;
        }
    }

    public void stop() {
        if (outgoingTrack != null) {
            try {
                outgoingTrack.stop();
                if (outgoingTrackPath != null && !assetManager.isLoaded(outgoingTrackPath)) {
                    outgoingTrack.dispose();
                }
            } catch (Exception ignored) {}
            outgoingTrack = null;
            outgoingTrackPath = null;
        }
        if (currentTrack != null) {
            try {
                if (currentTrack.isPlaying()) {
                    currentTrack.stop();
                }
            } catch (Exception ignored) {}
            if (currentTrackPath != null && !assetManager.isLoaded(currentTrackPath)) {
                try {
                    currentTrack.dispose();
                } catch (Exception ignored) {}
            }
        }
        currentTrack = null;
        currentTrackPath = null;
        pausedExplorationTrack = null;
        pausedExplorationPath = null;
        isCrossfading = false;
        currentState = MusicState.PAUSED;
        stopAmbientLoop();
    }

    public void dispose() {
        stop();
        for (Sound s : stingerCache.values()) {
            try {
                s.dispose();
            } catch (Exception ignored) {}
        }
        stingerCache.clear();
        try {
            assetManager.dispose();
        } catch (Exception ignored) {}
    }
}
