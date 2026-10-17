package com.bpm.minotaur.managers;

import com.badlogic.gdx.Input;
import com.bpm.minotaur.ui.UiGlyphs;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * The TAB legend lists every key the game reads (#170): the fixed keys sit beside the
 * rebindable ones, binding an action onto a fixed key is reported, and the card badges
 * name the bound key.
 */
public class FixedKeysTest {

    private static String keysFor(String role) {
        for (FixedKeys.Entry e : FixedKeys.LEGEND) {
            if (e.role.equals(role)) return e.keys;
        }
        return null;
    }

    @Test
    public void theLegendListsEveryFixedKeyTheAuditFoundMissing() {
        assertEquals("I", keysFor("Inventory"));
        assertEquals("Space", keysFor("Combat Menu"));
        assertEquals("G", keysFor("Shield Guard or Bash"));
        assertEquals(".", keysFor("Wait a Turn"));
        assertEquals("Z X V B N / Shift+1-5", keysFor("Cast Spell Slot 1-5"));
        assertEquals("1-6", keysFor("Use Quick Slot (a weapon is thrown)"));
        assertEquals("Walk into a monster to attack", FixedKeys.BUMP_TO_ATTACK);
    }

    @Test
    public void everyLegendStringIsDrawableByTheFont() {
        for (FixedKeys.Entry e : FixedKeys.LEGEND) {
            assertEquals(e.keys, -1, UiGlyphs.firstUnsupportedIndex(e.keys));
            assertEquals(e.role, -1, UiGlyphs.firstUnsupportedIndex(e.role));
        }
        assertEquals(-1, UiGlyphs.firstUnsupportedIndex(FixedKeys.BUMP_TO_ATTACK));
        for (String d : SettingsManager.getInstance().getKeyBindingDescriptions().values()) {
            assertEquals(d, -1, UiGlyphs.firstUnsupportedIndex(d));
        }
    }

    @Test
    public void theBindingDescriptionsTellTheTruthAboutEAndH() {
        Map<String, String> d = SettingsManager.getInstance().getKeyBindingDescriptions();
        assertEquals("Use / Loot / Equip, else Swap with Pack", d.get("SWAP_PACK"));
        assertEquals("Open/Interact", d.get("INTERACT"));
        assertEquals("Quick Heal", d.get("QUICK_HEAL"));
        assertFalse(d.containsKey("REST"));
    }

    @Test
    public void bindingAnActionOntoAFixedKeyIsAClash() {
        assertEquals("Shield Guard or Bash", FixedKeys.clashFor("INTERACT", Input.Keys.G));
        assertEquals("Use Quick Slot (a weapon is thrown)", FixedKeys.clashFor("QUICK_HEAL", Input.Keys.NUM_3));
        assertEquals("Cast Spell Slot 1-5", FixedKeys.clashFor("SWAP_PACK", Input.Keys.X));
        assertEquals("Inventory", FixedKeys.clashFor("MAP", Input.Keys.I));
        assertNull(FixedKeys.clashFor("INTERACT", Input.Keys.O));
    }

    @Test
    public void mapOnlyActionsNeverClashBecauseTheWorldIsNotReadingThen() {
        assertNull(FixedKeys.clashFor("MAP_SUGGEST", Input.Keys.G));
        assertNull(FixedKeys.clashFor("MAP_PIN", Input.Keys.I));
    }

    @Test
    public void noDefaultBindingClashes() {
        for (String action : SettingsManager.getInstance().getKeyBindingDescriptions().keySet()) {
            assertNull(action, FixedKeys.clashFor(action, SettingsManager.defaultKeyFor(action)));
        }
    }

    @Test
    public void badgesNameTheBoundKey() {
        Map<String, Integer> bound = new HashMap<>();
        ActionKeys defaults = new ActionKeys(a -> bound.containsKey(a) ? bound.get(a) : SettingsManager.defaultKeyFor(a));
        assertEquals("[ O ]", defaults.badge("INTERACT"));
        assertEquals("[ O / E ]", defaults.badge("INTERACT", "SWAP_PACK"));
        bound.put("INTERACT", Input.Keys.Y);
        assertEquals("[ Y ]", defaults.badge("INTERACT"));
    }
}
