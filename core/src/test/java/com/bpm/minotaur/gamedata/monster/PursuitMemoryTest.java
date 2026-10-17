package com.bpm.minotaur.gamedata.monster;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** How long a monster keeps looking for something it has lost. */
public class PursuitMemoryTest {

    @Test
    public void aDullCreatureLosesInterestQuickly() {
        assertEquals(PursuitMemory.MIN_PATIENCE_TURNS, PursuitMemory.patienceTurns(0));
        assertFalse(PursuitMemory.shouldGiveUp(2, 0));
        assertTrue(PursuitMemory.shouldGiveUp(3, 0));
    }

    @Test
    public void aCleverCreatureKeepsSearching() {
        // The hearing check already scales by intelligence; pursuit tenacity scales with it too,
        // so the thing that can hear you through fog is also the thing that keeps hunting.
        assertEquals(11, PursuitMemory.patienceTurns(8));
        assertFalse(PursuitMemory.shouldGiveUp(10, 8));
        assertTrue(PursuitMemory.shouldGiveUp(11, 8));
    }

    @Test
    public void patienceRisesWithIntelligence() {
        int previous = -1;
        for (int intelligence = 0; intelligence <= 20; intelligence++) {
            int patience = PursuitMemory.patienceTurns(intelligence);
            assertTrue("patience must never decrease as intelligence rises", patience >= previous);
            previous = patience;
        }
    }

    @Test
    public void nonsenseIntelligenceStillGivesAUsableNumber() {
        // Monster data is generated; a negative or absurd stat must not produce a monster that
        // hunts forever or gives up instantly.
        assertEquals(PursuitMemory.MIN_PATIENCE_TURNS, PursuitMemory.patienceTurns(-5));
        assertTrue(PursuitMemory.patienceTurns(9999) <= PursuitMemory.MAX_PATIENCE_TURNS);
    }

    @Test
    public void aMonsterThatJustSawYouHasNotGivenUp() {
        assertFalse(PursuitMemory.shouldGiveUp(0, 0));
        assertFalse(PursuitMemory.shouldGiveUp(0, 20));
    }
}
