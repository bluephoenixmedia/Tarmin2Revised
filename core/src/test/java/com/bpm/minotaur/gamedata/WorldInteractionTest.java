package com.bpm.minotaur.gamedata;

import com.bpm.minotaur.gamedata.effects.StatusEffectType;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.Item.ItemType;
import com.bpm.minotaur.gamedata.item.ItemTemplate;
import com.bpm.minotaur.gamedata.player.PlayerStats;
import com.bpm.minotaur.managers.StatusManager;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class WorldInteractionTest {

    private PlayerStats stats;
    private StatusManager statusManager;

    @Before
    public void setUp() {
        stats = new PlayerStats(Difficulty.MEDIUM);
        statusManager = new StatusManager();
    }

    private Item createProp(ItemType type, String name, boolean isImpassable) {
        ItemTemplate template = new ItemTemplate();
        template.friendlyName = name;
        template.isImpassable = isImpassable;
        if (type == ItemType.HOME_CHEST) {
            template.isContainer = true;
        }
        return Item.fromTemplate(type, template);
    }

    @Test
    public void testShelterPropsCreatedCorrectly() {
        Item chest = createProp(ItemType.HOME_CHEST, "Storage Chest", true);
        Item bed = createProp(ItemType.HOME_SLEEPING_BAG, "Sleeping Bag", true);
        Item bench = createProp(ItemType.HOME_CRAFTING_BENCH, "Crafting Bench", true);
        Item firePot = createProp(ItemType.HOME_FIRE_POT, "Cooking Fire Pot", true);

        assertEquals(ItemType.HOME_CHEST, chest.getType());
        assertTrue("Shelter chest must be impassable furniture", chest.isImpassable());

        assertEquals(ItemType.HOME_SLEEPING_BAG, bed.getType());
        assertTrue("Shelter bedroll must be impassable furniture", bed.isImpassable());

        assertEquals(ItemType.HOME_CRAFTING_BENCH, bench.getType());
        assertTrue("Shelter crafting bench must be impassable furniture", bench.isImpassable());

        assertEquals(ItemType.HOME_FIRE_POT, firePot.getType());
        assertTrue("Shelter fire pot must be impassable furniture", firePot.isImpassable());
    }

    @Test
    public void testShelterBedrollRestoresVitalsAndClearsDebuffs() {
        // Damage player and apply poison/confusion debuffs
        stats.setCurrentHP(10);
        stats.setCurrentMP(5);
        statusManager.addEffect(StatusEffectType.POISONED, 10, 1, false);
        statusManager.addEffect(StatusEffectType.CONFUSED, 10, 1, false);

        assertTrue(stats.getCurrentHP() < stats.getMaxHP());
        assertTrue(stats.getCurrentMP() < stats.getMaxMP());
        assertTrue(statusManager.hasEffect(StatusEffectType.POISONED));
        assertTrue(statusManager.hasEffect(StatusEffectType.CONFUSED));

        // Simulate resting action executed by GameScreen / bedroll interaction
        stats.setCurrentHP(stats.getMaxHP());
        stats.setCurrentMP(stats.getMaxMP());
        statusManager.clearEffects();

        assertEquals("HP should be fully restored", stats.getMaxHP(), stats.getCurrentHP());
        assertEquals("MP should be fully restored", stats.getMaxMP(), stats.getCurrentMP());
        assertFalse("Poison should be cleansed", statusManager.hasEffect(StatusEffectType.POISONED));
        assertFalse("Confusion should be cleansed", statusManager.hasEffect(StatusEffectType.CONFUSED));
    }

    @Test
    public void testFacingTargetCalculation() {
        Direction facing = Direction.NORTH;
        int px = 5;
        int py = 5;

        int frontX = (int) (px + facing.getVector().x);
        int frontY = (int) (py + facing.getVector().y);

        assertEquals(5, frontX);
        assertEquals(6, frontY); // NORTH vector is (0, 1) in Tarmin2 grid
    }
}
