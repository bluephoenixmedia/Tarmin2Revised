package com.bpm.minotaur.rendering.weaponview;

import com.bpm.minotaur.paperdoll.calibration.CalibrationStore;
import com.bpm.minotaur.paperdoll.calibration.LayerCalibration;

/**
 * The editing rules behind the F11 weapon tuner, with no LibGDX dependency.
 *
 * Edits land in the store immediately, and the overlay reads the store every frame, so
 * every key press is visible on the very next frame -- in the idle stance and, with a
 * preview swing, across the attack arc. Saving only decides whether they outlive the
 * session.
 *
 * Two scopes, because weapons come in families: tune the archetype once so every sword
 * sits roughly right, then override the handful whose art is drawn differently.
 */
public class WeaponViewTuner {

    public enum Hand { MAIN, OFF }

    public enum Scope { WEAPON, ARCHETYPE }

    /** Scale may approach zero but never reach it: a weapon at zero cannot be grabbed back. */
    private static final float MIN_SCALE = 0.05f;

    private final CalibrationStore store;

    private String mainArchetype;
    private String mainItemKey;
    private String offItemKey;

    private Hand hand = Hand.MAIN;
    private Scope scope = Scope.WEAPON;
    private boolean dirty = false;

    public WeaponViewTuner(CalibrationStore store) {
        this.store = store;
    }

    /** Called every frame with what is actually equipped, so the tuner follows swaps. */
    public void setEquipment(String mainArchetype, String mainItemKey, String offItemKey) {
        this.mainArchetype = mainArchetype;
        this.mainItemKey = mainItemKey;
        this.offItemKey = offItemKey;
    }

    public Hand hand() {
        return hand;
    }

    public Scope scope() {
        return scope;
    }

    public void toggleHand() {
        hand = (hand == Hand.MAIN) ? Hand.OFF : Hand.MAIN;
    }

    public void toggleScope() {
        scope = (scope == Scope.WEAPON) ? Scope.ARCHETYPE : Scope.WEAPON;
    }

    public String archetype() {
        return hand == Hand.MAIN ? mainArchetype : WeaponViewCalibration.OFF_HAND;
    }

    public String itemKey() {
        return hand == Hand.MAIN ? mainItemKey : offItemKey;
    }

    /** False when the selected hand is empty: there is nothing on screen to tune against. */
    public boolean hasTarget() {
        return archetype() != null && itemKey() != null;
    }

    /** The key edits are written under: the weapon's own, or its archetype's default. */
    public String targetKey() {
        if (!hasTarget()) {
            return null;
        }
        return scope == Scope.WEAPON
                ? WeaponViewCalibration.key(archetype(), itemKey())
                : archetype();
    }

    /** True when the target has an entry of its own rather than inheriting one. */
    public boolean targetHasEntry() {
        if (!hasTarget()) {
            return false;
        }
        return scope == Scope.WEAPON
                ? store.has(targetKey())
                : store.slotDefault(archetype()) != null;
    }

    /**
     * What the target currently draws with, including anything inherited. Never null.
     *
     * In archetype scope this is deliberately the archetype default even when the
     * weapon on screen has its own override: showing the override's numbers would make
     * the display disagree with what the keys are editing.
     */
    public LayerCalibration current() {
        if (!hasTarget()) {
            return store.get(null);
        }
        if (scope == Scope.WEAPON) {
            return store.get(targetKey());
        }
        LayerCalibration def = store.slotDefault(archetype());
        return def != null ? def : store.get(null);
    }

    /**
     * The target's own entry, created on first edit.
     *
     * A new weapon entry starts from what the weapon was already drawing with -- its
     * archetype default -- so the first nudge moves it one step from where it visibly
     * is, not from the archetype's anchor. Copied, because the store hands out its
     * shared default instance on a miss.
     */
    private LayerCalibration editable() {
        if (scope == Scope.WEAPON) {
            String key = targetKey();
            if (!store.has(key)) {
                store.put(key, store.get(key).copy());
            }
            return store.all().get(key);
        }
        LayerCalibration def = store.slotDefault(archetype());
        if (def == null) {
            def = new LayerCalibration();
            store.putSlotDefault(archetype(), def);
        }
        return def;
    }

    /** Offsets in viewport fractions, +y up. */
    public void nudge(float dx, float dy) {
        if (!hasTarget()) {
            return;
        }
        LayerCalibration cal = editable();
        cal.offsetX += dx;
        cal.offsetY += dy;
        dirty = true;
    }

    /**
     * Uniform and multiplicative. Uniform because weapon art is drawn at its own aspect
     * and stretching it only ever looks wrong; multiplicative so a step feels the same
     * at every size.
     */
    public void scaleBy(float factor) {
        if (!hasTarget()) {
            return;
        }
        LayerCalibration cal = editable();
        cal.scaleX = Math.max(MIN_SCALE, cal.scaleX * factor);
        cal.scaleY = Math.max(MIN_SCALE, cal.scaleY * factor);
        dirty = true;
    }

    public void rotateBy(float degrees) {
        if (!hasTarget()) {
            return;
        }
        editable().rotation += degrees;
        dirty = true;
    }

    public void toggleFlip() {
        if (!hasTarget()) {
            return;
        }
        LayerCalibration cal = editable();
        cal.flipX = !cal.flipX;
        dirty = true;
    }

    /**
     * Drops the target's own entry. A weapon falls back to its archetype default; an
     * archetype falls back to no tuning at all, which is the hardcoded pose.
     */
    public void clear() {
        if (!targetHasEntry()) {
            return;
        }
        if (scope == Scope.WEAPON) {
            store.remove(targetKey());
        } else {
            store.removeSlotDefault(archetype());
        }
        dirty = true;
    }

    public boolean isDirty() {
        return dirty;
    }

    /** Called after the store has been written to disk, or re-read from it. */
    public void markSaved() {
        dirty = false;
    }
}
