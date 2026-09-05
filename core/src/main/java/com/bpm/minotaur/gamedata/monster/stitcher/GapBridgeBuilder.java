package com.bpm.minotaur.gamedata.monster.stitcher;

import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.graphics.g3d.utils.shapebuilders.CylinderShapeBuilder;
import com.badlogic.gdx.graphics.g3d.utils.shapebuilders.SphereShapeBuilder;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;

/**
 * Generates ball-and-socket bridging geometry between two anatomical points.
 *
 * The two input points (socketPenetrated, attachPenetrated) must already be
 * pushed inward into their respective meshes by the caller before this method
 * is invoked. Embedding the bridge endpoints inside the pre-authored geometry
 * hides the uneven cylinder edges during the depth-buffered bake, leaving a
 * perfectly contiguous pixel-art silhouette.
 *
 * Bridge geometry per joint:
 *   1. Sphere at socketPenetrated — masks the torso socket cut face.
 *   2. Sphere at attachPenetrated — masks the limb attachment cut face.
 *   3. Cylinder between the two points — fills any remaining topological void.
 *
 * The cylinder is oriented by composing a Matrix4 from:
 *   - Position  : midpoint between the two penetrated points
 *   - Rotation  : Quaternion mapping Vector3.Y → (attachPenetrated - socketPenetrated)
 *   - Scale     : identity (height is passed directly to CylinderShapeBuilder)
 *
 * All primitives share the same Material so creature_stylize.frag treats the
 * entire assembly—pre-authored meshes and bridges—as one contiguous silhouette.
 */
public final class GapBridgeBuilder {

    private static final long ATTRS = Usage.Position | Usage.Normal;
    private static final int  DIVS  = 8;

    private GapBridgeBuilder() {}

    /**
     * Builds a bridge between two already-penetrated world-space points.
     *
     * @param socketPenetrated  Torso socket centroid pushed into the torso mesh.
     * @param attachPenetrated  Limb attach centroid pushed into the limb mesh.
     * @param socketRadius      Approximate cross-sectional radius at the torso socket.
     * @param attachRadius      Approximate cross-sectional radius at the limb attach.
     * @param material          Shared material — must match adjacent mesh parts.
     * @return                  A new Model; caller is responsible for disposal.
     */
    public static Model build(Vector3 socketPenetrated, Vector3 attachPenetrated,
                              float socketRadius, float attachRadius,
                              Material material) {
        ModelBuilder mb = new ModelBuilder();
        mb.begin();
        MeshPartBuilder mpb = mb.part("bridge", GL20.GL_TRIANGLES, ATTRS, material);

        // Sphere masking the torso socket face
        float sd = Math.max(socketRadius * 2f, 0.04f);
        mpb.setVertexTransform(translationMatrix(socketPenetrated));
        SphereShapeBuilder.build(mpb, sd, sd, sd, DIVS, DIVS);

        // Sphere masking the limb attachment face
        float ad = Math.max(attachRadius * 2f, 0.04f);
        mpb.setVertexTransform(translationMatrix(attachPenetrated));
        SphereShapeBuilder.build(mpb, ad, ad, ad, DIVS, DIVS);

        // Cylinder bridging the gap
        Vector3 gap  = attachPenetrated.cpy().sub(socketPenetrated);
        float   dist = gap.len();
        if (dist > 0.005f) {
            Vector3 mid  = socketPenetrated.cpy().add(attachPenetrated).scl(0.5f);
            float   crad = Math.max(Math.min(socketRadius, attachRadius) * 0.85f, 0.02f);

            // Compose: translate to midpoint, rotate Y → gap direction
            Matrix4 cylTransform = new Matrix4(mid, rotationYToDir(gap), new Vector3(1f, 1f, 1f));
            mpb.setVertexTransform(cylTransform);
            CylinderShapeBuilder.build(mpb, crad * 2f, dist, crad * 2f, DIVS);
        }

        return mb.end();
    }

    /**
     * Estimates the cross-sectional radius of a mesh near the given local-space
     * Y coordinate by scanning vertices within a band and finding the maximum
     * radial distance from the Y axis. Returns a world-space radius via worldScale.
     */
    public static float estimateRadius(Model model, float localSliceY,
                                       float bandFraction, float worldScale) {
        if (model == null || model.meshes.size == 0) return 0.05f;

        com.badlogic.gdx.graphics.Mesh mesh = model.meshes.get(0);
        com.badlogic.gdx.graphics.VertexAttribute posAttr =
                mesh.getVertexAttribute(Usage.Position);
        if (posAttr == null) return 0.05f;

        int stride   = mesh.getVertexSize() / 4;
        int posOff   = posAttr.offset / 4;
        int numVerts = mesh.getNumVertices();
        float[] verts = new float[numVerts * stride];
        mesh.getVertices(verts);

        float yMax = Float.NEGATIVE_INFINITY, yMin = Float.POSITIVE_INFINITY;
        for (int i = 0; i < numVerts; i++) {
            float y = verts[i * stride + posOff + 1];
            if (y > yMax) yMax = y;
            if (y < yMin) yMin = y;
        }
        float band   = (yMax - yMin) * bandFraction;
        float maxR   = 0f;
        for (int i = 0; i < numVerts; i++) {
            float y = verts[i * stride + posOff + 1];
            if (Math.abs(y - localSliceY) <= band) {
                float x = verts[i * stride + posOff];
                float z = verts[i * stride + posOff + 2];
                float r = (float) Math.sqrt(x * x + z * z);
                if (r > maxR) maxR = r;
            }
        }
        return Math.max(maxR * worldScale, 0.02f);
    }

    // -------------------------------------------------------------------------

    /**
     * Computes a Quaternion that rotates Vector3.Y onto the given direction.
     * Guards against the anti-parallel degenerate case (dir ≈ -Y) where the
     * cross product vanishes and setFromCross produces NaN.
     */
    private static Quaternion rotationYToDir(Vector3 dir) {
        Vector3 n   = dir.cpy().nor();
        float   dot = Vector3.Y.dot(n);
        Quaternion q = new Quaternion();
        if (dot > 0.9999f) {
            q.idt();                         // already aligned — identity
        } else if (dot < -0.9999f) {
            q.set(Vector3.X, 180f);          // anti-parallel — rotate 180° around X
        } else {
            q.setFromCross(Vector3.Y, n);    // general case
        }
        return q;
    }

    private static Matrix4 translationMatrix(Vector3 pos) {
        return new Matrix4().setToTranslation(pos.x, pos.y, pos.z);
    }
}
