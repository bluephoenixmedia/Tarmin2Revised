package com.bpm.minotaur.gamedata.history.text;

import com.bpm.minotaur.gamedata.history.Nemesis;
import com.bpm.minotaur.ui.UiGlyphs;
import org.junit.Test;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import static org.junit.Assert.*;

/** "World Log: the Nemesis remembers, and its fall pays" (#284): what it says to the next life. */
public class NemesisSpeechTest {

    private static Nemesis gorvak(String... victims) {
        Nemesis n = new Nemesis(0, "Gorvak the Gnawer", -1, "KOBOLD", 3, 1, 1);
        for (String v : victims) n.victims.add(v);
        return n;
    }

    @Test
    public void itAlwaysNamesALifeItTook() {
        Nemesis n = gorvak("Brannoc Thatch", "Maud Fenn");
        Random rng = new Random(3L);
        for (int i = 0; i < 100; i++) {
            String line = NemesisSpeech.line(n, rng);
            assertTrue(line, line.contains("Brannoc Thatch") || line.contains("Maud Fenn") || line.toLowerCase().contains("two of you"));
        }
    }

    @Test
    public void itHasManyThingsToSayAndCountsOnlyWhenThereIsMoreThanOne() {
        Set<String> lines = new HashSet<>();
        Random rng = new Random(9L);
        for (int i = 0; i < 200; i++) {
            String line = NemesisSpeech.line(gorvak("Brannoc Thatch"), rng);
            assertFalse(line, line.contains(" of you"));
            lines.add(line);
        }
        assertTrue(lines.size() >= 4);
    }

    @Test
    public void itNeverGuessesWhatALifeWasAndTheFontCanWriteIt() {
        Random rng = new Random(1L);
        for (int i = 0; i < 300; i++) {
            String line = NemesisSpeech.line(gorvak("Brannoc Thatch", "Maud Fenn", "Skrag Ratbite"), rng);
            assertEquals(line, UiGlyphs.sanitize(line));
            String words = " " + line.toLowerCase().replaceAll("[^a-z ]", " ") + " ";
            for (String pronoun : new String[]{" he ", " she ", " him ", " her ", " his ", " hers "}) {
                assertFalse(line, words.contains(pronoun));
            }
        }
    }
}
