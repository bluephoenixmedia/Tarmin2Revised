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

    /** A pack with signets to give, and a count of rewards received. */
    static final class FakePack implements TownTalk.Pack {
        int signets;
        int rewards;

        public boolean hasSignet() { return signets > 0; }

        public boolean giveSignet() {
            if (signets == 0) return false;
            signets--;
            return true;
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
        pack.signets = 1;
        reeve.quest();
        assertTrue(h.quest(t.key).done);
        assertEquals(0, pack.signets);
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
        Town other = h.town(OTHER);
        TownTalk elder = new TownTalk(h, other, other.folk(Town.Role.ELDER), null, catalog, grammar, pack);
        assertTrue(elder.hasQuestBusiness());
        elder.quest();
        assertTrue(h.quest(t.key).done);
        assertTrue("the town that sent it is grateful", h.standing().of(t) > 0);
        assertTrue(chronicled(h, t));
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
