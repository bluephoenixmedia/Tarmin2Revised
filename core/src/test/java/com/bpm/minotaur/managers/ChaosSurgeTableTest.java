package com.bpm.minotaur.managers;

import com.badlogic.gdx.math.Vector2;
import com.bpm.minotaur.gamedata.DamageType;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.player.Player;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class ChaosSurgeTableTest {

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

        @Override
        public boolean isPassable(int x, int y) {
            return true;
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
    public void testTableHas20DistinctEvents() {
        List<ChaosSurgeEvent> events = ChaosSurgeTable.getAllEvents();
        assertEquals("ChaosSurgeTable must contain exactly 20 events", 20, events.size());

        for (ChaosSurgeEvent event : events) {
            assertNotNull("Event ID must not be null", event.getId());
            assertFalse("Event ID must not be empty", event.getId().trim().isEmpty());
            assertNotNull("Event Name must not be null", event.getName());
            assertFalse("Event Name must not be empty", event.getName().trim().isEmpty());
            assertNotNull("Event Description must not be null", event.getDescription());
            assertFalse("Event Description must not be empty", event.getDescription().trim().isEmpty());

            ChaosSurgeEvent retrieved = ChaosSurgeTable.getEventById(event.getId());
            assertNotNull("Event must be retrievable by ID: " + event.getId(), retrieved);
            assertEquals(event.getId(), retrieved.getId());
        }
    }

    @Test
    public void testChaosManagerRegistersAll20Events() {
        List<ChaosSurgeEvent> registered = chaos.getRegisteredEvents();
        assertTrue("ChaosManager should register at least 20 events, was: " + registered.size(), registered.size() >= 20);

        for (ChaosSurgeEvent event : ChaosSurgeTable.getAllEvents()) {
            assertNotNull("ChaosManager should have event: " + event.getId(), chaos.getEvent(event.getId()));
        }
    }

    @Test
    public void testAcidicStardustTickDamagesPlayer() {
        ChaosSurgeEvent event = ChaosSurgeTable.getEventById("acidic_stardust");
        assertNotNull(event);

        int startHp = player.getCurrentHP();
        chaos.triggerSurge(event, 5, maze, player, null, eventManager);

        // Turn tick triggers stardust damage
        chaos.onPlayerTurn(maze, player, null, eventManager);
        assertTrue("Player should take acid damage from acidic stardust", player.getCurrentHP() < startHp);
    }

    @Test
    public void testRadiantAuroraTickHealsPlayer() {
        ChaosSurgeEvent event = ChaosSurgeTable.getEventById("radiant_aurora");
        assertNotNull(event);

        player.setCurrentHP(player.getMaxHP() - 10);
        int damagedHp = player.getCurrentHP();

        chaos.triggerSurge(event, 5, maze, player, null, eventManager);
        chaos.onPlayerTurn(maze, player, null, eventManager);

        assertEquals("Player should be healed 1 HP by radiant aurora", damagedHp + 1, player.getCurrentHP());
    }

    @Test
    public void testGlassCannonsMultipliers() {
        ChaosSurgeEvent event = ChaosSurgeTable.getEventById("glass_cannons");
        assertNotNull(event);

        chaos.triggerSurge(event, 10, maze, player, null, eventManager);
        assertEquals(4.0f, chaos.getDamageDealtMultiplier(), 0.001f);
        assertEquals(4.0f, chaos.getDamageTakenMultiplier(), 0.001f);

        // End surge
        chaos.endSurge(new ChaosEventContext(maze, player, null, eventManager, 0, 10));
        assertEquals(1.0f, chaos.getDamageDealtMultiplier(), 0.001f);
        assertEquals(1.0f, chaos.getDamageTakenMultiplier(), 0.001f);
    }

    @Test
    public void testPacifismWaveMultiplier() {
        ChaosSurgeEvent event = ChaosSurgeTable.getEventById("pacifism_wave");
        assertNotNull(event);

        chaos.triggerSurge(event, 10, maze, player, null, eventManager);
        assertEquals(0.25f, chaos.getDamageDealtMultiplier(), 0.001f);
        assertEquals(1.0f, chaos.getDamageTakenMultiplier(), 0.001f);

        chaos.endSurge(new ChaosEventContext(maze, player, null, eventManager, 0, 10));
        assertEquals(1.0f, chaos.getDamageDealtMultiplier(), 0.001f);
    }

    @Test
    public void testMirrorBloodRecoil() {
        ChaosSurgeEvent event = ChaosSurgeTable.getEventById("mirror_blood");
        assertNotNull(event);

        chaos.triggerSurge(event, 10, maze, player, null, eventManager);
        assertEquals(0.5f, chaos.getRecoilFraction(), 0.001f);

        chaos.endSurge(new ChaosEventContext(maze, player, null, eventManager, 0, 10));
        assertEquals(0.0f, chaos.getRecoilFraction(), 0.001f);
    }

    @Test
    public void testInvertedDualityFlag() {
        ChaosSurgeEvent event = ChaosSurgeTable.getEventById("inverted_duality");
        assertNotNull(event);

        assertFalse(chaos.isInvertCombatStats());
        chaos.triggerSurge(event, 10, maze, player, null, eventManager);
        assertTrue(chaos.isInvertCombatStats());

        chaos.endSurge(new ChaosEventContext(maze, player, null, eventManager, 0, 10));
        assertFalse(chaos.isInvertCombatStats());
    }

    @Test
    public void testJestersFarceInvertsHealing() {
        ChaosSurgeEvent event = ChaosSurgeTable.getEventById("jesters_farce");
        assertNotNull(event);

        int initialHp = player.getCurrentHP();
        chaos.triggerSurge(event, 10, maze, player, null, eventManager);
        assertTrue(chaos.isJestersFarceActive());

        // Under Jester's Farce, heal(5) deals 5 damage!
        player.getStats().heal(5);
        assertEquals("Jester's Farce should invert healing into damage", initialHp - 5, player.getCurrentHP());

        chaos.endSurge(new ChaosEventContext(maze, player, null, eventManager, 0, 10));
        assertFalse(chaos.isJestersFarceActive());

        // Now heal(3) heals normally
        player.getStats().heal(3);
        assertEquals(initialHp - 2, player.getCurrentHP());
    }

    @Test
    public void testDenseNeonFogVisionCap() {
        ChaosSurgeEvent event = ChaosSurgeTable.getEventById("dense_neon_fog");
        assertNotNull(event);

        assertEquals(-1, chaos.getVisionCap());
        chaos.triggerSurge(event, 10, maze, player, null, eventManager);
        assertEquals(2, chaos.getVisionCap());

        chaos.endSurge(new ChaosEventContext(maze, player, null, eventManager, 0, 10));
        assertEquals(-1, chaos.getVisionCap());
    }

    @Test
    public void testWandOverchargeFlag() {
        ChaosSurgeEvent event = ChaosSurgeTable.getEventById("wand_overcharge");
        assertNotNull(event);

        assertFalse(chaos.isWandOverchargeActive());
        chaos.triggerSurge(event, 10, maze, player, null, eventManager);
        assertTrue(chaos.isWandOverchargeActive());

        chaos.endSurge(new ChaosEventContext(maze, player, null, eventManager, 0, 10));
        assertFalse(chaos.isWandOverchargeActive());
    }

    @Test
    public void testSlipstreamFlag() {
        ChaosSurgeEvent event = ChaosSurgeTable.getEventById("slipstream");
        assertNotNull(event);

        assertFalse(chaos.isSlipstreamActive());
        chaos.triggerSurge(event, 10, maze, player, null, eventManager);
        assertTrue(chaos.isSlipstreamActive());

        chaos.endSurge(new ChaosEventContext(maze, player, null, eventManager, 0, 10));
        assertFalse(chaos.isSlipstreamActive());
    }
}
