package com.bpm.minotaur.managers;

import com.bpm.minotaur.gamedata.Direction;
import com.bpm.minotaur.gamedata.Maze;
import org.junit.Test;

import static org.junit.Assert.*;

/** A monster beside the player across a wall cannot strike through it (playtest 2026-10-22). */
public class FlankThroughWallTest {

    @Test
    public void aWallBetweenThemStopsTheBlowFromEitherSide() {
        Maze maze = new Maze(1, new int[4][4]);
        assertFalse("open floor: the blow lands", CombatManager.wallBetween(maze, 1, 1, 2, 1));

        // The wall is kept on the monster's tile, on its east side.
        maze.getWallData()[1][1] |= Direction.EAST.getWallMask();
        assertTrue(CombatManager.wallBetween(maze, 1, 1, 2, 1));
        assertTrue("and seen from the player's side too", CombatManager.wallBetween(maze, 2, 1, 1, 1));
    }

    @Test
    public void aWallKeptOnThePlayersTileStopsItToo() {
        Maze maze = new Maze(1, new int[4][4]);
        maze.getWallData()[2][1] |= Direction.SOUTH.getWallMask(); // player at (1,2), wall on their south
        assertTrue(CombatManager.wallBetween(maze, 1, 1, 1, 2));
    }
}
