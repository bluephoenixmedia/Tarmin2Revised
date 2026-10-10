package com.bpm.minotaur.gamedata.player;

import com.bpm.minotaur.gamedata.item.Item;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Fixes 2026-10-10, item 2: walking over a blowgun needle picked it up and turned it into arrows,
 * which read as picking up a blowgun. Only what feeds the arrow and shot pools is gathered on a step.
 */
public class AmmoAutoPickupTest {

    @Test
    public void arrowsBoltsQuiversAndShotAreGatheredUnderfoot() {
        for (Item.ItemType t : new Item.ItemType[]{Item.ItemType.QUIVER, Item.ItemType.SHOT_POUCH,
                Item.ItemType.ARROW_FLIGHT, Item.ItemType.QUARREL_HEAVY}) {
            assertTrue(t.name(), Item.gathersUnderfoot(t));
        }
    }

    @Test
    public void blowgunDartsNeedlesDartsAndSlingStonesWaitToBePickedUp() {
        for (Item.ItemType t : new Item.ItemType[]{Item.ItemType.BLOWGUN, Item.ItemType.BLOWGUN_NEEDLE,
                Item.ItemType.BLOWGUN_BARBED_DART, Item.ItemType.DART, Item.ItemType.SLING_STONE}) {
            assertFalse(t.name(), Item.gathersUnderfoot(t));
        }
    }
}
