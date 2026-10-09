package com.bpm.minotaur.managers;

import com.bpm.minotaur.gamedata.history.DoctrineCatalog;
import com.bpm.minotaur.gamedata.history.DoctrineCatalogTest;
import com.bpm.minotaur.gamedata.history.EventType;
import com.bpm.minotaur.gamedata.history.HistoryEvent;
import com.bpm.minotaur.gamedata.history.text.ChronicleGrammar;
import com.bpm.minotaur.gamedata.history.text.ChronicleGrammarTest;
import com.bpm.minotaur.gamedata.history.town.Quest;
import com.bpm.minotaur.gamedata.history.town.Standing;
import com.bpm.minotaur.gamedata.history.town.Town;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

/** Plan T4.3-T4.5: talking with a town's folk, and doing its work. */
public class TownTalkTest {

    private static DoctrineCatalog catalog;
    private static ChronicleGrammar grammar;

    @BeforeClass
    public static void load() throws IOException {
        catalog = DoctrineCatalogTest.loadCatalog();
        grammar = ChronicleGrammarTest.loadGrammar();
    }

    /** A pack with signets to give, by the house each was taken from, and a count of rewards received. */
    static final class FakePack implements TownTalk.Pack {
        final List<Integer> signets = new java.util.ArrayList<>();
        int rewards;

        public boolean hasSignet(int houseId) { return signets.contains(houseId); }

        public boolean giveSignet(int houseId) {
            return signets.remove(Integer.valueOf(houseId));
        }

        public void reward(String what) { rewards++; }
    }

    private static final String OTHER = Town.keyOf(3, 40, 40);

    /** A town whose reeve offers {@code kind}, in a fresh history. */
    private static Object[] townOffering(Quest.Kind kind) {
        for (long seed = 0; seed < 50; seed++) {
            HistoryManager h = HistoryManager.create(seed, catalog);
            for (int i = 0; i < 60; i++) {
                Town t = h.town(Town.keyOf(3, i, i + 1));
                if (t.welcome != 0) continue;
                if (Quest.offer(h.world(), t, Collections.singletonList(OTHER)).kind == kind) return new Object[]{h, t};
            }
        }
        throw new AssertionError("no town offers " + kind);
    }

    private static TownTalk talk(HistoryManager h, Town t, Town.Role role, FakePack pack) {
        return new TownTalk(h, t, t.folk(role), Collections.singletonList(OTHER), catalog, grammar, pack);
    }

    private static boolean chronicled(HistoryManager h, Town t) {
        HistoryEvent last = h.world().events().get(h.world().events().size() - 1);
        return last.type == EventType.QUEST_DONE && t.name.equals(last.place);
    }

    @Test
    public void bringingASignetCompletesTheTask() {
        Object[] at = townOffering(Quest.Kind.RECOVER_SIGNET);
        HistoryManager h = (HistoryManager) at[0];
        Town t = (Town) at[1];
        FakePack pack = new FakePack();
        TownTalk reeve = talk(h, t, Town.Role.QUESTGIVER, pack);
        assertTrue(reeve.quest().contains("signet"));
        assertTrue(h.quest(t.key).accepted);
        reeve.quest();
        assertFalse("not without a signet", h.quest(t.key).done);
        int wanted = h.quest(t.key).houseId;
        int other = -1;
        for (com.bpm.minotaur.gamedata.history.House house : h.world().livingHouses()) if (house.id != wanted) other = house.id;
        pack.signets.add(other);
        String refused = reeve.quest();
        assertFalse("another house's signet will not do", h.quest(t.key).done);
        assertTrue(refused, refused.contains(h.world().house(wanted).name));
        assertEquals("and the player keeps it", 1, pack.signets.size());
        pack.signets.add(wanted);
        reeve.quest();
        assertTrue(h.quest(t.key).done);
        assertEquals(java.util.Collections.singletonList(other), pack.signets);
        assertEquals(1, pack.rewards);
        assertEquals(Standing.FAVOUR, h.standing().of(t) - (Standing.FAVOUR / 2));
        assertTrue(chronicled(h, t));
    }

