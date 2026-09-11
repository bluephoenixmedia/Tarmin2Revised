package com.bpm.minotaur.gamedata.player;

import com.bpm.minotaur.gamedata.Direction;
import com.bpm.minotaur.gamedata.Door;
import com.bpm.minotaur.gamedata.GameMode;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.managers.GameEventManager;
import com.bpm.minotaur.managers.UnlockManager;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class DoorBumpInteractionTest {

    private Maze maze;
    private Player player;
    private Door door;
    private GameEventManager eventManager;

    @Before
    public void setUp() {
        if (com.badlogic.gdx.Gdx.app == null) {
            com.badlogic.gdx.Gdx.app = (com.badlogic.gdx.Application) java.lang.reflect.Proxy.newProxyInstance(
                    com.badlogic.gdx.Application.class.getClassLoader(),
                    new Class<?>[]{com.badlogic.gdx.Application.class},
                    (proxy, method, args) -> null
            );
        }

        int[][] wallData = new int[10][10];
        maze = new Maze(1, wallData);
        door = new Door();
        maze.addGameObject(door, 2, 3);

        player = new Player(2, 2);
        player.setFacing(Direction.NORTH);
        eventManager = new GameEventManager();
    }

    @Test
    public void testBumpIntoClosedDoorStartsOpeningAndKeepsPlayerInPlace() {
        assertEquals(Door.DoorState.CLOSED, door.getState());
        long initialDoorsStat = UnlockManager.getInstance().getData().totalDoorsOpened;

        // Player is at (2, 2) facing NORTH towards closed door at (2, 3)
        player.moveForward(maze, eventManager, GameMode.CLASSIC);

        // Door should now be opening
        assertEquals("Closed door must start opening on bump", Door.DoorState.OPENING, door.getState());

        // Player must remain at (2, 2) on the bump turn
        assertEquals("Player X should remain unchanged on bump", 2.5f, player.getPosition().x, 0.001f);
        assertEquals("Player Y should remain unchanged on bump", 2.5f, player.getPosition().y, 0.001f);

        // Door stat should increment
        assertEquals("Doors stat must increment by 1 on door open",
                initialDoorsStat + 1, UnlockManager.getInstance().getData().totalDoorsOpened);

        // Event message must indicate door opened
        boolean foundMsg = false;
        for (String msg : eventManager.getMessageHistory()) {
            if (msg.contains("You open the door")) {
                foundMsg = true;
                break;
            }
        }
        assertTrue("Event manager must contain 'You open the door.' event", foundMsg);
    }

    @Test
    public void testStepThroughOpeningDoorOnSubsequentForwardMove() {
        assertEquals(Door.DoorState.CLOSED, door.getState());

        // Turn 1: Bump into door to open it
        player.moveForward(maze, eventManager, GameMode.CLASSIC);
        assertEquals(Door.DoorState.OPENING, door.getState());
        assertEquals(2.5f, player.getPosition().y, 0.001f);

        // Turn 2: Move forward again into the now-opening doorway
        player.moveForward(maze, eventManager, GameMode.CLASSIC);

        // Player should have stepped into the doorway at (2, 3)
        assertEquals("Player X must be on the doorway tile", 2.5f, player.getPosition().x, 0.001f);
        assertEquals("Player Y must be on the doorway tile (2, 3)", 3.5f, player.getPosition().y, 0.001f);
    }

    @Test
    public void testStepThroughFullyOpenDoor() {
        door.startOpening();
        door.update(1.0f); // Finish animation
        assertEquals(Door.DoorState.OPEN, door.getState());

        player.moveForward(maze, eventManager, GameMode.CLASSIC);

        assertEquals("Player X must be on the doorway tile", 2.5f, player.getPosition().x, 0.001f);
        assertEquals("Player Y must be on the doorway tile (2, 3)", 3.5f, player.getPosition().y, 0.001f);
    }

    @Test
    public void testBumpIntoClosingDoorReopensIt() {
        // Put door in CLOSING state
        door.startOpening();
        door.update(1.0f);
        door.startClosing();
        assertEquals(Door.DoorState.CLOSING, door.getState());

        long initialDoorsStat = UnlockManager.getInstance().getData().totalDoorsOpened;

        // Player moves forward into closing door
        player.moveForward(maze, eventManager, GameMode.CLASSIC);

        // Door should reverse to OPENING
        assertEquals("Closing door must transition to OPENING on forward bump", Door.DoorState.OPENING, door.getState());
        assertEquals("Player must remain in place when bumping closing door", 2.5f, player.getPosition().y, 0.001f);
        assertEquals(initialDoorsStat + 1, UnlockManager.getInstance().getData().totalDoorsOpened);
    }

    @Test
    public void testMovingBackwardIntoClosedDoorDoesNotOpenIt() {
        // Player is at (2, 2) facing SOUTH (away from door at 2, 3)
        player.setFacing(Direction.SOUTH);
        assertEquals(Door.DoorState.CLOSED, door.getState());
        long initialDoorsStat = UnlockManager.getInstance().getData().totalDoorsOpened;

        // Moving backward moves NORTH towards (2, 3)
        player.moveBackward(maze, eventManager, GameMode.CLASSIC);

        // Door must NOT open when moving backward into it
        assertEquals("Door must remain CLOSED when moving backward into it", Door.DoorState.CLOSED, door.getState());
        assertEquals("Player must remain at (2, 2)", 2.5f, player.getPosition().y, 0.001f);
        assertEquals("Doors stat must not increment", initialDoorsStat, UnlockManager.getInstance().getData().totalDoorsOpened);
    }

    @Test
    public void testExistingOKeyInteractionContinuesToWork() {
        assertEquals(Door.DoorState.CLOSED, door.getState());

        // Press 'O' (player.interact) facing closed door
        player.interact(maze, eventManager, null, GameMode.CLASSIC, null);
        assertEquals("Pressing 'O' must open closed door", Door.DoorState.OPENING, door.getState());

        // Finish opening animation
        door.update(1.0f);
        assertEquals(Door.DoorState.OPEN, door.getState());

        // Press 'O' again facing open door -> should close
        player.interact(maze, eventManager, null, GameMode.CLASSIC, null);
        assertEquals("Pressing 'O' on open door must start closing it", Door.DoorState.CLOSING, door.getState());
    }
}
