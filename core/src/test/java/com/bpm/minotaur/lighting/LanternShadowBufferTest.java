package com.bpm.minotaur.lighting;

import com.bpm.minotaur.gamedata.Direction;
import com.bpm.minotaur.gamedata.Door;
import com.bpm.minotaur.gamedata.Maze;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class LanternShadowBufferTest {

    private LanternShadowBuffer shadowBuffer;

    @Before
    public void setUp() {
        shadowBuffer = new LanternShadowBuffer(256);
    }

    @Test
    public void openMazeAllowsMaxDistance() {
        // 5x5 maze with no walls
        int[][] wallData = new int[5][5];
        Maze maze = new Maze(1, wallData);

        shadowBuffer.update(2.5f, 2.5f, maze);

        float[] distances = shadowBuffer.getDistances();
        assertNotNull(distances);
        assertEquals(256, distances.length);

        for (int i = 0; i < distances.length; i++) {
            assertTrue("Distance should reach max distance or boundary in open room: " + distances[i],
                    distances[i] >= 2.0f);
        }
    }

    @Test
    public void solidEastWallBlocksRay() {
        // 5x5 maze, wall East of (2, 2)
        int[][] wallData = new int[5][5];
        wallData[2][2] = Direction.EAST.getWallMask(); // East wall at tile (2, 2)
        Maze maze = new Maze(1, wallData);

        shadowBuffer.update(2.5f, 2.5f, maze);

        // Ray directly East (angle 0)
        float eastDist = shadowBuffer.getDistanceAtAngle(0.0f);
        // Player is at x=2.5, East wall of cell 2 is at x=3.0 -> distance is ~0.5
        assertEquals(0.5f, eastDist, 0.15f);
    }

    @Test
    public void closedDoorBlocksRayWhileOpenDoorPasses() {
        int[][] wallData = new int[5][5];
        // Door mask East of (2, 2)
        wallData[2][2] = Direction.EAST.getWallMask() << 1;
        Maze maze = new Maze(1, wallData);

        Door door = new Door();
        door.setOrientation(Door.Orientation.NORTH_SOUTH);
        door.setState(Door.DoorState.CLOSED, 0f);
        maze.addGameObject(door, 2, 2);

        // Closed door
        shadowBuffer.update(2.5f, 2.5f, maze);
        float closedDist = shadowBuffer.getDistanceAtAngle(0.0f);
        assertEquals(0.5f, closedDist, 0.15f);

        // Open door
        door.setState(Door.DoorState.OPEN, 1f);
        shadowBuffer.update(2.5f, 2.5f, maze);
        float openDist = shadowBuffer.getDistanceAtAngle(0.0f);
        assertTrue("Ray should pass through open door: " + openDist, openDist > 1.0f);
    }

    @Test
    public void distanceEncodingPrecision() {
        float originalDist = 7.425f;
        int encoded = LanternShadowBuffer.encodeDistance(originalDist, 16.0f);
        float decoded = LanternShadowBuffer.decodeDistance(encoded, 16.0f);
        assertEquals(originalDist, decoded, 0.002f);
    }
}
