package com.bpm.minotaur.gamedata.item;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class ShelterChestTest {

    private ShelterChest chest;

    @Before
    public void setUp() {
        chest = ShelterChest.getInstance();
        chest.clear();
    }

    @Test
    public void testChestCapacityAndAdd() {
        assertEquals(30, chest.getCapacity());
        assertEquals(0, chest.getItemCount());

        // Create mock or dummy items if needed, or test with empty/null slot checks
        Item dummyItem = new Item();
        assertTrue(chest.addItem(dummyItem));
        assertEquals(1, chest.getItemCount());
        assertSame(dummyItem, chest.getItem(0));
    }

    @Test
    public void testChestFull() {
        for (int i = 0; i < chest.getCapacity(); i++) {
            assertTrue(chest.addItem(new Item()));
        }
        assertEquals(chest.getCapacity(), chest.getItemCount());
        assertFalse("Chest should reject items when full", chest.addItem(new Item()));
    }

    @Test
    public void testRemoveItem() {
        Item item1 = new Item();
        Item item2 = new Item();
        chest.addItem(item1);
        chest.addItem(item2);

        Item removed = chest.removeItem(0);
        assertSame(item1, removed);
        assertEquals(1, chest.getItemCount());
        assertSame(item2, chest.getItem(0)); // Shifts or clears slot
    }
}
