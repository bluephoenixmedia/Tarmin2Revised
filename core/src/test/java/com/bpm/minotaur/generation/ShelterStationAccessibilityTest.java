package com.bpm.minotaur.generation;

import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.gamedata.Difficulty;
import com.bpm.minotaur.gamedata.GameMode;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.progression.ShelterAltar;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * Tests verifying that every shelter station (in both home shelter and outpost shelters)
 * is reachable from the shelter door and has clear adjacent open floor from which the player
 * can interact, including the Archive Lectern, War Table, Crafting Bench, and Training Dummy.
 */
public class ShelterStationAccessibilityTest {

    @Before
    public void setUp() {
        if (com.badlogic.gdx.Gdx.app == null) {
            com.badlogic.gdx.Gdx.app = (com.badlogic.gdx.Application) java.lang.reflect.Proxy.newProxyInstance(
                    com.badlogic.gdx.Application.class.getClassLoader(),
                    new Class<?>[]{com.badlogic.gdx.Application.class},
                    (proxy, method, args) -> null);
        }
        ShelterAltar.getInstance().reset();
    }

    private String[] homeShelterLayout() throws Exception {
        MazeChunkGenerator gen = new MazeChunkGenerator();
        Field f = MazeChunkGenerator.class.getDeclaredField("homeTile");
        f.setAccessible(true);
        return (String[]) f.get(gen);
    }

    @Test
    public void testHomeShelterStationsHaveAdjacentOpenFloor() throws Exception {
        String[] layout = homeShelterLayout();

        char[] stationChars = {'L', 'A', 'R', 'T', 'F', 'B', 'C', 'N'};
        for (char stationChar : stationChars) {
            boolean found = false;
            for (int y = 0; y < layout.length; y++) {
                for (int x = 0; x < layout[y].length(); x++) {
                    if (layout[y].charAt(x) == stationChar) {
                        found = true;
                        // Station must have at least one adjacent open floor '.'
                        boolean hasOpenAdjacent = (x + 1 < layout[y].length() && layout[y].charAt(x + 1) == '.')
                                || (x - 1 >= 0 && layout[y].charAt(x - 1) == '.')
                                || (y + 1 < layout.length && layout[y + 1].charAt(x) == '.')
                                || (y - 1 >= 0 && layout[y - 1].charAt(x) == '.');
                        assertTrue("Home shelter station '" + stationChar + "' at (" + x + "," + y
                                + ") must have an adjacent open floor tile '.' to approach from", hasOpenAdjacent);
                    }
                }
            }
            assertTrue("Station '" + stationChar + "' must exist in home shelter layout", found);
        }
    }

    @Test
    public void testCraftingBenchDoesNotBlockArchiveLectern() throws Exception {
        String[] layout = homeShelterLayout();

        GridPoint2 lecternPos = null;
        GridPoint2 craftingPos = null;

        for (int y = 0; y < layout.length; y++) {
            for (int x = 0; x < layout[y].length(); x++) {
                if (layout[y].charAt(x) == 'R') lecternPos = new GridPoint2(x, y);
                if (layout[y].charAt(x) == 'N') craftingPos = new GridPoint2(x, y);
            }
        }

        assertNotNull("Lectern 'R' must be in home shelter", lecternPos);
        assertNotNull("Crafting Bench 'N' must be in home shelter", craftingPos);

        // N must not be directly in front of R (y+1 or y-1 on same x)
        assertFalse("Crafting Bench must not be on the same column adjacent to Lectern",
                craftingPos.x == lecternPos.x && Math.abs(craftingPos.y - lecternPos.y) == 1);
    }

    @Test
    public void testAllHomeShelterFloorsReachableFromDoor() throws Exception {
        String[] layout = homeShelterLayout();

        GridPoint2 door = null;
        for (int y = 0; y < layout.length && door == null; y++) {
            int x = layout[y].indexOf('D');
            if (x >= 0) door = new GridPoint2(x, y);
        }
        assertNotNull("Home shelter layout has no door", door);

        Set<String> seen = new HashSet<>();
        ArrayDeque<GridPoint2> queue = new ArrayDeque<>();
        queue.add(door);
        seen.add(door.x + "," + door.y);

        int[] dx = {1, -1, 0, 0};
        int[] dy = {0, 0, 1, -1};
        while (!queue.isEmpty()) {
            GridPoint2 cur = queue.poll();
            for (int i = 0; i < 4; i++) {
                int nx = cur.x + dx[i];
                int ny = cur.y + dy[i];
                if (ny < 0 || ny >= layout.length || nx < 0 || nx >= layout[ny].length()) continue;
                char c = layout[ny].charAt(nx);
                if (c == '#' || c == 'W') continue; // Not walkable
                String key = nx + "," + ny;
                if (!seen.add(key)) continue;
                // Only open floor and door can be walked through to reach other tiles
                if (c == '.' || c == 'D') {
                    queue.add(new GridPoint2(nx, ny));
                }
            }
        }

        char[] stationChars = {'L', 'A', 'R', 'T', 'F', 'B', 'C', 'N', 'P'};
        for (int y = 0; y < layout.length; y++) {
            for (int x = 0; x < layout[y].length(); x++) {
                char c = layout[y].charAt(x);
                for (char sc : stationChars) {
                    if (c == sc) {
                        assertTrue("Station '" + sc + "' at (" + x + "," + y
                                + ") must be reachable from door via open floor", seen.contains(x + "," + y));
                    }
                }
            }
        }
    }

