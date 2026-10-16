package com.bpm.minotaur.managers;

import com.badlogic.gdx.Input;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.*;

/**
 * Every rebindable action is read through its binding (#171): a rebound action fires on
 * its new key and no longer on the old one, so the Controls legend never lies.
 */
public class ActionKeysTest {

    /** Defaults, with any overrides laid on top. */
    private static ActionKeys keys(Map<String, Integer> overrides) {
        return new ActionKeys(action -> overrides.containsKey(action)
                ? overrides.get(action)
                : SettingsManager.defaultKeyFor(action));
    }

    @Test
    public void everyWorldActionFiresOnItsDefaultKey() {
        ActionKeys keys = keys(new HashMap<>());
        assertEquals("MOVE_FORWARD", keys.resolve(Input.Keys.UP, ActionKeys.WORLD_ACTIONS));
        assertEquals("MOVE_BACK", keys.resolve(Input.Keys.DOWN, ActionKeys.WORLD_ACTIONS));
        assertEquals("TURN_LEFT", keys.resolve(Input.Keys.LEFT, ActionKeys.WORLD_ACTIONS));
        assertEquals("TURN_RIGHT", keys.resolve(Input.Keys.RIGHT, ActionKeys.WORLD_ACTIONS));
        assertEquals("INTERACT", keys.resolve(Input.Keys.O, ActionKeys.WORLD_ACTIONS));
        assertEquals("PICKUP_DROP", keys.resolve(Input.Keys.P, ActionKeys.WORLD_ACTIONS));
        assertEquals("USE_ITEM", keys.resolve(Input.Keys.U, ActionKeys.WORLD_ACTIONS));
        assertEquals("DESCEND", keys.resolve(Input.Keys.D, ActionKeys.WORLD_ACTIONS));
        assertEquals("REST", keys.resolve(Input.Keys.H, ActionKeys.WORLD_ACTIONS));
        assertEquals("FIRST_AID", keys.resolve(Input.Keys.R, ActionKeys.WORLD_ACTIONS));
        assertEquals("FIELD_CRAFTING", keys.resolve(Input.Keys.C, ActionKeys.WORLD_ACTIONS));
        assertEquals("FIELD_COOKING", keys.resolve(Input.Keys.J, ActionKeys.WORLD_ACTIONS));
        assertEquals("SWAP_HANDS", keys.resolve(Input.Keys.S, ActionKeys.HAND_ACTIONS));
        assertEquals("SWAP_PACK", keys.resolve(Input.Keys.E, ActionKeys.HAND_ACTIONS));
        assertEquals("ROTATE_PACK", keys.resolve(Input.Keys.T, ActionKeys.HAND_ACTIONS));
        assertTrue(keys.is(Input.Keys.A, "ATTACK"));
    }

    @Test
    public void aReboundActionFiresOnTheNewKeyAndNotTheOld() {
        Map<String, Integer> overrides = new HashMap<>();
        overrides.put("INTERACT", Input.Keys.Y);
        overrides.put("SWAP_PACK", Input.Keys.NUM_9);
        overrides.put("ATTACK", Input.Keys.F12);
        overrides.put("MOVE_FORWARD", Input.Keys.W);
        ActionKeys keys = keys(overrides);

        assertEquals("INTERACT", keys.resolve(Input.Keys.Y, ActionKeys.WORLD_ACTIONS));
        assertNull(keys.resolve(Input.Keys.O, ActionKeys.WORLD_ACTIONS));

        assertEquals("SWAP_PACK", keys.resolve(Input.Keys.NUM_9, ActionKeys.HAND_ACTIONS));
        assertNull(keys.resolve(Input.Keys.E, ActionKeys.HAND_ACTIONS));

        assertTrue(keys.is(Input.Keys.F12, "ATTACK"));
        assertFalse(keys.is(Input.Keys.A, "ATTACK"));

        assertEquals("MOVE_FORWARD", keys.resolve(Input.Keys.W, ActionKeys.WORLD_ACTIONS));
        assertNull(keys.resolve(Input.Keys.UP, ActionKeys.WORLD_ACTIONS));
    }

    @Test
    public void anUnboundKeyNeverMatches() {
        Map<String, Integer> overrides = new HashMap<>();
        overrides.put("INTERACT", Input.Keys.UNKNOWN);
        ActionKeys keys = keys(overrides);
        assertNull(keys.resolve(Input.Keys.UNKNOWN, ActionKeys.WORLD_ACTIONS));
        assertFalse(keys.is(Input.Keys.UNKNOWN, "INTERACT"));
    }

    @Test
    public void settingsFallBackToDefaultsBeforeLoad() {
        // Headless tests never call load(); the defaults must still answer.
        SettingsManager settings = SettingsManager.getInstance();
        int key = settings.getKey("SPELLBOOK");
        assertNotEquals(Input.Keys.UNKNOWN, key);
    }

    /**
     * The gameplay key handler must not match the keys of rebindable actions as literals.
     * A literal here is a key the legend lets the player rebind but the game ignores.
     */
    @Test
    public void gameScreenNeverMatchesABindableDefaultAsALiteral() throws IOException {
        Path src = locate("core/src/main/java/com/bpm/minotaur/screens/GameScreen.java");
        String code = new String(Files.readAllBytes(src), StandardCharsets.UTF_8);
        int start = code.indexOf("public boolean keyDown(int keycode) {\n        // --- Death cinematic");
        if (start < 0) start = code.indexOf("public boolean keyDown(int keycode) {\r\n        // --- Death cinematic");
        assertTrue("gameplay keyDown not found", start >= 0);
        int end = code.indexOf("public boolean keyUp(int keycode)", start);
        String keyDown = code.substring(start, end);

        String[] bindable = { "O", "P", "U", "D", "R", "H", "C", "J", "K", "S", "E", "T", "A",
                "UP", "DOWN", "LEFT", "RIGHT", "Q", "M", "L", "F" };
        for (String line : keyDown.split("\\R")) {
            // Ctrl chords (chronicle scroll, debug) are fixed keys, marked as such.
            if (line.contains("// fixed:")) continue;
            for (String k : bindable) {
                Matcher m = Pattern.compile("case Input\\.Keys\\." + k + ":|keycode == Input\\.Keys\\." + k + "\\b")
                        .matcher(line);
                assertFalse("GameScreen.keyDown matches Input.Keys." + k + " as a literal: " + line.trim(),
                        m.find());
            }
        }
    }

    private static Path locate(String relative) {
        Path p = Paths.get(relative);
        if (Files.exists(p)) return p;
        return Paths.get("..").resolve(relative);
    }
}
