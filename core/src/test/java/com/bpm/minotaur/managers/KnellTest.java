package com.bpm.minotaur.managers;

import com.bpm.minotaur.gamedata.history.DoctrineCatalog;
import com.bpm.minotaur.gamedata.history.DoctrineCatalogTest;
import com.bpm.minotaur.gamedata.history.EventType;
import com.bpm.minotaur.gamedata.history.Figure;
import com.bpm.minotaur.gamedata.history.HistoryEvent;
import com.bpm.minotaur.gamedata.history.HistorySimulator;
import com.bpm.minotaur.gamedata.history.HistoryWorld;
import com.bpm.minotaur.gamedata.history.House;
import com.bpm.minotaur.gamedata.history.text.ChronicleGrammar;
import com.bpm.minotaur.gamedata.history.text.ChronicleGrammarTest;
import com.bpm.minotaur.gamedata.history.text.Knell;
import com.bpm.minotaur.ui.UiGlyphs;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.util.EnumSet;
import java.util.List;

import static org.junit.Assert.*;

/** Plan K2-K4: what tolls Tarmin's knell, in what words, and how the crier batches it. */
public class KnellTest {

    private static DoctrineCatalog catalog;
    private static ChronicleGrammar grammar;

    @BeforeClass
    public static void load() throws IOException {
        catalog = DoctrineCatalogTest.loadCatalog();
        grammar = ChronicleGrammarTest.loadGrammar();
    }

    private static House great(HistoryWorld w) {
        return w.gashHolder(0);
    }

    private static House lesser(HistoryWorld w) {
        for (House h : w.livingHouses()) if (!h.isGreat() && !h.holdsCastle) return h;
        throw new AssertionError();
    }

    private static HistoryEvent event(EventType type) {
        return new HistoryEvent(0, 0, type);
    }

    // ------------------------------------------------------------------ K2: what tolls

    @Test
    public void greatEventsTollAndLesserNewsDoesNot() {
        HistoryWorld w = HistorySimulator.prehistory(71L, catalog);
        EnumSet<EventType> always = EnumSet.of(EventType.SEAT_SEIZED, EventType.HOUSE_EXTINGUISHED,
                EventType.LORD_SLAIN_IN_COURT, EventType.SLAIN_BY_PLAYER, EventType.PEACE, EventType.MEGABEAST_STIRS,
                EventType.MEGABEAST_SLAIN, EventType.MEGABEAST_PACIFIED, EventType.TOWN_BETRAYED, EventType.TARMIN_ASCENDANT);
        EnumSet<EventType> conditional = EnumSet.of(EventType.SUCCESSION, EventType.USURPATION,
                EventType.DISPUTED_SUCCESSION, EventType.BATTLE);
        for (EventType t : EventType.values()) {
            if (conditional.contains(t)) continue;
            assertEquals(t + (always.contains(t) ? " tolls" : " is a rumour at most"),
                    always.contains(t), Knell.tolls(w, event(t)));
        }
    }

    @Test
    public void theHeadOfAGreatHouseDyingTollsALesserOneDoesNot() {
        HistoryWorld w = HistorySimulator.prehistory(72L, catalog);
        for (EventType t : new EventType[]{EventType.SUCCESSION, EventType.USURPATION, EventType.DISPUTED_SUCCESSION}) {
            HistoryEvent e = event(t);
            e.houseA = great(w).id;
            assertTrue(t + " in a great house", Knell.tolls(w, e));
            e.houseA = lesser(w).id;
            assertFalse(t + " in a lesser house", Knell.tolls(w, e));
        }
    }

    @Test
    public void aBattleTollsOnlyWhenSomeoneOfNoteFalls() {
        HistoryWorld w = HistorySimulator.prehistory(73L, catalog);
        HistoryEvent e = event(EventType.BATTLE);
        assertFalse(Knell.tolls(w, e));
        e.figureB = 0;
        assertTrue(Knell.tolls(w, e));
    }

    // ------------------------------------------------------------------ K3: Tarmin's voice

    @Test
    public void tarminHasThreeThingsToSayOfEveryGreatEventAndSomethingForTheRest() {
        for (EventType t : EventType.values()) {
            if (!Knell.couldToll(t)) continue;
            assertTrue(t + " has three of Tarmin's lines", grammar.knellCount(t) >= 3);
        }
        assertTrue(grammar.knellCount(null) >= 2);
    }

    @Test
    public void everyKnellOfALongHistoryFillsItsSlotsAndTheFontCanDrawIt() {
        HistoryManager h = HistoryManager.create(74L, catalog);
        for (int i = 0; i < 120; i++) h.onSleep();
        int tolled = 0;
        for (HistoryEvent e : h.world().events()) {
            if (!Knell.tolls(h.world(), e)) continue;
            String line = Knell.line(h.world(), e, grammar, catalog);
            assertFalse(line, line.isEmpty() || line.contains("{") || line.contains("}") || line.contains("#"));
            assertEquals(line, UiGlyphs.sanitize(line), line);
            tolled++;
        }
        assertTrue("a long history has its great days: " + tolled, tolled > 10);
    }

    // ------------------------------------------------------------------ K4: the crier

    @Test
    public void theCrierTellsOnlyWhatIsNewAndUnlocksIt() {
        HistoryManager h = HistoryManager.create(75L, catalog);
        KnellCrier crier = new KnellCrier(h, grammar, catalog);
        assertTrue("old news does not toll", crier.listen().isEmpty());
        Figure lord = h.world().lordOf(great(h.world()));
        h.recordLordSlainInCourt(lord.id, -1);
        List<String> heard = crier.listen();
        assertEquals(1, heard.size());
        HistoryEvent e = h.world().events().get(h.world().events().size() - 1);
        assertTrue("told once, it is known history", h.isUnlocked(e.id));
        assertTrue("and not told twice", crier.listen().isEmpty());
    }

    @Test
    public void aCrowdedDayTollsOnceWithThreeLinesAndAShrug() {
        HistoryManager h = HistoryManager.create(76L, catalog);
        KnellCrier crier = new KnellCrier(h, grammar, catalog);
        int slain = 0;
        for (House house : h.world().livingHouses()) {
            Figure lord = h.world().lordOf(house);
            if (lord == null || !lord.isAlive() || lord.ageless) continue;
            h.recordLordSlainInCourt(lord.id, -1);
            if (++slain == 5) break;
        }
        List<String> heard = crier.listen();
        assertEquals("three lines, and Tarmin waving at the rest", KnellCrier.MAX_LINES + 1, heard.size());
    }

    @Test
    public void nothingFromBeforeALoadTolls() {
        HistoryManager h = HistoryManager.create(77L, catalog);
        h.recordLordSlainInCourt(h.world().lordOf(great(h.world())).id, -1);
        HistoryManager loaded = HistoryManager.fromSave(0L, h.toSave(), catalog);
        assertTrue(new KnellCrier(loaded, grammar, catalog).listen().isEmpty());
    }
}
