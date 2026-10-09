package com.bpm.minotaur.gamedata.history.text;

import com.bpm.minotaur.gamedata.history.CasusBelli;
import com.bpm.minotaur.gamedata.history.DoctrineCatalog;
import com.bpm.minotaur.gamedata.history.DoctrineCatalogTest;
import com.bpm.minotaur.gamedata.history.EventType;
import com.bpm.minotaur.gamedata.history.HistoryEvent;
import com.bpm.minotaur.gamedata.history.HistorySimulator;
import com.bpm.minotaur.gamedata.history.HistoryWorld;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.*;

/** Plan T1.8: the history, written by biased chroniclers. */
public class ChronicleGrammarTest {

    static DoctrineCatalog catalog;
    static ChronicleGrammar grammar;

    @BeforeClass
    public static void load() throws IOException {
        catalog = DoctrineCatalogTest.loadCatalog();
        grammar = loadGrammar();
    }

    public static ChronicleGrammar loadGrammar() throws IOException {
        File f = new File("assets/" + ChronicleGrammar.DATA_PATH);
        if (!f.isFile()) f = new File("../assets/" + ChronicleGrammar.DATA_PATH);
        return ChronicleGrammar.fromJson(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
    }

    @Test
    public void everyEventAndCasusHasThreeTemplatesInEveryVoice() {
        for (EventType t : EventType.values()) {
            for (ChronicleGrammar.Bias b : ChronicleGrammar.Bias.values()) {
                assertTrue(t + " " + b, grammar.templateCount(t.name(), b) >= 3);
            }
        }
        for (String variant : new String[]{"BATTLE_LORD_SLAIN", "VASSAL_OATH_FORCED", "SEEKER_FELL_UNKNOWN"}) {
            for (ChronicleGrammar.Bias b : ChronicleGrammar.Bias.values()) {
                assertTrue(variant + " " + b, grammar.templateCount(variant, b) >= 3);
            }
        }
        for (CasusBelli cb : CasusBelli.values()) {
            for (ChronicleGrammar.Bias b : ChronicleGrammar.Bias.values()) {
                assertTrue(cb + " " + b, grammar.casusCount(cb, b) >= 3);
            }
        }
    }

    @Test
    public void renderingIsDeterministicAndEveryTokenResolves() {
        for (long seed = 0; seed < 20; seed++) {
            HistoryWorld w = HistorySimulator.prehistory(seed, catalog);
            for (HistoryEvent e : w.events()) {
                for (ChronicleGrammar.Bias b : ChronicleGrammar.Bias.values()) {
                    Chronicler c = Chronicler.of(w, e, b, catalog);
                    String text = grammar.render(w, e, c);
                    assertEquals(text, grammar.render(w, e, Chronicler.of(w, e, b, catalog)));
                    assertFalse(seed + " " + e + " " + b + ": " + text, text.contains("{") || text.contains("#"));
                    assertTrue(text, Character.isUpperCase(text.charAt(0)) || text.charAt(0) == '"');
                }
            }
        }
    }

    @Test
    public void theTwoSidesTellTheSameBattleDifferently() {
        HistoryWorld w = HistorySimulator.prehistory(1L, catalog);
        HistoryEvent battle = null;
        for (HistoryEvent e : w.events()) {
            if (e.type == EventType.BATTLE) {
                battle = e;
                break;
            }
        }
        assertNotNull(battle);
        Chronicler winner = Chronicler.of(w, battle, ChronicleGrammar.Bias.FOR, catalog);
        Chronicler loser = Chronicler.of(w, battle, ChronicleGrammar.Bias.AGAINST, catalog);
        assertEquals(battle.houseA, winner.houseId);
        assertEquals(battle.houseB, loser.houseId);
        assertNotEquals(grammar.render(w, battle, winner), grammar.render(w, battle, loser));
    }

    @Test
    public void aUsurperIsTheUsurperOnlyToTheOtherSide() {
        for (long seed = 0; seed < 30; seed++) {
            HistoryWorld w = HistorySimulator.prehistory(seed, catalog);
            for (HistoryEvent e : w.events()) {
                if (e.type != EventType.USURPATION) continue;
                String own = Epithets.of(w, w.figure(e.figureA), e.houseA);
                String rival = Epithets.of(w, w.figure(e.figureA), -1);
                assertEquals("the Steadfast", own);
                assertEquals("the Usurper", rival);
                return;
            }
        }
        fail("no usurpation in 30 seeds");
    }

    @Test
    public void thePlayerEarnsAnEpithetFromTheirDeeds() {
        HistoryWorld w = HistorySimulator.prehistory(2L, catalog);
        assertEquals("the Seeker", Epithets.player(w));
        HistorySimulator.applyDeed(w, new com.bpm.minotaur.gamedata.history.PlayerDeed(
                com.bpm.minotaur.gamedata.history.PlayerDeed.Kind.SLEW_FIGURE, w.lordOf(w.gashHolder(0)).id, 0));
        assertEquals("Gashbreaker", Epithets.player(w));
    }

    @Test
    public void wakingNewsIsTheLoudestFewEventsInOrder() {
        HistoryWorld w = HistorySimulator.prehistory(4L, catalog);
        java.util.List<HistoryEvent> season = w.events().subList(200, 260);
        java.util.List<String> news = Headlines.of(w, season, 3, grammar, catalog);
        assertTrue(news.size() <= 3 && !news.isEmpty());
        for (String line : news) {
            assertFalse("a natural death is not news: " + line, line.contains("died in bed") || line.contains("Age took"));
        }
        assertTrue(Headlines.of(w, java.util.Collections.<HistoryEvent>emptyList(), 3, grammar, catalog).isEmpty());
    }

    @Test
    public void seedOneReadsAsItAlwaysHas() throws IOException {
        String actual = ChronicleDump.render(HistorySimulator.prehistory(1L, catalog), grammar, catalog, 40);
        File golden = new File("src/test/resources/chronicle/seed1.golden.txt");
        if (!golden.isFile()) golden = new File("core/src/test/resources/chronicle/seed1.golden.txt");
        File out = new File("build/chronicle/seed1.actual.txt");
        out.getParentFile().mkdirs();
        Files.write(out.toPath(), actual.getBytes(StandardCharsets.UTF_8));
        assertTrue("missing golden; review " + out.getAbsolutePath() + " and copy it to " + golden, golden.isFile());
        String expected = new String(Files.readAllBytes(golden.toPath()), StandardCharsets.UTF_8).replace("\r\n", "\n");
        assertEquals("chronicle changed; if intended, copy " + out.getAbsolutePath() + " over the golden file",
                expected, actual);
    }

    /** Writes whole chronicles for human prose review (plan T1.8): core/build/chronicle/seed-N.txt. */
    @Test
    public void dumpChroniclesForReview() throws IOException {
        for (long seed = 1; seed <= 3; seed++) {
            HistoryWorld w = HistorySimulator.prehistory(seed, catalog);
            File out = new File("build/chronicle/seed-" + seed + ".txt");
            out.getParentFile().mkdirs();
            Files.write(out.toPath(), ChronicleDump.render(w, grammar, catalog, Integer.MAX_VALUE)
                    .getBytes(StandardCharsets.UTF_8));
        }
    }
}
