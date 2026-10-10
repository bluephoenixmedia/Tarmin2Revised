package com.bpm.minotaur.gamedata.player;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Fixes 2026-10-10, item 1: after a death the seeker had no spell in slot 1, where a new game puts
 * Mote of Light. A new expedition now starts as a new game does, whatever emptied the slots.
 */
public class StartingSpellOnRespawnTest {

    @Test
    public void aNewExpeditionPreparesMoteOfLightIfNothingHasIt() {
        Player p = new Player(1, 1);
        p.restoreSpellbook(java.util.Collections.emptyList(), java.util.Collections.emptyList(),
                java.util.Arrays.asList(null, null), 2);
        assertNull("the slots were emptied", p.getPreparedSpells()[0]);

        p.prepareStartingSpells();

        assertTrue(p.getKnownSpellIds().contains(Player.STARTING_SPELL));
        assertEquals(Player.STARTING_SPELL, p.getPreparedSpells()[0]);
    }

    @Test
    public void aSeekerWhoMovedItKeepsTheirChoice() {
        Player p = new Player(1, 1);
        p.restoreSpellbook(java.util.Arrays.asList(Player.STARTING_SPELL, "MAGIC_MISSILE"), java.util.Collections.emptyList(),
                java.util.Arrays.asList("MAGIC_MISSILE", Player.STARTING_SPELL), 2);
        p.prepareStartingSpells();
        assertEquals("MAGIC_MISSILE", p.getPreparedSpells()[0]);
        assertEquals("not prepared twice", Player.STARTING_SPELL, p.getPreparedSpells()[1]);
    }

    @Test
    public void itTakesTheFirstFreeSlotNotAnOccupiedOne() {
        Player p = new Player(1, 1);
        p.restoreSpellbook(java.util.Arrays.asList("MAGIC_MISSILE"), java.util.Collections.emptyList(),
                java.util.Arrays.asList("MAGIC_MISSILE", null), 2);
        p.prepareStartingSpells();
        assertEquals("MAGIC_MISSILE", p.getPreparedSpells()[0]);
        assertEquals(Player.STARTING_SPELL, p.getPreparedSpells()[1]);
    }
}
