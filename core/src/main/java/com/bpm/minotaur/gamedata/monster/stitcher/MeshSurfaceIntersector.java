package com.bpm.minotaur.gamedata.monster.stitcher;

import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.Intersector;
import com.badlogic.gdx.math.collision.BoundingBox;
import com.badlogic.gdx.math.collision.Ray;

import java.util.HashMap;
import java.util.Map;

/**
 * Finds anatomical attachment points by raycasting from the mesh's geometric
 * center outward in a socket-specific direction and computing the exact
 * Ray-Triangle intersection on the raw mesh surface.
 *
 * This replaces the "Extremal Vertex Slice" heuristic which fails on non-convex
 * organic models: a bottom-10% slice of a horse torso captures four disconnected
 * hoof regions whose average floats in empty air beneath the belly.
 *
 * Raycasting from the interior guarantees the returned point lies on the
 * physical surface of the mesh, immune to non-convex geometry and outlier
 * appendages that corrupt bounding-box extremes.
 *
 * All results are returned in WORLD space via the supplied ModelInstance transform.
 */
public final class MeshSurfaceIntersector {

    /**
     * Direction to cast the ray on the TORSO to find each socket face.
     * Vectors point outward from the torso's geometric centre toward the
     * anatomical attachment region.
     */
    private static final Map<String, Vector3> TORSO_DIR = new HashMap<>();

    /**
     * Direction to cast the ray on the LIMB to find its attachment face
     * (the "cut" surface that was severed from its native body).
     */
    private static final Map<String, Vector3> LIMB_DIR = new HashMap<>();

    static {
        // HEAD socket is at the top of the torso; head attaches at its bottom face.
        TORSO_DIR.put("HEAD",  new Vector3( 0f,  1f,  0f));
        LIMB_DIR .put("HEAD",  new Vector3( 0f, -1f,  0f));

        // Leg sockets: angled slightly outward so LEG_L and LEG_R hit different faces.
        TORSO_DIR.put("LEG_L", new Vector3(-0.3f, -1f,  0f).nor());
        TORSO_DIR.put("LEG_R", new Vector3( 0.3f, -1f,  0f).nor());
        LIMB_DIR .put("LEG_L", new Vector3( 0f,   1f,  0f));
        LIMB_DIR .put("LEG_R", new Vector3( 0f,   1f,  0f));

        // Arm sockets: lateral faces of the torso, slightly upward toward shoulders.
        TORSO_DIR.put("ARM_L", new Vector3(-1f,  0.2f,  0f).nor());
        TORSO_DIR.put("ARM_R", new Vector3( 1f,  0.2f,  0f).nor());
        LIMB_DIR .put("ARM_L", new Vector3( 1f,  0.2f,  0f).nor());
        LIMB_DIR .put("ARM_R", new Vector3(-1f,  0.2f,  0f).nor());

        // Tail: rear-lower face of the torso; tail attaches at its front/top.
        TORSO_DIR.put("TAIL",  new Vector3( 0f, -0.3f,  1f).nor());
        LIMB_DIR .put("TAIL",  new Vector3( 0f, -0.3f, -1f).nor());
    }

    private MeshSurfaceIntersector() {}

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Casts a ray from the torso's geometric centre in the socket direction and
     * returns the exact world-space surface intersection point.
     *
     * @param model          Torso model to intersect.
     * @param socket         Socket name (HEAD, LEG_L, ARM_R, …).
     * @param worldTransform Transform already applied to the torso ModelInstance
     *                       (scale + translation). The result is in world space.
     */
    public static Vector3 findTorsoSocket(Model model, String socket,
                                           Matrix4 worldTransform) {
        Vector3 dir = TORSO_DIR.getOrDefault(socket, new Vector3(0f, 1f, 0f));
        return castFromCenter(model, dir, worldTransform);
    }

