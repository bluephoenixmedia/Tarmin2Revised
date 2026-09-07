package com.bpm.minotaur.gamedata;

import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.Item.ItemType;
import com.bpm.minotaur.gamedata.item.ItemColor;
import com.bpm.minotaur.gamedata.item.ItemDataManager;
import com.bpm.minotaur.gamedata.item.ItemTemplate;
import com.bpm.minotaur.gamedata.item.ShelterChest;
import com.bpm.minotaur.managers.CraftingManager;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class CraftingManagerTest {

    private CraftingManager craftingManager;
    private Inventory inventory;
    private ShelterChest shelterChest;

    @Before
    public void setUp() {
        // Mock ItemDataManager for isolated testing
        ItemDataManager itemDataManager = new ItemDataManager() {
            @Override
            public Item createItem(ItemType type, int x, int y, ItemColor color, com.badlogic.gdx.assets.AssetManager assetManager) {
                ItemTemplate t = new ItemTemplate();
                t.friendlyName = type.name();
                t.isWeapon = (type == ItemType.SWORD || type == ItemType.KNIFE || type == ItemType.AXE || type == ItemType.RUSTY_SWORD);
                t.isArmor = (type == ItemType.BREASTPLATE || type == ItemType.HELMET || type == ItemType.LEATHER_ARMOR);
                t.isShield = (type == ItemType.SMALL_SHIELD || type == ItemType.LARGE_SHIELD);
                t.isAmulet = (type == ItemType.AMULET);
                return Item.fromTemplate(type, t);
            }
        };

        craftingManager = new CraftingManager(itemDataManager, null);
        inventory = new Inventory();
        shelterChest = ShelterChest.getInstance();
        shelterChest.clear();
    }

    private Item createWeapon(ItemType type, String name) {
        ItemTemplate t = new ItemTemplate();
        t.friendlyName = name;
        t.isWeapon = true;
        return Item.fromTemplate(type, t);
    }

    private Item createArmor(ItemType type, String name, int ac) {
        ItemTemplate t = new ItemTemplate();
        t.friendlyName = name;
        t.isArmor = true;
        t.armorClassBonus = ac;
        return Item.fromTemplate(type, t);
    }

    private Item createDebris(ItemType type, String name) {
        ItemTemplate t = new ItemTemplate();
        t.friendlyName = name;
        return Item.fromTemplate(type, t);
    }

    @Test
    public void testSalvageYields() {
        Item ironSword = createWeapon(ItemType.SWORD, "Iron Sword");
        List<ItemType> swordYield = craftingManager.getSalvageYield(ironSword);
        assertTrue(swordYield.contains(ItemType.METAL_SCRAP));
        assertTrue(swordYield.contains(ItemType.STICK));

        Item hilt = createDebris(ItemType.BROKEN_HILT, "Broken Hilt");
        List<ItemType> hiltYield = craftingManager.getSalvageYield(hilt);
        assertTrue(hiltYield.contains(ItemType.METAL_SCRAP));
        assertTrue(hiltYield.contains(ItemType.STICK));

        Item skull = createDebris(ItemType.RAT_SKULL, "Rat Skull");
        List<ItemType> skullYield = craftingManager.getSalvageYield(skull);
        assertTrue(skullYield.contains(ItemType.BONE));

        Item leatherArmor = createArmor(ItemType.LEATHER_ARMOR, "Leather Tunic", 2);
        List<ItemType> leatherYield = craftingManager.getSalvageYield(leatherArmor);
        assertTrue(leatherYield.contains(ItemType.LEATHER_SCRAP));
    }

    @Test
    public void testJunkDebrisDetection() {
        Item nail = createDebris(ItemType.BENT_NAIL, "Bent Nail");
        Item chain = createDebris(ItemType.RUSTED_CHAIN, "Rusted Chain");
        Item cloth = createDebris(ItemType.DIRTY_CLOTH, "Dirty Cloth");
        Item sword = createWeapon(ItemType.SWORD, "Iron Sword");

        assertTrue(craftingManager.isJunkDebris(nail));
        assertTrue(craftingManager.isJunkDebris(chain));
        assertTrue(craftingManager.isJunkDebris(cloth));
        assertFalse("Valuable weapons should not be flagged as junk debris", craftingManager.isJunkDebris(sword));
    }

    @Test
    public void testHoningProgressionAndCap() {
        Item sword = createWeapon(ItemType.SWORD, "Broadsword");
        assertEquals(0, sword.getEnhancementLevel());

        // Add 25 metal scrap to player inventory
        for (int i = 0; i < 25; i++) {
            inventory.pickupToBackpack(createDebris(ItemType.METAL_SCRAP, "Scrap"));
        }
        inventory.pickupToBackpack(createDebris(ItemType.STRANGE_METAL, "Strange Metal"));

        // Hone to +1 (costs 3)
        assertTrue(craftingManager.canHone(sword, inventory));
        assertTrue(craftingManager.honeItem(inventory, sword));
        assertEquals(1, sword.getEnhancementLevel());
        assertTrue(sword.getDisplayName().contains("+1"));

        // Hone to +2 (costs 6)
        assertTrue(craftingManager.canHone(sword, inventory));
        assertTrue(craftingManager.honeItem(inventory, sword));
        assertEquals(2, sword.getEnhancementLevel());
        assertTrue(sword.getDisplayName().contains("+2"));

        // Hone to +3 (costs 12 + 1 strange metal)
        assertTrue(craftingManager.canHone(sword, inventory));
        assertTrue(craftingManager.honeItem(inventory, sword));
        assertEquals(3, sword.getEnhancementLevel());
        assertTrue(sword.getDisplayName().contains("+3"));

        // Cannot hone beyond +3
        assertFalse("Cannot hone beyond max +3 cap", craftingManager.canHone(sword, inventory));
        assertFalse(craftingManager.honeItem(inventory, sword));
    }

    @Test
    public void testTrophyInfusionAndCap() {
        Item dagger = createWeapon(ItemType.KNIFE, "Hunting Knife");
        assertEquals(0, dagger.getTrophyInfusionCount());

        Item claw = createDebris(ItemType.CLAW, "Beast Claw");
        Item eye = createDebris(ItemType.MONSTER_EYE, "Monster Eye");
        Item tooth = createDebris(ItemType.TOOTH, "Sharp Tooth");

        inventory.pickupToBackpack(claw);
        inventory.pickupToBackpack(eye);
        inventory.pickupToBackpack(tooth);

        // Infuse Claw (Bleed)
        assertTrue(craftingManager.canInfuse(dagger, claw, inventory));
        assertTrue(craftingManager.infuseTrophy(inventory, dagger, claw));
        assertEquals(1, dagger.getTrophyInfusionCount());

        // Infuse Eye (Crit Chance)
        assertTrue(craftingManager.canInfuse(dagger, eye, inventory));
        assertTrue(craftingManager.infuseTrophy(inventory, dagger, eye));
        assertEquals(2, dagger.getTrophyInfusionCount());

        // 3rd infusion should be rejected by 2-socket cap
        assertFalse("Cannot infuse beyond 2 sockets", craftingManager.canInfuse(dagger, tooth, inventory));
        assertFalse(craftingManager.infuseTrophy(inventory, dagger, tooth));
    }

    @Test
    public void testUnifiedShelterStorageAccess() {
        Item sword = createWeapon(ItemType.SWORD, "Shortsword");

        // Player pack has 1 scrap; shelter chest has 2 scrap (total 3 scrap for +1)
        inventory.pickupToBackpack(createDebris(ItemType.METAL_SCRAP, "Scrap"));
        shelterChest.addItem(createDebris(ItemType.METAL_SCRAP, "Chest Scrap 1"));
        shelterChest.addItem(createDebris(ItemType.METAL_SCRAP, "Chest Scrap 2"));

        assertEquals(3, craftingManager.countMaterial(inventory, ItemType.METAL_SCRAP));
        assertTrue(craftingManager.canHone(sword, inventory));

        // Honing consumes 3 scrap across pack and chest
        assertTrue(craftingManager.honeItem(inventory, sword));
        assertEquals(1, sword.getEnhancementLevel());
        assertEquals(0, craftingManager.countMaterial(inventory, ItemType.METAL_SCRAP));
        assertTrue(shelterChest.isEmpty());
    }

    @Test
    public void testBoneTalismanForging() {
        Item bone = createDebris(ItemType.BONE, "Sturdy Bone");
        Item eye = createDebris(ItemType.MONSTER_EYE, "Monster Eye");
        Item scrap = createDebris(ItemType.METAL_SCRAP, "Metal Scrap");

        inventory.pickupToBackpack(bone);
        inventory.pickupToBackpack(eye);
        inventory.pickupToBackpack(scrap);

        assertTrue(craftingManager.canForgeTalisman(inventory, bone, eye));
        Item talisman = craftingManager.forgeBoneTalisman(inventory, bone, eye);

        assertNotNull(talisman);
        assertEquals(ItemType.AMULET, talisman.getType());
        assertTrue(talisman.isModified());
    }

    @Test
    public void testBatchSalvageJunk() {
        inventory.pickupToBackpack(createDebris(ItemType.BENT_NAIL, "Nail"));
        inventory.pickupToBackpack(createDebris(ItemType.BROKEN_HILT, "Hilt"));
        shelterChest.addItem(createDebris(ItemType.RUSTED_CHAIN, "Chain"));

        int salvagedCount = craftingManager.salvageAllJunk(inventory);
        assertEquals(3, salvagedCount);
        assertTrue(shelterChest.isEmpty());
        // At least 3 metal scrap should be generated and deposited into backpack
        assertTrue(craftingManager.countMaterial(inventory, ItemType.METAL_SCRAP) >= 3);
    }
}
