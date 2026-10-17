package com.bpm.minotaur.ui;

/**
 * What {@code intellivision.ttf} can actually draw, and what to do about the rest.
 *
 * <p>The font is a faithful Intellivision character set: printable ASCII, {@code A-grave},
 * {@code A-acute} and the four curly quotes. That is 101 code points and nothing else. Every other
 * character the UI has reached for -- the degree sign in "37.0C", the acute accent someone typed
 * for an apostrophe in "ARTISAN'S", the ballot boxes used as bullets, the arrows in the stash's
 * "SELECT" hint -- renders as a missing-glyph box.
 *
 * <p>There is a second, nastier case. Underscore <i>is</i> in the font, but the Intellivision
 * charset draws it as a left arrow. So {@code GIANT_ANT} does not render as a debug string, it
 * renders as {@code GIANT<-ANT}, which reads like a typo rather than like a bug and survived
 * review for that reason. {@link #sanitize} treats it as unsupported.
 *
 * <p>Call {@link #sanitize} on anything assembled at runtime from data the UI does not control.
 * Static UI copy should simply be written in supported characters; {@code UiGlyphsTest} checks
 * that both hold.
 */
public final class UiGlyphs {

    private UiGlyphs() {
    }

    /** Lowest printable code point the font carries. */
    public static final char FIRST_PRINTABLE = ' ';
    /** Highest ASCII code point the font carries. */
    public static final char LAST_PRINTABLE = '~';

    /** The non-ASCII code points the font carries, beyond {@code ' '..'~'}. */
    private static final char[] EXTRA_SUPPORTED = {
            'À', // A grave
            'Á', // A acute
            '‘', '’', // single curly quotes
            '“', '”'  // double curly quotes
    };

    /** School glyphs (SPEC section 3) drawn from the supported set, so color is never the only cue. */
    public static final String GLYPH_WARFARE = "/";
    public static final String GLYPH_FINESSE = ">";
    public static final String GLYPH_ARCANA = "*";

    /**
     * True when the font has a glyph for {@code c} that draws what the character means.
     *
     * <p>Newline and tab are accepted because Scene2D handles them itself; underscore is rejected
     * even though the font has a glyph for it, because that glyph is an arrow.
     */
    public static boolean isSupported(char c) {
        if (c == '\n' || c == '\t' || c == '\r') {
            return true;
        }
        if (c == '_') {
            return false;
        }
        if (c >= FIRST_PRINTABLE && c <= LAST_PRINTABLE) {
            return true;
        }
        for (char extra : EXTRA_SUPPORTED) {
            if (c == extra) {
                return true;
            }
        }
        return false;
    }

    /** The first character of {@code s} the font cannot draw, or -1. Null and empty return -1. */
    public static int firstUnsupportedIndex(String s) {
        if (s == null) {
            return -1;
        }
        for (int i = 0; i < s.length(); i++) {
            if (!isSupported(s.charAt(i))) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Rewrites {@code s} into characters the font can draw.
     *
     * <p>Known characters are replaced with the closest ASCII that keeps the meaning -- an ellipsis
     * becomes three dots, a multiplication sign becomes {@code x}, a degree sign becomes {@code C}
     * is <i>not</i> attempted because that would be wrong for Fahrenheit, so it becomes
     * {@code deg}. Anything still unsupported after that becomes {@code ?}, which is visible in a
     * screenshot in a way a blank is not.
     *
     * <p>Null passes through, so callers can sanitize without guarding first.
     */
    public static String sanitize(String s) {
        if (s == null || s.isEmpty()) {
            return s;
        }
        if (firstUnsupportedIndex(s) < 0) {
            return s;
        }

        StringBuilder out = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (isSupported(c)) {
                out.append(c);
                continue;
            }
            out.append(replacementFor(c));
        }
        return out.toString();
    }

    private static String replacementFor(char c) {
        switch (c) {
            // Enum shape. Underscore draws as an arrow, so it can never survive to the screen.
            case '_':
                return " ";

            // Punctuation
            case '…': return "...";  // ellipsis
            case '—': return "-";    // em dash
            case '–': return "-";    // en dash
            case '•': return "-";    // bullet
            case '·': return "-";    // middle dot
            case '´': return "'";    // acute accent, typed for an apostrophe
            case '`': return "'";    // grave accent, likewise
            case '«': return "<<";
            case '»': return ">>";

            // Symbols
            case '×': return "x";    // multiplication sign
            case '°': return " deg";
            case '™': return "(TM)";
            case '©': return "(C)";
            case '®': return "(R)";

            // Marks and bullets that were used as state icons
            case '✓': case '✔': return "+";   // check
            case '✗': case '✘': return "x";   // ballot X
            case '☐': return "[ ]";                // empty ballot box
            case '☑': return "[+]";
            case '☒': return "[x]";
            case '★': case '☆': return "*";   // stars
            case '✦': case '✧': case '◆': case '◇': return "*";

            // Arrows
            case '←': return "<";
            case '→': return ">";
            case '↑': return "^";
            case '↓': return "v";
            case '↔': return "<>";

            // Fractions and superscripts that turn up in stat strings
            case '½': return "1/2";
            case '¼': return "1/4";
            case '¾': return "3/4";

            default:
                return "?";
        }
    }
}
