package com.bpm.minotaur.rendering.gate;

import com.badlogic.gdx.utils.JsonValue;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * How the Sector Passage Gate texture is laid onto its three models, adjustable by hand.
 *
 * <p>The gate comes from Blender, whose V axis points up, while libGDX has V pointing down, so the
 * texture sits upside down unless V is flipped. On top of that flip each part carries a rotation,
 * a scale and an offset, applied about the middle of the texture, so a mapping that is still off
 * can be corrected in the game ({@link GateUvTunerPanel}) instead of by editing the model.
 */
public final class GateUv {

    public static final String PATH = "data/gate_uv.json";
    public static final String[] PARTS = { "frame", "doorLeft", "doorRight" };

    public static final class Part {
        public boolean flipV = true;
        public float offsetU;
        public float offsetV;
        public float scaleU = 1f;
        public float scaleV = 1f;
        public float rotation;

        public void reset() {
            flipV = true;
            offsetU = 0f;
            offsetV = 0f;
            scaleU = 1f;
            scaleV = 1f;
            rotation = 0f;
        }

        public boolean isDefault() {
            return flipV && offsetU == 0f && offsetV == 0f && scaleU == 1f && scaleV == 1f && rotation == 0f;
        }

        /** Maps a model UV to the UV to sample: flip, then rotate and scale about the centre, then offset. */
        public void apply(float u, float v, float[] out) {
            float y = flipV ? 1f - v : v;
            float x = u - 0.5f;
            y -= 0.5f;
            double r = Math.toRadians(rotation);
            float cos = (float) Math.cos(r);
            float sin = (float) Math.sin(r);
            float rx = x * cos - y * sin;
            float ry = x * sin + y * cos;
            out[0] = rx * scaleU + 0.5f + offsetU;
            out[1] = ry * scaleV + 0.5f + offsetV;
        }
    }

    private final Map<String, Part> parts = new LinkedHashMap<>();

    public GateUv() {
        for (String name : PARTS) {
            parts.put(name, new Part());
        }
    }

    public Part part(String name) {
        return parts.get(name);
    }

    public static GateUv parse(JsonValue root) {
        GateUv uv = new GateUv();
        for (String name : PARTS) {
            JsonValue p = root == null ? null : root.get(name);
            if (p == null) {
                continue;
            }
            Part part = uv.parts.get(name);
            part.flipV = p.getBoolean("flipV", true);
            part.offsetU = p.getFloat("offsetU", 0f);
            part.offsetV = p.getFloat("offsetV", 0f);
            part.scaleU = p.getFloat("scaleU", 1f);
            part.scaleV = p.getFloat("scaleV", 1f);
            part.rotation = p.getFloat("rotation", 0f);
        }
        return uv;
    }

    public String toJson() {
        StringBuilder sb = new StringBuilder("{\n");
        int i = 0;
        for (Map.Entry<String, Part> e : parts.entrySet()) {
            Part p = e.getValue();
            sb.append(String.format(Locale.ROOT,
                    "  \"%s\": { \"flipV\": %b, \"offsetU\": %.4f, \"offsetV\": %.4f, \"scaleU\": %.4f, \"scaleV\": %.4f, \"rotation\": %.2f }%s\n",
                    e.getKey(), p.flipV, p.offsetU, p.offsetV, p.scaleU, p.scaleV, p.rotation,
                    ++i < parts.size() ? "," : ""));
        }
        return sb.append("}\n").toString();
    }

    /**
     * Rewrites the UVs in an interleaved vertex array from their pristine values.
     *
     * @param pristine every vertex as loaded from the model, never modified
     * @param target   the array to write the transformed UVs into (same layout)
     * @param stride   floats per vertex
     * @param uvOffset index of the U float within a vertex
     */
    public static void transform(Part part, float[] pristine, float[] target, int stride, int uvOffset) {
        float[] out = new float[2];
        for (int base = 0; base + stride <= pristine.length; base += stride) {
            part.apply(pristine[base + uvOffset], pristine[base + uvOffset + 1], out);
            target[base + uvOffset] = out[0];
            target[base + uvOffset + 1] = out[1];
        }
    }
}
