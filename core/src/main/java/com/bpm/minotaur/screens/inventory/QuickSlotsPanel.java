package com.bpm.minotaur.screens.inventory;

import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.utils.Align;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.ItemDataManager;

public class QuickSlotsPanel extends Table implements InventoryEventBus.Listener {

    private static final int   SLOT_COUNT = 6;
    private static final float SLOT_SIZE  = 56f;

    private final InventorySlot[] slots;
    private final Item[]          quickSlots; // reference into Inventory.getQuickSlots()

    public QuickSlotsPanel(Item[] quickSlots, InventorySkin skin,
                           ItemDataManager idm, InventoryDragDropHandler dnd) {
        this.quickSlots = quickSlots;
        this.slots      = new InventorySlot[SLOT_COUNT];

        Label title = new Label("Quick Slots:",
                new Label.LabelStyle(skin.getFontBody(), InventorySkin.COL_TEXT_HEADER));
        title.setAlignment(Align.left);
        add(title).left().padBottom(5).row();

        Table row = new Table();
        for (int i = 0; i < SLOT_COUNT; i++) {
            InventorySlot slot = new InventorySlot(
                    InventorySlot.SlotCategory.QUICK_SLOT, i, null, null, skin, idm);
            slots[i] = slot;
            dnd.register(slot);

            Table cell = new Table();
            cell.add(slot).size(SLOT_SIZE, SLOT_SIZE).row();

            Label num = new Label("[" + (i + 1) + "]",
                    new Label.LabelStyle(skin.getFontSmall(), InventorySkin.COL_TEXT_MUTED));
            num.setAlignment(Align.center);
            cell.add(num).center().padTop(2);

            row.add(cell).padRight(5);
        }
        add(row).left();
    }

    // ── Public ────────────────────────────────────────────────────────

    public void refresh() {
        for (int i = 0; i < SLOT_COUNT; i++) {
            slots[i].setItem(i < quickSlots.length ? quickSlots[i] : null);
        }
    }

    // ── EventBus listener ─────────────────────────────────────────────

    @Override public void onStatsChanged()                               { refresh(); }
    @Override public void onItemMoved(InventorySlot f, InventorySlot t, Item i) { refresh(); }
    @Override public void onItemDropped(Item i)                          { refresh(); }
}
