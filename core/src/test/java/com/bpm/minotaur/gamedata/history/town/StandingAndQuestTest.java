package com.bpm.minotaur.gamedata.history.town;

import com.bpm.minotaur.gamedata.history.DoctrineCatalog;
import com.bpm.minotaur.gamedata.history.DoctrineCatalogTest;
import com.bpm.minotaur.gamedata.history.HistorySimulator;
import com.bpm.minotaur.gamedata.history.HistoryWorld;
import com.bpm.minotaur.ui.UiGlyphs;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.EnumSet;

import static org.junit.Assert.*;

/** Plan T4.4, T4.5, D39, D41: standing among the towns, and the work their reeves give. */
public class StandingAndQuestTest {

    private static DoctrineCatalog catalog;

    @BeforeClass
    public static void load() throws IOException {
        catalog = DoctrineCatalogTest.loadCatalog();
    }

    private static Town townOf(Allegiance a, int from) {
        for (int i = from; ; i++) {
            Town t = Town.of(3L, Town.keyOf(3, i, i));
            if (t.allegiance == a && t.welcome == 0) return t;
        }
    }

    @Test
    public void aCrimeTurnsATownHostileAndItsSistersHearOfIt() {
        Standing s = new Standing();
        Town home = townOf(Allegiance.GOBLIN_CLANS, 0);
        Town sister = townOf(Allegiance.GOBLIN_CLANS, 100);
        Town rival = townOf(Allegiance.REFUGEE_COUNCIL, 0);
        assertFalse(s.isHostile(home));
        s.change(home, Standing.CRIME);
        assertTrue(s.isHostile(home));
        assertEquals(Standing.CRIME / 2, s.of(sister));
        assertTrue("a rival power thinks better of you", s.of(rival) > 0);
        assertEquals("Hated", s.word(home));
    }

    @Test
    public void favoursRaiseStandingAndItIsClamped() {
        Standing s = new Standing();
        Town t = townOf(Allegiance.OUTCAST_COVENANT, 0);
        for (int i = 0; i < 20; i++) s.change(t, Standing.FAVOUR);
        assertEquals(Standing.MAX, s.of(t));
        assertEquals("Honoured", s.word(t));
    }

    @Test
    public void everyKindOfTaskIsOfferedSomewhereAndAsksInPlainWords() {
        HistoryWorld w = HistorySimulator.prehistory(8L, catalog);
        EnumSet<Quest.Kind> seen = EnumSet.noneOf(Quest.Kind.class);
        for (int i = 0; i < 200; i++) {
            Town t = Town.of(w.seed, Town.keyOf(3, i, -i));
            Quest q = Quest.offer(w, t, Arrays.asList(Town.keyOf(2, 50, 50)));
            seen.add(q.kind);
            String asked = q.ask(w, t, key -> Town.of(w.seed, key));
            assertEquals(asked, UiGlyphs.sanitize(asked));
            assertFalse(asked.isEmpty());
            if (q.kind == Quest.Kind.UNMASK_AGENT) {
                Town.Role role = t.folk.get(q.agentIndex).role;
                assertNotEquals(Town.Role.QUESTGIVER, role);
                assertNotEquals(Town.Role.MERCHANT, role);
            }
        }
        assertEquals(EnumSet.allOf(Quest.Kind.class), seen);
    }

    @Test
    public void theSameTownOffersTheSameTask() {
        HistoryWorld w = HistorySimulator.prehistory(9L, catalog);
        Town t = Town.of(w.seed, Town.keyOf(2, 5, 5));
        Quest a = Quest.offer(w, t, null);
        Quest b = Quest.offer(w, t, null);
        assertEquals(a.kind, b.kind);
        assertEquals(a.houseId, b.houseId);
        assertEquals(a.beastId, b.beastId);
    }
}
