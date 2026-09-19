package com.bpm.minotaur.gamedata.gore;

import java.util.ArrayList;

/**
 * All the blood on one paperdoll layer: discrete stains, plus a soak level for the
 * saturation that builds up underneath them over a long expedition.
 *
 * Stains are what a single fight leaves; soak is what fifty fights leave. Every stain
 * adds soak in proportion to its area, so a piece that keeps getting hit darkens into
 * patchy dried gore even once the stain list is full -- which is what carries the look
 * from "bloodied" to something out of a horror film.
 */
public class BloodCoat {

    /** Enough for a drenched piece; beyond this the oldest stains sink into the soak. */
    public static final int MAX_STAINS = 300;
    /** Soak gained per unit of stain area, as a fraction of the 1024x1536 canvas. */
    static final float SOAK_PER_AREA = 2.0f;
    /** Soak gained each time a stain is displaced for being the oldest. */
    static final float OVERFLOW_SOAK = 0.001f;
    /** A single hit should never visibly jump the soak. */
    static final float MAX_SOAK_PER_STAIN = 0.02f;
    private static final float CANVAS_AREA = 1024f * 1536f;

    public ArrayList<BloodStain> stains = new ArrayList<BloodStain>();
    /** 0 = clean underneath, 1 = soaked through. */
    public float soak;

    /** Bumped on every change, so a renderer can tell a cached overlay is stale. */
    private transient int version;

    public void add(BloodStain stain) {
        if (stains.size() >= MAX_STAINS) {
            stains.remove(0);
            soak = Math.min(1f, soak + OVERFLOW_SOAK);
        }
        stains.add(stain);
        float area = (float) (Math.PI * stain.radius * stain.radius) / CANVAS_AREA;
        soak = Math.min(1f, soak + Math.min(MAX_SOAK_PER_STAIN, area * SOAK_PER_AREA));
        version++;
    }

    public void addSoak(float amount) {
        if (amount <= 0f) {
            return;
        }
        soak = Math.min(1f, soak + amount);
        version++;
    }

    /** Dries every stain by this many game turns. */
    public void age(int turns) {
        if (turns <= 0 || stains.isEmpty()) {
            return;
        }
        for (BloodStain s : stains) {
            s.age += turns;
        }
        version++;
    }

    public void clear() {
        if (isEmpty()) {
            return;
        }
        stains.clear();
        soak = 0f;
        version++;
    }

    public boolean isEmpty() {
        return stains.isEmpty() && soak <= 0f;
    }

    public int version() {
        return version;
    }
}
