package com.bpm.minotaur.gamedata;

import com.badlogic.gdx.math.GridPoint2;
import org.junit.Test;

import static org.junit.Assert.*;

public class DoorOrientationTest {

    @Test
    public void testDoorDefaultOrientation() {
        Door door = new Door();
        assertEquals(Door.Orientation.NORTH_SOUTH, door.getOrientation());

        door.setOrientation(Door.Orientation.EAST_WEST);
        assertEquals(Door.Orientation.EAST_WEST, door.getOrientation());
    }

    @Test
    public void testGateDefaultOrientation() {
        Gate gate = new Gate(5, 5);
        assertEquals(Door.Orientation.NORTH_SOUTH, gate.getOrientation());

        gate.setOrientation(Door.Orientation.EAST_WEST);
        assertEquals(Door.Orientation.EAST_WEST, gate.getOrientation());
    }

    @Test
    public void testDoorPassability() {
        int[][] wallData = new int[5][5];
        Maze maze = new Maze(1, wallData);

        Door door = new Door();
        maze.addGameObject(door, 2, 2);

        // Initially closed
        assertEquals(Door.DoorState.CLOSED, door.getState());
        assertFalse("Closed door must not be passable", maze.isPassable(2, 2));

        // Open the door
        door.startOpening();
        door.update(1.0f); // 0.5s is full animation, 1.0s guarantees OPEN
        assertEquals(Door.DoorState.OPEN, door.getState());
        assertTrue("Open door must be passable", maze.isPassable(2, 2));
    }

    @Test
    public void testDoorOccupancyLockPreventsClosingWithMonster() {
        int[][] wallData = new int[5][5];
        Maze maze = new Maze(1, wallData);

        Door door = new Door();
        door.startOpening();
        door.update(1.0f);
        assertEquals(Door.DoorState.OPEN, door.getState());
        maze.addGameObject(door, 2, 2);

        // Without monster, it starts closing:
        maze.toggleDoorAt(2, 2);
        assertEquals(Door.DoorState.CLOSING, door.getState());

        // Now reopen and add monster to the tile
        door.startOpening();
        door.update(1.0f);
        assertEquals(Door.DoorState.OPEN, door.getState());
        maze.getMonsters().put(new GridPoint2(2, 2), null); // Put key in monsters map

        maze.toggleDoorAt(2, 2);
        // Must still be OPEN because the doorway is occupied!
        assertEquals("Door must not close when an entity occupies the doorway", Door.DoorState.OPEN, door.getState());
    }

    @Test
    public void testChunkDataOrientationPersistenceAndFallback() {
        // Test DoorData constructor with door
        Door door = new Door();
        door.setOrientation(Door.Orientation.EAST_WEST);
        ChunkData.DoorData doorData = new ChunkData.DoorData(door);
        assertEquals(Door.Orientation.EAST_WEST, doorData.orientation);

        // Test GateData constructor with gate
        Gate gate = new Gate(1, 2);
        gate.setOrientation(Door.Orientation.EAST_WEST);
        ChunkData.GateData gateData = new ChunkData.GateData(gate);
        assertEquals(Door.Orientation.EAST_WEST, gateData.orientation);
    }
}
