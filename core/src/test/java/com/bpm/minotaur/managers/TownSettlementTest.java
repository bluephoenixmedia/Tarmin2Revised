package com.bpm.minotaur.managers;

import com.bpm.minotaur.gamedata.history.DoctrineCatalog;
import com.bpm.minotaur.gamedata.history.DoctrineCatalogTest;
import com.bpm.minotaur.gamedata.history.HistorySimulator;
import com.bpm.minotaur.gamedata.history.town.Mortal;
import com.bpm.minotaur.gamedata.history.town.Settlement;
import com.bpm.minotaur.gamedata.history.town.Town;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.*;

/** ADR 0005, plan T4.2 AC: "NPC names match history characters". */
public class TownSettlementTest {

    private static DoctrineCatalog catalog;

    @BeforeClass
    public static void load() throws IOException {
        catalog = DoctrineCatalogTest.loadCatalog();
    }

    @Test
    public void aTownIsASettlementOfTheHistoryAndEveryOneOfItsFolkIsAMortalOfIt() {
        HistoryManager h = HistoryManager.create(21L, catalog);
        Town t = h.town(Town.keyOf(3, 2, 5));
        Settlement s = h.settlementOf(t.key);
        assertNotNull(s);
        assertEquals(s.name, t.name);
        assertEquals(s.allegiance, t.allegiance);
        assertEquals(s.seats.length, t.folk.size());
        for (Town.Folk f : t.folk) {
            Mortal m = h.world().mortal(f.mortalId);
            assertNotNull("a character of the history: " + f.name, m);
            assertTrue(m.isAlive());
            assertEquals(m.name, f.name);
            assertEquals(s.seats[f.index], f.role);
        }
    }

    @Test
    public void eachTownIsItsOwnSettlementTheSameEveryTimeAndAfterALoad() {
        HistoryManager h = HistoryManager.create(22L, catalog);
        String a = Town.keyOf(3, 2, 5);
        String b = Town.keyOf(4, -7, 1);
        assertNotEquals(h.town(a).name, h.town(b).name);
        assertEquals(h.town(a).name, h.town(a).name);
        HistoryManager loaded = HistoryManager.fromSave(0L, h.toSave(), catalog);
        assertEquals(h.town(b).name, loaded.town(b).name);
        assertEquals(h.town(b).folk.get(0).name, loaded.town(b).folk.get(0).name);
    }

    @Test
    public void onceEverySettlementIsSpokenForTownsAreMadeUpAsBefore() {
        HistoryManager h = HistoryManager.create(23L, catalog);
        Set<String> names = new HashSet<>();
        for (int i = 0; i < Settlement.COUNT; i++) {
            Town t = h.town(Town.keyOf(3, i, 100));
            assertTrue("no settlement is two towns", names.add(t.name));
            assertTrue(t.folk.get(0).mortalId >= 0);
        }
        Town extra = h.town(Town.keyOf(5, 99, 99));
        assertNull(h.settlementOf(extra.key));
        assertEquals(-1, extra.folk.get(0).mortalId);
    }

    @Test
    public void whenAKeeperDiesTheirHeirStandsInTheirPlace() {
        HistoryManager h = HistoryManager.create(24L, catalog);
        String key = Town.keyOf(3, 2, 5);
        Town before = h.town(key);
        for (int i = 0; i < 400; i++) h.onSleep();
        Town after = h.town(key);
        assertEquals(before.name, after.name);
        boolean succeeded = false;
        for (int i = 0; i < before.folk.size(); i++) {
            assertEquals(before.folk.get(i).role, after.folk.get(i).role);
            if (before.folk.get(i).mortalId != after.folk.get(i).mortalId) {
                succeeded = true;
                // The heir of the heir, perhaps: but the line leads back to the keeper the player first met.
                int line = after.folk.get(i).mortalId;
                while (line >= 0 && line != before.folk.get(i).mortalId) line = h.world().mortal(line).predecessorId;
                assertEquals(before.folk.get(i).mortalId, line);
            }
        }
        assertTrue("a century of seasons buries someone", succeeded);
    }
}
