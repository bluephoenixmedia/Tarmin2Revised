package com.bpm.minotaur.screens.inventory;

import com.bpm.minotaur.gamedata.item.Item;
import java.util.ArrayList;
import java.util.List;

public class InventoryEventBus {

    public interface Listener {
        default void onItemMoved(InventorySlot from, InventorySlot to, Item item) {}
        default void onItemDropped(Item item) {}
        default void onStatsChanged() {}
        default void onSpellbookChanged() {}
    }

    private final List<Listener> listeners = new ArrayList<>();

    public void subscribe(Listener l)   { listeners.add(l); }
    public void unsubscribe(Listener l) { listeners.remove(l); }

    public void fireItemMoved(InventorySlot from, InventorySlot to, Item item) {
        for (Listener l : listeners) l.onItemMoved(from, to, item);
    }

    public void fireItemDropped(Item item) {
        for (Listener l : listeners) l.onItemDropped(item);
    }

    public void fireStatsChanged() {
        for (Listener l : listeners) l.onStatsChanged();
    }

    public void fireSpellbookChanged() {
        for (Listener l : listeners) l.onSpellbookChanged();
    }
}
