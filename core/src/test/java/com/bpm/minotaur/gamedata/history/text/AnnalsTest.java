package com.bpm.minotaur.gamedata.history.text;

import com.bpm.minotaur.gamedata.history.DoctrineCatalog;
import com.bpm.minotaur.gamedata.history.DoctrineCatalogTest;
import com.bpm.minotaur.gamedata.history.HistoryEvent;
import com.bpm.minotaur.gamedata.history.HistorySimulator;
import com.bpm.minotaur.gamedata.history.HistoryWorld;
import com.bpm.minotaur.gamedata.history.House;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;

/** Plan T1.11: what the Archive Lectern shows of the Maze's history. */
public class AnnalsTest {

    private static DoctrineCatalog catalog;
    private static ChronicleGrammar grammar;
    private static HistoryWorld world;
    private static Set<Integer> unlocked;

    @BeforeClass
    public static void load() throws IOException {
        catalog = DoctrineCatalogTest.loadCatalog();
        grammar = ChronicleGrammarTest.loadGrammar();
        world = HistorySimulator.prehistory(8L, catalog);
        unlocked = new LinkedHashSet<>();
        for (HistoryEvent e : world.events()) {
            if (e.id % 7 == 0) unlocked.add(e.id);
        }
    }

    @Test
    public void onlyUnlockedHistoryIsShown() {
        int shown = 0;
        for (Annals.Era era : Annals.entries(world, unlocked, -1, grammar, catalog)) {
            for (Annals.Entry entry : era.entries) {
                assertTrue(unlocked.contains(entry.eventId));
                shown++;
            }
        }
        assertEquals(unlocked.size(), shown);
        assertTrue(Annals.entries(world, new LinkedHashSet<>(), -1, grammar, catalog).isEmpty());
    }

    @Test
    public void aHouseShowsOnlyWhatConcernsIt() {
        House house = Annals.knownHouses(world, unlocked).get(0);
        for (Annals.Era era : Annals.entries(world, unlocked, house.id, grammar, catalog)) {
            for (Annals.Entry entry : era.entries) {
                assertTrue(world.event(entry.eventId).involvesHouse(house.id));
            }
        }
    }

    @Test
    public void erasRunInOrderAndEachEntryHasTwoContradictingAccounts() {
        List<Annals.Era> eras = Annals.entries(world, unlocked, -1, grammar, catalog);
        assertEquals("The First Century", eras.get(0).title);
        int lastYear = 0;
        for (Annals.Era era : eras) {
            for (Annals.Entry entry : era.entries) {
                assertTrue(entry.year >= lastYear);
                lastYear = entry.year;
                assertNotEquals(entry.ownSide.text, entry.otherSide.text);
                assertNotNull(entry.ownSide.byline);
                assertNotNull(entry.otherSide.byline);
            }
        }
    }

    @Test
    public void knownHousesAreThoseTheUnlockedHistoryNamesGreatHousesFirst() {
        List<House> houses = Annals.knownHouses(world, unlocked);
        assertFalse(houses.isEmpty());
        boolean seenLesser = false;
        for (House h : houses) {
            boolean named = false;
            for (int id : unlocked) named |= world.event(id).involvesHouse(h.id);
            assertTrue(h.name, named);
            if (!h.isGreat()) seenLesser = true;
            else assertFalse("great houses come first", seenLesser);
        }
    }
}
