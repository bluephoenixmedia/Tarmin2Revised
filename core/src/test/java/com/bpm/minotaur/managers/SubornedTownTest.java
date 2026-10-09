package com.bpm.minotaur.managers;

import com.bpm.minotaur.gamedata.history.DoctrineCatalog;
import com.bpm.minotaur.gamedata.history.DoctrineCatalogTest;
import com.bpm.minotaur.gamedata.history.EventType;
import com.bpm.minotaur.gamedata.history.FragmentKind;
import com.bpm.minotaur.gamedata.history.HistoryEvent;
import com.bpm.minotaur.gamedata.history.HistorySimulator;
import com.bpm.minotaur.gamedata.history.House;
import com.bpm.minotaur.gamedata.history.text.ChronicleGrammar;
import com.bpm.minotaur.gamedata.history.text.ChronicleGrammarTest;
import com.bpm.minotaur.gamedata.history.text.Headlines;
import com.bpm.minotaur.gamedata.history.town.Settlement;
import com.bpm.minotaur.gamedata.history.town.Standing;
import com.bpm.minotaur.gamedata.history.town.Town;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

/** Plan T4.6, D40: a town secretly bought by a house, found out in fragments, then betrayed. */
public class SubornedTownTest {

    private static DoctrineCatalog catalog;
    private static ChronicleGrammar grammar;

    @BeforeClass
    public static void load() throws IOException {
        catalog = DoctrineCatalogTest.loadCatalog();
        grammar = ChronicleGrammarTest.loadGrammar();
    }

    private static HistoryEvent last(HistoryManager h, EventType type) {
        List<HistoryEvent> events = h.world().events();
        for (int i = events.size() - 1; i >= 0; i--) if (events.get(i).type == type) return events.get(i);
        return null;
    }

    @Test
    public void prehistoryHasNoPlotsTheyComeInLivePlay() {
        HistoryManager h = HistoryManager.create(31L, catalog);
        assertNull("subornation is the houses reaching down during the run", last(h, EventType.TOWN_SUBORNED));
        int plots = 0;
        for (long seed = 31; seed < 36; seed++) {
            HistoryManager run = HistoryManager.create(seed, catalog);
            for (int i = 0; i < 160; i++) run.onSleep();
            for (Settlement s : run.world().settlements()) if (s.subornedBy >= 0 || s.betrayed) plots++;
        }
        assertTrue("forty years of play see some town bought: " + plots, plots > 0);
    }

    @Test
    public void aForcedSubornationIsFoundInFragmentsBeforeTheBetrayalWhichTurnsTheTownHostile() {
        HistoryManager h = HistoryManager.create(32L, catalog);
        String key = Town.keyOf(3, 4, 6);
        Town town = h.town(key);
        Settlement s = h.settlementOf(key);
        House buyer = h.world().livingHouses().get(0);
        while (h.standing().isHostile(town)) h.standing().change(town, Standing.FAVOUR);

        HistorySimulator.forceSuborn(h.world(), s.id, buyer.id);
        HistoryEvent plot = last(h, EventType.TOWN_SUBORNED);
        assertNotNull(plot);
        assertEquals(buyer.id, plot.houseA);
        assertEquals(s.name, plot.place);
        assertFalse("a secret: the town still smiles", h.standing().isHostile(h.town(key)));
        assertFalse("never a rumour", Headlines.pick(Collections.singletonList(plot), 3).contains(plot));

        HistoryEvent told = h.readFragment(FragmentKind.PAGE);
        assertEquals("the plot in motion is what a page gives up", plot.id, told.id);
        assertFalse("found out before the betrayal", s.betrayed);

        int seasons = 0;
        while (!s.betrayed && seasons++ < 40) h.onSleep();
        assertTrue("the betrayal comes", s.betrayed);
        HistoryEvent betrayal = last(h, EventType.TOWN_BETRAYED);
        assertEquals(buyer.id, betrayal.houseA);
        assertTrue("and it is news", Headlines.pick(Collections.singletonList(betrayal), 3).contains(betrayal));
        Town after = h.town(key);
        assertTrue("the betrayal flips the town hostile", h.standing().isHostile(after));
        TownTalk elder = new TownTalk(h, after, after.folk(Town.Role.ELDER), null, catalog, grammar, new TownTalkTest.FakePack());
        assertTrue(elder.hostile());
    }

    @Test
    public void plotsReplayFromTheSave() {
        HistoryManager h = HistoryManager.create(33L, catalog);
        for (int i = 0; i < 160; i++) h.onSleep();
        HistoryManager loaded = HistoryManager.fromSave(0L, h.toSave(), catalog);
        assertEquals(h.world().fingerprint(), loaded.world().fingerprint());
    }
}
