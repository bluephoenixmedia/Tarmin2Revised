package com.bpm.minotaur.rendering;

import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.ItemTemplate;
import org.junit.Test;

import static org.junit.Assert.*;

public class PickupToastQueueTest {

    private static Item item(Item.ItemType type) {
        return Item.fromTemplate(type, new ItemTemplate());
    }

    @Test
    public void aSingleItemShowsAndThenFades() {
        PickupToastQueue q = new PickupToastQueue();
        Item sword = item(Item.ItemType.SWORD);
        q.add(sword);
        assertSame(sword, q.current());
        assertEquals(1, q.size());
        q.update(PickupToastQueue.DISPLAY_SECONDS + 0.1f);
        assertNull(q.current());
        assertFalse(q.isActive());
    }

    @Test
    public void aSecondPickupDoesNotEraseTheFirstAndTheNewestShowsFirst() {
        PickupToastQueue q = new PickupToastQueue();
        Item first = item(Item.ItemType.SWORD);
        Item second = item(Item.ItemType.FOOD);
        q.add(first);
        q.add(second);
        assertSame(second, q.current());
        assertEquals(2, q.size());
        assertEquals(2, q.position());
    }

    @Test
    public void cyclingWalksTheItemsAndWraps() {
        PickupToastQueue q = new PickupToastQueue();
        Item a = item(Item.ItemType.SWORD);
        Item b = item(Item.ItemType.FOOD);
        q.add(a);
        q.add(b);
        assertTrue(q.cycle());
        assertSame(a, q.current());
        assertTrue(q.cycle());
        assertSame(b, q.current());
    }

    @Test
    public void cyclingDoesNothingWithOneItemOrNone() {
        PickupToastQueue q = new PickupToastQueue();
        assertFalse(q.cycle());
        q.add(item(Item.ItemType.SWORD));
        assertFalse("a lone item leaves Tab for the controls legend", q.cycle());
    }

    @Test
    public void cyclingDoesNothingOnceTheToastHasFaded() {
        PickupToastQueue q = new PickupToastQueue();
        q.add(item(Item.ItemType.SWORD));
        q.add(item(Item.ItemType.FOOD));
        q.update(PickupToastQueue.DISPLAY_SECONDS + 0.1f);
        assertFalse(q.cycle());
    }

    @Test
    public void swappingGivesTimeToReadTheNewItem() {
        PickupToastQueue q = new PickupToastQueue();
        q.add(item(Item.ItemType.SWORD));
        q.add(item(Item.ItemType.FOOD));
        q.update(PickupToastQueue.DISPLAY_SECONDS - 0.2f);
        assertTrue(q.remaining() < PickupToastQueue.SWAP_MIN_SECONDS);
        q.cycle();
        assertTrue(q.remaining() >= PickupToastQueue.SWAP_MIN_SECONDS);
    }

    @Test
    public void aNewPickupRestartsTheTimerForTheWholeGroup() {
        PickupToastQueue q = new PickupToastQueue();
        q.add(item(Item.ItemType.SWORD));
        q.update(2.0f);
        q.add(item(Item.ItemType.FOOD));
        assertEquals(PickupToastQueue.DISPLAY_SECONDS, q.remaining(), 0.001f);
        assertEquals(2, q.size());
    }

    @Test
    public void theQueueIsBoundedAndDropsTheOldest() {
        PickupToastQueue q = new PickupToastQueue();
        Item oldest = item(Item.ItemType.SWORD);
        q.add(oldest);
        for (int i = 0; i < PickupToastQueue.MAX_ITEMS; i++) {
            q.add(item(Item.ItemType.FOOD));
        }
        assertEquals(PickupToastQueue.MAX_ITEMS, q.size());
        for (int i = 0; i < PickupToastQueue.MAX_ITEMS; i++) {
            assertNotSame(oldest, q.current());
            q.cycle();
        }
    }
}
