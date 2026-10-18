package com.bpm.minotaur.gamedata.player;

import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.Item.ItemType;
import com.bpm.minotaur.gamedata.item.ItemColor;
import com.bpm.minotaur.gamedata.item.ItemDataManager;
import com.bpm.minotaur.gamedata.item.ItemTemplate;
import org.junit.Test;

import static org.junit.Assert.*;

/** A fresh character knows Cure Wounds and Mote of Light; herbs bind into a Poultice anywhere. */
public class StartingSpellsAndFieldPoulticeTest {

    private static final ItemDataManager ITEMS = new ItemDataManager() {
        @Override
        public Item createItem(ItemType type, int x, int y, ItemColor color, com.badlogic.gdx.assets.AssetManager assetManager) {
            ItemTemplate t = new ItemTemplate();
            t.friendlyName = type.name();
            return Item.fromTemplate(type, t);
        }
    };

    @Test
    public void aFreshCharacterKnowsBothAndPreparesCureWoundsFirst() {
        Player p = new Player(1, 1);
        assertTrue(p.getKnownSpellIds().contains("CURE_WOUNDS"));
        assertTrue(p.getKnownSpellIds().contains("MOTE_OF_LIGHT"));
        assertEquals("CURE_WOUNDS", p.getPreparedSpells()[0]);
    }

    @Test
    public void moteOfLightFillsTheSecondSlotWhenItUnlocks() {
        Player p = new Player(1, 1);
        for (int level = 2; level <= 10 && p.getUnlockedSpellSlots() < 2; level++) {
            p.setLevel(level);
            p.checkSpellSlotProgression(null);
        }
        assertEquals(2, p.getUnlockedSpellSlots());
        assertEquals("CURE_WOUNDS", p.getPreparedSpells()[0]);
        assertEquals("MOTE_OF_LIGHT", p.getPreparedSpells()[1]);
    }

    @Test
    public void glowingLichenBindsAlone() {
        Player p = new Player(1, 1);
        Item lichen = herb(ItemType.GLOWING_LICHEN);
        p.getInventory().pickupToBackpack(lichen);

        assertTrue(PoulticeBinding.bind(p, lichen, ITEMS, null, null));

        assertEquals(0, count(p, ItemType.GLOWING_LICHEN));
        assertEquals(1, count(p, ItemType.POULTICE));
    }

    @Test
    public void mossAndRootBindTogether() {
        Player p = new Player(1, 1);
        Item moss = herb(ItemType.MOSS_CLUMP);
        p.getInventory().pickupToBackpack(moss);
        p.getInventory().pickupToBackpack(herb(ItemType.TWISTED_ROOT));

        assertTrue(PoulticeBinding.bind(p, moss, ITEMS, null, null));

        assertEquals(0, count(p, ItemType.MOSS_CLUMP));
        assertEquals(0, count(p, ItemType.TWISTED_ROOT));
        assertEquals(1, count(p, ItemType.POULTICE));
    }

    @Test
    public void mossWithoutRootStaysMoss() {
        Player p = new Player(1, 1);
        Item moss = herb(ItemType.MOSS_CLUMP);
        p.getInventory().pickupToBackpack(moss);

        assertFalse(PoulticeBinding.bind(p, moss, ITEMS, null, null));

        assertEquals(1, count(p, ItemType.MOSS_CLUMP));
        assertEquals(0, count(p, ItemType.POULTICE));
    }

    private static Item herb(ItemType type) {
        return ITEMS.createItem(type, 0, 0, null, null);
    }

    private static int count(Player p, ItemType type) {
        int n = 0;
        for (Item i : p.getInventory().getAllItems()) {
            if (i != null && i.getType() == type) n++;
        }
        return n;
    }
}
