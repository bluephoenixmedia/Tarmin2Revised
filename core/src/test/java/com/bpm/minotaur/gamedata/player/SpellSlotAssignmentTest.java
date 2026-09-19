package com.bpm.minotaur.gamedata.player;

import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.monster.Monster;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/** Spell Slots are changed from the Spellbook, and only while no hostile is in view. */
public class SpellSlotAssignmentTest {

    private Player player;
    private Maze maze;

    @Before
    public void setUp() {
        if (com.badlogic.gdx.Gdx.app == null) {
            com.badlogic.gdx.Gdx.app = (com.badlogic.gdx.Application) java.lang.reflect.Proxy.newProxyInstance(
                    com.badlogic.gdx.Application.class.getClassLoader(),
                    new Class<?>[]{com.badlogic.gdx.Application.class},
                    (proxy, method, args) -> null);
        }
        maze = new Maze(1, new int[12][12]);
        player = new Player(1, 1);
        player.setUnlockedSpellSlots(3);
        player.learnSpellId("FIREBALL");
        player.learnSpellId("MAGIC_MISSILE");
    }

    @Test
    public void assignsAKnownSpellToAnUnlockedSlot() {
        assertEquals(Player.SlotChange.OK, player.assignSpellSlot(1, "FIREBALL", maze));
        assertEquals("FIREBALL", player.getPreparedSpell(1));
    }

    @Test
    public void clearingASlotLeavesItEmpty() {
        assertEquals(Player.SlotChange.OK, player.assignSpellSlot(0, null, maze));
        assertNull(player.getPreparedSpell(0));
    }

    @Test
    public void refusedWhileAHostileIsInView() {
        maze.addMonster(new Monster(Monster.MonsterType.GOBLIN, 10, 10, 4, 1));

        assertEquals(Player.SlotChange.HOSTILE_IN_VIEW, player.assignSpellSlot(1, "FIREBALL", maze));
        assertNull(player.getPreparedSpell(1));
    }

    @Test
    public void refusedForALockedSlot() {
        assertEquals(Player.SlotChange.SLOT_LOCKED, player.assignSpellSlot(3, "FIREBALL", maze));
        assertNull(player.getPreparedSpell(3));
    }

    @Test
    public void refusedForAnUnknownSpell() {
        assertEquals(Player.SlotChange.UNKNOWN_SPELL, player.assignSpellSlot(1, "WISH", maze));
        assertNull(player.getPreparedSpell(1));
    }

    @Test
    public void aSpellHoldsOneSlotSoMovingItSwapsWithTheTarget() {
        player.assignSpellSlot(1, "FIREBALL", maze);

        assertEquals(Player.SlotChange.OK, player.assignSpellSlot(0, "FIREBALL", maze));

        assertEquals("FIREBALL", player.getPreparedSpell(0));
        assertEquals("The displaced spell takes the vacated slot", "MOTE_OF_LIGHT", player.getPreparedSpell(1));
    }
}
