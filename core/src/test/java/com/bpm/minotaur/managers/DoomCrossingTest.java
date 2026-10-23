package com.bpm.minotaur.managers;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/** "World Log: Bridges and Reckonings" (#290): a death while the Warden stands is a crossing. */
public class DoomCrossingTest {

    private DoomManager doom;

    @Before
    public void setUp() {
        doom = DoomManager.getInstance();
        doom.resetDeaths();
    }

    @After
    public void tearDown() {
        doom.resetDeaths();
    }

    private void die(int times) {
        for (int i = 0; i < times; i++) doom.incrementDeaths();
    }

    @Test
    public void aDeathWhileTheWardenStandsIsACrossingNotAWipe() {
        die(DoomManager.maxDeaths());
        assertTrue(doom.isBridgeBossActive());
        assertEquals(1, doom.getBridgeBossSummons());

        die(1);
        assertTrue(doom.isCrossingPending());
        assertEquals("the Bridge begins again from nothing", 0, doom.getDeathCount());
        assertFalse(doom.isBridgeBossActive());
        assertEquals("the next Warden is harder for it", 1, doom.getBridgeBossSummons());

        doom.clearCrossing();
        assertFalse(doom.isCrossingPending());
    }

    @Test
    public void theWardensFallStillEndsTheBridge() {
        die(DoomManager.maxDeaths());
        doom.onBridgeBossDefeated();
        assertEquals(0, doom.getDeathCount());
        assertFalse(doom.isBridgeBossActive());
        assertFalse("a fall is no crossing", doom.isCrossingPending());
        die(DoomManager.maxDeaths());
        assertEquals(2, doom.getBridgeBossSummons());
    }
}