    @Test
    public void testOutpostShelterStationsReachable() {
        String[] layout = ShelterBuilder.LAYOUT;

        GridPoint2 door = null;
        for (int y = 0; y < layout.length && door == null; y++) {
            int x = layout[y].indexOf('D');
            if (x >= 0) door = new GridPoint2(x, y);
        }
        assertNotNull("Outpost shelter layout has no door", door);

        Set<String> seen = new HashSet<>();
        ArrayDeque<GridPoint2> queue = new ArrayDeque<>();
        queue.add(door);
        seen.add(door.x + "," + door.y);

        int[] dx = {1, -1, 0, 0};
        int[] dy = {0, 0, 1, -1};
        while (!queue.isEmpty()) {
            GridPoint2 cur = queue.poll();
            for (int i = 0; i < 4; i++) {
                int nx = cur.x + dx[i];
                int ny = cur.y + dy[i];
                if (ny < 0 || ny >= layout.length || nx < 0 || nx >= layout[ny].length()) continue;
                char c = layout[ny].charAt(nx);
                if (c == '#' || c == 'W') continue;
                String key = nx + "," + ny;
                if (!seen.add(key)) continue;
                if (c == '.' || c == 'D') {
                    queue.add(new GridPoint2(nx, ny));
                }
            }
        }

        char[] stationChars = {'L', 'A', 'R', 'T', 'F', 'H', 'C', 'B', 'N'};
        for (int y = 0; y < layout.length; y++) {
            for (int x = 0; x < layout[y].length(); x++) {
                char c = layout[y].charAt(x);
                for (char sc : stationChars) {
                    if (c == sc) {
                        assertTrue("Outpost station '" + sc + "' at (" + x + "," + y
                                + ") must be reachable from door via open floor", seen.contains(x + "," + y));
                    }
                }
            }
        }
    }

    private com.bpm.minotaur.gamedata.item.ItemDataManager mockItemDataManager() {
        return new com.bpm.minotaur.gamedata.item.ItemDataManager() {
            @Override
            public Item createItem(Item.ItemType type, int x, int y,
                                   com.bpm.minotaur.gamedata.item.ItemColor color,
                                   com.badlogic.gdx.assets.AssetManager assetManager) {
                com.bpm.minotaur.gamedata.item.ItemTemplate t = new com.bpm.minotaur.gamedata.item.ItemTemplate();
                t.friendlyName = type.name();
                t.baseValue = 50;
                Item item = Item.fromTemplate(type, t);
                item.setPosition(x, y);
                return item;
            }
        };
    }

    @Test
    public void testMaterialiseAllStationsPlacesWarTableAndLectern() {
        MazeChunkGenerator gen = new MazeChunkGenerator();
        com.bpm.minotaur.gamedata.item.ItemDataManager idm = mockItemDataManager();
        Maze maze = gen.generateChunk(new GridPoint2(0, 0), 1, 1,
                Difficulty.EASY, GameMode.ADVANCED,
                null, null, null, idm, null, null, null, 12345L, 0);

        ShelterAltar altar = ShelterAltar.getInstance();
        altar.materialiseAllStations(maze, idm, null);

        // Find Archive Lectern and War Table
        GridPoint2 lecternPos = null;
        GridPoint2 warTablePos = null;
        GridPoint2 craftingPos = null;
        GridPoint2 dummyPos = null;

        for (java.util.Map.Entry<GridPoint2, Item> entry : maze.getItems().entrySet()) {
            if (entry.getValue().getType() == Item.ItemType.HOME_ARCHIVE_LECTERN) {
                lecternPos = entry.getKey();
            }
            if (entry.getValue().getType() == Item.ItemType.HOME_WAR_TABLE) {
                warTablePos = entry.getKey();
            }
            if (entry.getValue().getType() == Item.ItemType.HOME_CRAFTING_BENCH) {
                craftingPos = entry.getKey();
            }
            if (entry.getValue().getType() == Item.ItemType.HOME_TRAINING_DUMMY) {
                dummyPos = entry.getKey();
            }
        }

        assertNotNull("Archive Lectern must be placed when materialiseAllStations is called", lecternPos);
        assertNotNull("War Table must be placed when materialiseAllStations is called", warTablePos);
        assertNotNull("Crafting Bench must be placed when materialiseAllStations is called", craftingPos);
        assertNotNull("Training Dummy must be placed when materialiseAllStations is called", dummyPos);

        // War table must be directly beside the Lectern (Manhattan distance 1)
        int manhattanDist = Math.abs(lecternPos.x - warTablePos.x) + Math.abs(lecternPos.y - warTablePos.y);
        assertEquals("War table must stand directly beside the lectern", 1, manhattanDist);

        // Crafting bench must not occupy the floor tile in front of the Lectern
        assertNotEquals("Crafting bench must not block Lectern front tile",
                new GridPoint2(lecternPos.x, lecternPos.y - 1), craftingPos);
    }
}
