package com.bpm.minotaur.rendering.mesh;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.gamedata.Difficulty;
import com.bpm.minotaur.gamedata.GameMode;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.generation.ForestChunkGenerator;
import com.bpm.minotaur.rendering.RetroTheme;
import org.junit.Test;

import java.lang.reflect.Proxy;

import static org.junit.Assert.*;

public class CanopyMeshBuilderTest {

    // 9x9 of trees with a 1-wide trail down x = 4 into a 5x5 clearing at the top left.
    private static final String[] TRAIL_INTO_CLEARING = {
            "TTTTTTTTT",
            "T.....TTT",
            "T.....TTT",
            "T.....TTT",
            "T.....TTT",
            "T.....TTT",
            "TTTT.TTTT",
            "TTTT.TTTT",
            "TTTT.TTTT",
    };

    @Test
    public void treesAreClosedTrailsAreASeamAndGladesAreOpen() {
        float[][] cov = CanopyMeshBuilder.tileCoverage(mazeOf(TRAIL_INTO_CLEARING));

        assertEquals("tree tile", 1f, cov[0][0], 0.001f);
        assertEquals("trail tile", CanopyMeshBuilder.TRAIL_COVERAGE, cov[1][4], 0.001f);
        assertEquals("middle of the clearing", 0f, cov[5][3], 0.001f);
    }

    @Test
    public void solidWallBlocksAreUnderTheCanopy() {
        String[] rows = {".....", ".....", ".....", ".....", "....."};
        Maze maze = mazeOf(rows);
        maze.getWallData()[2][2] = ChunkMeshBuilder.ALL_WALLS;

        assertEquals(1f, CanopyMeshBuilder.tileCoverage(maze)[2][2], 0.001f);
    }

    @Test
    public void vertexCoverageBlendsTheTilesItTouches() {
        float[][] cov = CanopyMeshBuilder.tileCoverage(mazeOf(TRAIL_INTO_CLEARING));
        float trail = CanopyMeshBuilder.TRAIL_COVERAGE;

        // Half-tile grid: tile (4,1) has its centre at (9,3).
        assertEquals("centre of the trail tile", trail, CanopyMeshBuilder.vertexCoverage(cov, 9, 3), 0.001f);
        assertEquals("edge between trail and tree", (trail + 1f) / 2f,
                CanopyMeshBuilder.vertexCoverage(cov, 8, 3), 0.001f);
        assertEquals("corner shared by two trail and two tree tiles", (2 * trail + 2f) / 4f,
                CanopyMeshBuilder.vertexCoverage(cov, 8, 2), 0.001f);
    }

    @Test
    public void vertexCoverageClampsAtTheChunkEdge() {
        float[][] cov = CanopyMeshBuilder.tileCoverage(mazeOf(TRAIL_INTO_CLEARING));

        assertEquals(1f, CanopyMeshBuilder.vertexCoverage(cov, 0, 0), 0.001f);
        assertEquals(1f, CanopyMeshBuilder.vertexCoverage(cov, 18, 18), 0.001f);
    }

    @Test
    public void aGeneratedForestClosesOverItsCliffsAndOpensOverItsCentralGlade() {
        if (Gdx.app == null) {
            Gdx.app = (Application) Proxy.newProxyInstance(Application.class.getClassLoader(),
                    new Class<?>[]{Application.class}, (proxy, method, args) -> null);
        }
        Maze maze = new ForestChunkGenerator().generateChunk(new GridPoint2(2, 2), 1, 1, Difficulty.MEDIUM,
                GameMode.ADVANCED, RetroTheme.FOREST_THEME, RetroTheme.STANDARD_THEME,
                null, null, null, null, null, 42L, 0);

        float[][] cov = CanopyMeshBuilder.tileCoverage(maze);

        assertEquals("the central glade is open sky", 0f, cov[18][18], 0.001f);
        int w = maze.getWidth();
        int h = maze.getHeight();
        for (int i = 0; i < w; i++) {
            assertEquals("cliff at (" + i + ",0)", 1f, cov[0][i], 0.001f);
            assertEquals("cliff at (" + i + "," + (h - 1) + ")", 1f, cov[h - 1][i], 0.001f);
        }
        for (int i = 0; i < h; i++) {
            assertEquals("cliff at (0," + i + ")", 1f, cov[i][0], 0.001f);
            assertEquals("cliff at (" + (w - 1) + "," + i + ")", 1f, cov[i][w - 1], 0.001f);
        }
    }

    /** Rows are top-down as written; maze y=0 is the bottom row. '.' open, 'T' blocked. */
    private static Maze mazeOf(String[] rows) {
        int h = rows.length;
        int w = rows[0].length();
        boolean[][] open = new boolean[h][w];
        for (int r = 0; r < h; r++) {
            for (int x = 0; x < w; x++) {
                open[h - 1 - r][x] = rows[r].charAt(x) == '.';
            }
        }
        return new Maze(1, new int[h][w]) {
            @Override
            public boolean isPassable(int x, int y) {
                return x >= 0 && y >= 0 && x < w && y < h && open[y][x];
            }
        };
    }
}
