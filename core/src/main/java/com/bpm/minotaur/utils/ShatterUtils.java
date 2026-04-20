package com.bpm.minotaur.utils;

import com.badlogic.gdx.graphics.g2d.PolygonRegion;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.DelaunayTriangulator;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ShortArray;

public class ShatterUtils {

    private static final DelaunayTriangulator triangulator = new DelaunayTriangulator();

    public static class Shard {
        public PolygonRegion region;
        public float centroidX;
        public float centroidY;

        public Shard(PolygonRegion region, float cx, float cy) {
            this.region = region;
            this.centroidX = cx;
            this.centroidY = cy;
        }
    }

    public static Array<Shard> shatter(TextureRegion region, int numPoints) {
        float width = region.getRegionWidth();
        float height = region.getRegionHeight();

        float[] points = new float[(numPoints + 4) * 2];
        int idx = 0;

        // Add corners
        points[idx++] = 0;
        points[idx++] = 0;
        points[idx++] = width;
        points[idx++] = 0;
        points[idx++] = width;
        points[idx++] = height;
        points[idx++] = 0;
        points[idx++] = height;

        // Add random points
        for (int i = 0; i < numPoints; i++) {
            points[idx++] = MathUtils.random(0, width);
            points[idx++] = MathUtils.random(0, height);
        }

        ShortArray triangles = triangulator.computeTriangles(points, false);

        Array<Shard> shards = new Array<>();

        // Each triangle is a shard
        for (int i = 0; i < triangles.size; i += 3) {
            int p1 = triangles.get(i) * 2;
            int p2 = triangles.get(i + 1) * 2;
            int p3 = triangles.get(i + 2) * 2;

            float x1 = points[p1], y1 = points[p1 + 1];
            float x2 = points[p2], y2 = points[p2 + 1];
            float x3 = points[p3], y3 = points[p3 + 1];

            // Calculate centroid
            float cx = (x1 + x2 + x3) / 3f;
            float cy = (y1 + y2 + y3) / 3f;

            // Use original vertices for correct texture mapping
            float[] vertices = new float[] {
                    x1, y1,
                    x2, y2,
                    x3, y3
            };

            short[] singleTriangle = new short[] { 0, 1, 2 };

            Shard shard = new Shard(new PolygonRegion(region, vertices, singleTriangle), cx, cy);
            shards.add(shard);
        }

        return shards;
    }
}
