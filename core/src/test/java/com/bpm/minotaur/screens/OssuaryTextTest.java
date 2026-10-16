package com.bpm.minotaur.screens;

import com.bpm.minotaur.gamedata.progression.ShelterAltar;
import com.bpm.minotaur.ui.UiGlyphs;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * The Ossuary explains itself (#198): the bench names it, the tab says what it is
 * for, and every slot says what goes in it and what it decides. All of it must be
 * printable in intellivision.ttf.
 */
public class OssuaryTextTest {

    private static final String[] TEXTS = {
            CraftingScreen.OSSUARY_INTRO,
            CraftingScreen.SLOT_TIP_STRUCTURE,
            CraftingScreen.SLOT_TIP_EDGE,
            CraftingScreen.SLOT_TIP_CORE,
            CraftingScreen.SLOT_TIP_TALISMAN_BONE,
            CraftingScreen.SLOT_TIP_TROPHY,
            CraftingScreen.SLOT_TIP_SCRAP,
            ShelterAltar.Station.CRAFTING_BENCH.getDescription(),
    };

    @Test
    public void everyOssuaryStringIsPrintable() {
        for (String s : TEXTS) {
            assertEquals("unprintable glyph in: " + s, -1, UiGlyphs.firstUnsupportedIndex(s));
        }
    }

    @Test
    public void theBenchCardNamesTheOssuaryAndWhatItMakes() {
        String card = ShelterAltar.Station.CRAFTING_BENCH.getDescription().toLowerCase();
        assertTrue(card, card.contains("ossuary"));
        assertTrue(card, card.contains("dice") || card.contains("die"));
    }

    @Test
    public void eachDieSlotNamesTheFacesItSets() {
        assertTrue(CraftingScreen.SLOT_TIP_STRUCTURE.contains("1 and 6"));
        assertTrue(CraftingScreen.SLOT_TIP_EDGE.contains("2 and 5"));
        assertTrue(CraftingScreen.SLOT_TIP_CORE.contains("3 and 4"));
    }
}
