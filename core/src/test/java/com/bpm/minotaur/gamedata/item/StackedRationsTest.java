package com.bpm.minotaur.gamedata.item;

import com.bpm.minotaur.gamedata.ChunkData;
import com.bpm.minotaur.gamedata.Inventory;
import com.bpm.minotaur.gamedata.item.Item.ItemType;
import com.bpm.minotaur.gamedata.save.ItemSaveData;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * 2026-10-03: a new game's thirty rations took thirty of the forty-eight
 * backpack slots, one each. Rations stack now: one slot, a count, one eaten
 * (or cooked, or sold) at a time.
 */
public class StackedRationsTest {

    private static Item ration() {
        ItemTemplate t = new ItemTemplate();
        t.friendlyName = "Food";
        t.isUsable = true;
        t.isFood = true;
        return Item.fromTemplate(ItemType.FOOD, t);
    }

    private static Item rations(int n) {
        Item r = ration();
        r.setStackCount(n);
        return r;
    }

    @Test
    public void rationsPickedUpJoinOneStack() {
        Inventory inv = new Inventory();
        for (int i = 0; i < 30; i++) {
            assertTrue(inv.pickupToBackpack(ration()));
        }
        assertEquals("thirty rations are one slot", 1, inv.getCarriedCount());
        assertEquals(30, inv.getMainInventory().get(0).getStackCount());
    }

    @Test
    public void quickSlotPickupsJoinTheStackToo() {
        Inventory inv = new Inventory();
        inv.pickup(ration());
        inv.pickup(ration());
        int stacks = 0;
        int total = 0;
        for (Item it : inv.getAllItems()) {
            if (it.getType() == ItemType.FOOD) {
                stacks++;
                total += it.getStackCount();
            }
        }
        assertEquals(1, stacks);
        assertEquals(2, total);
    }

    @Test
    public void eatingTakesOneAndTheLastOneEmptiesTheSlot() {
        Inventory inv = new Inventory();
        Item stack = rations(2);
        inv.pickupToBackpack(stack);

        assertTrue(inv.consumeOne(stack));
        assertEquals(1, stack.getStackCount());
        assertTrue("one left, still carried", inv.contains(stack));

        assertTrue(inv.consumeOne(stack));
        assertFalse("the last one leaves the slot empty", inv.contains(stack));
    }

    @Test
    public void theNameCarriesTheCountButOneIsJustOne() {
        Item stack = rations(30);
        assertEquals("Food x30", stack.getDisplayName());
        assertEquals("Food", stack.getSingleDisplayName());
        assertEquals("Food", ration().getDisplayName());
    }

    @Test
    public void mealsAndFleshDoNotStack() {
        Item meal = ration();
        meal.addMealEffect(com.bpm.minotaur.gamedata.effects.StatusEffectType.TELEPATHY);
        assertFalse("a meal has its own effects", meal.isStackable());

        Item flesh = ration();
        flesh.setCorpseSource(com.bpm.minotaur.gamedata.monster.Monster.MonsterType.GOBLIN);
        assertFalse("flesh has its own source", flesh.isStackable());

        assertTrue(ration().isStackable());
    }

    @Test
    public void anOldSavesLooseRationsFoldIntoOneStack() {
        Inventory inv = new Inventory();
        // What a pre-stacking save restores: thirty separate items, added raw.
        for (int i = 0; i < 30; i++) {
            inv.getMainInventory().add(ration());
        }
        inv.getQuickSlots()[0] = ration();

        inv.consolidateStacks();

        assertEquals(1, inv.getCarriedCount());
        assertNull(inv.getQuickSlots()[0]);
        int total = 0;
        for (Item it : inv.getAllItems()) total += it.getStackCount();
        assertEquals("no ration lost in the fold", 31, total);
    }

    @Test
    public void theCountSurvivesASaveAndTheFloor() {
        Item stack = rations(17);

        ItemSaveData saved = new ItemSaveData(stack);
        assertEquals(17, saved.stackCount);

        ChunkData.ItemData onFloor = new ChunkData.ItemData(stack);
        Item back = ration();
        onFloor.applyTo(back);
        assertEquals(17, back.getStackCount());
    }

    @Test
    public void anOldSaveWithNoCountReadsAsOne() {
        ChunkData.ItemData legacy = new ChunkData.ItemData(ration());
        legacy.stackCount = 0;
        Item back = ration();
        legacy.applyTo(back);
        assertEquals(1, back.getStackCount());
    }
}
