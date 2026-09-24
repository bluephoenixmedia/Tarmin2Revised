package com.bpm.minotaur.generation;

import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.gamedata.progression.BiomePortal;
import com.bpm.minotaur.managers.WorldManager;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * The portal gallery is an extension of the shelter, which is the one room the
 * player must always be able to cross. A niche they cannot reach, or a gallery
 * sealed off from the main room, would strand the feature behind a wall.
 */
public class ShelterPortalGalleryTest {

    private String[] shelterLayout() throws Exception {
        MazeChunkGenerator gen = new MazeChunkGenerator();
        Field f = MazeChunkGenerator.class.getDeclaredField("homeTile");
        f.setAccessible(true);
        return (String[]) f.get(gen);
    }

    private boolean walkable(String[] layout, int x, int y) {
        if (y < 0 || y >= layout.length) return false;
        if (x < 0 || x >= layout[y].length()) return false;
        char c = layout[y].charAt(x);
        // Only '#' and 'W' are structural; everything else is standable floor,
        // though station tiles hold an impassable item once bought.
        return c != '#' && c != 'W';
    }

    @Test
    public void theGalleryHasANicheForEveryPortalPlusRoomToGrow() throws Exception {
        String[] layout = shelterLayout();

        int niches = 0;
        for (String row : layout) {
            for (char c : row.toCharArray()) {
                if (c == 'P') niches++;
            }
        }

        assertTrue("Need at least one niche per portal, found " + niches
                        + " for " + BiomePortal.values().length + " portals",
                niches >= BiomePortal.values().length);
        assertTrue("Reserved slots should exist so a new biome is a data row, found " + niches,
                niches >= BiomePortal.values().length + 1);
    }

    @Test
    public void everyNicheIsInteractableFromAWalkableTile() throws Exception {
        String[] layout = shelterLayout();

        for (int y = 0; y < layout.length; y++) {
            for (int x = 0; x < layout[y].length(); x++) {
                if (layout[y].charAt(x) != 'P') continue;

                boolean approachable = walkable(layout, x + 1, y)
                        || walkable(layout, x - 1, y)
                        || walkable(layout, x, y + 1)
                        || walkable(layout, x, y - 1);
                assertTrue("Portal niche at (" + x + "," + y
                        + ") has no adjacent tile to stand on", approachable);
            }
        }
    }

    @Test
    public void theGalleryIsReachableFromTheShelterDoor() throws Exception {
        String[] layout = shelterLayout();

        GridPoint2 door = null;
        for (int y = 0; y < layout.length && door == null; y++) {
            int x = layout[y].indexOf('D');
            if (x >= 0) door = new GridPoint2(x, y);
        }
        assertNotNull("Shelter layout has no door", door);

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
                if (!walkable(layout, nx, ny)) continue;
                String key = nx + "," + ny;
                if (!seen.add(key)) continue;
                queue.add(new GridPoint2(nx, ny));
            }
        }

        for (int y = 0; y < layout.length; y++) {
            for (int x = 0; x < layout[y].length(); x++) {
                if (layout[y].charAt(x) != 'P') continue;
                assertTrue("Portal niche at (" + x + "," + y
                                + ") is sealed off from the shelter door",
                        seen.contains(x + "," + y));
            }
        }
    }

    @Test
    public void nearestBiomeChunkIsOutsideTheMazeAndDeterministic() {
        if (com.badlogic.gdx.Gdx.app == null) {
            com.badlogic.gdx.Gdx.app = (com.badlogic.gdx.Application) java.lang.reflect.Proxy.newProxyInstance(
                    com.badlogic.gdx.Application.class.getClassLoader(),
                    new Class<?>[]{com.badlogic.gdx.Application.class},
                    (proxy, method, args) -> null);
        }
        WorldManager wm = new WorldManager(com.bpm.minotaur.gamedata.GameMode.ADVANCED,
                com.bpm.minotaur.gamedata.Difficulty.EASY, 1, null, null, null, null, null, null);

        for (BiomePortal portal : BiomePortal.values()) {
            GridPoint2 first = wm.findNearestChunkOfBiome(portal.getDestination());
            assertNotNull(portal + ": no destination chunk found", first);

            int chebyshev = Math.max(Math.abs(first.x), Math.abs(first.y));
            assertTrue(portal + ": destination " + first + " is inside the maze",
                    chebyshev > WorldConstants.CENTRAL_MAZE_RADIUS);

            GridPoint2 second = wm.findNearestChunkOfBiome(portal.getDestination());
            assertEquals(portal + ": destination must be stable within a world", first, second);
        }
    }
}
