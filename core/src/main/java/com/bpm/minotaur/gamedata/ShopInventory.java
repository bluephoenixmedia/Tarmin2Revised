package com.bpm.minotaur.gamedata;

import com.badlogic.gdx.assets.AssetManager;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.Item.ItemType;
import com.bpm.minotaur.gamedata.item.ItemColor;
import com.bpm.minotaur.gamedata.item.ItemDataManager;
import com.bpm.minotaur.gamedata.item.ItemTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Utility class for generating and pricing the Shopkeeper's inventory.
 *
 * Buy price = baseValue × 2 (merchant markup)
 * Sell price = baseValue / 2 (merchant buyback)
 */
public class ShopInventory {

    // Item pools by category — all entries verified against ItemType enum
    private static final ItemType[] WEAPON_POOL = {
            ItemType.AXE, ItemType.KNIFE, ItemType.SWORD, ItemType.BOW,
            ItemType.SPEAR_ONE_HANDED, ItemType.MACE_FOOTMANS, ItemType.FLAIL_FOOTMANS,
            ItemType.MORNING_STAR, ItemType.CROSSBOW, ItemType.DART
    };

    private static final ItemType[] ARMOR_POOL = {
            ItemType.LEATHER_ARMOR, ItemType.HAUBERK, ItemType.SMALL_SHIELD,
            ItemType.LARGE_SHIELD, ItemType.HELMET, ItemType.LEATHER_HELM,
            ItemType.BOOTS, ItemType.GAUNTLETS, ItemType.CLOAK
    };

    private static final ItemType[] POTION_POOL = {
            ItemType.POTION_BLUE, ItemType.POTION_PINK, ItemType.POTION_GREEN,
            ItemType.POTION_GOLD, ItemType.POTION_SWIRLY, ItemType.POTION_BUBBLY
    };

    private static final ItemType[] SPECIAL_POOL = {
            ItemType.RING_GOLD, ItemType.AMULET, ItemType.SCROLL,
            ItemType.WAND, ItemType.FOOD, ItemType.COINS
    };

    private static final float MARKUP = 2.0f;
    private static final float BUYBACK = 0.5f;
    private static final int DEFAULT_BASE_VALUE = 50;

    private final Random rng = new Random();

    /**
     * Populate the shopkeeper's inventory with a random stock.
     * Should be called once after ShopkeeperNpc is constructed.
     */
    public void stock(ShopkeeperNpc shopkeeper, ItemDataManager itemDataManager,
            AssetManager assetManager, int dungeonLevel) {

        Inventory inv = shopkeeper.getInventory();
        int level = Math.max(1, dungeonLevel);

        // 3-5 weapons
        addRandomItems(inv, WEAPON_POOL, rng.nextInt(3) + 3, itemDataManager, assetManager, level);
        // 2-3 armor pieces
        addRandomItems(inv, ARMOR_POOL, rng.nextInt(2) + 2, itemDataManager, assetManager, level);
        // 3-5 potions
        addRandomItems(inv, POTION_POOL, rng.nextInt(3) + 3, itemDataManager, assetManager, level);
        // 1-2 specials
        addRandomItems(inv, SPECIAL_POOL, rng.nextInt(2) + 1, itemDataManager, assetManager, level);
    }

    private void addRandomItems(Inventory inv, ItemType[] pool, int count,
            ItemDataManager idm, AssetManager am, int level) {
        List<ItemType> available = new ArrayList<>();
        for (ItemType t : pool) {
            try {
                idm.getTemplate(t); // will throw if not present
                available.add(t);
            } catch (Exception ignored) {
            }
        }
        if (available.isEmpty())
            return;

        for (int i = 0; i < count; i++) {
            ItemType type = available.get(rng.nextInt(available.size()));
            try {
                Item item = idm.createItem(type, 0, 0, ItemColor.WHITE, am);
                if (!inv.pickupToBackpack(item))
                    break; // inventory full
            } catch (Exception e) {
                com.badlogic.gdx.Gdx.app.error("ShopInventory",
                        "Failed to create item " + type + ": " + e.getMessage());
            }
        }
    }

    // ── Static price helpers ──────────────────────────────────────────────────

    public static int getBuyPrice(Item item, ItemDataManager idm) {
        int base = getBaseValue(item, idm);
        return Math.max(1, (int) (base * MARKUP));
    }

    public static int getSellPrice(Item item, ItemDataManager idm) {
        int base = getBaseValue(item, idm);
        return Math.max(1, (int) (base * BUYBACK));
    }

    private static int getBaseValue(Item item, ItemDataManager idm) {
        try {
            ItemTemplate t = idm.getTemplate(item.getType());
            return t.baseValue > 0 ? t.baseValue : DEFAULT_BASE_VALUE;
        } catch (Exception e) {
            return DEFAULT_BASE_VALUE;
        }
    }
}
