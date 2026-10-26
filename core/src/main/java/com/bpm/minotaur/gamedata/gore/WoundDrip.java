package com.bpm.minotaur.gamedata.gore;

import com.badlogic.gdx.math.MathUtils;

import java.util.Random;

/**
 * Blood trickling down a monster's sprite from a wound (#220), the way it runs down a wall
 * (see the drip in {@link WallDecal}). It grows from the wound toward a target, quickest while
 * fresh, then thickens, slows and stops. Lengths are fractions of the sprite's height.
 *
 * <p>The compositor bakes the trickle into the monster's wound texture, so {@link #update}
 * reports only the frames on which it has grown by a visible {@link #STEP}: a fight with
 * several bleeding monsters re-composites a handful of times, not every frame.
 */
public final class WoundDrip {

    /** Wounds shorter than this never drip: a nick has no blood to run (the wall's DRIP_MIN_RADIUS). */
    public static final float MIN_WOUND_LENGTH = 0.11f;
    /** How far a trickle may run, as a fraction of the sprite's height. */
    static final float MIN_TARGET = 0.12f;
    static final float MAX_TARGET = 0.30f;
    /** Fraction of the sprite per second while fresh. */
    static final float SPEED = 0.14f;
    /** The growth that is worth a re-composite: about four pixels of a 256-pixel sprite. */
    public static final float STEP = 1f / 64f;

    private final float target;
    private float length;
    private int shownSteps;

    private WoundDrip(float target) {
        this.target = target;
    }

    /**
     * The trickle a fresh wound starts, or null: a bloodless body ({@link GoreProfile#hasBlood})
     * and a small wound never drip. It never runs past the bottom of the sprite.
     */
    public static WoundDrip start(WoundDecal wound, GoreProfile profile, Random random) {
        if (wound == null || wound.length < MIN_WOUND_LENGTH) return null;
        if (profile != null && !profile.hasBlood) return null;
        float wanted = MIN_TARGET + random.nextFloat() * (MAX_TARGET - MIN_TARGET);
        float target = Math.min(wanted, wound.v);
        return target >= STEP ? new WoundDrip(target) : null;
    }

    /**
     * Grows the trickle by {@code delta} seconds.
     *
     * @return true when it has grown by a visible step (or just reached its end), so the
     *         composited sprite should be redrawn
     */
    public boolean update(float delta) {
        if (!isRunning()) return false;
        // Runs quickest while fresh, then thickens and slows, as on the wall.
        float slow = 1f - 0.6f * MathUtils.clamp(length / target, 0f, 1f);
        length = Math.min(target, length + SPEED * slow * delta);
        int steps = length >= target ? Integer.MAX_VALUE : (int) (length / STEP);
        if (steps == shownSteps) return false;
        shownSteps = steps;
        return true;
    }

    public boolean isRunning() {
        return length < target;
    }

    /** How far it has run below the wound, as a fraction of the sprite's height. */
    public float length() {
        return length;
    }

    public float target() {
        return target;
    }
}
