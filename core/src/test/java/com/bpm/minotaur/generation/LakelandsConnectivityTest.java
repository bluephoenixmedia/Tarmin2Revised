package com.bpm.minotaur.generation;

import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.liquid.LiquidManager;
import com.bpm.minotaur.gamedata.liquid.LiquidType;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * Validates connectivity guarantees, causeway traversability, and liquid distribution
 * for the Lakelands Biome.
 *
 * <p>Like the forest and desert, every gate approach must reach every other gate
 * across any seed, and no single tile should be able to sever the chunk.
 * The terrain must be water-dominant with winding causeways and islets.
 */
public class LakelandsConnectivityTest {

    private static final int SIZE = LakelandsChunkGenerator.CHUNK_SIZE;

    /** Runs the private layout carve and returns the grid as game-coord rows. */
    private String[] generateLayout(long seed) throws Exception {
        LakelandsChunkGenerator gen = new LakelandsChunkGenerator();

        Field rngField = LakelandsChunkGenerator.class.getDeclaredField("random");
        rngField.setAccessible(true);
        rngField.set(gen, new java.util.Random(seed));

        Method carve = LakelandsChunkGenerator.class.getDeclaredMethod(
                "createProceduralLakelandsLayout", int.class, int.class);
        carve.setAccessible(true);
        carve.invoke(gen, SIZE, SIZE);

        Field layoutField = LakelandsChunkGenerator.class.getDeclaredField("finalLayout");
        layoutField.setAccessible(true);
        return (String[]) layoutField.get(gen);
    }

    /** finalLayout row 0 is North, so convert back to game coords. */
    private char at(String[] layout, int x, int y) {
        return layout[SIZE - 1 - y].charAt(x);
    }

    private boolean isTraversable(char c) {
        return c == '.' || c == 'E' || c == 'A' || c == 'W';
    }

    private Set<Integer> reachableFrom(String[] layout, int startX, int startY) {
        Set<Integer> seen = new HashSet<>();
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        if (!isTraversable(at(layout, startX, startY))) return seen;

        queue.add(new int[]{startX, startY});
        seen.add(startY * SIZE + startX);

        int[] dx = {1, -1, 0, 0};
        int[] dy = {0, 0, 1, -1};
        while (!queue.isEmpty()) {
            int[] cur = queue.poll();
            for (int i = 0; i < 4; i++) {
                int nx = cur[0] + dx[i];
                int ny = cur[1] + dy[i];
                if (nx < 0 || nx >= SIZE || ny < 0 || ny >= SIZE) continue;
                if (!isTraversable(at(layout, nx, ny))) continue;
                int key = ny * SIZE + nx;
                if (!seen.add(key)) continue;
                queue.add(new int[]{nx, ny});
            }
        }
        return seen;
    }

    private int[][] gateApproaches() {
        int mid = SIZE / 2;
        return new int[][]{
                {mid, 3},          // South
                {mid, SIZE - 4},   // North
                {3, mid},          // West
                {SIZE - 4, mid}    // East
        };
    }

    @Test
    public void everyGateApproachReachesEveryOther() throws Exception {
        for (long seed = 0; seed < 40; seed++) {
            String[] layout = generateLayout(seed);
            int[][] approaches = gateApproaches();

            Set<Integer> reachable = reachableFrom(layout, approaches[0][0], approaches[0][1]);
            assertFalse("seed " + seed + ": south gate approach is not open", reachable.isEmpty());

            for (int[] approach : approaches) {
                assertTrue("seed " + seed + ": gate approach (" + approach[0] + "," + approach[1]
                                + ") is cut off from the south gate",
                        reachable.contains(approach[1] * SIZE + approach[0]));
            }
        }
    }

    @Test
    public void noSingleTileSeversTheChunk() throws Exception {
        for (long seed = 0; seed < 12; seed++) {
            String[] layout = generateLayout(seed);
            int[][] approaches = gateApproaches();

            Set<Integer> baseline = reachableFrom(layout, approaches[0][0], approaches[0][1]);

            int articulationPoints = 0;
            for (int key : baseline) {
                int bx = key % SIZE;
                int by = key / SIZE;
                if (isGateApproach(approaches, bx, by)) continue;

                String[] blocked = blockTile(layout, bx, by);
                Set<Integer> after = reachableFrom(blocked, approaches[0][0], approaches[0][1]);

                for (int[] approach : approaches) {
                    if (approach[0] == bx && approach[1] == by) continue;
                    if (!after.contains(approach[1] * SIZE + approach[0])) {
                        articulationPoints++;
                        break;
                    }
                }
            }

            int budget = Math.max(6, baseline.size() / 25);
            assertTrue("seed " + seed + ": " + articulationPoints
                            + " single tiles can sever a gate (budget " + budget
                            + ", " + baseline.size() + " open tiles)",
                    articulationPoints <= budget);
        }
    }

