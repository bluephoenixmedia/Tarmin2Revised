package com.bpm.minotaur.rendering;

import com.badlogic.gdx.math.MathUtils;

/**
 * The death cinematic: the player's body collapsing, and the blood that covers the handover.
 *
 * <p>Pure state and curves, driven by {@code update(delta)} and read by the renderer, the HUD and
 * the screen. Keeping it free of rendering makes the timeline testable, which matters because
 * these beats are a contract: fifty deaths is a designed quantity, so this is watched dozens of
 * times per playthrough.
 *
 * <p>The load-bearing choice is that impact doubles as the blood trigger. The tumble and the wipe
 * overlap, so the roll never has to look convincing at rest -- the world is gone before the body
 * finishes moving. That also means the final resting angle barely matters, which is why it is
 * allowed to be random.
 *
 * <p>Applies to the 3D engine only. The raycaster has no camera to tilt and assumes a level
 * horizon, so there it contributes audio and blood but no collapse.
 */
public final class DeathSequence {

    /** Normal eye height, matching the camera's standing value. */
    public static final float EYE_HEIGHT_STANDING = 0.50f;
    private static final float EYE_HEIGHT_KNEELING = 0.28f;
    private static final float EYE_HEIGHT_FLOOR = 0.10f;

    private static final float PITCH_KNEELING = 18f;
    private static final float PITCH_FLOOR = 55f;

    // Beat boundaries, in seconds from the killing blow.
    private static final float T_KNEES_END = 0.55f;
    private static final float T_IMPACT = 1.10f;
    private static final float T_HANDOVER = 1.90f;

    /** How long the blood takes to reach full opacity once it starts. */
    private static final float BLOOD_WIPE_DURATION = T_HANDOVER - T_IMPACT;

    /** A skipped wipe still plays, just fast. An instant cut to a static screen reads as a crash. */
    private static final float BLOOD_WIPE_SKIPPED = 0.15f;

    /**
     * Players die with their hands on the attack keys. Without a lockout the killing blow and the
     * skip arrive in the same frame and the sequence is never seen at all, not even once.
     */
    private static final float SKIP_LOCKOUT = 0.40f;

    /** The HUD clears early so the fall has the whole frame. */
    private static final float HUD_FADE_DURATION = 0.50f;

    private boolean active;
    private boolean violentDeath;
    private float elapsed;
    private float targetRollDegrees;

    private boolean skipped;
    private float bloodStartTime;
    private float bloodDuration;

    /**
     * @param violent          whether death came from a blow rather than attrition; drives the grunt.
     * @param rollDegrees      the angle the body comes to rest at. Mostly hidden by the blood.
     */
    public void begin(boolean violent, float rollDegrees) {
        this.active = true;
        this.violentDeath = violent;
        this.elapsed = 0f;
        this.targetRollDegrees = rollDegrees;
        this.skipped = false;
        this.bloodStartTime = T_IMPACT;
        this.bloodDuration = BLOOD_WIPE_DURATION;
    }

    public void reset() {
        active = false;
        elapsed = 0f;
        skipped = false;
    }

    public void update(float delta) {
        if (!active) return;
        elapsed += delta;
    }

    public boolean isActive() {
        return active;
    }

    public boolean isViolentDeath() {
        return violentDeath;
    }

    public float getElapsed() {
        return elapsed;
    }

    /** True once the grunt and the first tip of the collapse have landed. */
    public boolean canSkip() {
        return active && elapsed >= SKIP_LOCKOUT;
    }

    /** Fast-forwards to the wipe. Ignored during the lockout. */
    public void skip() {
        if (!canSkip() || skipped) return;
        skipped = true;
        // Carry the wipe forward from wherever it already is. Restarting it would snap a
        // part-covered screen back to a clear world, which is worse than not skipping at all.
        float already = getBloodAlpha();
        bloodDuration = BLOOD_WIPE_SKIPPED;
        bloodStartTime = elapsed - already * bloodDuration;
    }

    public boolean wasSkipped() {
        return skipped;
    }

    /** Has the body landed? The cue for the impact groan. */
    public boolean hasLanded() {
        return active && elapsed >= T_IMPACT;
    }

    /** Camera height above the floor, dropping from standing to prone. */
    public float getEyeHeight() {
        if (!active) return EYE_HEIGHT_STANDING;
        if (elapsed <= 0f) return EYE_HEIGHT_STANDING;
        if (elapsed < T_KNEES_END) {
            // Legs give way: accelerating, not linear.
            float t = elapsed / T_KNEES_END;
            return MathUtils.lerp(EYE_HEIGHT_STANDING, EYE_HEIGHT_KNEELING, t * t);
        }
        if (elapsed < T_IMPACT) {
            float t = (elapsed - T_KNEES_END) / (T_IMPACT - T_KNEES_END);
            return MathUtils.lerp(EYE_HEIGHT_KNEELING, EYE_HEIGHT_FLOOR, t * t);
        }
        return EYE_HEIGHT_FLOOR;
    }

    /**
     * Downward pitch in degrees. Always forward into the floor -- falling backward pitches the
     * view up at the ceiling, and you die looking at nothing.
     */
    public float getPitchDegrees() {
        if (!active || elapsed <= 0f) return 0f;
        if (elapsed < T_KNEES_END) {
            float t = elapsed / T_KNEES_END;
            return MathUtils.lerp(0f, PITCH_KNEELING, t * t);
        }
        if (elapsed < T_IMPACT) {
            float t = (elapsed - T_KNEES_END) / (T_IMPACT - T_KNEES_END);
            return MathUtils.lerp(PITCH_KNEELING, PITCH_FLOOR, t);
        }
        return PITCH_FLOOR;
    }

    /**
     * A short vertical jolt as the body lands, in world units. Decays to nothing over ~0.18s.
     */
    public float getImpactJolt() {
        if (!active || elapsed < T_IMPACT) return 0f;
        float since = elapsed - T_IMPACT;
        if (since > 0.18f) return 0f;
        float decay = 1f - (since / 0.18f);
        return MathUtils.sin(since * 90f) * 0.035f * decay * decay;
    }

    /** Roll in degrees, building from the fall and decelerating after the body lands. */
    public float getRollDegrees() {
        if (!active || elapsed <= T_KNEES_END) return 0f;
        float t = MathUtils.clamp((elapsed - T_KNEES_END) / (T_HANDOVER - T_KNEES_END), 0f, 1f);
        // Ease out: most of the rotation happens during the fall, trailing off on the floor.
        float eased = 1f - (1f - t) * (1f - t);
        return targetRollDegrees * eased;
    }

    /** 0 = world fully visible, 1 = fully obscured. */
    public float getBloodAlpha() {
        if (!active || elapsed < bloodStartTime) return 0f;
        if (bloodDuration <= 0f) return 1f;
        return MathUtils.clamp((elapsed - bloodStartTime) / bloodDuration, 0f, 1f);
    }

    /** 1 = HUD fully visible, 0 = gone. */
    public float getHudAlpha() {
        if (!active) return 1f;
        return MathUtils.clamp(1f - (elapsed / HUD_FADE_DURATION), 0f, 1f);
    }

    /**
     * True once the blood is fully opaque. The screen swap happens here so the handover -- and any
     * hitch loading the death screen -- is invisible.
     */
    public boolean shouldHandOver() {
        return active && getBloodAlpha() >= 1f;
    }
}
