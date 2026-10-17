package com.bpm.minotaur.gamedata.history.town;

import com.bpm.minotaur.gamedata.history.DoctrineCatalog;
import com.bpm.minotaur.gamedata.history.DoctrineCatalogTest;
import com.bpm.minotaur.gamedata.history.HistorySimulator;
import com.bpm.minotaur.gamedata.history.HistoryWorld;
import com.bpm.minotaur.ui.UiGlyphs;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.*;

/** ADR 0005, plan T4.2: the history's own settlements, and the mortals who keep them. */
public class SettlementsTest {

    private static DoctrineCatalog catalog;

    @BeforeClass
    public static void load() throws IOException {
        catalog = DoctrineCatalogTest.loadCatalog();
    }

    @Test
    public void prehistoryFoundsSettlementsWithEverySeatHeldByALivingMortal() {
        HistoryWorld w = HistorySimulator.prehistory(11L, catalog);
        assertEquals(Settlement.COUNT, w.settlements().size());
        Set<String> names = new HashSet<>();
        for (Settlement s : w.settlements()) {
            assertTrue("one name to a settlement: " + s.name, names.add(s.name));
            assertTrue(s.seats.length >= Town.MIN_FOLK && s.seats.length <= Town.MAX_FOLK);
            boolean reeve = false;
            for (int seat = 0; seat < s.seats.length; seat++) {
                Mortal m = w.mortal(s.holders[seat]);
                assertTrue(m.isAlive());
                assertEquals(s.id, m.settlementId);
                assertEquals(seat, m.seat);
                assertEquals(m.name, UiGlyphs.sanitize(m.name));
                reeve |= s.seats[seat] == Town.Role.QUESTGIVER;
            }
            assertTrue("every settlement has a reeve", reeve);
        }
    }

    @Test
    public void mortalsAgeDieAndAreSucceededByTheirOwn() {
        HistoryWorld w = HistorySimulator.prehistory(12L, catalog);
        int dead = 0;
        for (Mortal m : w.mortals()) {
            if (!m.isAlive()) dead++;
            if (m.predecessorId < 0) continue;
            Mortal before = w.mortal(m.predecessorId);
            assertFalse("a seat passes on a death", before.isAlive());
            assertEquals(before.deathSeason, m.seatedSeason);
            assertEquals("the seat stays in the family", before.family, m.family);
            assertEquals(before.seat, m.seat);
            assertTrue("born before taking the seat", m.birthSeason < m.seatedSeason);
        }
        assertTrue("three centuries leave graves: " + dead, dead > Settlement.COUNT * 4);
    }

    @Test
    public void theSameSeedGivesTheSameMortals() {
        assertEquals(HistorySimulator.prehistory(13L, catalog).fingerprint(), HistorySimulator.prehistory(13L, catalog).fingerprint());
        assertNotEquals(HistorySimulator.prehistory(13L, catalog).mortals().get(0).name + HistorySimulator.prehistory(13L, catalog).settlements().get(0).name,
                HistorySimulator.prehistory(14L, catalog).mortals().get(0).name + HistorySimulator.prehistory(14L, catalog).settlements().get(0).name);
    }

    @Test
    public void theLivingSeasonsGoOnBurying() {
        HistoryWorld w = HistorySimulator.prehistory(15L, catalog);
        int before = w.mortals().size();
        for (int i = 0; i < 200; i++) HistorySimulator.tickSeason(w, catalog);
        assertTrue("fifty years of play see successions", w.mortals().size() > before);
        for (Settlement s : w.settlements()) {
            for (int id : s.holders) assertTrue(w.mortal(id).isAlive());
        }
    }

    @Test
    public void mortalsLeaveTheHousesHistoryAsItWas() {
        HistoryWorld w = HistorySimulator.prehistory(16L, catalog);
        for (com.bpm.minotaur.gamedata.history.Figure f : w.figures()) assertTrue(w.house(f.houseId) != null);
        assertEquals("figure ids are still their place in the list", w.figures().size() - 1, w.figures().get(w.figures().size() - 1).id);
    }
}
