package com.bpm.minotaur.ui;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Catches the next missing-glyph box before a player sees it.
 *
 * <p>{@link UiGlyphsTest} proves what the font can draw; this proves that the screens only ask
 * it for those characters. The audit found boxes on four screens -- a degree sign in the hearth's
 * temperature readout, ballot boxes used as bullets, arrows in the stash's key hint, a star on
 * the level-up banner -- and every one of them was a literal typed into a Java string that
 * nobody had checked against the font.
 *
 * <p>Scope is the source that draws with {@code intellivision.ttf}: {@code screens/} and
 * {@code rendering/}. Lines that only reach a log are skipped, because {@code Gdx.app.log} goes
 * to a console with its own encoding, and the developer tools in {@code debug/} are excluded
 * outright -- they render with libGDX's default font and are not shipped UI.
 *
 * <p>If this fails on a string that genuinely should carry the character, the answer is
 * {@link UiGlyphs#sanitize}, not an exemption.
 */
public class UiSourceGlyphTest {

    /** Files whose strings never reach a player through the game font. */
    private static final Set<String> EXEMPT_FILES = new HashSet<>(Arrays.asList(
            // Developer-only screens, drawn with the libGDX default font.
            "CreatureDevScreen.java",
            "PaperdollEditorScreen.java",
            "UXScreenCaptureScreen.java",
            // Writes a comment header into a config file on disk, not to the screen.
            "InventoryLayoutConfig.java"
    ));

    private static final Pattern STRING_LITERAL = Pattern.compile("\"(?:[^\"\\\\\\n]|\\\\.)*\"");

    /** Calls whose argument is a log line rather than something drawn. */
    private static final String[] LOG_MARKERS = {
            "Gdx.app.log", "Gdx.app.error", "Gdx.app.debug", "System.out", "System.err",
            "throw new", "Exception(", "assert "
    };

    @Test
    public void noScreenOrHudStringAsksTheFontForAGlyphItLacks() throws IOException {
        List<File> sources = new ArrayList<>();
        collect(resolve("core/src/main/java/com/bpm/minotaur/screens"), sources);
        collect(resolve("core/src/main/java/com/bpm/minotaur/rendering"), sources);

        // If the tree moved, the test must fail loudly rather than pass on nothing.
        org.junit.Assert.assertFalse("found no sources to scan", sources.isEmpty());

        List<String> offenders = new ArrayList<>();
        for (File source : sources) {
            if (EXEMPT_FILES.contains(source.getName())) {
                continue;
            }
            String[] lines = new String(Files.readAllBytes(source.toPath()), StandardCharsets.UTF_8).split("\n", -1);
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i];
                if (isLogOnly(line) || isComment(line)) {
                    continue;
                }
                Matcher m = STRING_LITERAL.matcher(line);
                while (m.find()) {
                    // Underscore is unsupported for display -- the font draws it as an arrow --
                    // but it is everywhere in this source as an enum constant, an asset path
                    // and a log tag, none of which reach a Label unformatted. The runtime side
                    // of that is UiNames and UiGlyphs.sanitize, covered by their own tests;
                    // scanning for it here would be all false positives.
                    int bad = UiGlyphs.firstUnsupportedIndex(m.group().replace('_', ' '));
                    if (bad >= 0) {
                        offenders.add(source.getName() + ":" + (i + 1) + "  "
                                + String.format("U+%04X", (int) m.group().charAt(bad))
                                + "  " + m.group().trim());
                    }
                }
            }
        }

        org.junit.Assert.assertEquals(
                "these strings contain characters intellivision.ttf cannot draw, so they render as "
                        + "boxes -- use an ASCII spelling or UiGlyphs.sanitize:\n  "
                        + String.join("\n  ", offenders) + "\n",
                0, offenders.size());
    }

    private static boolean isLogOnly(String line) {
        for (String marker : LOG_MARKERS) {
            if (line.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isComment(String line) {
        String t = line.trim();
        return t.startsWith("//") || t.startsWith("*") || t.startsWith("/*");
    }

    private static File resolve(String relative) {
        File f = new File(relative);
        return f.exists() ? f : new File("../" + relative);
    }

    private static void collect(File dir, List<File> out) {
        File[] children = dir.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            if (child.isDirectory()) {
                collect(child, out);
            } else if (child.getName().endsWith(".java")) {
                out.add(child);
            }
        }
    }
}
