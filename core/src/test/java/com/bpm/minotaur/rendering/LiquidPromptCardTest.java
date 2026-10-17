package com.bpm.minotaur.rendering;

import com.badlogic.gdx.Input;
import com.bpm.minotaur.gamedata.liquid.LiquidType;
import com.bpm.minotaur.managers.ActionKeys;
import com.bpm.minotaur.ui.UiGlyphs;
import org.junit.Test;

import static org.junit.Assert.*;

/** The facing-tile drink card (#193): bound keys, the liquid's risk, glyph-safe text. */
public class LiquidPromptCardTest {

    private static ActionKeys keys(int drink, int fill) {
        return new ActionKeys(a -> a.equals("INTERACT") ? drink : a.equals("PICKUP_DROP") ? fill : Input.Keys.UNKNOWN);
    }

    @Test
    public void showsDrinkOnOAndFillOnPByDefault() {
        ActionKeys k = keys(Input.Keys.O, Input.Keys.P);
        assertEquals("Drink [O]  |  Fill Waterskin [P]", LiquidPromptCard.actionText(k));
        assertEquals("[ O / P ]", LiquidPromptCard.keyBadge(k));
    }

    @Test
    public void followsARebinding() {
        assertTrue(LiquidPromptCard.actionText(keys(Input.Keys.G, Input.Keys.H)).startsWith("Drink [G]"));
    }

    @Test
    public void warnsOfEachLiquidsRisk() {
        assertEquals("[SAFE TO DRINK]", LiquidPromptCard.subTag(LiquidType.WATER, false));
        assertEquals("[SMALL RISK]", LiquidPromptCard.subTag(LiquidType.WATER, true));
        assertEquals("[GREAT RISK]", LiquidPromptCard.subTag(LiquidType.BLOOD, false));
        assertEquals("[CERTAIN ILLNESS]", LiquidPromptCard.subTag(LiquidType.BLACK_MUCK, false));
        assertEquals("[CHILLING]", LiquidPromptCard.subTag(LiquidType.FREEZING_SLUSH, true));
        assertTrue(LiquidPromptCard.description(LiquidType.BLOOD, true).contains("70"));
    }

    @Test
    public void everyStringSurvivesTheFont() {
        ActionKeys k = keys(Input.Keys.O, Input.Keys.P);
        for (LiquidType l : LiquidType.values()) {
            for (boolean strata : new boolean[]{false, true}) {
                for (String s : new String[]{LiquidPromptCard.tag(strata), LiquidPromptCard.subTag(l, strata),
                        LiquidPromptCard.title(l), LiquidPromptCard.description(l, strata),
                        LiquidPromptCard.keyBadge(k), LiquidPromptCard.actionText(k)}) {
                    assertEquals(s, -1, UiGlyphs.firstUnsupportedIndex(s));
                }
            }
        }
    }
}
