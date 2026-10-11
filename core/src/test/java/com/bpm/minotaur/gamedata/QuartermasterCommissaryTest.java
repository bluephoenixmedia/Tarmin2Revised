package com.bpm.minotaur.gamedata;

import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.ItemDataManager;
import com.bpm.minotaur.managers.Relics;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/**
 * Living War W18: The war camp quartermaster commissary.
 * Stocks war provisions, doctrine arms, tabards, and gives sworn military discounts.
 */
public class QuartermasterCommissaryTest {

    private static ItemDataManager idm;

    @BeforeClass
    public static void setup() {
        com.bpm.minotaur.managers.BattleSpoilsTest.setUpGdx();
        idm = new ItemDataManager();
        idm.load();
        idm.loadWeapons();
        idm.loadArmor();
    }

    @org.junit.AfterClass
    public static void tearDown() {
        com.bpm.minotaur.managers.BattleSpoilsTest.tearDownGdx();
    }

    @Test
    public void quartermasterCarriesProvisionsArmsAndTabard() {
        ShopkeeperNpc qm = new ShopkeeperNpc(1f, 1f, null);
        qm.setCustomDisplayName("Quartermaster of Ashen Penitents");

        ShopInventory shop = new ShopInventory();
        shop.stockQuartermaster(qm, idm, null, 2, "ashen_penitents", 3, "Ashen Penitents");

        assertEquals("Quartermaster of Ashen Penitents", qm.getDisplayName());

        List<Item> stock = qm.getInventory().getAllItems();
        assertFalse("Quartermaster inventory must not be empty", stock.isEmpty());

        boolean hasAmmo = false;
        boolean hasProvisions = false;
        boolean hasDoctrineWeapon = false;
        boolean hasTabard = false;

        String signatureWeapon = Relics.weaponOf("ashen_penitents"); // MACE_GREAT

        for (Item item : stock) {
            if (item.getType() == Item.ItemType.QUIVER || item.getType() == Item.ItemType.SHOT_POUCH) {
                hasAmmo = true;
            }
            if (item.getType() == Item.ItemType.FOOD || item.getType() == Item.ItemType.POTION_PINK || item.getType() == Item.ItemType.POTION_BLUE) {
                hasProvisions = true;
            }
            if (item.getType().name().equals(signatureWeapon)) {
                hasDoctrineWeapon = true;
            }
            if (item.getType() == Item.ItemType.TABARD) {
                hasTabard = true;
                assertEquals("ashen_penitents", item.getHeraldry());
                assertEquals(3, item.getTrophyHouseId());
                assertTrue(item.getDisplayName().contains("Ashen Penitents"));
            }
        }

        assertTrue("Quartermaster must carry ammunition", hasAmmo);
        assertTrue("Quartermaster must carry provisions", hasProvisions);
        assertTrue("Quartermaster must carry doctrine signature weapon", hasDoctrineWeapon);
        assertTrue("Quartermaster must carry house tabard", hasTabard);
    }

    @Test
    public void swornMilitaryDiscountAppliesTwentyPercent() {
        ShopkeeperNpc qm = new ShopkeeperNpc(1f, 1f, null);
        qm.setMilitaryDiscount(0.20f);
        assertEquals(0.20f, qm.getMilitaryDiscount(), 0.001f);

        ShopInventory shop = new ShopInventory();
        shop.stockQuartermaster(qm, idm, null, 1, "chainwrights", 2, "Chainwrights");

        List<Item> stock = qm.getInventory().getAllItems();
        assertFalse(stock.isEmpty());
        Item item = stock.get(0);

        int normalPrice = ShopInventory.getEffectiveBuyPrice(item, idm, 0f);
        int discountedPrice = ShopInventory.getEffectiveBuyPrice(item, idm, qm.getMilitaryDiscount());

        assertTrue("Sworn discount must reduce price", discountedPrice < normalPrice);
        assertEquals(Math.max(1, Math.round(normalPrice * 0.80f)), discountedPrice);
    }
}
