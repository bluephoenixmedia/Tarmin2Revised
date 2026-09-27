package com.bpm.minotaur.gamedata.player;

import com.bpm.minotaur.gamedata.Difficulty;
import com.bpm.minotaur.gamedata.Inventory;
import com.bpm.minotaur.gamedata.dice.DiceFactory;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.ItemColor;
import com.bpm.minotaur.rendering.animation.AnimationArchetype;
import org.junit.Test;

import static org.junit.Assert.*;

public class RespawnLoadoutTest {

    @Test
    public void testDeathExpeditionInventoryWipe() {
        Inventory inv = new Inventory();
        PlayerEquipment equip = new PlayerEquipment();

        // Equip armor and weapons
        Item weapon = new Item();
        inv.setRightHand(weapon);
        Item shield = new Item();
        inv.setLeftHand(shield);

        Item chestArmor = new Item();
        equip.setWornChest(chestArmor);

        // Put items in quickslots and backpack
        Item potion = new Item();
        inv.setQuickSlot(0, potion);
        Item loot1 = new Item();
        Item loot2 = new Item();
        inv.getMainInventory().add(loot1);
        inv.getMainInventory().add(loot2);

        assertEquals(2, inv.getMainInventory().size());
        assertNotNull(inv.getRightHand());
        assertNotNull(inv.getLeftHand());
        assertNotNull(inv.getQuickSlots()[0]);
        assertEquals(1, equip.getAllEquipped().size());

        // Perform death inventory strip
        inv.setRightHand(null);
        inv.setLeftHand(null);
        equip.stripAllEquipped();
        inv.clearQuickSlots();
        inv.getMainInventory().clear();

        assertNull("Right hand weapon must be stripped", inv.getRightHand());
        assertNull("Left hand shield must be stripped", inv.getLeftHand());
        assertTrue("Worn equipment must be empty", equip.getAllEquipped().isEmpty());
        for (Item qs : inv.getQuickSlots()) {
            assertNull("Quickslots must be stripped on death", qs);
        }
        assertTrue("Backpack must be emptied on death", inv.getMainInventory().isEmpty());
    }

    @Test
    public void testStarterWeaponArchetypeIsSlashing1H() {
        Item rustySword = new Item(Item.ItemType.RUSTY_SWORD, 0, 0, ItemColor.GRAY, null, null);

        AnimationArchetype arch = AnimationArchetype.fromItem(rustySword);
        assertEquals("Rusty Sword must map to 1H Slashing archetype", AnimationArchetype.SLASHING_1H, arch);
    }

    @Test
    public void testDicePoolResetOnRespawn() {
        PlayerStats stats = new PlayerStats(Difficulty.MEDIUM);
        stats.getDicePool().clear();

        // Previous run accumulated random dice
        stats.getDicePool().add(DiceFactory.create("Warrior's Red Die"));
        stats.getDicePool().add(DiceFactory.create("Pyromancer's Ember Die"));
        assertEquals(2, stats.getDicePool().size());

        // Reset to expedition starter pool with Rusty Iron Die
        stats.getDicePool().clear();
        stats.getDicePool().add(DiceFactory.create("Rusty Iron Die"));

        assertEquals(1, stats.getDicePool().size());
        assertEquals("Rusty Iron Die", stats.getDicePool().get(0).getName());
    }
}
