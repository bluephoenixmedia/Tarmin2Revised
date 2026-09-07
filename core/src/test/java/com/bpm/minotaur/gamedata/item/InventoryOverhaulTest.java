package com.bpm.minotaur.gamedata.item;

import com.bpm.minotaur.gamedata.Inventory;
import com.bpm.minotaur.gamedata.item.Item.ItemType;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class InventoryOverhaulTest {

    private Inventory inventory;

    @Before
    public void setUp() {
        inventory = new Inventory();
    }

    private Item createConsumable(ItemType type, String name) {
        ItemTemplate t = new ItemTemplate();
        t.friendlyName = name;
        t.isUsable = true;
        if (type.name().contains("POTION")) t.isPotion = true;
        if (type.name().contains("FOOD")) t.isFood = true;
        return Item.fromTemplate(type, t);
    }

    private Item createEquipment(ItemType type, String name, boolean isWeapon, boolean isArmor) {
        ItemTemplate t = new ItemTemplate();
        t.friendlyName = name;
        t.isWeapon = isWeapon;
        t.isArmor = isArmor;
        return Item.fromTemplate(type, t);
    }

    @Test
    public void testConsumableAutoSlottingToQuickSlots() {
        Item potion = createConsumable(ItemType.POTION_BLUE, "Healing Potion");
        Item food = createConsumable(ItemType.FOOD, "Ration");
        Item scroll = createConsumable(ItemType.SCROLL, "Scroll of Light");

        assertTrue("Potion should be classified as consumable", potion.isConsumableOrTool());
        assertTrue("Food should be classified as consumable", food.isConsumableOrTool());
        assertTrue("Scroll should be classified as consumable", scroll.isConsumableOrTool());

        // Picking up consumables routes into empty Quick Slots (0..5) first
        assertTrue(inventory.pickup(potion));
        assertSame(potion, inventory.getQuickSlots()[0]);
        assertTrue(inventory.getMainInventory().isEmpty());

        assertTrue(inventory.pickup(food));
        assertSame(food, inventory.getQuickSlots()[1]);
        assertTrue(inventory.getMainInventory().isEmpty());

        assertTrue(inventory.pickup(scroll));
        assertSame(scroll, inventory.getQuickSlots()[2]);
        assertTrue(inventory.getMainInventory().isEmpty());
    }

    @Test
    public void testEquipmentGoesToMainBackpackFirst() {
        Item sword = createEquipment(ItemType.SWORD, "Iron Longsword", true, false);
        Item shield = createEquipment(ItemType.SHIELD, "Bronze Shield", false, true);

        assertFalse("Sword is not consumable", sword.isConsumableOrTool());
        assertFalse("Shield is not consumable", shield.isConsumableOrTool());

        // Equipment goes into main inventory (backpack) first
        assertTrue(inventory.pickup(sword));
        assertTrue(inventory.getMainInventory().contains(sword));
        assertNull(inventory.getQuickSlots()[0]);

        assertTrue(inventory.pickup(shield));
        assertTrue(inventory.getMainInventory().contains(shield));
        assertNull(inventory.getQuickSlots()[1]);
    }

    @Test
    public void testConsumableSpilloverToBackpackWhenQuickSlotsFull() {
        // Fill all 6 quick slots
        for (int i = 0; i < 6; i++) {
            Item pot = createConsumable(ItemType.POTION_BLUE, "Potion " + i);
            assertTrue(inventory.pickup(pot));
            assertSame(pot, inventory.getQuickSlots()[i]);
        }
        assertEquals(0, inventory.getMainInventory().size());

        // 7th consumable should spill over to backpack
        Item seventhPot = createConsumable(ItemType.POTION_GOLD, "Golden Elixir");
        assertTrue(inventory.pickup(seventhPot));
        assertEquals(1, inventory.getMainInventory().size());
        assertSame(seventhPot, inventory.getMainInventory().get(0));
    }

    @Test
    public void testRemoveItemFromQuickSlots() {
        Item pot = createConsumable(ItemType.POTION_BLUE, "Healing Potion");
        inventory.pickup(pot);
        assertSame(pot, inventory.getQuickSlots()[0]);

        inventory.removeItem(pot);
        assertNull(inventory.getQuickSlots()[0]);
    }
}
