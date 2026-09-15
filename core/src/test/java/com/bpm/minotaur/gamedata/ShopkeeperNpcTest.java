package com.bpm.minotaur.gamedata;

import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.Item.ItemType;
import com.bpm.minotaur.gamedata.item.ItemDataManager;
import com.bpm.minotaur.gamedata.item.ItemTemplate;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/**
 * The traveling merchant's death drops -- specifically the loot gate on his
 * Void chain laser: it always drops, but spent, so restoring it (see
 * {@code CraftingManagerTest}) is the "careful planning" half of getting a
 * working copy of the weapon.
 */
public class ShopkeeperNpcTest {

    @Test
    public void testDeathDropsAlwaysIncludeTheSpentVoidChainLaser() {
        ShopkeeperNpc merchant = new ShopkeeperNpc(4, 4, null);
        ItemDataManager itemDataManager = new ItemDataManager() {
            @Override
            public Item createItem(ItemType type, int x, int y, com.bpm.minotaur.gamedata.item.ItemColor color,
                                   com.badlogic.gdx.assets.AssetManager assetManager) {
                ItemTemplate t = new ItemTemplate();
                t.friendlyName = type.name();
                return Item.fromTemplate(type, t);
            }
        };

        List<Item> drops = merchant.createDeathDrops(itemDataManager, null);

        boolean hasSpentLaser = false;
        for (Item drop : drops) {
            if (drop.getType() == ItemType.VOID_CHAIN_LASER_SPENT) {
                hasSpentLaser = true;
            }
            assertNotEquals("The weapon never drops already restored -- that is the point of the cell being spent",
                    ItemType.VOID_CHAIN_LASER, drop.getType());
        }
        assertTrue("Killing the merchant must always yield the spent weapon", hasSpentLaser);
    }
}
