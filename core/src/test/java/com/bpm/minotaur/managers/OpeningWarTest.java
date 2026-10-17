package com.bpm.minotaur.managers;

import com.bpm.minotaur.gamedata.history.DoctrineCatalog;
import com.bpm.minotaur.gamedata.history.DoctrineCatalogTest;
import com.bpm.minotaur.gamedata.history.EventType;
import com.bpm.minotaur.gamedata.history.HistoryEvent;
import com.bpm.minotaur.gamedata.history.HistorySimulator;
import com.bpm.minotaur.gamedata.history.HistoryWorld;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.*;

/** Living War W6: a new game always opens on a war, so there is a war to hear from the first step. */
public class OpeningWarTest {

    private static DoctrineCatalog catalog;

    @BeforeClass
    public static void load() throws IOException {
        catalog = DoctrineCatalogTest.loadCatalog();
    }

    @Test
    public void everyNewWorldOpensWithAWarUnderWay() {
        for (long seed = 1; seed <= 100; seed++) {
            HistoryManager h = HistoryManager.create(seed, catalog);
            assertFalse("seed " + seed + " opens in peace", h.world().activeWars().isEmpty());
        }
    }

    @Test
    public void aQuietPrehistoryIsBrokenByItsMostAggrievedHouse() {
        int quiet = 0;
        for (long seed = 1; seed <= 200 && quiet < 3; seed++) {
            HistoryWorld past = HistorySimulator.prehistory(seed, catalog);
            if (!past.activeWars().isEmpty()) continue;
            quiet++;
            HistoryManager h = HistoryManager.create(seed, catalog);
            HistoryEvent declared = h.world().events().get(h.world().events().size() - 1);
            assertEquals(EventType.WAR_DECLARED, declared.type);
            assertNotNull("it has a reason", declared.casusBelli);
            assertEquals("declared in the first season of live play", past.season(), declared.season);
        }
        assertTrue("some prehistory ended in peace, or this test proves nothing", quiet > 0);
    }

    @Test
    public void theOpeningWarReplaysFromTheSave() {
        for (long seed = 1; seed <= 200; seed++) {
            if (!HistorySimulator.prehistory(seed, catalog).activeWars().isEmpty()) continue;
            HistoryManager h = HistoryManager.create(seed, catalog);
            h.onSleep();
            HistoryManager loaded = HistoryManager.fromSave(0L, h.toSave(), catalog);
            assertEquals(h.world().fingerprint(), loaded.world().fingerprint());
            return;
        }
        fail("no prehistory ended in peace");
    }

    @Test
    public void aWorldAlreadyAtWarIsLeftAlone() {
        for (long seed = 1; seed <= 50; seed++) {
            HistoryWorld past = HistorySimulator.prehistory(seed, catalog);
            if (past.activeWars().isEmpty()) continue;
            assertEquals(past.fingerprint(), HistoryManager.create(seed, catalog).world().fingerprint());
            return;
        }
    }
}
