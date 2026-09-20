package com.bpm.minotaur.gamedata.firearm;

import com.bpm.minotaur.gamedata.item.Item;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Which weapons are firearms, and what black powder costs to fire.
 *
 * <p>A firearm trades rate of fire for impact: it hits far harder than a bow at the same
 * range, and pays for it with a multi-turn reload, a noise pulse that wakes the level,
 * and ammunition that does not litter the floor.
 */
public class FirearmProfileTest {

    @Test
    public void testTheFiveFirearmsAreRecognised() {
        assertTrue(FirearmProfile.isFirearm(Item.ItemType.MUSKET));
        assertTrue(FirearmProfile.isFirearm(Item.ItemType.ARQUEBUS));
        assertTrue(FirearmProfile.isFirearm(Item.ItemType.BLUNDERBUS));
        assertTrue(FirearmProfile.isFirearm(Item.ItemType.PISTOL_STARWHEEL));
        assertTrue(FirearmProfile.isFirearm(Item.ItemType.TUFENK));
    }

    @Test
    public void testBowsAndBladesAreNotFirearms() {
        assertFalse(FirearmProfile.isFirearm(Item.ItemType.BOW));
        assertFalse(FirearmProfile.isFirearm(Item.ItemType.CROSSBOW));
        assertFalse(FirearmProfile.isFirearm(Item.ItemType.SWORD));
        assertFalse(FirearmProfile.isFirearm(null));
    }

    @Test
    public void testSidearmsReloadFasterThanLongGuns() {
        assertEquals("a pistol is loaded one-handed", 2,
                FirearmProfile.reloadTurns(Item.ItemType.PISTOL_STARWHEEL));

        assertEquals(3, FirearmProfile.reloadTurns(Item.ItemType.MUSKET));
        assertEquals(3, FirearmProfile.reloadTurns(Item.ItemType.ARQUEBUS));
        assertEquals(3, FirearmProfile.reloadTurns(Item.ItemType.BLUNDERBUS));
        assertEquals(3, FirearmProfile.reloadTurns(Item.ItemType.TUFENK));
    }

    @Test
    public void testNonFirearmsNeverReload() {
        assertEquals(0, FirearmProfile.reloadTurns(Item.ItemType.BOW));
        assertEquals(0, FirearmProfile.reloadTurns(null));
    }

    @Test
    public void testNoiseCarriesFurtherThanSight() {
        // Sight is 8 tiles. Every firearm must be heard beyond it, or the drawback is
        // invisible -- the player would only ever wake things already looking at them.
        for (Item.ItemType type : FirearmProfile.all()) {
            assertTrue(type + " must be audible past the 8-tile sight range",
                    FirearmProfile.noiseRadius(type) > 8);
        }
    }

    @Test
    public void testLouderGunsWakeMoreOfTheLevel() {
        int pistol = FirearmProfile.noiseRadius(Item.ItemType.PISTOL_STARWHEEL);
        int musket = FirearmProfile.noiseRadius(Item.ItemType.MUSKET);
        int blunderbus = FirearmProfile.noiseRadius(Item.ItemType.BLUNDERBUS);

        assertTrue("a sidearm is the quiet option", pistol < musket);
        assertTrue("the blunderbuss is the loudest thing you can carry", musket < blunderbus);
    }

    @Test
    public void testSilentWeaponsMakeNoNoise() {
        assertEquals(0, FirearmProfile.noiseRadius(Item.ItemType.BOW));
        assertEquals(0, FirearmProfile.noiseRadius(null));
    }
}