    @Test
    public void killingTheBeastCompletesTheTask() {
        Object[] at = townOffering(Quest.Kind.SLAY_BEAST);
        HistoryManager h = (HistoryManager) at[0];
        Town t = (Town) at[1];
        TownTalk reeve = talk(h, t, Town.Role.QUESTGIVER, new FakePack());
        reeve.quest();
        h.recordMegabeastSlain(h.quest(t.key).beastId);
        reeve.quest();
        assertTrue(h.quest(t.key).done);
        assertTrue(chronicled(h, t));
    }

    @Test
    public void buyingTheBeastsPeaceCompletesTheTaskToo() {
        Object[] at = townOffering(Quest.Kind.SLAY_BEAST);
        HistoryManager h = (HistoryManager) at[0];
        Town t = (Town) at[1];
        TownTalk reeve = talk(h, t, Town.Role.QUESTGIVER, new FakePack());
        assertTrue("the reeve says it can be bought off", reeve.quest().toLowerCase().contains("offering"));
        h.recordMegabeastPacified(h.quest(t.key).beastId);
        reeve.quest();
        assertTrue(h.quest(t.key).done);
        assertTrue(chronicled(h, t));
    }

    @Test
    public void greetingsComeFromTheGrammarAndSpeakOfTheWarsAbove() {
        HistoryManager h = null;
        for (long seed = 0; h == null; seed++) {
            HistoryManager c = HistoryManager.create(seed, catalog);
            if (!c.world().activeWars().isEmpty()) h = c;
        }
        boolean warSpoken = false;
        for (int i = 0; i < 40; i++) {
            Town t = h.town(Town.keyOf(3, i, 7));
            while (h.standing().isHostile(t)) h.standing().change(t, Standing.FAVOUR);
            for (Town.Folk f : t.folk) {
                if (f.role == Town.Role.MERCHANT) continue;
                String line = new TownTalk(h, t, f, null, catalog, grammar, new FakePack()).greet();
                assertTrue(line, line.startsWith(f.name + ": "));
                assertFalse("every slot filled: " + line, line.contains("{") || line.contains("}"));
                boolean fromGrammar = false;
                for (String tpl : grammar.talkLines(f.role.name())) {
                    String head = tpl.split("[{]")[0];
                    if (line.contains(head)) fromGrammar = true;
                }
                assertTrue("from the grammar: " + line, fromGrammar);
                for (com.bpm.minotaur.gamedata.history.War w : h.world().activeWars()) {
                    if (line.contains(h.world().house(w.attackerId).name)) warSpoken = true;
                }
            }
        }
        assertTrue("somebody talks about a war that is really being fought", warSpoken);
    }

    @Test
    public void namingTheAgentCompletesTheTaskAndNamingTheWrongOneCostsStanding() {
        Object[] at = townOffering(Quest.Kind.UNMASK_AGENT);
        HistoryManager h = (HistoryManager) at[0];
        Town t = (Town) at[1];
        FakePack pack = new FakePack();
        talk(h, t, Town.Role.QUESTGIVER, pack).quest();
        Quest q = h.quest(t.key);
        Town.Folk innocent = null;
        for (Town.Folk f : t.folk) {
            if (f.index != q.agentIndex && f.role != Town.Role.QUESTGIVER && f.role != Town.Role.MERCHANT) innocent = f;
        }
        if (innocent != null) {
            TownTalk wrong = new TownTalk(h, t, innocent, null, catalog, grammar, pack);
            assertTrue(wrong.canAccuse());
            wrong.accuse();
            assertTrue(h.standing().of(t) < 0);
            assertFalse(q.done);
        }
        TownTalk right = new TownTalk(h, t, t.folk.get(q.agentIndex), null, catalog, grammar, pack);
        right.accuse();
        assertTrue(q.done);
        assertTrue(chronicled(h, t));
    }

    @Test
    public void carryingTheMessageToTheOtherElderCompletesTheTask() {
        Object[] at = townOffering(Quest.Kind.CARRY_MESSAGE);
        HistoryManager h = (HistoryManager) at[0];
        Town t = (Town) at[1];
        FakePack pack = new FakePack();
        talk(h, t, Town.Role.QUESTGIVER, pack).quest();
        Quest q = h.quest(t.key);
        if (q.warId >= 0) h.onFront(frontOf(h, q.warId));
        Town other = h.town(OTHER);
        TownTalk elder = new TownTalk(h, other, other.folk(Town.Role.ELDER), null, catalog, grammar, pack);
        assertTrue(elder.hasQuestBusiness());
        elder.quest();
        assertTrue(h.quest(t.key).done);
        assertTrue("the town that sent it is grateful", h.standing().of(t) > 0);
        assertTrue(chronicled(h, t));
    }

