package com.bpm.minotaur.managers;

import com.badlogic.gdx.Input;

import java.util.List;

/**
 * The keys the game reads as literal keycodes rather than through a {@link SettingsManager}
 * binding (#170). The TAB legend lists them beside the rebindable actions, and the Controls
 * screen warns when an action is rebound onto one: the world reads the bound action first,
 * so the fixed role is lost on that key.
 */
public final class FixedKeys {

    /** One legend row: the keys as the player reads them, what they do, and the keycodes. */
    public static final class Entry {
        public final String keys;
        public final String role;
        private final int[] keycodes;

        Entry(String keys, String role, int... keycodes) {
            this.keys = keys;
            this.role = role;
            this.keycodes = keycodes;
        }

        boolean covers(int keycode) {
            for (int k : keycodes) {
                if (k == keycode) return true;
            }
            return false;
        }
    }

    /** The line that teaches bump-to-attack; it has no key of its own. */
    public static final String BUMP_TO_ATTACK = "Walk into a monster to attack";

    public static final List<Entry> LEGEND = List.of(
            new Entry("I", "Inventory", Input.Keys.I),
            new Entry("Space", "Combat Menu", Input.Keys.SPACE),
            new Entry("G", "Shield Guard or Bash", Input.Keys.G),
            new Entry(".", "Wait a Turn", Input.Keys.PERIOD),
            new Entry("Z X V B N / Shift+1-5", "Cast Spell Slot 1-5",
                    Input.Keys.Z, Input.Keys.X, Input.Keys.V, Input.Keys.B, Input.Keys.N),
            new Entry("1-6", "Use Quick Slot (a weapon is thrown)",
                    Input.Keys.NUM_1, Input.Keys.NUM_2, Input.Keys.NUM_3,
                    Input.Keys.NUM_4, Input.Keys.NUM_5, Input.Keys.NUM_6,
                    Input.Keys.NUMPAD_1, Input.Keys.NUMPAD_2, Input.Keys.NUMPAD_3,
                    Input.Keys.NUMPAD_4, Input.Keys.NUMPAD_5, Input.Keys.NUMPAD_6),
            new Entry("Tab", "Show / Hide Controls", Input.Keys.TAB));

    private FixedKeys() {
    }

    /**
     * The fixed role {@code action} would take over if bound to {@code keycode}, or null.
     * The {@code MAP_} actions are read only while the map is open, and the {@code ART_} actions
     * only with Ctrl held, so they never clash.
     */
    public static String clashFor(String action, int keycode) {
        if (action == null || action.startsWith("MAP_") || SettingsManager.isArtAction(action)) return null;
        for (Entry e : LEGEND) {
            if (e.covers(keycode)) return e.role;
        }
        return null;
    }
}
