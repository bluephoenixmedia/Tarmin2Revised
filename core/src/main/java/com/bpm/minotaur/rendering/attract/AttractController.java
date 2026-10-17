package com.bpm.minotaur.rendering.attract;

import com.badlogic.gdx.math.MathUtils;

/**
 * Manages the state machine, idle timeout, UI cross-fading,
 * and expedition dive sequence for the Main Menu Attract Mode.
 */
public class AttractController {

    public static final float IDLE_TIMEOUT = 15.0f; // 15 seconds of inactivity triggers attract mode
    public static final float FADE_OUT_SPEED = 0.85f; // Fades out over ~1.2 seconds
    public static final float FADE_IN_SPEED = 4.0f;  // Restores quickly on input (~0.25s)
    public static final float DIVE_DURATION = 0.8f;  // 0.8s acceleration dive towards castle gate

    private float idleTimer = 0f;
    private float uiAlpha = 1.0f;

    private boolean diving = false;
    private float diveTimer = 0f;
    private Runnable onDiveComplete = null;

    public void update(float delta) {
        if (diving) {
            diveTimer += delta;
            // Force UI to fade during dive
            uiAlpha = Math.max(0f, uiAlpha - delta * 2.5f);
            if (diveTimer >= DIVE_DURATION) {
                diving = false;
                if (onDiveComplete != null) {
                    Runnable callback = onDiveComplete;
                    onDiveComplete = null;
                    callback.run();
                }
            }
            return;
        }

        idleTimer += delta;
        if (idleTimer > IDLE_TIMEOUT) {
            uiAlpha = Math.max(0.0f, uiAlpha - delta * FADE_OUT_SPEED);
        } else {
            uiAlpha = Math.min(1.0f, uiAlpha + delta * FADE_IN_SPEED);
        }
    }

    /**
     * Resets idle counter and wakes the UI back to 100% opacity.
     */
    public void notifyInputReceived() {
        idleTimer = 0f;
    }

    /**
     * Initiates the dramatic camera acceleration toward the castle gate before switching screens.
     */
    public void startDive(Runnable onComplete) {
        this.diving = true;
        this.diveTimer = 0f;
        this.onDiveComplete = onComplete;
    }

    public boolean isAttractModeActive() {
        return idleTimer > IDLE_TIMEOUT;
    }

    public float getUiAlpha() {
        return uiAlpha;
    }

    public boolean isDiving() {
        return diving;
    }

    public float getDiveProgress01() {
        return MathUtils.clamp(diveTimer / DIVE_DURATION, 0f, 1f);
    }

    public float getIdleTimer() {
        return idleTimer;
    }
}
