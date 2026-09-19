// Path: core/src/main/java/com/bpm/minotaur/managers/MusicManager.java
package com.bpm.minotaur.managers;

import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.audio.Music;

public class MusicManager {
    private static MusicManager instance;
    private final AssetManager assetManager;
    private Music currentTrack;
    private String currentTrackPath;
    private float volume = 0.5f; // Default volume

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
        assetManager.load(path, Music.class);
    }

    public void finishLoading() {
        assetManager.finishLoading();
    }

    public void playTrack(String path) {
        // Don't restart the music if it's already playing
        if (path.equals(currentTrackPath)) {
            return;
        }

        // If another track is playing, stop it first
        if (currentTrack != null && currentTrack.isPlaying()) {
            currentTrack.stop();
        }

        currentTrack = assetManager.get(path, Music.class);
        currentTrack.setLooping(true);
        currentTrack.setVolume(volume);
        currentTrack.play();
        currentTrackPath = path;
    }

    private Music ambientTrack;
    private String ambientTrackPath;
    private float ambientVolume = 0.5f;

    public void playAmbientLoop(String path, float targetVol) {
        if (path == null) {
            stopAmbientLoop();
            return;
        }
        if (path.equals(ambientTrackPath)) {
            if (ambientTrack != null) ambientTrack.setVolume(targetVol);
            return;
        }
        if (ambientTrack != null && ambientTrack.isPlaying()) {
            ambientTrack.stop();
        }
        try {
            if (!assetManager.isLoaded(path)) {
                assetManager.load(path, Music.class);
                assetManager.finishLoading();
            }
            ambientTrack = assetManager.get(path, Music.class);
            ambientTrack.setLooping(true);
            ambientTrack.setVolume(targetVol);
            ambientTrack.play();
            ambientTrackPath = path;
        } catch (Exception e) {
            com.badlogic.gdx.Gdx.app.error("MusicManager", "Failed to play ambient loop: " + path, e);
        }
    }

    public void stopAmbientLoop() {
        if (ambientTrack != null) {
            ambientTrack.stop();
            ambientTrackPath = null;
        }
    }

    public void duckMusic(float factor) {
        if (currentTrack != null) {
            currentTrack.setVolume(volume * Math.max(0.0f, Math.min(1.0f, factor)));
        }
    }

    public void restoreMusicVolume() {
        if (currentTrack != null) {
            currentTrack.setVolume(volume);
        }
    }

    public void stop() {
        if (currentTrack != null) {
            currentTrack.stop();
            currentTrackPath = null;
        }
        stopAmbientLoop();
    }

    public float getVolume() {
        return volume;
    }

    public void setVolume(float volume) {
        this.volume = volume;
        if (currentTrack != null) {
            currentTrack.setVolume(volume);
        }
    }

    public void dispose() {
        if (currentTrack != null) {
            currentTrack.stop();
        }
        if (ambientTrack != null) {
            ambientTrack.stop();
        }
        assetManager.dispose();
    }
}
