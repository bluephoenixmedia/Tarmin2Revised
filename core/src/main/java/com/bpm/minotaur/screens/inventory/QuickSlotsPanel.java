package com.bpm.minotaur.screens.inventory;

import com.badlogic.gdx.scenes.scene2d.ui.WidgetGroup;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.ItemDataManager;

public class QuickSlotsPanel extends WidgetGroup implements InventoryEventBus.Listener {

    private static final int   SLOT_COUNT = 6;  // number of quick slots

    /** Visual size of each slot icon in stage pixels. */
    private static final float SLOT_SIZE  = 56f;

    // ── Spacing — edit this to adjust how far apart the slots are ────
    // CELL_W is the centre-to-centre horizontal distance between adjacent slots.
    // Tuned to match the quick-slot row spacing in new_inventory.png
    // (image 2676×1568 scaled to 1920×1080 stage → ~85 px between centres).
    private static final float CELL_W   = 85f;   // increase → slots spread further apart

    // Derived values — do not edit; change CELL_W instead.
    private static final float SLOT_PAD = (CELL_W - SLOT_SIZE) / 2f; // 14.5 px padding each side
    private static final float PREF_W   = SLOT_COUNT * CELL_W;        // total row width = 510
    private static final float PREF_H   = SLOT_SIZE + SLOT_PAD * 2f; // total row height = 85

    private final InventorySlot[] slots;
    private final Item[]          quickSlots;

    public QuickSlotsPanel(Item[] quickSlots, InventorySkin skin,
                           ItemDataManager idm, InventoryDragDropHandler dnd) {
        this.quickSlots = quickSlots;
        this.slots      = new InventorySlot[SLOT_COUNT];

        for (int i = 0; i < SLOT_COUNT; i++) {
            InventorySlot slot = new InventorySlot(
                    InventorySlot.SlotCategory.QUICK_SLOT, i, null, null, skin, idm);
            slots[i] = slot;
            dnd.register(slot);

            slot.setPosition(i * CELL_W + SLOT_PAD, SLOT_PAD);
            slot.setSize(SLOT_SIZE, SLOT_SIZE);
            addActor(slot);
        }

        setSize(PREF_W, PREF_H);
    }

    // ── Public ────────────────────────────────────────────────────────

    @Override public float getPrefWidth()  { return PREF_W; }
    @Override public float getPrefHeight() { return PREF_H; }

    public void refresh() {
        for (int i = 0; i < SLOT_COUNT; i++) {
            slots[i].setItem(i < quickSlots.length ? quickSlots[i] : null);
        }
    }

    // ── EventBus listener ─────────────────────────────────────────────

    @Override public void onStatsChanged()                                    { refresh(); }
    @Override public void onItemMoved(InventorySlot f, InventorySlot t, Item i) { refresh(); }
    @Override public void onItemDropped(Item i)                               { refresh(); }
}
