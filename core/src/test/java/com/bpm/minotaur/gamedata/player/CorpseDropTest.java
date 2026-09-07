package com.bpm.minotaur.gamedata.player;

import com.bpm.minotaur.gamedata.Inventory;
import com.bpm.minotaur.gamedata.item.Item;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class CorpseDropTest {

    @Test
    public void testCorpseContainerStoresAndRecoversItems() {
        Inventory inventory = new Inventory();

        // Populate backpack
        Item item1 = new Item();
        Item item2 = new Item();
        inventory.pickup(item1);
        inventory.pickup(item2);

        assertEquals(2, inventory.getMainInventory().size());

        // Create a corpse container holding all inventory items
        Item corpse = new Item();
        corpse.setContents(new ArrayList<>(inventory.getMainInventory()));
        inventory.getMainInventory().clear();

        assertEquals(0, inventory.getMainInventory().size());
        assertEquals(2, corpse.getContents().size());

        // Simulate recovering items from the corpse container
        List<Item> recovered = new ArrayList<>(corpse.getContents());
        for (Item item : recovered) {
            assertTrue(inventory.pickup(item));
        }
        corpse.getContents().clear();

        assertEquals(2, inventory.getMainInventory().size());
        assertEquals(0, corpse.getContents().size());
    }
}
