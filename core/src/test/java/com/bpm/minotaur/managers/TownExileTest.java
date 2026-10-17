package com.bpm.minotaur.managers;

import com.bpm.minotaur.gamedata.history.DoctrineCatalog;
import com.bpm.minotaur.gamedata.history.DoctrineCatalogTest;
import com.bpm.minotaur.gamedata.history.Figure;
import com.bpm.minotaur.gamedata.history.House;
import com.bpm.minotaur.gamedata.history.text.ChronicleGrammar;
import com.bpm.minotaur.gamedata.history.text.ChronicleGrammarTest;
import com.bpm.minotaur.gamedata.history.town.Quest;
import com.bpm.minotaur.gamedata.history.town.Town;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * Plan T4.2, D37: a town's folk include characters of the history -- the sworn swords of broken
 * houses, who outlived the blood they served and went under the ground.
 */
public class TownExileTest {

    private static DoctrineCatalog catalog;
    private static ChronicleGrammar grammar;

    @BeforeClass
    public static void load() throws IOException {
        catalog = DoctrineCatalogTest.loadCatalog();
        grammar = ChronicleGrammarTest.loadGrammar();
    }

    /** A history with at least {@code n} living exiles. */
    private static HistoryManager withExiles(int n) {
        for (long seed = 0; seed < 300; seed++) {
            HistoryManager h = HistoryManager.create(seed, catalog);
            if (h.world().exiles().size() >= n) return h;
        }
        throw new AssertionError("no history with " + n + " exiles");
    }

    private static Town.Folk exileOf(Town t) {
        for (Town.Folk f : t.folk) if (f.role == Town.Role.EXILE) return f;
        return null;
    }

    private static Town arrive(HistoryManager h, String key) {
        h.seatExile(key);
        return h.town(key);
    }

    @Test
    public void exilesAreLivingFiguresWhoHoldNoSeat() {
        int seen = 0;
        for (long seed = 0; seed < 30; seed++) {
            HistoryManager h = HistoryManager.create(seed, catalog);
            for (Figure f : h.world().exiles()) {
                assertTrue(f.isAlive());
                House house = h.world().house(f.houseId);
                assertNotEquals("a lord is not in exile", house.lordId, f.id);
                assertTrue("a sword whose house stands, or a kinsman who lost a succession",
                        house.isExtinct() || f.role == Figure.Role.KIN);
                seen++;
            }
        }
        assertTrue("some histories leave exiles", seen > 0);
    }

    @Test
    public void aTownTakesInAnExileOfTheHistoryByNameAndNoExileLivesInTwo() {
        HistoryManager h = withExiles(2);
        Town a = arrive(h, Town.keyOf(3, 1, 2));
        Town b = arrive(h, Town.keyOf(4, -5, 7));
        Town.Folk first = exileOf(a);
        Town.Folk second = exileOf(b);
        assertNotNull(first);
        assertNotNull(second);
        assertEquals(h.world().figure(first.figureId).name, first.name);
        assertNotEquals(first.figureId, second.figureId);
        assertEquals("an exile is the last of a town's folk", a.folk.size() - 1, first.index);
        assertEquals("asking again finds the same exile", first.figureId, exileOf(arrive(h, a.key)).figureId);
    }

    @Test
    public void exilesStayWhereTheyWereAfterSaveAndLoad() {
        HistoryManager h = withExiles(2);
        arrive(h, Town.keyOf(3, 1, 2));
        String b = Town.keyOf(4, -5, 7);
        arrive(h, b);
        HistoryManager loaded = HistoryManager.fromSave(0L, h.toSave(), catalog);
        assertEquals(exileOf(h.town(b)).figureId, exileOf(loaded.town(b)).figureId);
    }

    @Test
    public void onceTheExilesAreSpentTownsHaveNone() {
        HistoryManager h = withExiles(1);
        int n = h.world().exiles().size();
        Set<Integer> seated = new HashSet<>();
        for (int i = 0; i < n; i++) assertTrue(seated.add(exileOf(arrive(h, Town.keyOf(3, i, -i - 1))).figureId));
        assertNull(exileOf(arrive(h, Town.keyOf(5, 99, 99))));
        assertNull("a town the player has not stood in has taken no one in", exileOf(h.town(Town.keyOf(5, 98, 98))));
    }

    @Test
    public void anExileWhoDiesIsNoLongerInTheirTown() {
        HistoryManager h = withExiles(1);
        String key = Town.keyOf(3, 1, 2);
        Town.Folk e = exileOf(arrive(h, key));
        h.world().figure(e.figureId).deathSeason = h.world().season();
        assertNull(exileOf(h.town(key)));
    }

    @Test
    public void anExileSpeaksOfTheHouseTheyServed() {
        HistoryManager h = withExiles(1);
        Town t = arrive(h, Town.keyOf(3, 1, 2));
        Town.Folk exile = exileOf(t);
        House fallen = h.world().house(h.world().figure(exile.figureId).houseId);
        TownTalk talk = new TownTalk(h, t, exile, Collections.emptyList(), catalog, grammar, new TownTalkTest.FakePack());
        while (talk.hostile()) h.standing().change(t, com.bpm.minotaur.gamedata.history.town.Standing.FAVOUR);
        assertTrue(talk.greet(), talk.greet().contains(fallen.name));
        assertTrue(talk.header(), talk.header().contains("Exile"));
    }

    @Test
    public void anExileIsNeverTheHouseAgentTheReeveSeeks() {
        HistoryManager h = withExiles(1);
        for (int i = 0; i < 200; i++) {
            String key = Town.keyOf(3, i, i + 1);
            Quest before = Quest.offer(h.world(), h.town(key), Collections.singletonList(Town.keyOf(3, 40, 40)));
            if (before.kind != Quest.Kind.UNMASK_AGENT) continue;
            Town t = arrive(h, key);
            Quest q = Quest.offer(h.world(), t, Collections.singletonList(Town.keyOf(3, 40, 40)));
            assertEquals("taking in an exile does not change the reeve's suspect", before.agentIndex, q.agentIndex);
            assertNotEquals(Town.Role.EXILE, t.folk.get(q.agentIndex).role);
            h.acceptQuest(q);
            TownTalk talk = new TownTalk(h, t, exileOf(t), Collections.emptyList(), catalog, grammar, new TownTalkTest.FakePack());
            assertFalse(talk.canAccuse());
            return;
        }
        fail("no town in 200 sought an agent");
    }
}
