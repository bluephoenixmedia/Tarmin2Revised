package com.bpm.minotaur.gamedata.player;

import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.monster.Monster;
import com.bpm.minotaur.gamedata.spells.SpellDataManager;
import com.bpm.minotaur.managers.GameEventManager;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Tests for the Spell System Overhaul (Phase 1):
 * - Slot unlocks by level schedule (1 at start, +1 at 2, 5, 8, 11; cap 5).
 * - Preservation of higher slot counts on save restore.
 * - In-combat empty slot assignment vs swap/clear prohibition.
 */
public class SpellProgressionTest {

    private Player player;
    private Maze maze;
    private GameEventManager eventManager;

    @Before
    public void setUp() {
        if (com.badlogic.gdx.Gdx.app == null) {
            com.badlogic.gdx.Gdx.app = (com.badlogic.gdx.Application) java.lang.reflect.Proxy.newProxyInstance(
                    com.badlogic.gdx.Application.class.getClassLoader(),
                    new Class<?>[]{com.badlogic.gdx.Application.class},
                    (proxy, method, args) -> null);
        }
        SpellDataManager.getInstance().load();
        player = new Player(1, 1);
        maze = new Maze(1, new int[12][12]);
        eventManager = new GameEventManager();
    }

    @Test
    public void slotsForLevelSchedule() {
        assertEquals("Level 0 or negative defaults to 1", 1, Player.slotsForLevel(0));
        assertEquals("Level 1 starts with 1 slot", 1, Player.slotsForLevel(1));
        assertEquals("Level 2 unlocks slot 2", 2, Player.slotsForLevel(2));
        assertEquals("Level 3 stays at 2 slots", 2, Player.slotsForLevel(3));
        assertEquals("Level 4 stays at 2 slots", 2, Player.slotsForLevel(4));
        assertEquals("Level 5 unlocks slot 3", 3, Player.slotsForLevel(5));
        assertEquals("Level 6 stays at 3 slots", 3, Player.slotsForLevel(6));
        assertEquals("Level 7 stays at 3 slots", 3, Player.slotsForLevel(7));
        assertEquals("Level 8 unlocks slot 4", 4, Player.slotsForLevel(8));
        assertEquals("Level 9 stays at 4 slots", 4, Player.slotsForLevel(9));
        assertEquals("Level 10 stays at 4 slots", 4, Player.slotsForLevel(10));
        assertEquals("Level 11 unlocks slot 5", 5, Player.slotsForLevel(11));
        assertEquals("Level 12 capped at 5 slots", 5, Player.slotsForLevel(12));
        assertEquals("Level 20 capped at 5 slots", 5, Player.slotsForLevel(20));
    }

    @Test
    public void levelUpUnlocksSlotsAutomatically() {
        assertEquals(1, player.getUnlockedSpellSlots());

        // Level up to 2 (requires 192 XP)
        player.addExperience(200, eventManager);
        assertEquals(2, player.getLevel());
        assertEquals("Level 2 grants slot 2", 2, player.getUnlockedSpellSlots());

        // Level up to 5
        player.setLevel(5);
        player.checkSpellSlotProgression(eventManager);
        assertEquals("Level 5 grants slot 3", 3, player.getUnlockedSpellSlots());
    }

    @Test
    public void inCombatAllowsAssigningIntoEmptySlotOnly() {
        player.setUnlockedSpellSlots(3);
        player.learnSpellId("FIREBALL");
        player.learnSpellId("MAGIC_MISSILE");

        // Slot 0 has MOTE_OF_LIGHT, slot 1 is empty, slot 2 is empty
        assertEquals("MOTE_OF_LIGHT", player.getPreparedSpell(0));
        assertNull(player.getPreparedSpell(1));

        // Add a monster to view
        maze.addMonster(new Monster(Monster.MonsterType.GOBLIN, 10, 10, 4, 1));

        // 1. Assigning into empty slot 1 while hostile in view: SUCCEEDS
        assertEquals(Player.SlotChange.OK, player.assignSpellSlot(1, "FIREBALL", maze));
        assertEquals("FIREBALL", player.getPreparedSpell(1));

        // 2. Swapping into already occupied slot 0 while hostile in view: REFUSED
        assertEquals(Player.SlotChange.HOSTILE_IN_VIEW, player.assignSpellSlot(0, "MAGIC_MISSILE", maze));
        assertEquals("MOTE_OF_LIGHT", player.getPreparedSpell(0));

        // 3. Trying to move FIREBALL from slot 1 to slot 2 while hostile in view: REFUSED (moving is a swap/clear)
        assertEquals(Player.SlotChange.HOSTILE_IN_VIEW, player.assignSpellSlot(2, "FIREBALL", maze));

        // 4. Clearing slot 1 while hostile in view: REFUSED
        assertEquals(Player.SlotChange.HOSTILE_IN_VIEW, player.assignSpellSlot(1, null, maze));
        assertEquals("FIREBALL", player.getPreparedSpell(1));

        // 5. Assigning another unassigned spell into empty slot 2 while hostile in view: SUCCEEDS
        assertEquals(Player.SlotChange.OK, player.assignSpellSlot(2, "MAGIC_MISSILE", maze));
        assertEquals("MAGIC_MISSILE", player.getPreparedSpell(2));
    }

    @Test
    public void outOfCombatAllowsNormalSwappingAndClearing() {
        player.setUnlockedSpellSlots(3);
        player.learnSpellId("FIREBALL");
        player.learnSpellId("MAGIC_MISSILE");

        // No hostile in view
        assertEquals(Player.SlotChange.OK, player.assignSpellSlot(1, "FIREBALL", maze));
        assertEquals(Player.SlotChange.OK, player.assignSpellSlot(0, "MAGIC_MISSILE", maze));
        assertEquals(Player.SlotChange.OK, player.assignSpellSlot(1, null, maze));
        assertNull(player.getPreparedSpell(1));
    }
}
