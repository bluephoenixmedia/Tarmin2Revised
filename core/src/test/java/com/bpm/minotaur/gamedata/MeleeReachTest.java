package com.bpm.minotaur.gamedata;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * A monster standing in a cell whose only wall is on its west edge must be
 * strikable. WEST's wall mask is 0b1, so such a cell has wall data == 1, which
 * {@link Maze#isWall} reads as a solid block -- the player's bump then found no
 * target and the game reported "You strike a solid wall" while the monster
 * kept hitting back.
 */
public class MeleeReachTest {

    @Test
    public void monsterInWestWalledCellCanBeStruck() {
        Maze maze = new Maze(1, new int[5][5]);
        maze.setTile(3, 2, Direction.WEST.getWallMask());
        assertTrue("fixture: the legacy solid-cell sentinel collides with the west mask", maze.isWall(3, 2));

        // Player at (3,1) facing north, target at (3,2). The only wall is on the
        // target's west edge, which this step never crosses.
        assertTrue(maze.canMeleeInto(3, 1, 3, 2));
    }

    @Test
    public void solidWallStillBlocksMelee() {
        Maze maze = new Maze(1, new int[5][5]);
        maze.setTile(2, 2, Direction.EAST.getWallMask());
        assertFalse(maze.canMeleeInto(2, 2, 3, 2));
    }

    @Test
    public void outOfBoundsIsNeverReachable() {
        Maze maze = new Maze(1, new int[5][5]);
        assertFalse(maze.canMeleeInto(4, 2, 5, 2));
    }
}
