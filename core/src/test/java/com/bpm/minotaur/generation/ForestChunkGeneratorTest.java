package com.bpm.minotaur.generation;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.gamedata.*;
import com.bpm.minotaur.rendering.RetroTheme;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.*;

import static org.junit.Assert.*;

public class ForestChunkGeneratorTest {

    @Before
    public void setUp() {
        if (Gdx.app == null) {
            Gdx.app = (Application) Proxy.newProxyInstance(
                    Application.class.getClassLoader(),
                    new Class<?>[]{Application.class},
                    (proxy, method, args) -> null
            );
        }
    }

    private Maze generateTestChunk(long seed) {
        ForestChunkGenerator generator = new ForestChunkGenerator();
        return generator.generateChunk(
                new GridPoint2(2, 2),
                1,
                1,
                Difficulty.MEDIUM,
                GameMode.ADVANCED,
                RetroTheme.FOREST_THEME,
                RetroTheme.STANDARD_THEME,
                null,
                null,
                null,
                null,
                null,
                seed,
                0
        );
    }

    @Test
    public void testForestChunkDimensionsAndBiome() {
        Maze maze = generateTestChunk(42L);

        assertEquals("Forest chunk width must be 36", 36, maze.getWidth());
        assertEquals("Forest chunk height must be 36", 36, maze.getHeight());
        assertEquals("Biome must be FOREST", Biome.FOREST, maze.getBiome());
        assertEquals("Theme must be FOREST_THEME", RetroTheme.FOREST_THEME, maze.getTheme());
    }

    @Test
    public void testCardinalTransitionGates() {
        Maze maze = generateTestChunk(100L);

        assertNotNull("Maze gates map must not be null", maze.getGates());
        assertEquals("Forest must have exactly 4 cardinal transition gates", 4, maze.getGates().size());

        GridPoint2 northPos = new GridPoint2(18, 35);
        GridPoint2 southPos = new GridPoint2(18, 0);
        GridPoint2 eastPos = new GridPoint2(35, 18);
        GridPoint2 westPos = new GridPoint2(0, 18);

        Gate northGate = maze.getGates().get(northPos);
        Gate southGate = maze.getGates().get(southPos);
        Gate eastGate = maze.getGates().get(eastPos);
        Gate westGate = maze.getGates().get(westPos);

        assertNotNull("North transition gate must exist at (18, 35)", northGate);
        assertNotNull("South transition gate must exist at (18, 0)", southGate);
        assertNotNull("East transition gate must exist at (35, 18)", eastGate);
        assertNotNull("West transition gate must exist at (0, 18)", westGate);

        assertEquals("North gate must be NORTH_SOUTH", Door.Orientation.NORTH_SOUTH, northGate.getOrientation());
        assertEquals("South gate must be NORTH_SOUTH", Door.Orientation.NORTH_SOUTH, southGate.getOrientation());
        assertEquals("East gate must be EAST_WEST", Door.Orientation.EAST_WEST, eastGate.getOrientation());
        assertEquals("West gate must be EAST_WEST", Door.Orientation.EAST_WEST, westGate.getOrientation());
    }

    @Test
    public void testGateConnectivityAndTraversability() {
        // Test connectivity across multiple seeds to ensure robustness
        long[] seeds = {101L, 202L, 303L, 404L, 505L};

        for (long seed : seeds) {
            Maze maze = generateTestChunk(seed);

            // Breadth-first search from South gate (18, 0)
            GridPoint2 start = new GridPoint2(18, 0);
            Set<GridPoint2> visited = new HashSet<>();
            Queue<GridPoint2> queue = new LinkedList<>();

            visited.add(start);
            queue.add(start);

            while (!queue.isEmpty()) {
                GridPoint2 cur = queue.poll();
                for (Direction dir : Direction.values()) {
                    if (maze.isWallBlocking(cur.x, cur.y, dir)) continue;

                    int nx = cur.x + (int) dir.getVector().x;
                    int ny = cur.y + (int) dir.getVector().y;
                    if (nx < 0 || nx >= maze.getWidth() || ny < 0 || ny >= maze.getHeight()) continue;

                    GridPoint2 next = new GridPoint2(nx, ny);
                    if (visited.contains(next)) continue;

                    // Impassable scenery (trees, rocks) blocks traversal
                    Scenery sc = maze.getScenery().get(next);
                    if (sc != null && sc.isImpassable()) continue;

                    visited.add(next);
                    queue.add(next);
                }
            }

            GridPoint2 northPos = new GridPoint2(18, 35);
            GridPoint2 eastPos = new GridPoint2(35, 18);
            GridPoint2 westPos = new GridPoint2(0, 18);

            boolean centerReachable = false;
            for (int dy = -2; dy <= 2; dy++) {
                for (int dx = -2; dx <= 2; dx++) {
                    if (visited.contains(new GridPoint2(18 + dx, 18 + dy))) {
                        centerReachable = true;
                        break;
                    }
                }
            }

            assertTrue("Seed " + seed + ": Central Glade area around (18, 18) must be reachable from South Gate",
                    centerReachable);
            assertTrue("Seed " + seed + ": North Gate (18, 35) must be reachable from South Gate",
                    visited.contains(northPos));
            assertTrue("Seed " + seed + ": East Gate (35, 18) must be reachable from South Gate",
                    visited.contains(eastPos));
            assertTrue("Seed " + seed + ": West Gate (0, 18) must be reachable from South Gate",
                    visited.contains(westPos));
        }
    }

