package com.bpm.minotaur.generation;

import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.Scenery;
import com.bpm.minotaur.gamedata.liquid.LiquidManager;
import com.bpm.minotaur.gamedata.liquid.LiquidType;
import com.bpm.minotaur.lighting.LightSource;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

import static org.junit.Assert.*;

/**
 * Validates connectivity guarantees, gate traversability, permafrost hazard distribution,
 * and campsite spawning for the Siberian Tundra Biome.
 */
public class TundraConnectivityTest {

    private static final int SIZE = TundraChunkGenerator.CHUNK_SIZE;

    /** Runs the private layout carve and returns the grid as game-coord rows. */
    private String[] generateLayout(long seed) throws Exception {
        TundraChunkGenerator gen = new TundraChunkGenerator();

        Field rngField = TundraChunkGenerator.class.getDeclaredField("random");
        rngField.setAccessible(true);
        rngField.set(gen, new Random(seed));

        Method carve = TundraChunkGenerator.class.getDeclaredMethod(
                "createProceduralTundraLayout", int.class, int.class);
        carve.setAccessible(true);
        carve.invoke(gen, SIZE, SIZE);

        Field layoutField = TundraChunkGenerator.class.getDeclaredField("finalLayout");
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
    public void permafrostHazardsGenerated() throws Exception {
        TundraChunkGenerator gen = new TundraChunkGenerator();
        Method distribute = TundraChunkGenerator.class.getDeclaredMethod("distributeTundraHazards", Maze.class, long.class);
        distribute.setAccessible(true);

        for (long seed = 0; seed < 10; seed++) {
            String[] layout = generateLayout(seed);

            int[][] bitmask = new int[SIZE][SIZE];
            for (int y = 0; y < SIZE; y++) {
                for (int x = 0; x < SIZE; x++) {
                    if (at(layout, x, y) == '#') {
                        bitmask[y][x] = 0b01010101;
                    }
                }
            }
            Maze maze = new Maze(1, bitmask);
            distribute.invoke(gen, maze, seed);

            LiquidManager lm = maze.getLiquidManager();
            int packedIceCount = 0;
            int slushCount = 0;
            int traversableFloorCount = 0;

            for (int y = 0; y < SIZE; y++) {
                for (int x = 0; x < SIZE; x++) {
                    if (isTraversable(at(layout, x, y))) {
                        traversableFloorCount++;
                        LiquidType lt = lm.getLiquidAt(x, y);
                        if (lt == LiquidType.PACKED_ICE) packedIceCount++;
                        else if (lt == LiquidType.FREEZING_SLUSH) slushCount++;
                    }
                }
            }

            assertTrue("seed " + seed + ": Traversable floor must exist", traversableFloorCount > 200);
            assertTrue("seed " + seed + ": Tundra must generate frozen lake packed ice", packedIceCount >= 40);
            assertTrue("seed " + seed + ": Tundra must generate freezing slush hazards", slushCount >= 20);
        }
    }

    @Test
    public void campsitesAndCampfiresSpawned() throws Exception {
        long[] seeds = {42L, 101L, 999L};
        for (long seed : seeds) {
            TundraChunkGenerator gen = new TundraChunkGenerator();

            Field rngField = TundraChunkGenerator.class.getDeclaredField("random");
            rngField.setAccessible(true);
            rngField.set(gen, new Random(seed));

            Method carve = TundraChunkGenerator.class.getDeclaredMethod(
                    "createProceduralTundraLayout", int.class, int.class);
            carve.setAccessible(true);
            carve.invoke(gen, SIZE, SIZE);

            Field layoutField = TundraChunkGenerator.class.getDeclaredField("finalLayout");
            layoutField.setAccessible(true);
            String[] layout = (String[]) layoutField.get(gen);

            int[][] bitmask = new int[SIZE][SIZE];
            for (int y = 0; y < SIZE; y++) {
                for (int x = 0; x < SIZE; x++) {
                    if (at(layout, x, y) == '#') {
                        bitmask[y][x] = 0b01010101;
                    }
                }
            }
            Maze maze = new Maze(1, bitmask);

            Method comp = TundraChunkGenerator.class.getDeclaredMethod("computeReachableTiles", Maze.class);
            comp.setAccessible(true);
            @SuppressWarnings("unchecked")
            Set<GridPoint2> reachable = (Set<GridPoint2>) comp.invoke(gen, maze);

            Method spawnCamps = TundraChunkGenerator.class.getDeclaredMethod(
                    "spawnCampsites", Maze.class, Set.class, com.bpm.minotaur.gamedata.item.ItemDataManager.class,
                    com.badlogic.gdx.assets.AssetManager.class, long.class);
            spawnCamps.setAccessible(true);
            spawnCamps.invoke(gen, maze, reachable, null, null, seed);

            // Verify campfire scenery placed
            int campfireSceneryCount = 0;
            int woodPileCount = 0;
            int shackCount = 0;
            for (Scenery sc : maze.getScenery().values()) {
                String path = sc.getTexturePath();
                if (path != null) {
                    if (path.contains("campfire_01")) campfireSceneryCount++;
                    if (path.contains("wood_pile_01")) woodPileCount++;
                    if (path.contains("ice_hut_01")) shackCount++;
                }
            }

            assertTrue("seed " + seed + ": must spawn campsite campfires", campfireSceneryCount >= 1);
            assertTrue("seed " + seed + ": must spawn wood piles", woodPileCount >= 1);

            // Verify campfire light source registered
            int campfireLights = 0;
            for (LightSource ls : maze.getLights()) {
                if (ls.getId().contains("campfire")) {
                    campfireLights++;
                }
            }
            assertTrue("seed " + seed + ": campfire light source must be registered", campfireLights >= 1);
        }
    }

    @Test
    public void testTundraPineTreesSpawning() throws Exception {
        long[] seeds = {42L, 1337L, 55555L};
        for (long seed : seeds) {
            TundraChunkGenerator gen = new TundraChunkGenerator();

            Field rngField = TundraChunkGenerator.class.getDeclaredField("random");
            rngField.setAccessible(true);
            rngField.set(gen, new Random(seed));

            Method carve = TundraChunkGenerator.class.getDeclaredMethod(
                    "createProceduralTundraLayout", int.class, int.class);
            carve.setAccessible(true);
            carve.invoke(gen, SIZE, SIZE);

            Field layoutField = TundraChunkGenerator.class.getDeclaredField("finalLayout");
            layoutField.setAccessible(true);
            String[] layout = (String[]) layoutField.get(gen);

            int[][] bitmask = new int[SIZE][SIZE];
            for (int y = 0; y < SIZE; y++) {
                for (int x = 0; x < SIZE; x++) {
                    if (at(layout, x, y) == '#') {
                        bitmask[y][x] = 0b01010101;
                    }
                }
            }
            Maze maze = new Maze(1, bitmask);

            Method comp = TundraChunkGenerator.class.getDeclaredMethod("computeReachableTiles", Maze.class);
            comp.setAccessible(true);
            @SuppressWarnings("unchecked")
            Set<GridPoint2> reachable = (Set<GridPoint2>) comp.invoke(gen, maze);

            Method spawnTrees = TundraChunkGenerator.class.getDeclaredMethod(
                    "spawnTundraTrees", Maze.class, Set.class, com.badlogic.gdx.assets.AssetManager.class, long.class);
            spawnTrees.setAccessible(true);
            spawnTrees.invoke(gen, maze, reachable, null, seed);

            // Verify solid trees placed
            int solidTrees = 0;
            for (Scenery sc : maze.getScenery().values()) {
                if (sc.getType() == Scenery.SceneryType.TREE && sc.isImpassable()) {
                    solidTrees++;
                }
            }
            assertTrue("seed " + seed + ": must spawn solid pines (got " + solidTrees + ")", solidTrees >= 20);

            // Verify backdrop trees placed on moraine crags
            int backdropTrees = 0;
            for (Scenery sc : maze.getBackdropScenery()) {
                if (sc.getType() == Scenery.SceneryType.TREE) {
                    backdropTrees++;
                }
            }
            assertTrue("seed " + seed + ": must spawn backdrop pines (got " + backdropTrees + ")", backdropTrees >= 20);
        }
    }

    @Test
    public void testScatterGroundCoverSpawning() throws Exception {
        long[] seeds = {777L, 888L, 999L};
        for (long seed : seeds) {
            TundraChunkGenerator gen = new TundraChunkGenerator();

            Field rngField = TundraChunkGenerator.class.getDeclaredField("random");
            rngField.setAccessible(true);
            rngField.set(gen, new Random(seed));

            Method carve = TundraChunkGenerator.class.getDeclaredMethod(
                    "createProceduralTundraLayout", int.class, int.class);
            carve.setAccessible(true);
            carve.invoke(gen, SIZE, SIZE);

            Field layoutField = TundraChunkGenerator.class.getDeclaredField("finalLayout");
            layoutField.setAccessible(true);
            String[] layout = (String[]) layoutField.get(gen);

            int[][] bitmask = new int[SIZE][SIZE];
            for (int y = 0; y < SIZE; y++) {
                for (int x = 0; x < SIZE; x++) {
                    if (at(layout, x, y) == '#') {
                        bitmask[y][x] = 0b01010101;
                    }
                }
            }
            Maze maze = new Maze(1, bitmask);

            Method comp = TundraChunkGenerator.class.getDeclaredMethod("computeReachableTiles", Maze.class);
            comp.setAccessible(true);
            @SuppressWarnings("unchecked")
            Set<GridPoint2> reachable = (Set<GridPoint2>) comp.invoke(gen, maze);

            Method scatter = TundraChunkGenerator.class.getDeclaredMethod(
                    "scatterGroundCover", Maze.class, Set.class, com.badlogic.gdx.assets.AssetManager.class, long.class);
            scatter.setAccessible(true);
            scatter.invoke(gen, maze, reachable, null, seed);

            int scatterCount = 0;
            Set<String> observedScatter = new HashSet<>();
            for (Scenery sc : maze.getBackdropScenery()) {
                if (sc.getTexturePath() != null) {
                    observedScatter.add(sc.getTexturePath());
                    scatterCount++;
                }
            }
            for (Scenery sc : maze.getScenery().values()) {
                if (sc.getTexturePath() != null && (sc.getTexturePath().contains("log_snow_01") || sc.getTexturePath().contains("pine_stump_01"))) {
                    observedScatter.add(sc.getTexturePath());
                    scatterCount++;
                }
            }

            assertTrue("seed " + seed + ": must spawn scatter props (got " + scatterCount + ")", scatterCount >= 20);
            assertTrue("seed " + seed + ": must contain snow mounds or stumps or logs",
                    observedScatter.contains("images/tundra/snow_mound_01.png")
                            || observedScatter.contains("images/tundra/pine_stump_01.png")
                            || observedScatter.contains("images/tundra/log_snow_01.png"));
        }
    }

    @Test
    public void testFirewoodHarvestingAndUsage() {
        int[][] bitmask = new int[10][10];
        Maze maze = new Maze(1, bitmask);
        com.bpm.minotaur.managers.GameEventManager eventManager = new com.bpm.minotaur.managers.GameEventManager();
        com.bpm.minotaur.gamedata.player.Player player = new com.bpm.minotaur.gamedata.player.Player(5.0f, 5.0f);
        player.setFacing(com.bpm.minotaur.gamedata.Direction.NORTH);
        player.getStats().setBodyTemperature(30.0f); // chilly

        // Place harvestable fallen log in front of player at (5, 6)
        Scenery log = new Scenery(Scenery.SceneryType.PROP, 5, 6, "images/tundra/log_snow_01.png");
        log.setImpassable(true);
        maze.addScenery(log);

        // Player bumps into log
        player.moveForward(maze, eventManager, com.bpm.minotaur.gamedata.GameMode.ADVANCED);

        assertTrue("Harvesting log sets harvestedScenery flag", player.consumeHarvestedScenery());
        assertFalse("Log removed from impassable scenery", maze.getScenery().containsKey(new GridPoint2(5, 6)));
        assertTrue("Player inventory must contain harvested Firewood",
                player.getInventory().hasItemOfType(com.bpm.minotaur.gamedata.item.Item.ItemType.FIREWOOD));

        com.bpm.minotaur.gamedata.item.Item firewood = null;
        for (com.bpm.minotaur.gamedata.item.Item item : player.getInventory().getAllItems()) {
            if (item != null && item.getType() == com.bpm.minotaur.gamedata.item.Item.ItemType.FIREWOOD) {
                firewood = item;
                break;
            }
        }
        assertNotNull("Player inventory must contain harvested Firewood", firewood);

        // Test field campfire creation by using firewood
        player.useItem(firewood, eventManager, null, maze);
        boolean foundFieldCampfireLight = false;
        for (LightSource ls : maze.getLights()) {
            if (ls.getId() != null && ls.getId().contains("tundra_campfire_field")) {
                foundFieldCampfireLight = true;
                break;
            }
        }
        assertTrue("Using firewood in open field creates campfire light source", foundFieldCampfireLight);
        assertTrue("Field campfire increases body temperature", player.getStats().getBodyTemperature() > 30.0f);

        // Test kindling campfire when near existing campfire
        com.bpm.minotaur.gamedata.item.Item firewood2 = com.bpm.minotaur.gamedata.item.Item.createFirewood(5, 5);
        player.getInventory().addItem(firewood2);
        player.getStats().setBodyTemperature(32.0f);

        player.useItem(firewood2, eventManager, null, maze);
        assertEquals("Kindling campfire near warmth restores body temperature to 37.0°C", 37.0f, player.getStats().getBodyTemperature(), 0.01f);
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
