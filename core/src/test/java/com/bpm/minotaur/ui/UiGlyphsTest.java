package com.bpm.minotaur.ui;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Checks {@link UiGlyphs} against the real {@code intellivision.ttf}.
 *
 * <p>The audit found missing-glyph boxes on four screens -- the chronicle's circle headers, the
 * hearth's bullets and degree sign, the stash's arrow keycaps, the level-up banner. In every case
 * the character had simply never been checked against the font. This test reads the font's
 * character map and fails if {@link UiGlyphs#isSupported} ever disagrees with it, so swapping the
 * font or widening the supported set cannot silently reintroduce the boxes.
 */
public class UiGlyphsTest {

    private static File fontFile() {
        File f = new File("assets/fonts/intellivision.ttf");
        if (!f.exists()) {
            f = new File("../assets/fonts/intellivision.ttf");
        }
        return f;
    }

    @Test
    public void supportedSetMatchesTheFontsCharacterMap() throws IOException {
        Set<Character> inFont = readCharacterMap(fontFile());

        // The font is small enough to state outright: printable ASCII, two accented capitals and
        // the four curly quotes. If this number moves, the font moved.
        assertEquals("intellivision.ttf coverage changed", 101, inFont.size());

        StringBuilder claimedButAbsent = new StringBuilder();
        for (char c = 0; c < 0x2200; c++) {
            if (UiGlyphs.isSupported(c) && !inFont.contains(c) && c != '\n' && c != '\t' && c != '\r') {
                claimedButAbsent.append(String.format("U+%04X ", (int) c));
            }
        }
        assertEquals("UiGlyphs claims glyphs the font does not have: " + claimedButAbsent,
                0, claimedButAbsent.length());
    }

    @Test
    public void underscoreIsRejectedEvenThoughTheFontHasAGlyphForIt() throws IOException {
        // This is the whole of RC4's second half. The font draws underscore as a left arrow, so
        // GIANT_ANT reaches the player as GIANT<-ANT and reads like a typo rather than a leak.
        assertTrue("the font does have an underscore glyph", readCharacterMap(fontFile()).contains('_'));
        assertFalse("underscore must never reach a Label", UiGlyphs.isSupported('_'));
        assertEquals("Giant Ant", UiGlyphs.sanitize("Giant_Ant"));
    }

    @Test
    public void sanitizeAlwaysProducesRenderableText() {
        String[] offenders = {
                "37.0°C",              // hearth temperature readout
                "ARTISAN´S WORKSHOP",  // acute accent typed for an apostrophe
                "☒ Warm meal",         // ballot box used as a bullet
                "Mote o…",             // ellipsis
                "Crossbow Disk ×3",    // multiplication sign
                "[←→] SELECT",    // stash keycap arrows
                "★ PURE RESONANCE",    // star
                "GIB_FLESH",
                "Circle 3 — Sealed"
        };
        for (String offender : offenders) {
            String clean = UiGlyphs.sanitize(offender);
            assertEquals(offender + " -> " + clean + " still has an unrenderable char",
                    -1, UiGlyphs.firstUnsupportedIndex(clean));
            assertFalse(clean + " kept a question mark, so a replacement is missing",
                    clean.contains("?"));
        }
    }

    @Test
    public void sanitizeLeavesCleanTextAlone() {
        String clean = "COMMUNE AT THE ALTAR (15 Divinities)";
        assertTrue(clean == UiGlyphs.sanitize(clean));
        assertEquals(null, UiGlyphs.sanitize(null));
    }

    @Test
    public void schoolGlyphsAreRenderable() {
        for (String glyph : new String[]{UiGlyphs.GLYPH_WARFARE, UiGlyphs.GLYPH_FINESSE, UiGlyphs.GLYPH_ARCANA}) {
            assertEquals(glyph + " is not in the font", -1, UiGlyphs.firstUnsupportedIndex(glyph));
        }
    }

    // --- A minimal TrueType 'cmap' reader -------------------------------------------------
    // Enough of the format to answer "which code points does this file map", and no more.
    // FreeType would need a GL context; the bytes do not.

    private static Set<Character> readCharacterMap(File file) throws IOException {
        byte[] font = Files.readAllBytes(file.toPath());
        int tableCount = u16(font, 4);
        int cmapOffset = -1;
        for (int i = 0; i < tableCount; i++) {
            int record = 12 + 16 * i;
            String tag = new String(font, record, 4, "ISO-8859-1");
            if ("cmap".equals(tag)) {
                cmapOffset = (int) u32(font, record + 8);
                break;
            }
        }
        if (cmapOffset < 0) {
            throw new IOException("no cmap table in " + file);
        }

        int subtableCount = u16(font, cmapOffset + 2);
        int format4 = -1;
        for (int i = 0; i < subtableCount; i++) {
            int record = cmapOffset + 4 + 8 * i;
            int subtable = cmapOffset + (int) u32(font, record + 4);
            if (u16(font, subtable) == 4) {
                format4 = subtable;
            }
        }
        if (format4 < 0) {
            throw new IOException("no format-4 cmap subtable in " + file);
        }

        int segCountX2 = u16(font, format4 + 6);
        int segCount = segCountX2 / 2;
        int endBase = format4 + 14;
        int startBase = endBase + segCountX2 + 2;

        Set<Character> codes = new TreeSet<>();
        for (int seg = 0; seg < segCount; seg++) {
            int end = u16(font, endBase + 2 * seg);
            int start = u16(font, startBase + 2 * seg);
            if (end == 0xFFFF) {
                continue; // the mandatory terminating segment
            }
            for (int code = start; code <= end; code++) {
                codes.add((char) code);
            }
        }
        return codes;
    }

    private static int u16(byte[] b, int at) {
        return ((b[at] & 0xFF) << 8) | (b[at + 1] & 0xFF);
    }

    private static long u32(byte[] b, int at) {
        return ((long) (b[at] & 0xFF) << 24) | ((b[at + 1] & 0xFF) << 16)
                | ((b[at + 2] & 0xFF) << 8) | (b[at + 3] & 0xFF);
    }
}
