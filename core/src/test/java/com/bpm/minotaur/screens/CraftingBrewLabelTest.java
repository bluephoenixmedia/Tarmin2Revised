package com.bpm.minotaur.screens;

import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.managers.CraftingManager;
import com.bpm.minotaur.ui.UiGlyphs;
import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertEquals;

/** The alchemy button names what the recipe makes: a Poultice is prepared, not brewed (#190). */
public class CraftingBrewLabelTest {

    private static CraftingManager.AlchemyRecipe recipe(Item.ItemType output) {
        return new CraftingManager.AlchemyRecipe("r", null, Collections.emptyMap(), output, "");
    }

    @Test
    public void aPoulticeIsPreparedAndAPotionIsBrewed() {
        assertEquals("[ PREPARE POULTICE ]", CraftingScreen.brewLabel(recipe(Item.ItemType.POULTICE)));
        assertEquals("[ BREW POTION ]", CraftingScreen.brewLabel(recipe(Item.ItemType.POTION_OF_HEALING)));
        assertEquals("[ BREW POTION ]", CraftingScreen.brewLabel(null));
    }

    @Test
    public void theLabelsSurviveTheFont() {
        String label = CraftingScreen.brewLabel(recipe(Item.ItemType.POULTICE));
        assertEquals(label, UiGlyphs.sanitize(label));
    }
}