    private static com.bpm.minotaur.gamedata.history.war.Front frontOf(HistoryManager h, int warId) {
        for (com.bpm.minotaur.gamedata.history.War w : h.world().wars()) {
            if (w.id == warId) return new com.bpm.minotaur.gamedata.history.war.Front(w.id, w.attackerId, w.defenderId,
                    new com.badlogic.gdx.math.GridPoint2(4, 4));
        }
        throw new AssertionError("no war " + warId);
    }

    @Test
    public void aMessageInWartimeMustCrossThatWarsFrontBeforeTheElderTakesIt() {
        HistoryManager h = null;
        Town t = null;
        search:
        for (long seed = 0; seed < 80; seed++) {
            HistoryManager candidate = HistoryManager.create(seed, catalog);
            for (int i = 0; i < 60; i++) {
                Town town = candidate.town(Town.keyOf(3, i, i + 1));
                if (town.welcome != 0) continue;
                Quest offered = Quest.offer(candidate.world(), town, Collections.singletonList(OTHER));
                if (offered.kind == Quest.Kind.CARRY_MESSAGE && offered.warId >= 0) {
                    h = candidate;
                    t = town;
                    break search;
                }
            }
        }
        assertNotNull("some reeve sends a message through a war", h);
        FakePack pack = new FakePack();
        String asked = talk(h, t, Town.Role.QUESTGIVER, pack).quest();
        Quest q = h.quest(t.key);
        com.bpm.minotaur.gamedata.history.War war = null;
        for (com.bpm.minotaur.gamedata.history.War w : h.world().wars()) if (w.id == q.warId) war = w;
        assertTrue("the reeve names the war: " + asked, asked.contains(h.world().house(war.attackerId).name));

        Town other = h.town(OTHER);
        TownTalk elder = new TownTalk(h, other, other.folk(Town.Role.ELDER), null, catalog, grammar, pack);
        String early = elder.quest();
        assertFalse("not until it has crossed the lines", q.done);
        assertTrue(early, early.toLowerCase().contains("war"));

        assertNull("another war's front is not this one's",
                h.onFront(new com.bpm.minotaur.gamedata.history.war.Front(q.warId + 1000, 0, 1, new com.badlogic.gdx.math.GridPoint2())));
        assertNotNull("standing on the war's front carries it through", h.onFront(frontOf(h, q.warId)));
        assertTrue(HistoryManager.fromSave(0L, h.toSave(), catalog).quest(t.key).crossedFront);
        elder.quest();
        assertTrue(q.done);
    }

    @Test
    public void tasksAndStandingSurviveALoad() {
        Object[] at = townOffering(Quest.Kind.RECOVER_SIGNET);
        HistoryManager h = (HistoryManager) at[0];
        Town t = (Town) at[1];
        talk(h, t, Town.Role.QUESTGIVER, new FakePack()).quest();
        h.standing().change(t, -10);
        HistoryManager loaded = HistoryManager.fromSave(0L, h.toSave(), catalog);
        assertTrue(loaded.quest(t.key).accepted);
        assertEquals(h.standing().of(t), loaded.standing().of(t));
    }

    @Test
    public void rumoursAreRecentNewsAndAHostileTownWillNotTalk() {
        HistoryManager h = HistoryManager.create(3L, catalog);
        for (int i = 0; i < 4; i++) h.onSleep();
        Town t = null;
        for (int i = 0; t == null || t.welcome != 0; i++) t = h.town(Town.keyOf(3, i, 2));
        TownTalk inn = talk(h, t, Town.Role.INNKEEPER, new FakePack());
        List<String> heard = inn.rumours();
        assertFalse(heard.isEmpty());
        assertTrue(heard.size() <= TownTalk.RUMOURS);
        assertTrue(inn.greet().startsWith(t.folk(Town.Role.INNKEEPER).name));
        h.standing().change(t, Standing.CRIME);
        assertTrue(inn.hostile());
        assertTrue(inn.greet().contains("Get out"));
    }
}
