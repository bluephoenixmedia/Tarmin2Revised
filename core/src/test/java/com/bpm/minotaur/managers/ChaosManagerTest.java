package com.bpm.minotaur.managers;

import com.badlogic.gdx.math.Vector2;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.player.Player;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class ChaosManagerTest {

    private ChaosManager chaos;
    private TestMaze maze;
    private TestPlayer player;
    private GameEventManager eventManager;

    private static class TestMaze extends Maze {
        boolean homeTile = false;

        TestMaze() {
            super(1, new int[16][16]);
        }

        @Override
        public boolean isHomeTile(int x, int y) {
            return homeTile;
        }
    }

    private static class TestPlayer extends Player {
        private final Vector2 pos = new Vector2(5, 5);

        TestPlayer() {
            super(5, 5);
        }

        @Override
        public Vector2 getPosition() {
            return pos;
        }
    }

    @Before
    public void setUp() {
        ChaosManager.resetInstanceForTesting();
        chaos = ChaosManager.getInstance();

        maze = new TestMaze();
        player = new TestPlayer();
        eventManager = new GameEventManager();
    }

    @Test
    public void testInitialState() {
        assertFalse("Surge should not be active initially", chaos.isSurgeActive());
        assertEquals("Remaining turns should be 0", 0, chaos.getRemainingTurns());
        assertEquals("Cooldown should be 0", 0, chaos.getCooldownRemaining());
        assertEquals("Total surges triggered should be 0", 0, chaos.getTotalSurgesTriggered());
    }

    @Test
    public void testDurationRollRange() {
        for (int i = 0; i < 50; i++) {
            int duration = chaos.rollDuration();
            assertTrue("Duration should be >= 17 (15 + 2d12 min): " + duration, duration >= 17);
            assertTrue("Duration should be <= 39 (15 + 2d12 max): " + duration, duration <= 39);
        }
    }

    @Test
    public void testSurgeTriggerAndLifecycle() {
        final int[] activateCount = {0};
        final int[] tickCount = {0};
        final int[] expireCount = {0};

        ChaosSurgeEvent testEvent = new ChaosSurgeEvent() {
            @Override
            public String getId() {
                return "test_event";
            }

            @Override
            public String getName() {
                return "Test Event";
            }

            @Override
            public String getDescription() {
                return "Test Description";
            }

            @Override
            public void onActivate(ChaosEventContext context) {
                activateCount[0]++;
            }

            @Override
            public void onTurnTick(ChaosEventContext context) {
                tickCount[0]++;
            }

            @Override
            public void onExpire(ChaosEventContext context) {
                expireCount[0]++;
            }
        };

        chaos.registerEvent(testEvent);
        chaos.triggerSurge(testEvent, 3, maze, player, null, eventManager);

        assertTrue("Surge should be active", chaos.isSurgeActive());
        assertEquals(1, activateCount[0]);
        assertEquals(3, chaos.getRemainingTurns());
        assertEquals("test_event", chaos.getActiveSurgeId());

        // Step 1 turn
        chaos.onPlayerTurn(maze, player, null, eventManager);
        assertEquals(2, chaos.getRemainingTurns());
        assertEquals(1, tickCount[0]);
        assertTrue(chaos.isSurgeActive());

        // Step 2nd turn
        chaos.onPlayerTurn(maze, player, null, eventManager);
        assertEquals(1, chaos.getRemainingTurns());
        assertEquals(2, tickCount[0]);
        assertTrue(chaos.isSurgeActive());

        // Step 3rd turn (expires)
        chaos.onPlayerTurn(maze, player, null, eventManager);
        assertEquals(0, chaos.getRemainingTurns());
        assertEquals(3, tickCount[0]);
        assertEquals(1, expireCount[0]);
        assertFalse("Surge should end after duration expires", chaos.isSurgeActive());
        assertEquals("Cooldown should be set to 150", ChaosManager.MIN_COOLDOWN_TURNS, chaos.getCooldownRemaining());
    }

    @Test
    public void testShelterCampImmunity() {
        maze.homeTile = true;

        final int[] tickCount = {0};
        ChaosSurgeEvent testEvent = new ChaosSurgeEvent() {
            @Override
            public String getId() {
                return "shelter_test";
            }

            @Override
            public String getName() {
                return "Shelter Test";
            }

            @Override
            public String getDescription() {
                return "";
            }

            @Override
            public void onActivate(ChaosEventContext context) {
            }

            @Override
            public void onTurnTick(ChaosEventContext context) {
                tickCount[0]++;
            }

            @Override
            public void onExpire(ChaosEventContext context) {
            }
        };

        chaos.registerEvent(testEvent);
        chaos.triggerSurge(testEvent, 5, maze, player, null, eventManager);

        // Turn in shelter: remaining turns should not decrement
        chaos.onPlayerTurn(maze, player, null, eventManager);
        assertEquals("Turns should not tick inside shelter camp", 5, chaos.getRemainingTurns());
        assertEquals("onTurnTick should not fire in shelter", 0, tickCount[0]);
    }

    @Test
    public void testCooldownDecrementsOutsideShelter() {
        chaos.setCooldownRemaining(10);

        chaos.onPlayerTurn(maze, player, null, eventManager);
        assertEquals(9, chaos.getCooldownRemaining());

        chaos.onPlayerTurn(maze, player, null, eventManager);
        assertEquals(8, chaos.getCooldownRemaining());
    }

    @Test
    public void testSlotScopedReset() {
        ChaosSurgeEvent testEvent = new ChaosSurgeEvent() {
            @Override
            public String getId() {
                return "reset_test";
            }

            @Override
            public String getName() {
                return "Reset Test";
            }

            @Override
            public String getDescription() {
                return "";
            }

            @Override
            public void onActivate(ChaosEventContext context) {
            }

            @Override
            public void onTurnTick(ChaosEventContext context) {
            }

            @Override
            public void onExpire(ChaosEventContext context) {
            }
        };

        chaos.registerEvent(testEvent);
        chaos.triggerSurge(testEvent, 10, maze, player, null, eventManager);

        assertTrue(chaos.isSurgeActive());
        chaos.resetForNewGame();

        assertFalse("resetForNewGame should clear active surge", chaos.isSurgeActive());
        assertEquals(0, chaos.getRemainingTurns());
        assertEquals(0, chaos.getCooldownRemaining());
    }
}
