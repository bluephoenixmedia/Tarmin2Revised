package com.bpm.minotaur.gamedata.events;

import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.gamedata.ChunkData;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.Scenery;
import org.junit.Test;

import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class EventPlacerTest {

    private static final String JSON = "{ \"events\": ["
            + "{ \"id\": \"MAZE_EVENT\", \"biomes\": [\"MAZE\"] },"
            + "{ \"id\": \"FOREST_EVENT\", \"biomes\": [\"FOREST\"] }"
            + "] }";

    private final EventCatalog catalog = EventCatalog.fromJson(JSON);

    /** A Random whose rolls always come up {@code roll}, so the per-chunk chance is under test control. */
    private static Random rolling(float roll) {
        return new Random(7) {
            @Override
            public float nextFloat() {
                return roll;
            }
        };
    }

    @Test
    public void wildernessRollsAtTwentyFivePercentAndMazeAtFifteen() {
        assertEquals(0.25f, EventPlacer.chanceFor("FOREST"), 0.0001f);
        assertEquals(0.25f, EventPlacer.chanceFor("DESERT"), 0.0001f);
        assertEquals(0.25f, EventPlacer.chanceFor("LAKELANDS"), 0.0001f);
        assertEquals(0.15f, EventPlacer.chanceFor("MAZE"), 0.0001f);
    }

    @Test
    public void placesOneEventOnAnOpenTileAndMarksItSeen() {
        Maze maze = new Maze(1, new int[16][16]);
        Set<String> seen = new HashSet<>();

        String placed = EventPlacer.place(maze, "MAZE", 1, false, catalog, seen, rolling(0.01f));

        assertEquals("MAZE_EVENT", placed);
        assertEquals(1, maze.getChoiceEvents().size());
        assertTrue(seen.contains("MAZE_EVENT"));
        GridPoint2 tile = maze.getChoiceEvents().keySet().iterator().next();
        assertFalse(maze.isWall(tile.x, tile.y));
    }

    @Test
    public void placesNothingWhenTheChanceFails() {
        Maze maze = new Maze(1, new int[16][16]);
        assertNull(EventPlacer.place(maze, "MAZE", 1, false, catalog, new HashSet<>(), rolling(0.5f)));
        assertTrue(maze.getChoiceEvents().isEmpty());
    }

    @Test
    public void neverPlacesOnTheShelterChunk() {
        Maze maze = new Maze(1, new int[16][16]);
        assertNull(EventPlacer.place(maze, "MAZE", 1, true, catalog, new HashSet<>(), rolling(0.01f)));
    }

    @Test
    public void neverRepeatsAnEventWithinARun() {
        Set<String> seen = new HashSet<>();
        seen.add("MAZE_EVENT");
        Maze maze = new Maze(1, new int[16][16]);
        assertNull(EventPlacer.place(maze, "MAZE", 1, false, catalog, seen, rolling(0.01f)));
    }

    @Test
    public void avoidsTilesThatAreAlreadyTaken() {
        int[][] walls = new int[4][4];
        Maze maze = new Maze(1, walls);
        // Fill every tile but one with a statue event or scenery.
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 4; x++) {
                if (x == 2 && y == 1) {
                    continue;
                }
                maze.addScenery(new Scenery(Scenery.SceneryType.STATUE, x, y, null));
            }
        }
        EventPlacer.place(maze, "MAZE", 1, false, catalog, new HashSet<>(), rolling(0.01f));
        Map<GridPoint2, String> placed = maze.getChoiceEvents();
        assertEquals(1, placed.size());
        assertEquals(new GridPoint2(2, 1), placed.keySet().iterator().next());
    }

    @Test
    public void neverPlacesOnTheChunkEdgeWhereSeamlessCrossingsLand() {
        Maze maze = new Maze(1, new int[3][3]);
        EventPlacer.place(maze, "MAZE", 1, false, catalog, new HashSet<>(), rolling(0.01f));
        assertEquals(new GridPoint2(1, 1), maze.getChoiceEvents().keySet().iterator().next());
    }

    @Test
    public void choiceEventsSurviveAChunkSaveApartFromStatueEvents() {
        Maze maze = new Maze(1, new int[16][16]);
        maze.addEvent(3, 3, "EVENT_DRAGON_STATUE");
        maze.addChoiceEvent(5, 6, "MAZE_EVENT");

        Maze restored = new ChunkData(maze).buildMaze(null, null, null);

        assertEquals("MAZE_EVENT", restored.getChoiceEventAt(5, 6));
        assertNull(restored.getEventAt(5, 6));
        assertEquals("EVENT_DRAGON_STATUE", restored.getEventAt(3, 3));
        assertNull(restored.getChoiceEventAt(3, 3));
    }
}
