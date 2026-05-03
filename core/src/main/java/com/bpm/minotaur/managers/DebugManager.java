package com.bpm.minotaur.managers;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.MathUtils;

/**
 * Singleton manager for handling global debug state and render mode transitions.
 */
public class DebugManager {
    private static final DebugManager INSTANCE = new DebugManager();
    private boolean isDebugOverlayVisible = false;

    public enum RenderMode {
        MODERN,
        RETRO
    }

    public enum TransitionState {
        NONE,
        FADING_OUT,  // current mode fading to black (before mode switch)
        FADING_IN    // new mode fading in from black (after mode switch)
    }

    // Each phase takes this long in seconds
    private static final float PHASE_DURATION = 0.55f;

    private RenderMode renderMode = RenderMode.RETRO;
    private RenderMode targetRenderMode = null;
    private TransitionState transitionState = TransitionState.NONE;
    private float transitionProgress = 0f; // 0→1 within the current phase
    private boolean isModernToRetro = false;

    private DebugManager() {
    }

    public static DebugManager getInstance() {
        return INSTANCE;
    }

    public boolean isDebugOverlayVisible() {
        return isDebugOverlayVisible;
    }

    public void toggleOverlay() {
        isDebugOverlayVisible = !isDebugOverlayVisible;
    }

    public void setDebugOverlayVisible(boolean visible) {
        this.isDebugOverlayVisible = visible;
    }

    public RenderMode getRenderMode() {
        return renderMode;
    }

    /**
     * Begins a visual transition to the opposite render mode. No-op if already
     * transitioning.
     */
    public void toggleRenderMode() {
        if (transitionState != TransitionState.NONE) return;

        targetRenderMode = (renderMode == RenderMode.MODERN) ? RenderMode.RETRO : RenderMode.MODERN;
        isModernToRetro = (renderMode == RenderMode.MODERN);
        transitionState = TransitionState.FADING_OUT;
        transitionProgress = 0f;

        Gdx.app.log("DebugManager [RETRO]",
                "Render mode transition started: " + renderMode + " → " + targetRenderMode);
    }

    /** Must be called once per frame from the game loop to advance the transition. */
    public void update(float delta) {
        if (transitionState == TransitionState.NONE) return;

        transitionProgress += delta / PHASE_DURATION;

        if (transitionState == TransitionState.FADING_OUT && transitionProgress >= 1f) {
            RenderMode previous = renderMode;
            renderMode = targetRenderMode;
            transitionState = TransitionState.FADING_IN;
            transitionProgress = 0f;
            Gdx.app.log("DebugManager [RETRO]",
                    "Render mode switched at transition midpoint: " + previous + " → " + renderMode);
            logRetroModeActivated();
        } else if (transitionState == TransitionState.FADING_IN && transitionProgress >= 1f) {
            transitionState = TransitionState.NONE;
            transitionProgress = 0f;
            targetRenderMode = null;
            Gdx.app.log("DebugManager [RETRO]",
                    "Render mode transition complete. Active mode: " + renderMode);
        }
    }

    private void logRetroModeActivated() {
        if (renderMode == RenderMode.RETRO) {
            Gdx.app.log("DebugManager [RETRO]",
                    "RETRO mode is now active. All entities will render via ASCII sprite data. " +
                    "Watch for [RETRO] log tags for per-entity render diagnostics.");
        } else {
            Gdx.app.log("DebugManager [RETRO]",
                    "MODERN mode is now active. All entities will render via texture atlases.");
        }
    }

    public boolean isTransitioning() {
        return transitionState != TransitionState.NONE;
    }

    public TransitionState getTransitionState() {
        return transitionState;
    }

    /** Whether the current or most recent transition was MODERN → RETRO. */
    public boolean isModernToRetroTransition() {
        return isModernToRetro;
    }

    /**
     * Alpha of the black transition overlay: 0 = fully transparent, 1 = fully
     * black. Uses ease-in for fade-out and ease-out for fade-in to feel snappy.
     */
    public float getTransitionOverlayAlpha() {
        float p = MathUtils.clamp(transitionProgress, 0f, 1f);
        if (transitionState == TransitionState.FADING_OUT) {
            // Ease-in: slow start, fast end → screen quickly goes dark
            return p * p;
        } else if (transitionState == TransitionState.FADING_IN) {
            // Ease-out: fast start, slow end → new mode punches in quickly
            return (1f - p) * (1f - p);
        }
        return 0f;
    }

    /**
     * Normalised 0→1→0 value across the full transition, useful for scanline
     * intensity calculations in the overlay renderer.
     */
    public float getTransitionPeakFactor() {
        if (transitionState == TransitionState.FADING_OUT) {
            return MathUtils.clamp(transitionProgress, 0f, 1f);
        } else if (transitionState == TransitionState.FADING_IN) {
            return MathUtils.clamp(1f - transitionProgress, 0f, 1f);
        }
        return 0f;
    }
}
