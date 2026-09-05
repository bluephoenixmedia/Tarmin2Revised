package com.bpm.minotaur.gamedata.monster.stitcher;

import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;

/**
 * Extracts anatomical attachment points from raw mesh vertex data by computing
 * the spatial centroid of vertices in an extremal slice of the mesh.
 *
 * This avoids the "bounding box fallacy" where a raised tail or perked ear
 * corrupts the AABB max, causing floating attachment geometry.
 *
 * All results are returned in the mesh's LOCAL space. The caller must transform
 * them into world space by multiplying with the ModelInstance's transform matrix.
 */
public final class MeshVertexExtractor {

    private MeshVertexExtractor() {}

    /**
     * Returns the centroid of vertices in the top {@code sliceFraction} of the mesh
     * along the Y axis (local space). Use for finding the attachment joint at the
     * top of a limb (e.g. the severed neck of a head, or the hip of a leg).
     *
     * @param model         Source model — uses its first mesh part.
     * @param sliceFraction Fraction of total height to include (0.10 = top 10%).
     */
    public static Vector3 extractTopCentroid(Model model, float sliceFraction) {
        return extractCentroid(model, sliceFraction, true);
    }

    /**
     * Returns the centroid of vertices in the bottom {@code sliceFraction} of the mesh
     * along the Y axis (local space). Use for finding socket joints on the torso
     * (e.g. where legs attach at the pelvis, or where the neck meets the shoulders).
     */
    public static Vector3 extractBottomCentroid(Model model, float sliceFraction) {
        return extractCentroid(model, sliceFraction, false);
    }

    /**
     * Transforms a local-space centroid into world space using the given matrix.
     * Pass the ModelInstance's transform here after it has been scaled and positioned.
     */
    public static Vector3 toWorldSpace(Vector3 localCentroid, Matrix4 worldTransform) {
        return localCentroid.cpy().mul(worldTransform);
    }

    // -------------------------------------------------------------------------

    private static Vector3 extractCentroid(Model model, float sliceFraction, boolean top) {
        if (model == null || model.meshes.size == 0) return Vector3.Zero.cpy();

        Mesh mesh = model.meshes.get(0);
        VertexAttribute posAttr = mesh.getVertexAttribute(Usage.Position);
        if (posAttr == null) return Vector3.Zero.cpy();

        int stride    = mesh.getVertexSize() / 4;   // floats per vertex
        int posOffset = posAttr.offset / 4;          // float index of X within stride

        int   numVerts = mesh.getNumVertices();
        float[] verts  = new float[numVerts * stride];
        mesh.getVertices(verts);

        // Pass 1 — find local Y extent
        float yMax = Float.NEGATIVE_INFINITY;
        float yMin = Float.POSITIVE_INFINITY;
        for (int i = 0; i < numVerts; i++) {
            float y = verts[i * stride + posOffset + 1];
            if (y > yMax) yMax = y;
            if (y < yMin) yMin = y;
        }
        if (yMax == yMin) return new Vector3(0, yMax, 0);

        float totalHeight = yMax - yMin;
        float threshold   = top
                ? yMax - totalHeight * sliceFraction   // top slice
                : yMin + totalHeight * sliceFraction;  // bottom slice

        // Pass 2 — accumulate centroid of slice vertices
        double sx = 0, sy = 0, sz = 0;
        int count = 0;
        for (int i = 0; i < numVerts; i++) {
            float y = verts[i * stride + posOffset + 1];
            boolean inSlice = top ? (y >= threshold) : (y <= threshold);
            if (inSlice) {
                sx += verts[i * stride + posOffset];
                sy += y;
                sz += verts[i * stride + posOffset + 2];
                count++;
            }
        }

        if (count == 0) {
            // Fallback: use the extreme point directly
            return new Vector3(0, top ? yMax : yMin, 0);
        }
        return new Vector3((float)(sx / count), (float)(sy / count), (float)(sz / count));
    }
}
