package com.bpm.minotaur.paperdoll.calibration;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;

import java.util.HashMap;
import java.util.Map;

/**
 * Explicit item-key to paperdoll-layer mapping, read from
 * assets/data/paperdoll_layers.json.
 *
 * Replaces the three-way filename guessing this used to do at runtime -- texture
 * basename, then enum name, then slugified display name. That guessing is why the
 * baker had to emit a duplicate PNG under every name a lookup might try; making the
 * mapping explicit lets several items share one layer file, and one calibration,
 * instead of each owning a copy that can silently drift.
 *
 * Items with no visual layer (ammunition, projectiles) resolve to null, which is a
 * normal state rather than an error.
 */
public class PaperdollLayerMap {

    /** Where one item's artwork lives: a slot folder and a layer name within it. */
    public static final class LayerRef {
        public final String slot;
        public final String layer;

        public LayerRef(String slot, String layer) {
            this.slot = slot;
            this.layer = layer;
        }

        /** Key into {@link CalibrationStore}. */
        public String layerId() {
            return CalibrationStore.layerId(slot, layer);
        }

        /** Path of the baked PNG, relative to the assets root. */
        public String texturePath() {
            return "images/paperdoll/" + slot + "/" + layer + ".png";
        }
    }

    private final Map<String, LayerRef> items = new HashMap<String, LayerRef>();

    public LayerRef resolve(String itemKey) {
        if (itemKey == null) {
            return null;
        }
        return items.get(itemKey);
    }

    public boolean has(String itemKey) {
        return itemKey != null && items.containsKey(itemKey);
    }

    public int size() {
        return items.size();
    }

    public void parse(String json) {
        items.clear();
        if (json == null || json.trim().isEmpty()) {
            return;
        }
        JsonValue root = new JsonReader().parse(json);
        if (root == null) {
            return;
        }
        JsonValue itemsNode = root.get("items");
        if (itemsNode == null) {
            return;
        }
        for (JsonValue entry = itemsNode.child; entry != null; entry = entry.next) {
            String slot = entry.getString("slot", null);
            String layer = entry.getString("layer", null);
            // A half-specified entry would resolve to a path like "images/paperdoll/null/x.png"
            // and fail as a missing texture much further away from the cause. Skip it here.
            if (slot == null || layer == null) {
                continue;
            }
            items.put(entry.name, new LayerRef(slot, layer));
        }
    }

    public void load(FileHandle handle) {
        if (handle != null && handle.exists()) {
            parse(handle.readString("UTF-8"));
        } else {
            items.clear();
        }
    }
}