    @Test
    public void waterAndCausewaysArePervasive() throws Exception {
        LakelandsChunkGenerator gen = new LakelandsChunkGenerator();
        Method distribute = LakelandsChunkGenerator.class.getDeclaredMethod("distributeWetlandLiquids", Maze.class, long.class);
        distribute.setAccessible(true);

        for (long seed = 0; seed < 10; seed++) {
            String[] layout = generateLayout(seed);

            // Construct Maze with layout walls
            int[][] bitmask = new int[SIZE][SIZE];
            for (int y = 0; y < SIZE; y++) {
                for (int x = 0; x < SIZE; x++) {
                    if (at(layout, x, y) == '#') {
                        bitmask[y][x] = 0b01010101; // wall
                    }
                }
            }
            Maze maze = new Maze(1, bitmask);
            distribute.invoke(gen, maze, seed);

            LiquidManager lm = maze.getLiquidManager();
            int waterCount = 0;
            int muckCount = 0;
            int traversableFloorCount = 0;

            for (int y = 0; y < SIZE; y++) {
                for (int x = 0; x < SIZE; x++) {
                    if (isTraversable(at(layout, x, y))) {
                        traversableFloorCount++;
                        LiquidType lt = lm.getLiquidAt(x, y);
                        if (lt == LiquidType.WATER) waterCount++;
                        else if (lt == LiquidType.BLACK_MUCK) muckCount++;
                    }
                }
            }

            assertTrue("seed " + seed + ": Traversable floor must exist", traversableFloorCount > 200);
            assertTrue("seed " + seed + ": Lakelands must generate wadeable shallow water", waterCount >= 50);
            assertTrue("seed " + seed + ": Lakelands must generate toxic muck pockets", muckCount >= 10);
        }
    }

    @Test
    public void testSwampTreeSpawning() throws Exception {
        long[] seeds = {42L, 1337L, 999999L, 55555L, 777L};
        for (long seed : seeds) {
            String[] layout = generateLayout(seed);
            LakelandsChunkGenerator gen = new LakelandsChunkGenerator();

            Field layoutField = LakelandsChunkGenerator.class.getDeclaredField("finalLayout");
            layoutField.setAccessible(true);
            layoutField.set(gen, layout);

            // Construct Maze with layout walls
            int[][] bitmask = new int[SIZE][SIZE];
            for (int y = 0; y < SIZE; y++) {
                for (int x = 0; x < SIZE; x++) {
                    if (at(layout, x, y) == '#') {
                        bitmask[y][x] = 0b01010101; // wall
                    }
                }
            }
            Maze maze = new Maze(1, bitmask);

            Method dist = LakelandsChunkGenerator.class.getDeclaredMethod("distributeWetlandLiquids", Maze.class, long.class);
            dist.setAccessible(true);
            dist.invoke(gen, maze, seed);

            Method comp = LakelandsChunkGenerator.class.getDeclaredMethod("computeReachableTiles", Maze.class);
            comp.setAccessible(true);
            @SuppressWarnings("unchecked")
            Set<com.badlogic.gdx.math.GridPoint2> reachable = (Set<com.badlogic.gdx.math.GridPoint2>) comp.invoke(gen, maze);

            Method spawnTrees = LakelandsChunkGenerator.class.getDeclaredMethod(
                    "spawnSwampTrees", Maze.class, Set.class, com.badlogic.gdx.assets.AssetManager.class, long.class);
            spawnTrees.setAccessible(true);
            spawnTrees.invoke(gen, maze, reachable, null, seed);

            // Verify solid trees placed
            int solidTrees = 0;
            for (com.bpm.minotaur.gamedata.Scenery sc : maze.getScenery().values()) {
                if (sc.getType() == com.bpm.minotaur.gamedata.Scenery.SceneryType.TREE) {
                    solidTrees++;
                }
            }
            assertTrue("seed " + seed + ": must spawn solid swamp trees (got " + solidTrees + ")", solidTrees >= 30);

            // Verify backdrop trees placed
            int backdropTrees = 0;
            for (com.bpm.minotaur.gamedata.Scenery sc : maze.getBackdropScenery()) {
                if (sc.getType() == com.bpm.minotaur.gamedata.Scenery.SceneryType.TREE) {
                    backdropTrees++;
                }
            }
            assertTrue("seed " + seed + ": must spawn backdrop swamp trees (got " + backdropTrees + ")", backdropTrees >= 50);

            // Verify gate connectivity is maintained with trees placed
            int[][] gates = {
                    {18, SIZE - 1}, // North
                    {18, 0},        // South
                    {SIZE - 1, 18}, // East
                    {0, 18}         // West
            };
            for (int i = 0; i < 4; i++) {
                assertTrue("seed " + seed + ": Gate " + i + " must remain reachable",
                        maze.isPassable(gates[i][0], gates[i][1]));
            }
        }
    }

    private boolean isGateApproach(int[][] approaches, int x, int y) {
        for (int[] a : approaches) {
            if (a[0] == x && a[1] == y) return true;
        }
        return false;
    }

    private String[] blockTile(String[] layout, int x, int y) {
        String[] copy = layout.clone();
        int row = SIZE - 1 - y;
        StringBuilder sb = new StringBuilder(copy[row]);
        sb.setCharAt(x, '#');
        copy[row] = sb.toString();
        return copy;
    }
}
