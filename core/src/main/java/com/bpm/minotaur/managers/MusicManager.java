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

    public void stop() {
        if (currentTrack != null) {
            currentTrack.stop();
            currentTrackPath = null;
        }
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
        assetManager.dispose();
    }
}
