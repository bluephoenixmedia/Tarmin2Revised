package com.bpm.minotaur.gamedata;

import com.badlogic.gdx.math.GridPoint2;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the Player class to verify movement and turning logic.
 */
public class PlayerTest {

    private Player player;
    private Maze testMaze;

    // Wall bitmask constants for test maze creation
    private static final int WALL_TOP = 0b01000000;
    private static final int WALL_RIGHT = 0b00010000;
    private static final int WALL_BOTTOM = 0b00000100;
    private static final int WALL_LEFT = 0b00000001;

    /**
     * This method runs before each test, setting up a fresh player and maze.
     * The maze is a simple 3x3 room with the player in the center.
     */
    @BeforeEach
    void setUp() {
        player = new Player();

        int[][] wallData = new int[Maze.MAZE_HEIGHT][Maze.MAZE_WIDTH];
        // Center of the room is (5,5). Walls are around it.
        wallData[6][4] = WALL_TOP | WALL_LEFT;
        wallData[6][5] = WALL_TOP;
        wallData[6][6] = WALL_TOP | WALL_RIGHT;
        wallData[5][4] = WALL_LEFT;
        wallData[5][6] = WALL_RIGHT;
        wallData[4][4] = WALL_BOTTOM | WALL_LEFT;
        wallData[4][5] = WALL_BOTTOM;
        wallData[4][6] = WALL_BOTTOM | WALL_RIGHT;

        testMaze = new Maze(1, wallData);
        player.getPosition().set(5, 5); // Start player in the center of the room
    }

    @Test
    void testTurning() {
        player.getPosition().set(5,5); // Start at center
        player.turnRight(); // Start facing SOUTH, turn right -> WEST
        assertEquals(Direction.WEST, player.getFacing(), "Player should be facing WEST after turning right from SOUTH");

        player.turnRight(); // WEST -> NORTH
        assertEquals(Direction.NORTH, player.getFacing(), "Player should be facing NORTH after turning right from WEST");

        player.turnLeft(); // NORTH -> WEST
        assertEquals(Direction.WEST, player.getFacing(), "Player should be facing WEST after turning left from NORTH");
    }

    @Test
    void testMoveForward_Success() {
        player.getPosition().set(5, 5); // Start at center
        player.turnRight(); // Face WEST
        player.turnRight(); // Face NORTH

        player.moveForward(testMaze); // Move into open space at (5,6)
        assertEquals(new GridPoint2(5, 6), player.getPosition(), "Player should move forward into an open space.");
    }

    @Test
    void testMoveForward_Blocked() {
        player.getPosition().set(5, 5); // Start at center, facing SOUTH

        player.moveForward(testMaze); // Attempt to move into wall at (5,4)
        assertEquals(new GridPoint2(5, 5), player.getPosition(), "Player should not move forward into a wall.");
    }

    @Test
    void testMoveBackward_Success() {
        player.getPosition().set(5, 5); // Start at center, facing SOUTH

        player.moveBackward(testMaze); // Move backward (NORTH) into open space at (5,6)
        assertEquals(new GridPoint2(5, 6), player.getPosition(), "Player should move backward into an open space.");
    }

    @Test
    void testMoveBackward_Blocked() {
        player.getPosition().set(5, 5); // Start at center
        player.turnRight(); // Face WEST
        player.turnRight(); // Face NORTH

        player.moveBackward(testMaze); // Attempt to move backward (SOUTH) into a wall at (5,4)
        assertEquals(new GridPoint2(5, 5), player.getPosition(), "Player should not move backward into a wall.");
    }
}