    @Test
    public void testCentralGladeAndLandmarks() {
        Maze maze = generateTestChunk(777L);

        // Verify Central Glade around (18, 18) has open walkable space
        int openTilesInCenter = 0;
        for (int dy = -3; dy <= 3; dy++) {
            for (int dx = -3; dx <= 3; dx++) {
                int cx = 18 + dx;
                int cy = 18 + dy;
                Scenery sc = maze.getScenery().get(new GridPoint2(cx, cy));
                if (sc == null || !sc.isImpassable()) {
                    openTilesInCenter++;
                }
            }
        }
        assertTrue("Central Glade must have substantial open walkable space, found: " + openTilesInCenter,
                openTilesInCenter >= 15);

        // Verify themed landmark props exist in the scenery
        boolean hasLandmarkProp = false;
        for (Scenery sc : maze.getScenery().values()) {
            if (sc.getPropId() != null) {
                String id = sc.getPropId();
                if (id.equals("campfire") || id.equals("runestone") || id.equals("ruined_pillar") ||
                    id.equals("stump_pine") || id.equals("log_pile") || id.equals("log_fallen")) {
                    hasLandmarkProp = true;
                    break;
                }
            }
        }
        assertTrue("Forest chunk must contain central landmark props", hasLandmarkProp);
    }

    @Test
    public void testSceneryVarietyAndNoCorridorTemplates() {
        Maze maze = generateTestChunk(888L);

        int treeCount = 0;
        int rockCount = 0;
        int bushCount = 0;
        int propCount = 0;
        int flippedCount = 0;

        for (Scenery sc : maze.getScenery().values()) {
            if (sc.isFlippedX()) flippedCount++;
            switch (sc.getType()) {
                case TREE: treeCount++; break;
                case ROCK: rockCount++; break;
                case BUSH: bushCount++; break;
                case PROP: propCount++; break;
                default: break;
            }
        }

        assertTrue("Forest must have abundant trees, found: " + treeCount, treeCount > 50);
        assertTrue("Forest must have rocks/boulders, found: " + rockCount, rockCount > 10);
        assertTrue("Forest must have bushes/undergrowth, found: " + bushCount, bushCount > 5);
        assertTrue("Forest must have props, found: " + propCount, propCount > 0);
        assertTrue("Scenery must utilize horizontal flipping for visual variety, flipped: " + flippedCount,
                flippedCount > 20);

        // Verify interior (5 <= x <= 30, 5 <= y <= 30) has ZERO wall bitmasks
        // Wall bitmasks in Tarmin2 represent stone corridor walls. Forest interior should use scenery billboards, not stone walls!
        int interiorWallCount = 0;
        for (int y = 5; y <= 30; y++) {
            for (int x = 5; x <= 30; x++) {
                if (maze.getWallDataAt(x, y) != 0) {
                    interiorWallCount++;
                }
            }
        }
        assertEquals("Forest interior must have ZERO stone wall bitmasks (natural clearing/trees only)",
                0, interiorWallCount);
    }

    @Test
    public void testDownLaddersAreReachable() {
        Maze maze = generateTestChunk(999L);

        int downLadders = 0;
        for (Ladder ladder : maze.getLadders().values()) {
            if (ladder.getType() == Ladder.LadderType.DOWN) {
                downLadders++;
                GridPoint2 pos = new GridPoint2((int) ladder.getPosition().x, (int) ladder.getPosition().y);

                // Ensure ladder is not placed on impassable scenery
                Scenery sc = maze.getScenery().get(pos);
                assertFalse("Ladder at " + pos + " must not be blocked by impassable scenery",
                        sc != null && sc.isImpassable());
            }
        }

        assertTrue("Forest chunk must contain 2 to 3 DOWN ladders, found: " + downLadders,
                downLadders >= 2 && downLadders <= 3);
    }
}
