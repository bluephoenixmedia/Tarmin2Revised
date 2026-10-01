package com.bpm.minotaur.gamedata.item;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * The data gives nearly every item the default weight of 1, so weight has to come from what an
 * item is. These check the ordering that matters, not the exact numbers, which are tuning.
 */
public class ItemWeightsTest {

    private static Item item(Item.ItemType type, java.util.function.Consumer<ItemTemplate> shape) {
        ItemTemplate t = new ItemTemplate();
        t.friendlyName = "Test";
        shape.accept(t);
        return Item.fromTemplate(type, t);
    }

    @Test
    public void anExplicitWeightInTheDataIsHonoured() {
        Item heavy = item(Item.ItemType.SWORD, t -> {
            t.isWeapon = true;
            t.weight = 7.5f;
        });
        assertEquals(7.5f, ItemWeights.of(heavy), 0.001f);
    }

    @Test
    public void weaponsWeighMoreTheMoreUnwieldyTheyAre() {
        Item dagger = item(Item.ItemType.KNIFE, t -> {
            t.isWeapon = true;
            t.isFinesse = true;
        });
        Item sword = item(Item.ItemType.SWORD, t -> t.isWeapon = true);
        Item greatsword = item(Item.ItemType.SWORD, t -> {
            t.isWeapon = true;
            t.isTwoHanded = true;
        });
        assertTrue(ItemWeights.of(dagger) < ItemWeights.of(sword));
        assertTrue(ItemWeights.of(sword) < ItemWeights.of(greatsword));
    }

    @Test
    public void bodyArmourOutweighsAHelmetWhichOutweighsARing() {
        Item chest = item(Item.ItemType.SWORD, t -> {
            t.isArmor = true;
            t.isTorso = true;
        });
        Item helmet = item(Item.ItemType.SWORD, t -> {
            t.isArmor = true;
            t.isHelmet = true;
        });
        Item ring = item(Item.ItemType.SWORD, t -> t.isRing = true);
        assertTrue(ItemWeights.of(chest) > ItemWeights.of(helmet));
        assertTrue(ItemWeights.of(helmet) > ItemWeights.of(ring));
    }

    @Test
    public void heavyArmourWeighsMoreThanLightOfTheSameSlot() {
        Item light = item(Item.ItemType.SWORD, t -> {
            t.isArmor = true;
            t.isTorso = true;
            t.armorCategory = "LIGHT";
        });
        Item heavy = item(Item.ItemType.SWORD, t -> {
            t.isArmor = true;
            t.isTorso = true;
            t.armorCategory = "HEAVY";
        });
        assertTrue(ItemWeights.of(heavy) > ItemWeights.of(light) * 1.5f);
    }

    @Test
    public void smallThingsAreLightAndNeverFree() {
        Item scroll = item(Item.ItemType.SCROLL, t -> {});
        Item potion = item(Item.ItemType.SWORD, t -> t.isPotion = true);
        Item sword = item(Item.ItemType.SWORD, t -> t.isWeapon = true);
        assertTrue(ItemWeights.of(scroll) > 0f);
        assertTrue(ItemWeights.of(scroll) < ItemWeights.of(potion));
        assertTrue(ItemWeights.of(potion) < ItemWeights.of(sword));
    }

    @Test
    public void aContainerWeighsItsContentsToo() {
        Item bag = item(Item.ItemType.SWORD, t -> t.isContainer = true);
        float empty = ItemWeights.of(bag);
        bag.getContents().add(item(Item.ItemType.SWORD, t -> t.isWeapon = true));
        assertTrue(ItemWeights.of(bag) > empty);
    }

    @Test
    public void aNullItemWeighsNothing() {
        assertEquals(0f, ItemWeights.of(null), 0.001f);
    }
}
