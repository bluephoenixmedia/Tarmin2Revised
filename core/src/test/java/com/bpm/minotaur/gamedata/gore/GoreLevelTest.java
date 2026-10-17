package com.bpm.minotaur.gamedata.gore;

import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.*;

public class GoreLevelTest {

    @After
    public void restore() {
        GoreLevel.setCurrent(GoreLevel.NORMAL);
    }

    @Test
    public void normalIsTheDefault() {
        assertEquals(GoreLevel.NORMAL, GoreLevel.current());
    }

    @Test
    public void offSpawnsNothing() {
        assertFalse(GoreLevel.OFF.enabled());
        assertEquals(0, GoreLevel.OFF.count(12));
    }

    @Test
    public void lowHalvesCountsButNeverToZero() {
        assertEquals(6, GoreLevel.LOW.count(12));
        assertEquals(1, GoreLevel.LOW.count(1));
        assertEquals(0, GoreLevel.LOW.count(0));
    }

    @Test
    public void brutalDoublesCountsAndRaisesBudgets() {
        assertEquals(24, GoreLevel.BRUTAL.count(12));
        assertEquals(600, GoreLevel.BRUTAL.budget(400));
        assertEquals(400, GoreLevel.NORMAL.budget(400));
    }

    @Test
    public void onlyNormalAndBrutalKeepBloodAndDrip() {
        assertFalse(GoreLevel.LOW.persistent());
        assertFalse(GoreLevel.LOW.drips());
        assertTrue(GoreLevel.NORMAL.persistent());
        assertTrue(GoreLevel.NORMAL.drips());
        assertTrue(GoreLevel.BRUTAL.persistent());
        assertTrue(GoreLevel.BRUTAL.drips());
    }

    @Test
    public void cycleWrapsThroughEveryLevel() {
        assertEquals(GoreLevel.LOW, GoreLevel.OFF.next());
        assertEquals(GoreLevel.OFF, GoreLevel.BRUTAL.next());
    }

    @Test
    public void parseFallsBackToNormal() {
        assertEquals(GoreLevel.BRUTAL, GoreLevel.parse("BRUTAL"));
        assertEquals(GoreLevel.NORMAL, GoreLevel.parse("nonsense"));
        assertEquals(GoreLevel.NORMAL, GoreLevel.parse(null));
    }
}