    /**
     * Casts a ray from the limb's geometric centre toward its attachment face
     * and returns the world-space surface intersection point.
     *
     * @param model          Limb model to intersect.
     * @param socket         The socket this limb will occupy (drives direction).
     * @param worldTransform Transform already applied to the limb ModelInstance
     *                       (scale only — no translation yet at call time).
     */
    public static Vector3 findLimbAttach(Model model, String socket,
                                          Matrix4 worldTransform) {
        Vector3 dir = LIMB_DIR.getOrDefault(socket, new Vector3(0f, 1f, 0f));
        return castFromCenter(model, dir, worldTransform);
    }

    /**
     * Returns the outward socket direction for the torso (used to compute the
     * penetration push vector — push opposite this direction to go into the mesh).
     */
    public static Vector3 torsoSocketDir(String socket) {
        return TORSO_DIR.getOrDefault(socket, new Vector3(0f, 1f, 0f)).cpy().nor();
    }

    /**
     * Returns the outward attach direction for the limb (used to compute the
     * penetration push vector — push opposite this direction to go into the limb).
     */
    public static Vector3 limbAttachDir(String socket) {
        return LIMB_DIR.getOrDefault(socket, new Vector3(0f, 1f, 0f)).cpy().nor();
    }

    // -------------------------------------------------------------------------
    // Core raycasting
    // -------------------------------------------------------------------------

    private static Vector3 castFromCenter(Model model, Vector3 localDir,
                                           Matrix4 worldTransform) {
        if (model == null || model.meshes.size == 0) {
            return new Vector3(0f, 0f, 0f).mul(worldTransform);
        }

        Mesh mesh = model.meshes.get(0);
        VertexAttribute posAttr = mesh.getVertexAttribute(Usage.Position);
        if (posAttr == null) return new Vector3(0f, 0f, 0f).mul(worldTransform);

        int stride = mesh.getVertexSize() / 4;  // floats per vertex
        int posOff = posAttr.offset / 4;        // float index of X within stride

        int numVerts = mesh.getNumVertices();
        float[] raw = new float[numVerts * stride];
        mesh.getVertices(raw);

        // --- Compact positions (3 floats per vertex) so Intersector can stride safely ---
        float[] positions = new float[numVerts * 3];
        double cx = 0, cy = 0, cz = 0;
        for (int i = 0; i < numVerts; i++) {
            float x = raw[i * stride + posOff];
            float y = raw[i * stride + posOff + 1];
            float z = raw[i * stride + posOff + 2];
            positions[i * 3]     = x;
            positions[i * 3 + 1] = y;
            positions[i * 3 + 2] = z;
            cx += x; cy += y; cz += z;
        }
        Vector3 localCenter = new Vector3(
                (float)(cx / numVerts),
                (float)(cy / numVerts),
                (float)(cz / numVerts));

        Vector3 localHit = new Vector3();
        boolean hit      = false;

        int numIndices = mesh.getNumIndices();
        if (numIndices > 0) {
            short[] indices = new short[numIndices];
            mesh.getIndices(indices);

            Ray localRay = new Ray(localCenter, localDir.cpy().nor());
            hit = Intersector.intersectRayTriangles(localRay, positions, indices, 3, localHit);
        } else if (numVerts >= 3) {
            // Non-indexed mesh: positions array already has consecutive XYZ triplets.
            Ray localRay = new Ray(localCenter, localDir.cpy().nor());
            hit = Intersector.intersectRayTriangles(localRay, positions, localHit);
        }

        if (!hit) {
            // Fallback: AABB extreme in the socket direction.
            BoundingBox box = new BoundingBox();
            mesh.calculateBoundingBox(box);
            Vector3 half = new Vector3(
                    (box.max.x - box.min.x) * 0.5f,
                    (box.max.y - box.min.y) * 0.5f,
                    (box.max.z - box.min.z) * 0.5f);
            Vector3 n = localDir.cpy().nor();
            localHit.set(
                    localCenter.x + n.x * half.x,
                    localCenter.y + n.y * half.y,
                    localCenter.z + n.z * half.z);
        }

        // Transform local-space hit to world space
        return localHit.mul(worldTransform);
    }
}
