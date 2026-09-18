package com.bpm.minotaur.paperdoll.calibration;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;

import java.util.Map;
import java.util.TreeMap;

/**
 * Reads and writes assets/data/paperdoll_calibration.json.
 *
 * Layers are keyed "slot/layerName" (e.g. "chest/breastplate"). A layer with no entry
 * falls back to its slot's default placement, so an empty file is a valid file and a
 * half-finished calibration pass never breaks the doll.
 *
 * Serialisation is hand-rolled rather than delegated to libGDX's {@code Json}, which
 * AGENT.md otherwise mandates for assets/data. {@code Json} emits
 * {@code {"class":"java.lang.Float","value":797}} wrappers — see the legacy
 * assets/data/skeleton.json for what that produces. This file is meant to be a
 * hand-edit escape hatch and to diff readably, which those wrappers defeat.
 *
 * {@code tools/bake_paperdoll_layers.py} writes the same format. The two writers must
 * agree; PaperdollCalibrationFormatTest pins that against the real generated file.
 */
public class CalibrationStore {

    private final Map<String, LayerCalibration> layers = new TreeMap<String, LayerCalibration>();
    private final Map<String, LayerCalibration> slotDefaults = new TreeMap<String, LayerCalibration>();

    /** Returned when a layer has neither an entry nor a slot default; never mutated. */
    private static final LayerCalibration IDENTITY = new LayerCalibration();

    public static String layerId(String slotFolder, String layerName) {
        return slotFolder + "/" + layerName;
    }

    /**
     * Never null. Falls back to the layer's slot default, then to identity.
     *
     * Identity became a poor fallback once layers are baked normalised: a normalised
     * layer is cropped, scaled into a per-slot box and centred, so drawing it at
     * identity puts it at the middle of the canvas — a new helmet would render at
     * chest height. The slot default (the median placement of that slot's calibrated
     * layers, written by the baker) puts new art roughly where its slot belongs.
     *
     * Returns shared instances on the miss paths: this runs once per layer per frame,
     * so allocating here would churn the heap for nothing.
     */
    public LayerCalibration get(String layerId) {
        // The base portrait has no layer id, and TreeMap.get(null) throws rather than
        // missing — so this guard is what keeps the draw loop from NPEing every frame.
        if (layerId == null) {
            return IDENTITY;
        }
        LayerCalibration cal = layers.get(layerId);
        if (cal != null) {
            return cal;
        }
        int sep = layerId.indexOf('/');
        if (sep > 0) {
            LayerCalibration fallback = slotDefaults.get(layerId.substring(0, sep));
            if (fallback != null) {
                return fallback;
            }
        }
        return IDENTITY;
    }

    /** Median placement for a slot, or null when the baker recorded none. */
    public LayerCalibration slotDefault(String slot) {
        return slotDefaults.get(slot);
    }

    public boolean has(String layerId) {
        return layers.containsKey(layerId);
    }

    public void put(String layerId, LayerCalibration cal) {
        layers.put(layerId, cal);
    }

    public Map<String, LayerCalibration> all() {
        return layers;
    }

    public int size() {
        return layers.size();
    }

    public void parse(String json) {
        layers.clear();
        slotDefaults.clear();
        if (json == null || json.trim().isEmpty()) {
            return;
        }
        JsonValue root = new JsonReader().parse(json);

        JsonValue defaultsNode = root.get("slotDefaults");
        if (defaultsNode != null) {
            for (JsonValue entry = defaultsNode.child; entry != null; entry = entry.next) {
                slotDefaults.put(entry.name, readCalibration(entry));
            }
        }

        JsonValue layersNode = root.get("layers");
        if (layersNode == null) {
            return;
        }
        for (JsonValue entry = layersNode.child; entry != null; entry = entry.next) {
            layers.put(entry.name, readCalibration(entry));
        }
    }

