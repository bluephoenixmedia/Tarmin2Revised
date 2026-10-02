package com.bpm.minotaur.debug;

import com.badlogic.gdx.Input;

import java.util.ArrayList;
import java.util.List;

/**
 * The debug keys, in one table that both handles them and draws the on-screen legend, so the legend
 * can never list a key the game does not honour (or miss one it does).
 */
public final class DebugKeys {

    public enum Action { LEARN_ALL_SPELLS, LEVEL_UP, OPEN_ALL_ITEMS_CHEST, REFILL }

    public static final class Entry {
        public final int keycode;
        public final String keyName;
        public final String description;
        /** Null for the always-available keys that are only listed. */
        public final Action action;

        Entry(int keycode, String keyName, String description, Action action) {
            this.keycode = keycode;
            this.keyName = keyName;
            this.description = description;
            this.action = action;
        }
    }

    /** Active only while debug mode (F5) is on. */
    public static final Entry[] DEBUG_ONLY = {
        new Entry(Input.Keys.INSERT, "Insert", "learn every spell", Action.LEARN_ALL_SPELLS),
        new Entry(Input.Keys.PAGE_UP, "PgUp", "level up one level", Action.LEVEL_UP),
        new Entry(Input.Keys.HOME, "Home", "open a chest with one of every item", Action.OPEN_ALL_ITEMS_CHEST),
        new Entry(Input.Keys.END, "End", "refill HP, MP, food and water", Action.REFILL),
    };

    /** Always available while developing; listed here for reference. */
    public static final String[][] ALWAYS = {
        { "F1", "debug overlay" },
        { "F2", "Modern / Retro" },
        { "F3", "force monster modifiers" },
        { "F4", "warp to next themed chunk" },
        { "F6", "CRT filter" },
        { "F7", "render engine" },
        { "F8", "predict portals (log)" },
        { "F9", "dump exploration (log)" },
        { "F10", "weather intensity" },
        { "Shift+F11", "cycle weather" },
        { "F11", "weapon view tuner" },
        { "Ctrl+F11", "gate texture tuner" },
        { "F12", "monster debug overlay" },
        { "[ ]", "field of view" },
    };

    private DebugKeys() {
    }

    /** The debug-only action bound to this key, or null. Always null when debug mode is off. */
    public static Action actionFor(int keycode, boolean debugMode) {
        if (!debugMode) {
            return null;
        }
        for (Entry e : DEBUG_ONLY) {
            if (e.keycode == keycode) {
                return e.action;
            }
        }
        return null;
    }

    /** The legend as lines of text, ready to draw. */
    public static List<String> legend(boolean debugMode) {
        List<String> lines = new ArrayList<>();
        lines.add("DEBUG MODE (F5): " + (debugMode ? "ON - all shelter features unlocked" : "OFF - press F5"));
        lines.add("");
        lines.add("Only while debug mode is on:");
        for (Entry e : DEBUG_ONLY) {
            lines.add(String.format("  %-9s %s", e.keyName, e.description));
        }
        lines.add("");
        lines.add("Always available:");
        for (String[] k : ALWAYS) {
            lines.add(String.format("  %-9s %s", k[0], k[1]));
        }
        return lines;
    }
}
