package com.bpm.minotaur.paperdoll.calibration;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;

import java.util.Map;
import java.util.TreeMap;

/**
 * Reads and writes assets/data/paperdoll_calibration.json.
 *
 * Layers are keyed "slot/layerName" (e.g. "chest/breastplate"). Anything absent
 * resolves to identity, so an empty file is a valid file and a half-finished
 * calibration pass never breaks the doll.
 *
 * Serialisation is hand-rolled rather than delegated to libGDX's {@code Json}, which
 * emits {@code {"class":"java.lang.Float","value":797}} wrappers — see the legacy
 * assets/data/skeleton.json for what that looks like. The file is meant to be
 * hand-editable and to produce readable diffs.
 */
public class CalibrationStore {

    private final Map<String, LayerCalibration> layers = new TreeMap<String, LayerCalibration>();

    public static String layerId(String slotFolder, String layerName) {
        return slotFolder + "/" + layerName;
    }

    /** Never null: an uncalibrated layer draws exactly as baked. */
    public LayerCalibration get(String layerId) {
        LayerCalibration cal = layers.get(layerId);
        return cal != null ? cal : new LayerCalibration();
    }

    public boolean has(String layerId) {
        return layers.containsKey(layerId);
    }

    public void put(String layerId, LayerCalibration cal) {
        layers.put(layerId, cal);
    }

    public void remove(String layerId) {
        layers.remove(layerId);
    }

    public Map<String, LayerCalibration> all() {
        return layers;
    }

    public int size() {
        return layers.size();
    }

    public void clear() {
        layers.clear();
    }

    public void parse(String json) {
        layers.clear();
        if (json == null || json.trim().isEmpty()) {
            return;
        }
        JsonValue root = new JsonReader().parse(json);
        if (root == null) {
            return;
        }
        JsonValue layersNode = root.get("layers");
        if (layersNode == null) {
            return;
        }
        for (JsonValue entry = layersNode.child; entry != null; entry = entry.next) {
            LayerCalibration cal = new LayerCalibration();
            // Defaults are supplied per-field so a hand-edited entry can specify only
            // what it cares about without zeroing the scale and vanishing the layer.
            cal.offsetX = entry.getFloat("offsetX", 0f);
            cal.offsetY = entry.getFloat("offsetY", 0f);
            cal.scaleX = entry.getFloat("scaleX", 1f);
            cal.scaleY = entry.getFloat("scaleY", 1f);
            cal.rotation = entry.getFloat("rotation", 0f);
            cal.hidesHair = entry.getBoolean("hidesHair", false);
            cal.hidesBeard = entry.getBoolean("hidesBeard", false);
            cal.needsArtRedo = entry.getBoolean("needsArtRedo", false);
            cal.sourceHash = entry.getString("sourceHash", null);
            layers.put(entry.name, cal);
        }
    }

    public String serialize() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"_comment\": \"Paperdoll layer calibration. Offsets are master-canvas pixels (1024x1536); +y is DOWN. Safe to hand-edit; the in-game editor reloads on F5.\",\n");
        sb.append("  \"layers\": {\n");
        int i = 0;
        for (Map.Entry<String, LayerCalibration> e : layers.entrySet()) {
            LayerCalibration c = e.getValue();
            sb.append("    \"").append(e.getKey()).append("\": {");
            sb.append("\"offsetX\": ").append(num(c.offsetX));
            sb.append(", \"offsetY\": ").append(num(c.offsetY));
            sb.append(", \"scaleX\": ").append(num(c.scaleX));
            sb.append(", \"scaleY\": ").append(num(c.scaleY));
            sb.append(", \"rotation\": ").append(num(c.rotation));
            if (c.hidesHair) sb.append(", \"hidesHair\": true");
            if (c.hidesBeard) sb.append(", \"hidesBeard\": true");
            if (c.needsArtRedo) sb.append(", \"needsArtRedo\": true");
            if (c.sourceHash != null) sb.append(", \"sourceHash\": \"").append(c.sourceHash).append("\"");
            sb.append("}");
            if (++i < layers.size()) sb.append(",");
            sb.append("\n");
        }
        sb.append("  }\n");
        sb.append("}\n");
        return sb.toString();
    }

    /** Trims trailing zeroes so diffs stay readable and numbers stay hand-editable. */
    private static String num(float f) {
        if (f == Math.rint(f) && !Float.isInfinite(f)) {
            return String.valueOf((long) f);
        }
        return String.valueOf(Math.round(f * 10000f) / 10000f);
    }

    public void load(FileHandle handle) {
        if (handle != null && handle.exists()) {
            parse(handle.readString("UTF-8"));
        } else {
            layers.clear();
        }
    }

    public void save(FileHandle handle) {
        handle.writeString(serialize(), false, "UTF-8");
    }
}
