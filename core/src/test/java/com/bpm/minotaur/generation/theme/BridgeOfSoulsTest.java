package com.bpm.minotaur.generation.theme;

import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.Scenery;
import com.bpm.minotaur.gamedata.liquid.LiquidType;
import com.bpm.minotaur.gamedata.monster.Monster;
import org.junit.Test;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * The Bridge of Souls, as agreed 2026-10-10: an island in a chasm of molten fire, a two-tile bridge
 * to it from every gate, the undead holding the bridges and the Bringer of Death alone on his
 * island, which he will not leave.
 */
public class BridgeOfSoulsTest {

    private static Maze bridge(long seed) {
        Maze maze = CarveGeometryTest.mazeChunk();
        ChunkThemeDecorator.decorate(maze, ChunkTheme.BRIDGE_OF_SOULS, seed, null, null, null);
        return maze;
    }

    private static boolean fire(Maze maze, int x, int y) {
        return maze.getLiquidManager().getLiquidAt(x, y) == LiquidType.MOLTEN_FIRE;
    }

    /** Tiles reachable on foot from {@code from}, by walls, fire and props; monsters move, so they do not count. */
    private static Set<GridPoint2> walk(Maze maze, GridPoint2 from) {
        Set<GridPoint2> seen = new HashSet<>();
        ArrayDeque<GridPoint2> queue = new ArrayDeque<>();
        seen.add(from);
        queue.add(from);
        com.bpm.minotaur.gamedata.Direction[] dirs = com.bpm.minotaur.gamedata.Direction.values();
        while (!queue.isEmpty()) {
            GridPoint2 at = queue.poll();
            for (com.bpm.minotaur.gamedata.Direction d : dirs) {
                if (maze.isWallBlocking(at.x, at.y, d)) continue;
                GridPoint2 n = new GridPoint2(at.x + (int) d.getVector().x, at.y + (int) d.getVector().y);
                if (n.x < 1 || n.y < 1 || n.x > maze.getWidth() - 2 || n.y > maze.getHeight() - 2) continue;
                if (fire(maze, n.x, n.y)) continue;
                Scenery s = maze.getScenery().get(n);
                if (s != null && s.isImpassable()) continue;
                if (seen.add(n)) queue.add(n);
            }
        }
        return seen;
    }

    private static Monster bringer(Maze maze) {
        for (Monster m : maze.getMonsters().values()) if (m.isThemeChampion()) return m;
        return null;
    }

    @Test
    public void aChasmOfFireFillsTheChunkAroundTheIsland() {
        Maze maze = bridge(3L);
        int fire = 0, interior = 0;
        for (int y = 1; y < maze.getHeight() - 1; y++) {
            for (int x = 1; x < maze.getWidth() - 1; x++) {
                interior++;
                if (fire(maze, x, y)) {
                    fire++;
                    assertFalse("nothing stands in the fire at " + x + "," + y,
                            maze.getMonsters().containsKey(new GridPoint2(x, y)));
                }
            }
        }
        assertTrue("the chasm is most of the chunk: " + fire + " of " + interior, fire * 2 > interior);
    }

    @Test
    public void everyGateHasItsBridgeToTheIslandTwoTilesWide() {
        for (long seed = 1; seed <= 5; seed++) {
            Maze maze = bridge(seed);
            Monster keeper = bringer(maze);
            assertNotNull(keeper);
            GridPoint2 island = new GridPoint2((int) keeper.getPosition().x, (int) keeper.getPosition().y);
            for (GridPoint2 gate : maze.getGates().keySet()) {
                int dx = gate.x == 0 ? 1 : gate.x == maze.getWidth() - 1 ? -1 : 0;
                int dy = gate.y == 0 ? 1 : gate.y == maze.getHeight() - 1 ? -1 : 0;
                GridPoint2 arrival = new GridPoint2(gate.x + dx, gate.y + dy);
                assertTrue("from the gate at " + gate + " a bridge reaches the island",
                        walk(maze, arrival).contains(island));
                // Two abreast: a step onto the bridge, and the tile beside it is bridge too.
                GridPoint2 onto = new GridPoint2(arrival.x + dx * 4, arrival.y + dy * 4);
                GridPoint2 beside = new GridPoint2(onto.x + (dx == 0 ? 1 : 0), onto.y + (dy == 0 ? 1 : 0));
                assertFalse(fire(maze, onto.x, onto.y));
                assertFalse("the bridge is two tiles wide at " + onto, fire(maze, beside.x, beside.y));
            }
        }
    }

    @Test
    public void theBringerHoldsHisIslandAloneAndTheUndeadHoldTheBridges() {
        Maze maze = bridge(7L);
        Monster keeper = bringer(maze);
        assertEquals(Monster.MonsterType.BRINGER_OF_DEATH, keeper.getType());
        assertNotNull("he is tethered", keeper.getTether());
        int[] t = keeper.getTether();
        int onBridges = 0;
        for (Monster m : maze.getMonsters().values()) {
            if (m == keeper) continue;
            int x = (int) m.getPosition().x, y = (int) m.getPosition().y;
            boolean onIsland = x >= t[0] && x <= t[2] && y >= t[1] && y <= t[3];
            assertFalse("the island is his alone: " + m.getType() + " at " + x + "," + y, onIsland);
            onBridges++;
        }
        assertTrue("the undead hold the bridges: " + onBridges, onBridges > 0);
        // He cannot step off the island onto a bridge.
        assertFalse(keeper.mayStandAt(t[2] + 1, (t[1] + t[3]) / 2));
    }

    @Test
    public void railingsLineTheBridgesAndArchesMarkWhereTheyLand() {
        Maze maze = bridge(9L);
        int rails = 0, arches = 0;
        for (Scenery s : maze.getScenery().values()) {
            if ("bone_spike_rail".equals(s.getPropId()) || "arena_chain".equals(s.getPropId())) rails++;
            if ("stone_arch".equals(s.getPropId())) arches++;
        }
        assertTrue("rails along the bridges: " + rails, rails >= 8);
        assertEquals("an arch where each bridge lands", maze.getGates().size(), arches);
    }
}
