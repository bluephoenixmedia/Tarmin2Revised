package com.bpm.minotaur.gamedata.item;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Turning stored item names into something a player should read.
 *
 * <p>{@code weapons.json} and {@code armor.json} store names category-first so they alphabetise by
 * weapon class -- "Axe, Battle", "Shield, Round Wooden". That is right for data and wrong for
 * display: 168 of 266 weapons carry a comma, and the stored form was reaching the HUD, the
 * inventory and pickup messages verbatim.
 *
 * <p>Qualifiers beyond the first are kept, not dropped -- "Two-Handed" is mechanically meaningful.
 */
public class ItemNameTest {

    @Test
    public void testNameWithoutCommaIsUnchanged() {
        assertEquals("Scythe", ItemName.natural("Scythe"));
        assertEquals("Rusty Sword", ItemName.natural("Rusty Sword"));
    }

    @Test
    public void testTwoPartNameIsReordered() {
        assertEquals("Battle Axe", ItemName.natural("Axe, Battle"));
        assertEquals("Elven Bow", ItemName.natural("Bow, Elven"));
        assertEquals("Broad Sword", ItemName.natural("Sword, Broad"));
    }

    @Test
    public void testMultiWordQualifierIsReordered() {
        assertEquals("Round Wooden Shield", ItemName.natural("Shield, Round Wooden"));
    }

    @Test
    public void testExtraQualifiersAreKeptInParentheses() {
        assertEquals("Stone Javelin (Two-Handed)", ItemName.natural("Javelin, Stone, Two-Handed"));
        assertEquals("Light Lance (Horse, Ceremonial)",
                ItemName.natural("Lance, Light, Horse, Ceremonial"));
    }

    @Test
    public void testSpacingAndEmptyPartsAreTolerated() {
        assertEquals("Elven Bow", ItemName.natural("Bow,Elven"));
        assertEquals("Elven Bow", ItemName.natural("  Bow ,  Elven  "));
        assertEquals("Elven Bow", ItemName.natural("Bow,,Elven"));
    }

    @Test
    public void testNullAndBlankPassThrough() {
        assertEquals(null, ItemName.natural(null));
        assertEquals("   ", ItemName.natural("   "));
    }

    @Test
    public void testTrailingCommaDoesNotProduceStrayPunctuation() {
        assertEquals("Scythe", ItemName.natural("Scythe,"));
    }

    @Test
    public void testNameThatIsNothingButPunctuationDoesNotBecomeItsOwnCategory() {
        // "," used to parse to category="," and display as ",". Degenerate input should fall
        // back to the raw string, never invent a category out of punctuation.
        assertEquals("", ItemName.parse(",").category);
        assertEquals(",", ItemName.natural(","));
        assertEquals("", ItemName.parse(", ,").category);
    }

    @Test
    public void testPartsAreExposedForCallersThatWantThemSeparately() {
        ItemName n = ItemName.parse("Javelin, Stone, Two-Handed");
        assertEquals("Javelin", n.category);
        assertEquals("Stone", n.qualifier);
        assertEquals("Two-Handed", n.extra);

        ItemName plain = ItemName.parse("Scythe");
        assertEquals("Scythe", plain.category);
        assertEquals("", plain.qualifier);
        assertEquals("", plain.extra);
    }
}
