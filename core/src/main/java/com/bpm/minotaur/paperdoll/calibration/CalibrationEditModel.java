package com.bpm.minotaur.paperdoll.calibration;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * The editing rules behind the paperdoll editor, with no LibGDX dependency.
 *
 * The acceptance condition for this pipeline was that calibrating a piece is easy and
 * happens once. That makes the unglamorous parts load-bearing: stepping between layers
 * without losing an edit, nudging by a predictable amount, and knowing at a glance
 * whether anything is unsaved. Keeping them out of the screen means they can be tested
 * without a GL context.
 */
public class CalibrationEditModel {

    private final CalibrationStore store;
    private final List<String> order = new ArrayList<String>();

    private int index = -1;
    /** The live copy being edited; written back to the store on {@link #commit()}. */
    private LayerCalibration working = new LayerCalibration();
    /** What the selected layer looked like when it was selected, for {@link #revert()}. */
    private LayerCalibration pristine = new LayerCalibration();
    private boolean dirty = false;

    /** Scale may approach zero but never reach it: a layer at zero cannot be grabbed back. */
    private static final float MIN_SCALE = 0.02f;

    public CalibrationEditModel(CalibrationStore store) {
        this.store = store;
        order.addAll(store.all().keySet());
    }

    /** Distinct slot folders, in the order layers are walked. */
    public List<String> slots() {
        TreeSet<String> slots = new TreeSet<String>();
        for (String id : order) {
            int sep = id.indexOf('/');
            if (sep > 0) {
                slots.add(id.substring(0, sep));
            }
        }
        return new ArrayList<String>(slots);
    }

    public List<String> layersInSlot(String slot) {
        List<String> out = new ArrayList<String>();
        String prefix = slot + "/";
        for (String id : order) {
            if (id.startsWith(prefix)) {
                out.add(id);
            }
        }
        return out;
    }

    public List<String> allLayers() {
        return new ArrayList<String>(order);
    }

    public String selectedLayerId() {
        return (index >= 0 && index < order.size()) ? order.get(index) : null;
    }

    /** Never null, so the screen can bind sliders before anything is selected. */
    public LayerCalibration current() {
        return working;
    }

    public void select(String layerId) {
        int at = order.indexOf(layerId);
        if (at < 0) {
            return;
        }
        // Stepping away from a layer keeps its edit: losing one on every step is the
        // difference between calibrating 106 pieces once and calibrating them twice.
        commit();
        index = at;
        working = store.get(layerId).copy();
        pristine = working.copy();
    }

    /**
     * Writes the working copy back to the store. Safe to call with nothing selected.
     *
     * Dirtiness is decided here by comparing against the last saved state, rather than
     * being set by each mutator. The screen binds checkboxes straight to
     * {@link #current()}, so a flag toggled that way would otherwise look set, never be
     * counted as unsaved work, and be lost on exit.
     */
    public void commit() {
        String id = selectedLayerId();
        if (id != null) {
            store.put(id, working.copy());
            if (working.differsFrom(pristine)) {
                dirty = true;
            }
        }
    }

    public void next() {
        if (index >= 0 && index + 1 < order.size()) {
            select(order.get(index + 1));
        }
    }

    public void previous() {
        if (index > 0) {
            select(order.get(index - 1));
        }
    }

    /** Stepping stops at the ends rather than wrapping, so finishing a pass is visible. */
    public boolean atFirst() {
        return index <= 0;
    }

    public boolean atLast() {
        return index < 0 || index >= order.size() - 1;
    }

    public void nudge(float dx, float dy) {
        if (selectedLayerId() == null) {
            return;
        }
        working.offsetX += dx;
        working.offsetY += dy;
        dirty = true;
        commit();
    }

    /**
     * Multiplicative, so a step feels the same at every size. An additive 0.05 is a 5%
     * change at scale 1.0 but a 50% change at 0.1.
     */
    public void scaleBy(float fx, float fy) {
        if (selectedLayerId() == null) {
            return;
        }
        working.scaleX = Math.max(MIN_SCALE, working.scaleX * fx);
        working.scaleY = Math.max(MIN_SCALE, working.scaleY * fy);
        dirty = true;
        commit();
    }

    public void rotateBy(float degrees) {
        if (selectedLayerId() == null) {
            return;
        }
        working.rotation += degrees;
        dirty = true;
        commit();
    }

    public void revert() {
        if (selectedLayerId() == null) {
            return;
        }
        working = pristine.copy();
        commit();
    }

    /** Falls back to the baker's median placement for the slot — the nonsense escape hatch. */
    public void resetToSlotDefault() {
        String id = selectedLayerId();
        if (id == null) {
            return;
        }
        int sep = id.indexOf('/');
        if (sep <= 0) {
            return;
        }
        LayerCalibration def = store.slotDefault(id.substring(0, sep));
        if (def == null) {
            return;
        }
        String hash = working.sourceHash;
        working = def.copy();
        working.sourceHash = hash;
        dirty = true;
        commit();
    }

    public boolean isDirty() {
        return dirty;
    }

    /** Called after the store has been written to disk. */
    public void markSaved() {
        dirty = false;
        pristine = working.copy();
    }

    /** Flags edited through {@link #current()} still count as unsaved work. */
    public void markDirty() {
        dirty = true;
    }

    public CalibrationStore store() {
        return store;
    }

    public int selectedIndex() {
        return index;
    }

    public int layerCount() {
        return order.size();
    }
}
