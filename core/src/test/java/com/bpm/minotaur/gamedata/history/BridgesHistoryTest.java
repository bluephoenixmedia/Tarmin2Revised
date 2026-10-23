package com.bpm.minotaur.gamedata.history;

import com.bpm.minotaur.managers.HistoryManager;
import org.junit.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.Assert.*;

/** "World Log: Bridges and Reckonings" (#290): the ends of a Bridge, kept by the history. */
public class BridgesHistoryTest {

    private static HistoryManager fresh() throws IOException {
        return HistoryManager.create(11L, DoctrineCatalogTest.loadCatalog());
    }

    @Test
    public void wardensFallenAndCrossingsReplay() throws IOException {
        HistoryManager m = fresh();
        m.recordWardenFell();
        m.recordCrossing();
        m.recordCrossing();
        assertEquals(1, m.world().wardensFallen());
        assertEquals(2, m.world().crossings());
        HistoryManager loaded = HistoryManager.fromSave(11L, m.toSave(), DoctrineCatalogTest.loadCatalog());
        assertEquals(1, loaded.world().wardensFallen());
        assertEquals(2, loaded.world().crossings());
    }

    @Test
    public void asterionIsSpentLastAndTheKeepersCountDown() throws IOException {
        HistoryManager m = fresh();
        assertEquals(13, OldGod.keepers(m.world()));
        List<OldGod> first = OldGod.spendable(m.world());
        assertEquals(12, first.size());
        assertFalse("the warden's god is spent last", first.contains(OldGod.ASTERION));

        for (OldGod god : first) m.recordGodSpent(god);
        assertEquals(1, OldGod.keepers(m.world()));
        assertEquals(java.util.Collections.singletonList(OldGod.ASTERION), OldGod.spendable(m.world()));

        m.recordGodSpent(OldGod.ASTERION);
        assertEquals(0, OldGod.keepers(m.world()));
        assertTrue("no keeper is left: the next crossing is the last", OldGod.spendable(m.world()).isEmpty());
    }
}
