package com.bpm.minotaur.gamedata.monster.stitcher;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.model.MeshPart;
import com.badlogic.gdx.graphics.g3d.model.Node;
import com.badlogic.gdx.graphics.g3d.model.NodePart;

import java.io.IOException;
import java.io.Writer;
import java.nio.ShortBuffer;

/**
 * Serializes a libGDX {@link Model} to the .g3dj JSON format understood by
 * {@code G3dModelLoader} / {@code G3dLoader}.
 *
 * g3dj schema (version "0.1"):
 * <pre>
 * {
 *   "version": [0, 1],
 *   "id": "",
 *   "meshes": [ { "attributes": [...], "vertices": [...], "parts": [ {"id":..., "type":"TRIANGLES", "indices":[...]} ] } ],
 *   "materials": [ { "id": "...", "diffuse": [r,g,b] } ],
 *   "nodes": [ { "id": "...", "parts": [ {"meshpartid":..., "materialid":...} ] } ],
 *   "animations": []
 * }
 * </pre>
 *
 * Only POSITION + NORMAL vertex attributes are written; materials are written as
 * simple diffuse entries derived from the model's ColorAttribute when present.
 */
public final class G3djWriter {

    private G3djWriter() {}

    /**
     * Writes {@code model} to {@code dest} as .g3dj JSON.
     * Creates or overwrites the file. The destination directory must exist.
     */
    public static void write(Model model, FileHandle dest) throws IOException {
        try (Writer w = dest.writer(false, "UTF-8")) {
            writeModel(model, w);
        }
    }

    // -------------------------------------------------------------------------

    private static void writeModel(Model model, Writer w) throws IOException {
        w.write("{\n");
        w.write("  \"version\": [0, 1],\n");
        w.write("  \"id\": \"\",\n");

        // --- meshes ---
        w.write("  \"meshes\": [\n");
        for (int mi = 0; mi < model.meshes.size; mi++) {
            if (mi > 0) w.write(",\n");
            writeMesh(model, model.meshes.get(mi), w, mi);
        }
        w.write("\n  ],\n");

        // --- materials ---
        w.write("  \"materials\": [\n");
        for (int i = 0; i < model.materials.size; i++) {
            if (i > 0) w.write(",\n");
            writeMaterial(model.materials.get(i), w);
        }
        w.write("\n  ],\n");

        // --- nodes ---
        w.write("  \"nodes\": [\n");
        for (int i = 0; i < model.nodes.size; i++) {
            if (i > 0) w.write(",\n");
            writeNode(model.nodes.get(i), w);
        }
        w.write("\n  ],\n");

        // --- animations (empty) ---
        w.write("  \"animations\": []\n");
        w.write("}\n");
    }

    private static void writeMesh(Model model, Mesh mesh, Writer w, int meshIndex) throws IOException {
        w.write("    {\n");

        // attributes array
        w.write("      \"attributes\": [");
        boolean first = true;
        for (VertexAttribute attr : mesh.getVertexAttributes()) {
            if (!first) w.write(", ");
            w.write("\"" + g3djAttributeName(attr) + "\"");
            first = false;
        }
        w.write("],\n");

        // vertices array
        int vertCount  = mesh.getNumVertices();
        int floatCount = vertCount * (mesh.getVertexSize() / 4);
        float[] verts  = new float[floatCount];
        mesh.getVertices(verts);

        w.write("      \"vertices\": [");
        for (int i = 0; i < verts.length; i++) {
            if (i > 0) w.write(", ");
            w.write(formatFloat(verts[i]));
        }
        w.write("],\n");

        // parts (one per MeshPart that references this mesh)
        w.write("      \"parts\": [\n");
        boolean firstPart = true;
        for (MeshPart mp : model.meshParts) {
            if (mp.mesh != mesh) continue;
            if (!firstPart) w.write(",\n");
            writeMeshPart(mesh, mp, w);
            firstPart = false;
        }
        w.write("\n      ]\n");

        w.write("    }");
    }

    private static void writeMeshPart(Mesh mesh, MeshPart part, Writer w) throws IOException {
        w.write("        {\n");
        w.write("          \"id\": \"" + escapeJson(part.id) + "\",\n");
        w.write("          \"type\": \"TRIANGLES\",\n");
        w.write("          \"indices\": [");

        // Read indices via ShortBuffer
        int indexCount = mesh.getNumIndices();
        ShortBuffer ib = mesh.getIndicesBuffer(false);
        ib.rewind();
        int limit = Math.min(part.offset + part.size, indexCount);
        for (int i = part.offset; i < limit; i++) {
            if (i > part.offset) w.write(", ");
            w.write(Short.toUnsignedInt(ib.get(i)) + "");
        }

        w.write("]\n");
        w.write("        }");
    }

    private static void writeMaterial(Material mat, Writer w) throws IOException {
        w.write("    {\n");
        w.write("      \"id\": \"" + escapeJson(mat.id) + "\"");

        com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute diffuse =
                (com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute)
                mat.get(com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute.Diffuse);
        if (diffuse != null) {
            w.write(",\n      \"diffuse\": ["
                    + formatFloat(diffuse.color.r) + ", "
                    + formatFloat(diffuse.color.g) + ", "
                    + formatFloat(diffuse.color.b) + "]");
        }

        w.write("\n    }");
    }

    private static void writeNode(Node node, Writer w) throws IOException {
        w.write("    {\n");
        w.write("      \"id\": \"" + escapeJson(node.id) + "\"");

        if (node.parts.size > 0) {
            w.write(",\n      \"parts\": [\n");
            for (int i = 0; i < node.parts.size; i++) {
                if (i > 0) w.write(",\n");
                NodePart np = node.parts.get(i);
                w.write("        {\n");
                w.write("          \"meshpartid\": \"" + escapeJson(np.meshPart.id) + "\",\n");
                w.write("          \"materialid\": \"" + escapeJson(np.material.id) + "\"\n");
                w.write("        }");
            }
            w.write("\n      ]");
        }

        if (node.hasChildren()) {
            w.write(",\n      \"children\": [\n");
            boolean first = true;
            for (Node child : node.getChildren()) {
                if (!first) w.write(",\n");
                writeNode(child, w);
                first = false;
            }
            w.write("\n      ]");
        }

        w.write("\n    }");
    }

    // -------------------------------------------------------------------------

    private static String g3djAttributeName(VertexAttribute attr) {
        switch (attr.usage) {
            case com.badlogic.gdx.graphics.VertexAttributes.Usage.Position: return "POSITION";
            case com.badlogic.gdx.graphics.VertexAttributes.Usage.Normal:   return "NORMAL";
            case com.badlogic.gdx.graphics.VertexAttributes.Usage.TextureCoordinates:
                return "TEXCOORD" + attr.unit;
            case com.badlogic.gdx.graphics.VertexAttributes.Usage.ColorUnpacked:
            case com.badlogic.gdx.graphics.VertexAttributes.Usage.ColorPacked:
                return "COLOR";
            case com.badlogic.gdx.graphics.VertexAttributes.Usage.Tangent:   return "TANGENT";
            case com.badlogic.gdx.graphics.VertexAttributes.Usage.BiNormal:  return "BINORMAL";
            default: return "UNKNOWN";
        }
    }

    private static String formatFloat(float v) {
        if (v == (long) v) return Long.toString((long) v);
        // 6 significant figures is enough for mesh geometry
        String s = String.format("%.6f", v);
        // trim trailing zeros after decimal point
        if (s.contains(".")) {
            s = s.replaceAll("0+$", "");
            if (s.endsWith(".")) s += "0";
        }
        return s;
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
