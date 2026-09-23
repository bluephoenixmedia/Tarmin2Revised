package com.bpm.minotaur.rendering.mesh;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;

import java.util.ArrayList;
import java.util.List;

/**
 * High-performance 2D geometric polygon clipper for billboard bisection.
 * Bisects a camera-facing sprite quad along the weapon's strike trajectory line,
 * generating two distinct convex polygon meshes with exact interpolated UVs
 * and the interior cut seam coordinates.
 */
public class BillboardSlicer {

    public static class Vertex2D {
        public float x, y;
        public float u, v;

        public Vertex2D(float x, float y, float u, float v) {
            this.x = x;
            this.y = y;
            this.u = u;
            this.v = v;
        }

        public static Vertex2D lerp(Vertex2D v1, Vertex2D v2, float t) {
            return new Vertex2D(
                    v1.x + t * (v2.x - v1.x),
                    v1.y + t * (v2.y - v1.y),
                    v1.u + t * (v2.u - v1.u),
                    v1.v + t * (v2.v - v1.v)
            );
        }
    }

    public static class SlicedResult {
        public boolean isSliced = false;
        public float[] trunkVertices;   // [x0, y0, x1, y1, ...]
        public float[] trunkUVs;        // [u0, v0, u1, v1, ...]
        public float[] severedVertices; // [x0, y0, x1, y1, ...]
        public float[] severedUVs;      // [u0, v0, u1, v1, ...]
        public float[] seamVertices;    // [x0, y0, x1, y1] endpoints of cut line
        public Vector2 trunkCentroid = new Vector2();
        public Vector2 severedCentroid = new Vector2();
    }

    private static final float EPSILON = 1e-4f;

    /**
     * Slices a billboard quad using normalized weapon swing coordinates [0..1]
     * projected onto the billboard's local dimension [-halfW..halfW, 0..height].
     */
    public static SlicedResult sliceFromSwing(
            float width, float height, TextureRegion region,
            float swingStartX, float swingStartY,
            float swingEndX, float swingEndY
    ) {
        float halfW = width * 0.5f;

        // Map normalized swing line [0..1, 0..1] into local billboard coords
        // swing coordinates: (0,0) is top-left, (1,1) is bottom-right
        float p1x = -halfW + swingStartX * width;
        float p1y = height * (1.0f - swingStartY);
        float p2x = -halfW + swingEndX * width;
        float p2y = height * (1.0f - swingEndY);

        // Direction vector of the cut
        float dx = p2x - p1x;
        float dy = p2y - p1y;
        float len = (float) Math.sqrt(dx * dx + dy * dy);

        if (len < 0.001f) {
            // Degenerate stroke: default to horizontal neck cut at 75% height
            dx = 1f;
            dy = 0f;
            p1x = -halfW;
            p1y = height * 0.70f;
        } else {
            dx /= len;
            dy /= len;
        }

        // Line equation: A*x + B*y + C = 0
        // Normal to the swing line: (-dy, dx)
        float a = -dy;
        float b = dx;
        float midX = (p1x + p2x) * 0.5f;
        float midY = (p1y + p2y) * 0.5f;
        float c = -(a * midX + b * midY);

        return sliceBillboard(width, height, region, a, b, c);
    }

