package com.bpm.minotaur.gamedata;

import com.badlogic.gdx.math.GridPoint2;
import com.badlogic.gdx.math.Vector2;
import com.bpm.minotaur.gamedata.player.Player;
import com.bpm.minotaur.managers.GameEventManager;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class WindowTest {

    @Test
    public void testWindowPosition() {
        Window window = new Window(3, 6);
        assertEquals(new Vector2(3, 6), window.getPosition());
    }

    @Test
    public void testWindowPassability() {
        int[][] wallData = new int[10][10];
        Maze maze = new Maze(1, wallData);

        Window window = new Window(3, 6);
        maze.addGameObject(window, 3, 6);

        assertFalse("Window tile must not be passable", maze.isPassable(3, 6));
        assertTrue("Adjacent empty tile should be passable", maze.isPassable(4, 6));
    }

    @Test
    public void testChunkDataWindowPersistence() {
        int[][] wallData = new int[10][10];
        Maze maze = new Maze(1, wallData);
        Window window = new Window(3, 6);
        maze.addGameObject(window, 3, 6);

        ChunkData chunkData = new ChunkData(maze);
        assertEquals(1, chunkData.windows.size());
        assertEquals(3, chunkData.windows.get(0).x);
        assertEquals(6, chunkData.windows.get(0).y);

        Maze restored = chunkData.buildMaze(null, null, null);
        Object restoredObj = restored.getGameObjectAt(3, 6);
        assertNotNull(restoredObj);
        assertTrue(restoredObj instanceof Window);
        assertEquals(new Vector2(3, 6), ((Window) restoredObj).getPosition());
    }

    @Test
    public void testLegacyHomeTilesFallback() {
        // Test chunk without windows in ChunkData but with shelter homeTiles
        ChunkData legacyChunk = new ChunkData();
        legacyChunk.level = 1;
        legacyChunk.wallData = new int[12][12];

        // Simulate home tile footprint (tx 3 to 8, tileY 3 to 7 -> gameY 4 to 8, gameX 3 to 8)
        List<GridPoint2> homeTiles = new ArrayList<>();
        for (int x = 3; x <= 8; x++) {
            for (int y = 4; y <= 8; y++) {
                homeTiles.add(new GridPoint2(x, y));
            }
        }
        legacyChunk.homeTiles = homeTiles;

        Maze restored = legacyChunk.buildMaze(null, null, null);
        // In shelter footprint: minX = 3, maxY = 8 -> targetX = 3, targetY = 8 - 2 = 6
        Object windowObj = restored.getGameObjectAt(3, 6);
        assertNotNull("Legacy chunk with homeTiles must restore the shelter window", windowObj);
        assertTrue(windowObj instanceof Window);
    }

    @Test
    public void testPlayerExamineWindowInteraction() {
        int[][] wallData = new int[10][10];
        Maze maze = new Maze(1, wallData);
        Window window = new Window(3, 6);
        maze.addGameObject(window, 3, 6);

        Player player = new Player(4, 6); // standing directly East of window
        player.setFacing(Direction.WEST); // facing West toward window

        GameEventManager eventManager = new GameEventManager();
        player.interact(maze, eventManager, null, GameMode.CLASSIC, null);

        assertFalse("Interacting with window should generate a game event", eventManager.getMessageHistory().isEmpty());
        String msg = eventManager.getMessageHistory().get(0);
        assertTrue("Event message should describe looking through the iron bars", msg.contains("peer through the iron bars"));
    }
}
