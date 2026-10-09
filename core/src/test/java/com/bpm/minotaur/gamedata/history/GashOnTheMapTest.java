package com.bpm.minotaur.gamedata.history;

import com.bpm.minotaur.gamedata.boss.SealLord;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.*;

/** Plan T2.8, D32: a gash that changes hands during the run says so on the map. */
public class GashOnTheMapTest {

    private static DoctrineCatalog catalog;

    @BeforeClass
    public static void load() throws IOException {
        catalog = DoctrineCatalogTest.loadCatalog();
    }

    /** As a war would: the seat goes to {@code winner}, in this season. */
    private static void seize(HistoryWorld w, int gash, House winner) {
        House loser = w.gashHolder(gash);
        loser.gashIndex = -1;
        winner.gashIndex = gash;
        HistoryEvent e = new HistoryEvent(w.events.size(), w.season, EventType.SEAT_SEIZED);
        e.houseA = winner.id;
        e.houseB = loser.id;
        e.gashIndex = gash;
        w.events.add(e);
    }

    private static House lesser(HistoryWorld w) {
        for (House h : w.livingHouses()) if (!h.isGreat() && !h.holdsCastle) return h;
        throw new AssertionError();
    }

    @Test
    public void theMapNamesTheHolderAndAGashTakenThisRunSaysFromWhom() {
        HistoryWorld w = HistorySimulator.prehistory(5L, catalog);
        House before = w.gashHolder(1);
        String quiet = SealLord.holderLine(w, 1);
        assertTrue(quiet, quiet.contains(before.name));
        assertFalse("seizures of prehistory are old news", quiet.contains("taken from"));

        HistorySimulator.tickSeason(w, catalog);
        House winner = lesser(w);
        seize(w, 1, winner);
        String line = SealLord.holderLine(w, 1);
        assertTrue(line, line.startsWith("held by " + winner.name));
        assertTrue(line, line.contains("taken from " + before.name + " in the year " + (w.season() / HistoryWorld.SEASONS_PER_YEAR + 1)));
        assertNull("the other gashes are unchanged", w.seizedThisRun(0));

        String notice = SealLord.seizureNotice(w, w.seizedThisRun(1));
        assertTrue(notice, notice.toLowerCase().startsWith((w.gashName(1) + " has passed to " + winner.name).toLowerCase()));
        assertTrue("a sentence", Character.isUpperCase(notice.charAt(0)));
        assertTrue(notice, notice.contains("map"));
        assertNull(SealLord.seizureNotice(w, w.events().get(0)));
    }
}
