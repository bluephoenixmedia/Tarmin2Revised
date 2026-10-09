package com.bpm.minotaur.gamedata.history;

import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.*;

/** Plan T1.4: the generated past of a world. */
public class PrehistoryTest {

    private static DoctrineCatalog catalog;

    @BeforeClass
    public static void load() throws IOException {
        catalog = DoctrineCatalogTest.loadCatalog();
    }

    @Test
    public void sameSeedSameHistory() {
        HistoryWorld a = HistorySimulator.prehistory(42L, catalog);
        HistoryWorld b = HistorySimulator.prehistory(42L, catalog);
        assertEquals(a.fingerprint(), b.fingerprint());
        assertNotEquals(a.fingerprint(), HistorySimulator.prehistory(43L, catalog).fingerprint());
    }

    @Test
    public void spansThreeHundredYearsAndTenGenerations() {
        HistoryWorld w = HistorySimulator.prehistory(7L, catalog);
        assertEquals(HistorySimulator.PREHISTORY_YEARS, w.year());
        int maxDepth = 0;
        for (Figure f : w.figures()) {
            int depth = 0;
            for (Figure p = w.figure(f.parentId); p != null; p = w.figure(p.parentId)) depth++;
            maxDepth = Math.max(maxDepth, depth + 1);
        }
        assertTrue("generations: " + maxDepth, maxDepth >= 10 && maxDepth <= 14);
    }

    @Test
    public void exactlyThreeGashesAreHeldByDistinctLivingHouses() {
        for (long seed = 0; seed < 100; seed++) {
            HistoryWorld w = HistorySimulator.prehistory(seed, catalog);
            java.util.Set<Integer> holders = new java.util.HashSet<>();
            for (int g = 0; g < HistoryWorld.GASH_COUNT; g++) {
                House h = w.gashHolder(g);
                assertNotNull("seed " + seed + " gash " + g, h);
                assertNotNull("seed " + seed + " gash " + g + " holder has a lord", w.lordOf(h));
                assertTrue(w.lordOf(h).isAlive());
                holders.add(h.id);
            }
            assertEquals("seed " + seed, 3, holders.size());
        }
    }

    @Test
    public void everyWarHasACasusBelli() {
        for (long seed = 0; seed < 50; seed++) {
            HistoryWorld w = HistorySimulator.prehistory(seed, catalog);
            int wars = 0;
            for (HistoryEvent e : w.events()) {
                if (e.type == EventType.WAR_DECLARED) {
                    wars++;
                    assertNotNull("seed " + seed + " " + e, e.casusBelli);
                }
            }
            assertTrue("seed " + seed + " had " + wars + " wars", wars >= 5);
        }
    }

    @Test
    public void disputedSuccessionsAreCommon() {
        int withDispute = 0;
        for (long seed = 0; seed < 100; seed++) {
            HistoryWorld w = HistorySimulator.prehistory(seed, catalog);
            for (HistoryEvent e : w.events()) {
                if (e.type == EventType.DISPUTED_SUCCESSION || e.type == EventType.USURPATION) {
                    withDispute++;
                    break;
                }
            }
        }
        assertTrue(withDispute + "/100", withDispute >= 95);
    }

    @Test
    public void tarminZulHasRisenAndHoldsTheCastle() {
        HistoryWorld w = HistorySimulator.prehistory(99L, catalog);
        House t = w.tarminHouse();
        assertNotNull(t);
        assertTrue(t.holdsCastle);
        assertFalse(t.isGreat());
        assertEquals(DoctrineCatalog.TARMIN_ZUL_DOCTRINE, t.doctrineId);
        assertTrue(w.lordOf(t).isAlive());
        assertEquals(1, count(w, EventType.TARMIN_ZUL_RISES));
    }

    @Test
    public void fourToEightLesserHousesSurvive() {
        for (long seed = 0; seed < 50; seed++) {
            HistoryWorld w = HistorySimulator.prehistory(seed, catalog);
            int lesser = 0;
            for (House h : w.livingHouses()) {
                if (!h.isGreat() && !h.holdsCastle) lesser++;
            }
            assertTrue("seed " + seed + " lesser " + lesser, lesser >= 4 && lesser <= 8);
        }
    }

    @Test
    public void everyKindOfPoliticalEventHappensAcrossWorlds() {
        java.util.EnumSet<EventType> seen = java.util.EnumSet.noneOf(EventType.class);
        for (long seed = 0; seed < 30; seed++) {
            for (HistoryEvent e : HistorySimulator.prehistory(seed, catalog).events()) seen.add(e.type);
        }
        for (EventType t : EventType.values()) {
            if (t == EventType.SLAIN_BY_PLAYER || t == EventType.SEEKER_FELL || t == EventType.MEGABEAST_SLAIN
                    || t == EventType.TARMIN_ASCENDANT) continue;
            assertTrue("never saw " + t, seen.contains(t));
        }
    }

    @Test
    public void generatesWellUnderTwoSeconds() {
        HistorySimulator.prehistory(1L, catalog); // warm up
        long start = System.nanoTime();
        HistorySimulator.prehistory(2L, catalog);
        long ms = (System.nanoTime() - start) / 1_000_000;
        assertTrue("took " + ms + " ms", ms < 2000);
    }

    private static int count(HistoryWorld w, EventType type) {
        int n = 0;
        for (HistoryEvent e : w.events()) if (e.type == type) n++;
        return n;
    }

    @Test
    public void fourToEightLesserHousesHoldThroughLivePlayToo() {
        for (long seed = 0; seed < 10; seed++) {
            HistoryWorld w = HistorySimulator.prehistory(seed, catalog);
            for (int season = 0; season < 200; season++) {
                HistorySimulator.tickSeason(w, catalog);
                int lesser = 0;
                for (House h : w.livingHouses()) {
                    if (!h.isGreat() && !h.holdsCastle) lesser++;
                }
                assertTrue("seed " + seed + " season " + season + " lesser " + lesser, lesser >= 4 && lesser <= 8);
            }
        }
    }
}
