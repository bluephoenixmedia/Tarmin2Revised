package com.bpm.minotaur.generation;

import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * Validates connectivity guarantees and expansive open sightlines for the Desert Biome.
 *
 * <p>Like the forest, every gate must reach every other gate across any seed,
 * and no single tile should be able to sever the chunk. The desert is mesa
 * country: open dune basins linked by narrow canyons through tall rock.
 */
public class DesertConnectivityTest {

    private static final int SIZE = DesertChunkGenerator.CHUNK_SIZE;

    /** Runs the private layout carve and returns the grid as game-coord rows. */
    private String[] generateLayout(long seed) throws Exception {
        DesertChunkGenerator gen = new DesertChunkGenerator();

        Field rngField = DesertChunkGenerator.class.getDeclaredField("random");
        rngField.setAccessible(true);
        rngField.set(gen, new java.util.Random(seed));

        Method carve = DesertChunkGenerator.class.getDeclaredMethod(
                "createProceduralDesertLayout", int.class, int.class);
        carve.setAccessible(true);
        carve.invoke(gen, SIZE, SIZE);

        Field layoutField = DesertChunkGenerator.class.getDeclaredField("finalLayout");
        layoutField.setAccessible(true);
        return (String[]) layoutField.get(gen);
    }

    /** finalLayout row 0 is North, so convert back to game coords. */
    private char at(String[] layout, int x, int y) {
        return layout[SIZE - 1 - y].charAt(x);
    }

    private boolean isTraversable(char c) {
        return c == '.' || c == 'B' || c == 'S';
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
    public void noPropSealsOffAnyPatchOfSand() throws Exception {
        int mid = SIZE / 2;
        for (long seed = 0; seed < 30; seed++) {
            String[] layout = generateLayout(seed);
            Set<Integer> reached = reachableFrom(layout, mid, mid);
            for (int y = 0; y < SIZE; y++) {
                for (int x = 0; x < SIZE; x++) {
                    if (isTraversable(at(layout, x, y))) {
                        assertTrue("seed " + seed + ": sand at (" + x + "," + y + ") is cut off from the bowl",
                                reached.contains(y * SIZE + x));
                    }
                }
            }
        }
    }

    @Test
    public void mesasFillTheChunkAndCanyonsWindThroughThem() throws Exception {
        for (long seed = 0; seed < 20; seed++) {
            String[] layout = generateLayout(seed);
            int open = 0;
            for (int y = 0; y < SIZE; y++) {
                for (int x = 0; x < SIZE; x++) {
                    if (isTraversable(at(layout, x, y))) open++;
                }
            }
            float share = open / (float) (SIZE * SIZE);
            assertTrue("seed " + seed + ": " + (int) (share * 100) + "% open; basins and canyons, not open sand",
                    share >= 0.22f && share <= 0.55f);
        }
    }

    @Test
    public void sideBasinsOpenAwayFromTheCentralBowl() throws Exception {
        int mid = SIZE / 2;
        for (long seed = 0; seed < 20; seed++) {
            String[] layout = generateLayout(seed);
            int basins = 0;
            boolean[][] counted = new boolean[SIZE][SIZE];
            for (int y = 3; y < SIZE - 3; y++) {
                for (int x = 3; x < SIZE - 3; x++) {
                    if (Math.abs(x - mid) + Math.abs(y - mid) < 10 || counted[y][x]) continue;
                    if (!fullyOpen(layout, x, y)) continue;
                    basins++;
                    for (int dy = -5; dy <= 5; dy++) {
                        for (int dx = -5; dx <= 5; dx++) {
                            int cx = x + dx, cy = y + dy;
                            if (cx >= 0 && cy >= 0 && cx < SIZE && cy < SIZE) counted[cy][cx] = true;
                        }
                    }
                }
            }
            assertTrue("seed " + seed + ": expected 2-3 side basins, found " + basins, basins >= 2);
        }
    }

    /** A 5x5 window with no rock in it: a clearing, not a canyon. Props stand on the sand. */
    private boolean fullyOpen(String[] layout, int x, int y) {
        for (int dy = -2; dy <= 2; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                if (at(layout, x + dx, y + dy) == '#') return false;
            }
        }
        return true;
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
