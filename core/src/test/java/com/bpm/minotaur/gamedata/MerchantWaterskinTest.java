package com.bpm.minotaur.gamedata;

import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.ItemDataManager;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.*;

/** A lost waterskin can always be bought back from the merchant (#208). */
public class MerchantWaterskinTest {

    private static ItemDataManager idm;

    @BeforeClass
    public static void setup() {
        com.bpm.minotaur.managers.BattleSpoilsTest.setUpGdx();
        idm = new ItemDataManager();
        idm.load();
        idm.loadWeapons();
        idm.loadArmor();
    }

    @AfterClass
    public static void tearDown() {
        com.bpm.minotaur.managers.BattleSpoilsTest.tearDownGdx();
    }

    @Test
    public void everyMerchantStocksAFullWaterskin() {
        for (int i = 0; i < 25; i++) {
            ShopkeeperNpc merchant = new ShopkeeperNpc(1f, 1f, null);
            new ShopInventory().stock(merchant, idm, null, 1);
            Item skin = null;
            for (Item item : merchant.getInventory().getAllItems()) {
                if (item.getType() == Item.ItemType.WATERSKIN) skin = item;
            }
            assertNotNull("the merchant sells a waterskin", skin);
            assertFalse("it is sold full", com.bpm.minotaur.gamedata.item.Waterskin.isEmpty(skin));
            assertTrue("it has a price", ShopInventory.getBuyPrice(skin, idm) > 0);
        }
    }
}
