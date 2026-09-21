package com.bpm.minotaur.rendering;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Turning stored item names into trophy names.
 *
 * <p>{@code weapons.json} and {@code armor.json} store names sort-first -- "Axe, Battle",
 * "Shield, Round Wooden" -- so they alphabetise by category. Printed verbatim on a reward card
 * that reads as a database row, and worse, the least specific word ends up as the headline, so
 * unlocking two axes would produce two cards both titled AXE.
 */
public class DiscoveryNameTest {

    @Test
    public void testPlainNameIsUnchanged() {
        DiscoveryName n = DiscoveryName.of("Scythe");
        assertEquals("SCYTHE", n.headline);
        assertEquals("", n.subtitle);
    }

    @Test
    public void testTwoPartNameIsReordered() {
        DiscoveryName n = DiscoveryName.of("Axe, Battle");
        assertEquals("BATTLE AXE", n.headline);
        assertEquals("", n.subtitle);
    }

    @Test
    public void testMultiWordQualifierIsReordered() {
        DiscoveryName n = DiscoveryName.of("Shield, Round Wooden");
        assertEquals("ROUND WOODEN SHIELD", n.headline);
        assertEquals("", n.subtitle);
    }

    @Test
    public void testThirdPartBecomesSubtitle() {
        DiscoveryName n = DiscoveryName.of("Javelin, Stone, Two-Handed");
        assertEquals("STONE JAVELIN", n.headline);
        assertEquals("Two-Handed", n.subtitle);
    }

    @Test
    public void testFurtherPartsJoinTheSubtitle() {
        DiscoveryName n = DiscoveryName.of("Lance, Light, Horse, Ceremonial");
        assertEquals("LIGHT LANCE", n.headline);
        assertEquals("Horse, Ceremonial", n.subtitle);
    }

    @Test
    public void testSpacingIsTolerated() {
        DiscoveryName n = DiscoveryName.of("Bow,Elven");
        assertEquals("ELVEN BOW", n.headline);
    }

    @Test
    public void testNullAndBlankDegradeGracefully() {
        assertEquals("UNKNOWN", DiscoveryName.of(null).headline);
        assertEquals("UNKNOWN", DiscoveryName.of("   ").headline);
    }
}
