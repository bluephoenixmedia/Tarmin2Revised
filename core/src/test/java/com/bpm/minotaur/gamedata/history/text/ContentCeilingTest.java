package com.bpm.minotaur.gamedata.history.text;

import com.bpm.minotaur.gamedata.history.DoctrineCatalog;
import com.bpm.minotaur.gamedata.history.DoctrineCatalogTest;
import com.bpm.minotaur.gamedata.history.HistoryEvent;
import com.bpm.minotaur.gamedata.history.HistorySimulator;
import com.bpm.minotaur.gamedata.history.HistoryWorld;
import com.bpm.minotaur.gamedata.history.House;
import com.bpm.minotaur.ui.UiGlyphs;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.regex.Pattern;

import static org.junit.Assert.*;

/**
 * Plan T1.9, decision D4: the chronicle is grim, political and gory, and never sexual. Every
 * string it can produce must also draw in intellivision.ttf. This test is never cut.
 */
public class ContentCeilingTest {

    /** Whole-word, case-insensitive. Kept blunt on purpose: a false alarm costs a reword. */
    private static final Pattern FORBIDDEN = Pattern.compile("\\b(" + String.join("|",
            "sex\\w*", "rape\\w*", "raping", "ravish\\w*", "lust\\w*", "naked", "nude", "nudity",
            "breast\\w*", "genital\\w*", "groin", "loins", "erotic\\w*", "seduc\\w*", "brothel\\w*",
            "whore\\w*", "harlot\\w*", "concubine\\w*", "bedded", "bedding", "orgasm\\w*", "arous\\w*",
            "aphrodisiac\\w*", "lewd\\w*", "molest\\w*", "violat\\w*", "defil\\w*", "debauch\\w*",
            "incest\\w*", "fornicat\\w*", "carnal", "lover\\w*", "kiss\\w*", "caress\\w*", "pleasure\\w*")
            + ")\\b", Pattern.CASE_INSENSITIVE);

    @Test
    public void theRawGrammarAndDoctrinesAreClean() throws IOException {
        for (String path : new String[]{ChronicleGrammar.DATA_PATH, DoctrineCatalog.DATA_PATH}) {
            File f = new File("assets/" + path);
            if (!f.isFile()) f = new File("../assets/" + path);
            String text = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
            assertFalse(path + ": " + firstMatch(text), FORBIDDEN.matcher(text).find());
            assertEquals(path + " has a character the font cannot draw", -1, UiGlyphs.firstUnsupportedIndex(text.replace("_", "")));
        }
    }

    @Test
    public void twoHundredWorldsOfChronicleStayWithinTheCeiling() throws IOException {
        DoctrineCatalog catalog = DoctrineCatalogTest.loadCatalog();
        ChronicleGrammar grammar = ChronicleGrammarTest.loadGrammar();
        for (long seed = 0; seed < 200; seed++) {
            HistoryWorld w = HistorySimulator.prehistory(seed, catalog);
            // The live-only events: a slain lord, fallen seekers (by a house and by no one), every doom stage.
            HistorySimulator.applyDeed(w, new com.bpm.minotaur.gamedata.history.PlayerDeed(
                    com.bpm.minotaur.gamedata.history.PlayerDeed.Kind.SLEW_FIGURE, w.lordOf(w.gashHolder(0)).id, 0));
            HistorySimulator.applyDeed(w, new com.bpm.minotaur.gamedata.history.PlayerDeed(
                    com.bpm.minotaur.gamedata.history.PlayerDeed.Kind.SEEKER_FELL, w.gashHolder(1).id, 0));
            HistorySimulator.applyDeed(w, new com.bpm.minotaur.gamedata.history.PlayerDeed(
                    com.bpm.minotaur.gamedata.history.PlayerDeed.Kind.SEEKER_FELL, -1, 0));
            for (int stage = 2; stage <= 4; stage++) {
                HistorySimulator.applyDeed(w, new com.bpm.minotaur.gamedata.history.PlayerDeed(
                        com.bpm.minotaur.gamedata.history.PlayerDeed.Kind.DOOM_STAGE, stage, 0));
            }
            HistorySimulator.tickSeason(w, catalog);
            for (int g = 0; g < HistoryWorld.GASH_COUNT; g++) {
                com.bpm.minotaur.gamedata.boss.SealLord.Spec lord = com.bpm.minotaur.gamedata.boss.SealLord.compose(w, g, catalog);
                check(seed, lord.name);
                for (com.bpm.minotaur.gamedata.boss.SealLord.Retainer r : lord.retinue) check(seed, r.name);
            }
            for (String line : Headlines.of(w, w.events(), 5, grammar, catalog)) check(seed, line);
            for (House h : w.houses()) {
                check(seed, h.name + " / " + h.sigil + " / " + h.words + " / " + h.holdfast);
            }
            for (int g = 0; g < HistoryWorld.GASH_COUNT; g++) check(seed, w.gashName(g));
            for (HistoryEvent e : w.events()) {
                for (ChronicleGrammar.Bias b : ChronicleGrammar.Bias.values()) {
                    Chronicler c = Chronicler.of(w, e, b, catalog);
                    check(seed, c.byline);
                    check(seed, grammar.render(w, e, c));
                }
            }
            check(seed, Epithets.player(w));
        }
    }

    private static void check(long seed, String text) {
        assertFalse("seed " + seed + ": " + text, FORBIDDEN.matcher(text).find());
        assertEquals("seed " + seed + " does not survive the font: " + text, text, UiGlyphs.sanitize(text));
    }

    private static String firstMatch(String text) {
        java.util.regex.Matcher m = FORBIDDEN.matcher(text);
        return m.find() ? m.group() : "";
    }
}