    /**
     * Bisects the quad using the line A*x + B*y + C = 0.
     * Convention: Points with A*x + B*y + C > 0 belong to the severed piece,
     * points with A*x + B*y + C <= 0 belong to the remaining trunk.
     */
    public static SlicedResult sliceBillboard(
            float width, float height, TextureRegion region,
            float a, float b, float c
    ) {
        SlicedResult result = new SlicedResult();
        float halfW = width * 0.5f;

        float u1 = (region != null) ? region.getU() : 0f;
        float v1 = (region != null) ? region.getV2() : 0f; // Bottom
        float u2 = (region != null) ? region.getU2() : 1f;
        float v2 = (region != null) ? region.getV() : 1f;  // Top

        // Quad in CCW order: BL, BR, TR, TL
        List<Vertex2D> quad = new ArrayList<>(4);
        quad.add(new Vertex2D(-halfW, 0f, u1, v1));
        quad.add(new Vertex2D(halfW, 0f, u2, v1));
        quad.add(new Vertex2D(halfW, height, u2, v2));
        quad.add(new Vertex2D(-halfW, height, u1, v2));

        // Find seam endpoints: intersections of line with quad edges
        List<Vertex2D> seamPoints = new ArrayList<>();
        int qLen = quad.size();
        for (int i = 0; i < qLen; i++) {
            Vertex2D vCur = quad.get(i);
            Vertex2D vNext = quad.get((i + 1) % qLen);
            float d1 = a * vCur.x + b * vCur.y + c;
            float d2 = a * vNext.x + b * vNext.y + c;

            // Check if edge crosses or touches line
            float denom = d2 - d1;
            if (Math.abs(denom) > 1e-6f) {
                float t = -d1 / denom;
                if (t >= -EPSILON && t <= 1.0f + EPSILON) {
                    float clampedT = MathUtils.clamp(t, 0f, 1f);
                    Vertex2D inter = Vertex2D.lerp(vCur, vNext, clampedT);
                    addUniquePoint(seamPoints, inter);
                }
            } else if (Math.abs(d1) <= EPSILON) {
                // Entire edge is collinear
                addUniquePoint(seamPoints, vCur);
                addUniquePoint(seamPoints, vNext);
            }
        }

        if (seamPoints.size() < 2) {
            result.isSliced = false;
            return result;
        }

        // Two-pass Sutherland-Hodgman clipping
        List<Vertex2D> trunk = clipPolygon(quad, a, b, c, false);
        List<Vertex2D> severed = clipPolygon(quad, a, b, c, true);

        trunk = deduplicate(trunk);
        severed = deduplicate(severed);

        if (trunk.size() < 3 || severed.size() < 3) {
            result.isSliced = false;
            return result;
        }

        result.isSliced = true;
        result.trunkVertices = toVertexArray(trunk);
        result.trunkUVs = toUVArray(trunk);
        result.severedVertices = toVertexArray(severed);
        result.severedUVs = toUVArray(severed);

        Vertex2D s1 = seamPoints.get(0);
        Vertex2D s2 = seamPoints.get(1);
        result.seamVertices = new float[]{ s1.x, s1.y, s2.x, s2.y };

        computeCentroid(trunk, result.trunkCentroid);
        computeCentroid(severed, result.severedCentroid);

        return result;
    }

    private static List<Vertex2D> clipPolygon(List<Vertex2D> poly, float a, float b, float c, boolean keepPositive) {
        List<Vertex2D> output = new ArrayList<>();
        int n = poly.size();
        for (int i = 0; i < n; i++) {
            Vertex2D cur = poly.get(i);
            Vertex2D next = poly.get((i + 1) % n);
            float dCur = a * cur.x + b * cur.y + c;
            float dNext = a * next.x + b * next.y + c;

            boolean curIn = keepPositive ? (dCur >= -EPSILON) : (dCur <= EPSILON);
            boolean nextIn = keepPositive ? (dNext >= -EPSILON) : (dNext <= EPSILON);

            if (curIn) {
                output.add(cur);
                if (!nextIn) {
                    float t = -dCur / (dNext - dCur);
                    output.add(Vertex2D.lerp(cur, next, MathUtils.clamp(t, 0f, 1f)));
                }
            } else {
                if (nextIn) {
                    float t = -dCur / (dNext - dCur);
                    output.add(Vertex2D.lerp(cur, next, MathUtils.clamp(t, 0f, 1f)));
                }
            }
        }
        return output;
    }

    private static void addUniquePoint(List<Vertex2D> list, Vertex2D pt) {
        for (Vertex2D existing : list) {
            float dx = existing.x - pt.x;
            float dy = existing.y - pt.y;
            if (dx * dx + dy * dy < 1e-6f) {
                return;
            }
        }
        list.add(pt);
    }

    private static List<Vertex2D> deduplicate(List<Vertex2D> verts) {
        List<Vertex2D> result = new ArrayList<>();
        for (Vertex2D v : verts) {
            if (result.isEmpty() || dst2(result.get(result.size() - 1), v) > 1e-6f) {
                result.add(v);
            }
        }
        if (result.size() > 1 && dst2(result.get(0), result.get(result.size() - 1)) <= 1e-6f) {
            result.remove(result.size() - 1);
        }
        return result;
    }

    private static float dst2(Vertex2D v1, Vertex2D v2) {
        float dx = v1.x - v2.x;
        float dy = v1.y - v2.y;
        return dx * dx + dy * dy;
    }

    private static float[] toVertexArray(List<Vertex2D> verts) {
        float[] arr = new float[verts.size() * 2];
        for (int i = 0; i < verts.size(); i++) {
            arr[i * 2] = verts.get(i).x;
            arr[i * 2 + 1] = verts.get(i).y;
        }
        return arr;
    }

    private static float[] toUVArray(List<Vertex2D> verts) {
        float[] arr = new float[verts.size() * 2];
        for (int i = 0; i < verts.size(); i++) {
            arr[i * 2] = verts.get(i).u;
            arr[i * 2 + 1] = verts.get(i).v;
        }
        return arr;
    }

    private static void computeCentroid(List<Vertex2D> verts, Vector2 out) {
        float sumX = 0f, sumY = 0f;
        for (Vertex2D v : verts) {
            sumX += v.x;
            sumY += v.y;
        }
        if (!verts.isEmpty()) {
            out.set(sumX / verts.size(), sumY / verts.size());
        } else {
            out.setZero();
        }
    }
}
