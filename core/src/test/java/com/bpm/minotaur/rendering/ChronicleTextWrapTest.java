package com.bpm.minotaur.rendering;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class ChronicleTextWrapTest {

    @Test
    public void testShortMessageDoesNotWrap() {
        String shortMsg = "Picked up Blue Potion";
        List<String> lines = Hud.wrapMessageText(shortMsg, null, null, 620f);
        assertEquals(1, lines.size());
        assertEquals("Picked up Blue Potion", lines.get(0));
    }

    @Test
    public void testLongMessageWrapsAtWordBoundary() {
        String longMsg = "Equipped: Rusty Sword (Right) & Wooden Cross (Left). Press [S] to swap hands.";
        List<String> lines = Hud.wrapMessageText(longMsg, null, null, 620f);
        assertTrue("Long message must wrap into multiple lines", lines.size() >= 2);

        // Verify that rejoining with spaces recovers the original text (no words dropped)
        String rejoined = String.join(" ", lines);
        assertEquals(longMsg, rejoined);

        // Verify each line does not exceed max character width fallback
        for (String line : lines) {
            assertTrue("Each line must fit within character width boundary", line.length() <= 55);
        }
    }

    @Test
    public void testMultiSentenceLongMessage() {
        String multiMsg = "You awaken back at the Shelter... Tarmin's ancient hunger continues to gnaw at the roots of the world. Beware the Minotaur.";
        List<String> lines = Hud.wrapMessageText(multiMsg, null, null, 620f);
        assertTrue("Multi-sentence message must wrap into at least 3 lines", lines.size() >= 3);

        String rejoined = String.join(" ", lines);
        assertEquals(multiMsg, rejoined);
    }

    @Test
    public void testNullAndEmptyMessages() {
        assertTrue(Hud.wrapMessageText(null, null, null, 620f).isEmpty());
        assertTrue(Hud.wrapMessageText("", null, null, 620f).isEmpty());
    }

    @Test
    public void testLongWordSplitsWithoutDroppingCharacters() {
        String longWord = "SupercalifragilisticexpialidociousSupercalifragilisticexpialidocious";
        List<String> lines = Hud.wrapMessageText(longWord, null, null, 620f);
        assertTrue("Word longer than limit must split", lines.size() >= 2);
        String combined = String.join("", lines);
        assertEquals(longWord, combined);
    }
}
