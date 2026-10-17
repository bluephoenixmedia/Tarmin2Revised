package com.bpm.minotaur.managers;

import com.bpm.minotaur.gamedata.item.Item;
import org.junit.Test;

import java.util.EnumSet;
import java.util.Random;

import static org.junit.Assert.*;

/** Plan T1.10: every kind of fragment turns up in the world. */
public class FragmentSpawnTest {

    @Test
    public void everyKindOfFragmentSpawnsSomewhere() {
        EnumSet<Item.ItemType> seen = EnumSet.noneOf(Item.ItemType.class);
        Random rng = new Random(1);
        for (int i = 0; i < 400; i++) {
            Item.ItemType surface = SpawnManager.rollChronicleFragment(rng, 1);
            Item.ItemType deep = SpawnManager.rollChronicleFragment(rng, 3);
            if (surface != null) seen.add(surface);
            if (deep != null) seen.add(deep);
        }
        assertEquals(EnumSet.of(Item.ItemType.CHRONICLE_PAGE, Item.ItemType.HERALD_PROCLAMATION,
                Item.ItemType.TORN_BANNER), seen);
    }

    @Test
    public void mostChunksHaveNone() {
        Random rng = new Random(2);
        int found = 0;
        for (int i = 0; i < 1000; i++) {
            if (SpawnManager.rollChronicleFragment(rng, 2) != null) found++;
        }
        assertTrue("found " + found, found > 150 && found < 450);
    }
}
