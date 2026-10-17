package com.bpm.minotaur.rendering.gate;

import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g3d.Model;

import java.util.ArrayList;
import java.util.List;

/**
 * Re-maps the UVs of the three gate models from a {@link GateUv}. The vertices as loaded are kept
 * aside, so every re-map starts from the original and a tuned value can always be walked back.
 */
public final class GateUvMeshes {

    private static final class Target {
        final String part;
        final Mesh mesh;
        final float[] pristine;
        final float[] working;
        final int stride;
        final int uvOffset;

        Target(String part, Mesh mesh) {
            this.part = part;
            this.mesh = mesh;
            this.stride = mesh.getVertexSize() / 4;
            VertexAttribute uv = mesh.getVertexAttribute(VertexAttributes.Usage.TextureCoordinates);
            this.uvOffset = uv == null ? -1 : uv.offset / 4;
            this.pristine = new float[mesh.getNumVertices() * stride];
            mesh.getVertices(pristine);
            this.working = pristine.clone();
        }
    }

    private final List<Target> targets = new ArrayList<>();

    /** Any model may be null (a missing asset); its part is simply skipped. */
    public GateUvMeshes(Model frame, Model doorLeft, Model doorRight) {
        add("frame", frame);
        add("doorLeft", doorLeft);
        add("doorRight", doorRight);
    }

    private void add(String part, Model model) {
        if (model == null) {
            return;
        }
        for (Mesh mesh : model.meshes) {
            Target t = new Target(part, mesh);
            if (t.uvOffset >= 0) {
                targets.add(t);
            }
        }
    }

    public void apply(GateUv uv) {
        for (Target t : targets) {
            GateUv.transform(uv.part(t.part), t.pristine, t.working, t.stride, t.uvOffset);
            t.mesh.setVertices(t.working);
        }
    }
}