    private static LayerCalibration readCalibration(JsonValue entry) {
        LayerCalibration cal = new LayerCalibration();
        // Defaults are supplied per-field so a hand-edited entry can specify only what
        // it cares about without zeroing the scale and vanishing the layer.
        cal.offsetX = entry.getFloat("offsetX", 0f);
        cal.offsetY = entry.getFloat("offsetY", 0f);
        cal.scaleX = entry.getFloat("scaleX", 1f);
        cal.scaleY = entry.getFloat("scaleY", 1f);
        cal.rotation = entry.getFloat("rotation", 0f);
        cal.hidesHair = entry.getBoolean("hidesHair", false);
        cal.hidesBeard = entry.getBoolean("hidesBeard", false);
        cal.needsArtRedo = entry.getBoolean("needsArtRedo", false);
        cal.sourceHash = entry.getString("sourceHash", null);
        return cal;
    }

    public String serialize() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"_comment\": \"Paperdoll layer calibration. Offsets are master-canvas pixels (1024x1536); +y is DOWN. Safe to hand-edit; re-read by tools/bake_paperdoll_layers.py.\",\n");

        if (!slotDefaults.isEmpty()) {
            sb.append("  \"slotDefaults\": {\n");
            int d = 0;
            for (Map.Entry<String, LayerCalibration> e : slotDefaults.entrySet()) {
                sb.append("    \"").append(e.getKey()).append("\": ");
                appendCalibration(sb, e.getValue());
                if (++d < slotDefaults.size()) sb.append(",");
                sb.append("\n");
            }
            sb.append("  },\n");
        }

        sb.append("  \"layers\": {\n");
        int i = 0;
        for (Map.Entry<String, LayerCalibration> e : layers.entrySet()) {
            sb.append("    \"").append(e.getKey()).append("\": ");
            appendCalibration(sb, e.getValue());
            if (++i < layers.size()) sb.append(",");
            sb.append("\n");
        }
        sb.append("  }\n");
        sb.append("}\n");
        return sb.toString();
    }

    private static void appendCalibration(StringBuilder sb, LayerCalibration c) {
        sb.append("{");
        sb.append("\"offsetX\": ").append(num(c.offsetX));
        sb.append(", \"offsetY\": ").append(num(c.offsetY));
        sb.append(", \"scaleX\": ").append(num(c.scaleX));
        sb.append(", \"scaleY\": ").append(num(c.scaleY));
        sb.append(", \"rotation\": ").append(num(c.rotation));
        // Flags are omitted when false so the common case stays readable; absent parses
        // back as false. tools/bake_paperdoll_layers.py emits the same shape — a writer
        // that dropped these would erase needsArtRedo, which the triage list reads back.
        if (c.hidesHair) sb.append(", \"hidesHair\": true");
        if (c.hidesBeard) sb.append(", \"hidesBeard\": true");
        if (c.needsArtRedo) sb.append(", \"needsArtRedo\": true");
        if (c.sourceHash != null) sb.append(", \"sourceHash\": \"").append(c.sourceHash).append("\"");
        sb.append("}");
    }

    /**
     * Trims trailing zeroes so diffs stay readable and numbers stay hand-editable.
     *
     * Five decimals, matching tools/bake_paperdoll_layers.py. They have to agree: merely
     * stepping through a layer in the editor commits it, so a writer that rounded more
     * coarsely would rewrite every layer it visited and bury a handful of real edits in a
     * diff touching all 335.
     */
    private static String num(float f) {
        if (f == Math.rint(f) && !Float.isInfinite(f)) {
            return String.valueOf((long) f);
        }
        return String.valueOf(Math.round(f * 100000f) / 100000f);
    }

    public void load(FileHandle handle) {
        if (handle != null && handle.exists()) {
            parse(handle.readString("UTF-8"));
        } else {
            layers.clear();
            slotDefaults.clear();
        }
    }

    public void save(FileHandle handle) {
        handle.writeString(serialize(), false, "UTF-8");
    }
}
