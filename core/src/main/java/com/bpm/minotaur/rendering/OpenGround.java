package com.bpm.minotaur.rendering;

import com.badlogic.gdx.math.MathUtils;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.rendering.mesh.ChunkMeshBuilder;

/**
 * How open the ground is around a tile, for the wilderness biomes whose look
 * changes between the enclosed and the open: the forest's trails and glades,
 * the desert's canyons and basins.
 */
public final class OpenGround {

    /** Share of open tiles in the 5x5 window around a tile at which it starts to count as open, and is fully open. */
    private static final float OPENNESS_START = 0.45f;
    private static final float OPENNESS_FULL = 0.85f;
    private static final int RADIUS = 2;

    private OpenGround() {
    }

    /**
     * 0 in a corridor (a forest trail, a desert canyon) or inside the rock, 1 in
     * the middle of a clearing (a glade, a basin): the share of open tiles
     * around (x, y), eased so a corridor widening for a tile or two does not
     * count as a clearing.
     */
    public static float openness(Maze maze, int x, int y) {
        int open = 0;
        int total = 0;
        for (int dy = -RADIUS; dy <= RADIUS; dy++) {
            for (int dx = -RADIUS; dx <= RADIUS; dx++) {
                total++;
                if (isOpen(maze, x + dx, y + dy)) open++;
            }
        }
        float share = open / (float) total;
        float t = MathUtils.clamp((share - OPENNESS_START) / (OPENNESS_FULL - OPENNESS_START), 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    /**
     * Ground you could stand on. Maze.isPassable no longer looks at wall data,
     * so the cliff blocks around a wilderness chunk would otherwise count as open.
     */
    public static boolean isOpen(Maze maze, int x, int y) {
        return maze.isPassable(x, y)
                && (maze.getWallDataAt(x, y) & ChunkMeshBuilder.ALL_WALLS) != ChunkMeshBuilder.ALL_WALLS;
    }
}
