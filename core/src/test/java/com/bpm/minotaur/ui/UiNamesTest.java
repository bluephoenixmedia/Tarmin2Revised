package com.bpm.minotaur.ui;

import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.ItemCategory;
import com.bpm.minotaur.gamedata.item.ItemColor;
import com.bpm.minotaur.gamedata.item.PotionEffectType;
import com.bpm.minotaur.gamedata.item.RingEffectType;
import com.bpm.minotaur.gamedata.monster.Monster;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * RC4: no enum constant reaches a player in the shape the code stores it in.
 *
 * <p>The sweep over whole enums is the part that matters. Spot checks catch the cases the audit
 * screenshotted; the sweep catches the next few hundred, including every generated weapon and
 * armour constant, which is where the ID suffixes live.
 */
public class UiNamesTest {

    @Test
    public void everyItemTypeFormatsIntoRenderableText() {
        assertAllFormatCleanly(Item.ItemType.values());
    }

    @Test
    public void everyMonsterTypeFormatsIntoRenderableText() {
        assertAllFormatCleanly(Monster.MonsterType.values());
    }

    @Test
    public void everySupportingEnumFormatsIntoRenderableText() {
        assertAllFormatCleanly(ItemCategory.values());
        assertAllFormatCleanly(ItemColor.values());
        assertAllFormatCleanly(PotionEffectType.values());
        assertAllFormatCleanly(RingEffectType.values());
    }

    private static void assertAllFormatCleanly(Enum<?>[] values) {
        for (Enum<?> value : values) {
            String name = UiNames.of(value);
            String where = value.getDeclaringClass().getSimpleName() + "." + value.name() + " -> '" + name + "'";

            assertFalse(where + " is empty", name.isEmpty());
            assertFalse(where + " kept an underscore, which this font draws as an arrow",
                    name.indexOf('_') >= 0);
            assertEquals(where + " has a character the font cannot draw",
                    -1, UiGlyphs.firstUnsupportedIndex(name));
            assertFalse(where + " is still SHOUTING", name.equals(name.toUpperCase())
                    && name.length() > 3 && !isAllAcronym(name));
        }
    }

    /** "HP" and "MP" are legitimately all-caps; "GIANT ANT" is not. */
    private static boolean isAllAcronym(String name) {
        for (String token : name.split(" ")) {
            if (token.length() > 3) {
                return false;
            }
        }
        return true;
    }

    @Test
    public void theNamesTheAuditScreenshotted() {
        // LEVELUP-8, ALTAR-9
        assertEquals("Giant Ant", UiNames.fromConstant("GIANT_ANT"));
        assertEquals("Giant Snake", UiNames.fromConstant("GIANT_SNAKE"));
        // STASH-2
        assertEquals("Bat Guano", UiNames.fromConstant("BAT_GUANO"));
        // CHRON-2
        assertEquals("Ring of Resistance Fire", UiNames.fromConstant("RING_OF_RESISTANCE_FIRE"));
        // HEARTH-6
        assertEquals("Flesh", UiNames.fromConstant("GIB_FLESH"));
        assertEquals("Monster Eye", UiNames.fromConstant("MONSTER_EYE"));
    }

    @Test
    public void generatedIdSuffixesAreDropped() {
        // WORK-5: the forge printed "Spetum3 Polearm".
        assertEquals("Polearm Spetum", UiNames.fromConstant("POLEARM_SPETUM3"));
        assertEquals("Mancatcher", UiNames.fromConstant("MANCATCHER2"));
        assertEquals("Lance Heavy Horse", UiNames.fromConstant("LANCE_HEAVY_HORSE1"));
        assertEquals("Shield Body", UiNames.fromConstant("SHIELD_BODY_2"));
    }

    @Test
    public void smallWordsStayLowercaseUnlessTheyLead() {
        assertEquals("Belt of Giant Strength", UiNames.fromConstant("BELT_OF_GIANT_STRENGTH"));
        assertEquals("Of Course", UiNames.fromConstant("OF_COURSE"));
    }

    @Test
    public void possessivesComeFromTheOverrideTable() {
        assertEquals("Berserker's Gauntlets", UiNames.fromConstant("BERSERKERS_GAUNTLETS"));
        assertEquals("Footman's Mace", UiNames.fromConstant("MACE_FOOTMANS"));
    }

    @Test
    public void rulesetMarkersAreNotPartOfTheName() {
        assertEquals("Potion Invisibility", UiNames.fromConstant("POTION_INVISIBILITY_5E"));
    }

    @Test
    public void capsKeepsTheSmallWordHandling() {
        assertEquals("BELT OF GIANT STRENGTH", UiNames.caps(Item.ItemType.BELT_OF_GIANT_STRENGTH));
    }

    @Test
    public void nullsLeaveAGapRatherThanPrintingTheWordNull() {
        assertEquals("", UiNames.of(null));
        assertEquals("", UiNames.fromConstant(null));
        assertEquals("", UiNames.fromConstant(""));
    }

    @Test
    public void pluralsAreNotWrongAtOne() {
        // ALTAR-8: the altar showed "+1 Divinities".
        assertEquals("1 Divinity", UiNames.plural(1, "Divinity", "Divinities"));
        assertEquals("3 Divinities", UiNames.plural(3, "Divinity", "Divinities"));
        assertEquals("1 Crest", UiNames.plural(1, "Crest"));
        assertEquals("0 Crests", UiNames.plural(0, "Crest"));
    }

    @Test
    public void acronymsKeepTheirCasing() {
        assertTrue(UiNames.fromConstant("BONUS_HP").endsWith("HP"));
        assertTrue(UiNames.fromConstant("RING_OF_MP").endsWith("MP"));
    }
}
