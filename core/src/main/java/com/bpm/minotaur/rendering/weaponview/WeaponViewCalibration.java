package com.bpm.minotaur.rendering.weaponview;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.paperdoll.calibration.AssetDataFiles;
import com.bpm.minotaur.paperdoll.calibration.CalibrationStore;
import com.bpm.minotaur.paperdoll.calibration.LayerCalibration;

/**
 * Per-weapon tuning for the first-person view, in assets/data/weapon_view_calibration.json.
 *
 * Reuses the paperdoll's CalibrationStore, whose fallback rule is exactly the
 * archetype-default-plus-override scheme this needs. Entries are keyed
 * "ARCHETYPE/WEAPON_KEY"; a weapon with no entry falls back to its archetype's default,
 * and an archetype with no default falls back to identity -- which draws exactly as the
 * hardcoded pose always did. So an empty or absent file is valid and changes nothing.
 *
 * Every value is a DELTA on the pose the overlay already computes, never an absolute
 * position. That is what lets the same tuning move the idle stance and the attack arc
 * together: the arcs in CombatMotionProfile are authored against the overlay's base
 * anchors, so the anchors stay put and the tuning rides on top of both.
 *
 * Units: offsets are fractions of the viewport with +y UP (screen space); scale is a
 * multiplier on the hand's base sprite height; rotation is degrees. Note this differs
 * from paperdoll_calibration.json, which is canvas pixels with +y down.
 */
public class WeaponViewCalibration {

    public static final String PATH = "data/weapon_view_calibration.json";

    /** Archetype key for anything held in the off hand -- shields and off-hand weapons. */
    public static final String OFF_HAND = "OFF_HAND";

    private static final String HEADER =
            "First-person weapon tuning. Every value is a DELTA on the overlay's base pose: "
            + "offsets are viewport fractions with +y UP, scale multiplies the hand's base "
            + "height, rotation is degrees. Keys are ARCHETYPE/WEAPON_KEY; a weapon with no "
            + "entry uses its archetype default, then no tuning at all. Tuned live with F11.";

    private final CalibrationStore store = new CalibrationStore();

    public WeaponViewCalibration() {
        store.setHeaderComment(HEADER);
    }

    public static String key(String archetype, String itemKey) {
        return CalibrationStore.layerId(archetype, itemKey);
    }

    /**
     * The weapon half of the key. The overlay and the tuner must agree on it exactly, or
     * a tuned weapon saves under one key and is drawn from another.
     */
    public static String itemKey(Item item) {
        return item == null ? null : item.getTypeName();
    }

    /** Never null: the weapon's own tuning, else its archetype's, else none. */
    public LayerCalibration get(String archetype, String itemKey) {
        if (archetype == null) {
            return store.get(null);
        }
        if (itemKey == null) {
            LayerCalibration def = store.slotDefault(archetype);
            return def != null ? def : store.get(null);
        }
        return store.get(key(archetype, itemKey));
    }

    public CalibrationStore store() {
        return store;
    }

    public void load() {
        store.load(AssetDataFiles.readable(PATH));
    }

    public boolean save() {
        FileHandle handle = AssetDataFiles.writable(PATH);
        if (handle == null) {
            Gdx.app.error("WeaponViewCalibration", "No writable path for " + PATH);
            return false;
        }
        store.save(handle);
        return true;
    }
}
