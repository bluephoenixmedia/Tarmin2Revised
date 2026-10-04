package com.bpm.minotaur.rendering.mesh;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.math.GridPoint2;
import com.badlogic.gdx.utils.FloatArray;
import com.badlogic.gdx.utils.ShortArray;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.Scenery;
import com.bpm.minotaur.generation.Biome;
import com.bpm.minotaur.rendering.ForestAtmosphere;

/**
 * The leaf ceiling over the surface forest.
 *
 * Every tile gets a canopy coverage: 1 over trees and cliffs, a little over a
 * trail, nothing in a glade. The mesh is a 2x2 grid of quads per tile, and each
 * vertex carries the coverage of the tiles it touches in its colour alpha. The
 * shader multiplies that by the canopy texture's alpha (frond density) before
 * the cutoff, so the canopy closes over the trees, thins into a ragged seam of
 * sky above each trail, and lets the glades open to the sky.
 */
public final class CanopyMeshBuilder {

    /** Three times wall height: the tall pines rise into it and their tops are lost. */
    public static final float CANOPY_Y = 3.0f;
    /** Coverage over a trail tile: under the 0.5 cutoff so the middle opens, high enough that the edges mostly close. */
    public static final float TRAIL_COVERAGE = 0.45f;
    /** World units per texture repeat. */
    private static final float TEXTURE_SPAN = 2f;
    /** Vertex coverage times the densest frond alpha (1.0) has to reach the shader's cutoff. */
    private static final float VISIBLE_COVERAGE = 0.5f;

    private CanopyMeshBuilder() {
    }

    /**
     * The canopy for a surface forest chunk, or null when the chunk has none
     * (not forest, underground, or no texture).
     */
    public static ChunkSubMesh build(Maze maze, Texture canopyTexture, float worldOffsetX, float worldOffsetZ) {
        if (canopyTexture == null || !hasCanopy(maze)) {
            return null;
        }

        float[][] cov = tileCoverage(maze);
        FloatArray verts = new FloatArray();
        ShortArray indices = new ShortArray();
        Color corner = new Color(Color.WHITE);
        int w = maze.getWidth();
        int h = maze.getHeight();

        for (int hy = 0; hy < h * 2; hy++) {
            for (int hx = 0; hx < w * 2; hx++) {
                float c00 = vertexCoverage(cov, hx, hy);
                float c10 = vertexCoverage(cov, hx + 1, hy);
                float c11 = vertexCoverage(cov, hx + 1, hy + 1);
                float c01 = vertexCoverage(cov, hx, hy + 1);
                if (Math.max(Math.max(c00, c10), Math.max(c11, c01)) < VISIBLE_COVERAGE) continue;

                float x0 = hx * 0.5f;
                float x1 = x0 + 0.5f;
                float y0 = hy * 0.5f;
                float y1 = y0 + 0.5f;
                // Facing down, wound as the ceiling quads in ChunkMeshBuilder are.
                addVertex(verts, x0 + worldOffsetX, -y0 + worldOffsetZ, x0, y0, corner, c00);
                addVertex(verts, x0 + worldOffsetX, -y1 + worldOffsetZ, x0, y1, corner, c01);
                addVertex(verts, x1 + worldOffsetX, -y1 + worldOffsetZ, x1, y1, corner, c11);
                addVertex(verts, x1 + worldOffsetX, -y0 + worldOffsetZ, x1, y0, corner, c10);
                short base = (short) (verts.size / 9 - 4);
                indices.add(base);
                indices.add((short) (base + 1));
                indices.add((short) (base + 2));
                indices.add(base);
                indices.add((short) (base + 2));
                indices.add((short) (base + 3));
            }
        }

        if (indices.size == 0) return null;
        Mesh mesh = new Mesh(true, verts.size / 9, indices.size, ChunkMeshBuilder.VERTEX_ATTRIBUTES);
        mesh.setVertices(verts.toArray());
        mesh.setIndices(indices.toArray());
        return new ChunkSubMesh(canopyTexture, mesh, indices.size, ChunkSubMesh.Surface.CANOPY);
    }

    /** Only the surface forest has a canopy; underground forest has a rock ceiling. */
    public static boolean hasCanopy(Maze maze) {
        return maze != null && maze.getBiome() == Biome.FOREST && maze.getLevel() == 1;
    }

    /** Tiles under a fully closed canopy, indexed [y][x]; null when the chunk has no canopy. */
    public static boolean[][] closedTiles(Maze maze) {
        if (!hasCanopy(maze)) return null;
        float[][] cov = tileCoverage(maze);
        boolean[][] closed = new boolean[cov.length][cov[0].length];
        for (int y = 0; y < cov.length; y++) {
            for (int x = 0; x < cov[0].length; x++) {
                closed[y][x] = cov[y][x] >= 1f;
            }
        }
        return closed;
    }

    /** Canopy coverage per tile, indexed [y][x]: 1 is closed, 0 is open sky. */
    static float[][] tileCoverage(Maze maze) {
        int w = maze.getWidth();
        int h = maze.getHeight();
        float[][] cov = new float[h][w];
        GridPoint2 at = new GridPoint2();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                // A bush is walkable but grows among the trees: opening the canopy over
                // every one would scatter holes through it, so only real ground opens it.
                Scenery scenery = maze.getScenery().get(at.set(x, y));
                boolean bush = scenery != null && scenery.getType() == Scenery.SceneryType.BUSH;
                cov[y][x] = !bush && ForestAtmosphere.isOpen(maze, x, y)
                        ? TRAIL_COVERAGE * (1f - ForestAtmosphere.gladeFactor(maze, x, y))
                        : 1f;
            }
        }
        return cov;
    }

    /**
     * Coverage at a point on the half-tile grid: (hx / 2, hy / 2) in tile units.
     * A tile centre takes its own tile, an edge the two tiles either side, a
     * corner the four around it. Tiles past the chunk edge clamp to the edge.
     */
    static float vertexCoverage(float[][] cov, int hx, int hy) {
        int h = cov.length;
        int w = cov[0].length;
        int xLo = (hx % 2 == 1) ? (hx - 1) / 2 : hx / 2 - 1;
        int xHi = (hx % 2 == 1) ? xLo : xLo + 1;
        int yLo = (hy % 2 == 1) ? (hy - 1) / 2 : hy / 2 - 1;
        int yHi = (hy % 2 == 1) ? yLo : yLo + 1;
        float sum = 0f;
        int n = 0;
        for (int y = yLo; y <= yHi; y++) {
            for (int x = xLo; x <= xHi; x++) {
                sum += cov[clamp(y, h)][clamp(x, w)];
                n++;
            }
        }
        return sum / n;
    }

    private static int clamp(int i, int size) {
        return Math.max(0, Math.min(size - 1, i));
    }

    private static void addVertex(FloatArray verts, float x, float z, float tileX, float tileY,
                                  Color scratch, float coverage) {
        verts.add(x);
        verts.add(CANOPY_Y);
        verts.add(z);
        verts.add(0f);
        verts.add(-1f);
        verts.add(0f);
        verts.add(scratch.set(1f, 1f, 1f, coverage).toFloatBits());
        verts.add(tileX / TEXTURE_SPAN);
        verts.add(tileY / TEXTURE_SPAN);
    }
}
