package com.bpm.minotaur.managers;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages playback of dark cinematic texture and effect loops (Function Loops - ABYSS).
 *
 * Plays random loops at low, ambient volumes, smoothly fading them in and out
 * at randomized intervals to create an eerie, atmospheric backdrop during exploration.
 */
public class AbyssAmbienceManager {

    private enum State {
        IDLE,
        FADE_IN,
        PLAYING,
        FADE_OUT
    }

    private final List<String> loopPaths = new ArrayList<>();
    private State state = State.IDLE;
    private Music currentLoop = null;

    private float timer = 0f;
    private float currentVolume = 0f;
    private float targetVolume = 0.15f;
    private float fadeDuration = 5.0f;
    private float playDuration = 12.0f;

    // Cooldown between ambient loops: 18 to 50 seconds
    private static final float MIN_COOLDOWN = 18.0f;
    private static final float MAX_COOLDOWN = 50.0f;

    public AbyssAmbienceManager() {
        loadManifest();
        scheduleNextLoop();
    }

    private void loadManifest() {
        if (Gdx.files == null) return;
        try {
            FileHandle handle = Gdx.files.internal("data/abyss_loops.json");
            if (handle != null && handle.exists()) {
                JsonValue root = new JsonReader().parse(handle);
                JsonValue loops = root.get("loops");
                if (loops != null) {
                    for (JsonValue v = loops.child(); v != null; v = v.next()) {
                        loopPaths.add("sounds/ambient/abyss/" + v.asString());
                    }
                }
            }
        } catch (Exception e) {
            if (Gdx.app != null) {
                Gdx.app.error("AbyssAmbienceManager", "Could not load abyss loops manifest", e);
            }
        }
    }

    private void scheduleNextLoop() {
        timer = MathUtils.random(MIN_COOLDOWN, MAX_COOLDOWN);
        state = State.IDLE;
    }

    public void update(float delta) {
        if (loopPaths.isEmpty() || Gdx.audio == null) return;

        float sfxScale = 0.8f;
        if (SoundManager.getInstance() != null) {
            sfxScale = SoundManager.getInstance().getEffectiveSfxVolume();
        }

        switch (state) {
            case IDLE:
                timer -= delta;
                if (timer <= 0f) {
                    startRandomLoop();
                }
                break;

            case FADE_IN:
                timer += delta;
                float inProgress = Math.min(1.0f, timer / Math.max(0.1f, fadeDuration));
                currentVolume = MathUtils.lerp(0f, targetVolume, inProgress);
                if (currentLoop != null) {
                    currentLoop.setVolume(currentVolume * sfxScale);
                }
                if (inProgress >= 1.0f) {
                    state = State.PLAYING;
                    timer = playDuration;
                }
                break;

            case PLAYING:
                timer -= delta;
                if (currentLoop != null) {
                    currentLoop.setVolume(targetVolume * sfxScale);
                }
                if (timer <= 0f || (currentLoop != null && !currentLoop.isPlaying())) {
                    state = State.FADE_OUT;
                    timer = 0f;
                }
                break;

            case FADE_OUT:
                timer += delta;
                float outProgress = Math.min(1.0f, timer / Math.max(0.1f, fadeDuration));
                currentVolume = MathUtils.lerp(targetVolume, 0f, outProgress);
                if (currentLoop != null) {
                    currentLoop.setVolume(currentVolume * sfxScale);
                }
                if (outProgress >= 1.0f || (currentLoop != null && !currentLoop.isPlaying())) {
                    stopCurrent();
                    scheduleNextLoop();
                }
                break;
        }
    }

    private void startRandomLoop() {
        stopCurrent();
        if (loopPaths.isEmpty() || Gdx.audio == null) return;

        String path = loopPaths.get(MathUtils.random(loopPaths.size() - 1));
        try {
            FileHandle handle = Gdx.files.internal(path);
            if (!handle.exists()) {
                scheduleNextLoop();
                return;
            }
            currentLoop = Gdx.audio.newMusic(handle);
            currentLoop.setLooping(false);
            currentVolume = 0f;
            currentLoop.setVolume(0f);
            currentLoop.play();

            // Random volume between 0.08 and 0.20 ("at varying volumes but never too loud")
            targetVolume = MathUtils.random(0.08f, 0.20f);
            fadeDuration = MathUtils.random(4.0f, 6.5f);
            playDuration = MathUtils.random(10.0f, 22.0f);

            state = State.FADE_IN;
            timer = 0f;
        } catch (Exception e) {
            if (Gdx.app != null) {
                Gdx.app.error("AbyssAmbienceManager", "Error playing loop: " + path, e);
            }
            stopCurrent();
            scheduleNextLoop();
        }
    }

    private void stopCurrent() {
        if (currentLoop != null) {
            try {
                currentLoop.stop();
                currentLoop.dispose();
            } catch (Exception ignored) {
            }
            currentLoop = null;
        }
    }

    public void stop() {
        stopCurrent();
        state = State.IDLE;
        scheduleNextLoop();
    }

    public void dispose() {
        stopCurrent();
        loopPaths.clear();
    }
}
