package com.bpm.minotaur.gamedata;

import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.ItemTemplate;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * What the merchant quotes is what the merchant charges.
 *
 * <p>The shop list and the purchase used to compute the price separately, and only the
 * purchase applied the restitution discount -- so after the merchant's strays clipped
 * the player, every listed price was wrong. A shop that advertises one number and
 * charges another is worse than one that shows no prices at all.
 */
public class ShopPricingTest {

    private static Item itemWorth(int baseValue) {
        ItemTemplate t = new ItemTemplate();
        t.friendlyName = "Test Item";
        t.baseValue = baseValue;
        return Item.fromTemplate(Item.ItemType.SWORD, t);
    }

    @Test
    public void testTheMerchantMarksUpAndBuysBackLower() {
        Item item = itemWorth(100);

        int buy = ShopInventory.getBuyPrice(item, null);
        int sell = ShopInventory.getSellPrice(item, null);

        assertTrue("buying must cost more than selling returns", buy > sell);
    }

    @Test
    public void testWithNoDiscountTheEffectivePriceIsTheListPrice() {
        Item item = itemWorth(100);

        assertEquals(ShopInventory.getBuyPrice(item, null),
                ShopInventory.getEffectiveBuyPrice(item, null, 0f));
    }

    @Test
    public void testAnApologyDiscountLowersWhatIsCharged() {
        Item item = itemWorth(100);
        int full = ShopInventory.getBuyPrice(item, null);

        int discounted = ShopInventory.getEffectiveBuyPrice(item, null, 0.25f);

        assertTrue("a 25% apology must actually cost less", discounted < full);
        assertEquals(Math.round(full * 0.75f), discounted);
    }

    @Test
    public void testNothingIsEverFree() {
        Item item = itemWorth(1);

        assertTrue("even a total discount leaves a token price",
                ShopInventory.getEffectiveBuyPrice(item, null, 1f) >= 1);
        assertTrue(ShopInventory.getBuyPrice(item, null) >= 1);
        assertTrue(ShopInventory.getSellPrice(item, null) >= 1);
    }

    @Test
    public void testDeeperDiscountsCostLess() {
        Item item = itemWorth(200);

        int small = ShopInventory.getEffectiveBuyPrice(item, null, 0.1f);
        int large = ShopInventory.getEffectiveBuyPrice(item, null, 0.5f);

        assertTrue(large < small);
    }
}
