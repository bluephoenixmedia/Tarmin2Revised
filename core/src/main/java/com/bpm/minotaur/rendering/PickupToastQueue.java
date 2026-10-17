package com.bpm.minotaur.rendering;

import com.bpm.minotaur.gamedata.item.Item;

import java.util.ArrayList;
import java.util.List;

/**
 * The items named in the "Acquired" toast. Picking up two things in a moment used to replace the
 * first with the second, so the first was never read; now they are kept together and the player can
 * swap between them (Tab). The newest is shown first, and the whole group fades together.
 */
public final class PickupToastQueue {

    public static final float DISPLAY_SECONDS = 2.5f;
    /** Swapping gives the player time to read the one they swapped to. */
    public static final float SWAP_MIN_SECONDS = 1.5f;
    static final int MAX_ITEMS = 6;

    private final List<Item> items = new ArrayList<>();
    private int shown;
    private float timer;

    public void add(Item item) {
        if (item == null) {
            return;
        }
        if (items.size() >= MAX_ITEMS) {
            items.remove(0);
        }
        items.add(item);
        shown = items.size() - 1;
        timer = DISPLAY_SECONDS;
    }

    /** Shows the next item; false (and nothing changes) when there is not more than one to swap between. */
    public boolean cycle() {
        if (items.size() < 2 || timer <= 0f) {
            return false;
        }
        shown = (shown + 1) % items.size();
        timer = Math.max(timer, SWAP_MIN_SECONDS);
        return true;
    }

    public void update(float delta) {
        if (timer <= 0f) {
            return;
        }
        timer -= delta;
        if (timer <= 0f) {
            items.clear();
            shown = 0;
            timer = 0f;
        }
    }

    public boolean isActive() {
        return timer > 0f && !items.isEmpty();
    }

    public Item current() {
        return isActive() ? items.get(shown) : null;
    }

    public int size() {
        return isActive() ? items.size() : 0;
    }

    /** 1-based position of the item on show, for the "2/3" hint. */
    public int position() {
        return isActive() ? shown + 1 : 0;
    }

    /** Seconds left, for the fade. */
    public float remaining() {
        return Math.max(0f, timer);
    }
}
