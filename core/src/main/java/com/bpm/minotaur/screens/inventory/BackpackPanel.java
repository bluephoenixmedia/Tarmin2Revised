package com.bpm.minotaur.screens.inventory;

import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.WidgetGroup;
import com.badlogic.gdx.utils.Align;
import com.bpm.minotaur.gamedata.Inventory;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.ItemDataManager;

import java.util.List;

public class BackpackPanel extends WidgetGroup implements InventoryEventBus.Listener {

    private static final int   COLS       = 8;  // number of columns in the grid
    private static final int   ROWS       = 6;  // number of rows in the grid
    private static final int   SLOT_COUNT = COLS * ROWS; // 48 total slots

    /** Visual size of each slot icon in stage pixels. */
    private static final float SLOT_SIZE  = 56f;

    // ── Spacing — edit these to adjust how tightly slots are packed ───
    // CELL_W is the centre-to-centre horizontal distance between adjacent slots.
    // CELL_H is the centre-to-centre vertical   distance between adjacent slots.
    // These were tuned so the grid aligns with the slot outlines in new_inventory.png
    // (image 2676×1568 scaled to 1920×1080 stage → ~76 px horiz, ~73 px vert).
    private static final float CELL_W     = 76f;   // increase → more horizontal space between slots
    private static final float CELL_H     = 73f;   // increase → more vertical   space between slots

    // Derived padding — do not edit directly; change CELL_W / CELL_H instead.
    private static final float SLOT_PAD_H = (CELL_W - SLOT_SIZE) / 2f; // 10 px each side
    private static final float SLOT_PAD_V = (CELL_H - SLOT_SIZE) / 2f; // 8.5 px each side
    private static final float GRID_W     = COLS * CELL_W;  // total grid width  = 608
    private static final float GRID_H     = ROWS * CELL_H;  // total grid height = 438
    private static final float PREF_H     = GRID_H + 26f;   // + room for count label above

    private final InventorySlot[] slots = new InventorySlot[SLOT_COUNT];
    private final Label           countLabel;
    private final Inventory       inventory;

    public BackpackPanel(Inventory inventory, InventorySkin skin,
                         ItemDataManager idm, InventoryDragDropHandler dnd) {
        this.inventory = inventory;

        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                int i = row * COLS + col;
                InventorySlot slot = new InventorySlot(
                        InventorySlot.SlotCategory.BACKPACK, i, null, null, skin, idm);
                slots[i] = slot;
                dnd.register(slot);

                // Row 0 is topmost (highest stage-y); rows increase downward.
                float x = col * CELL_W + SLOT_PAD_H;
                float y = GRID_H - (row + 1) * CELL_H + SLOT_PAD_V;
                slot.setPosition(x, y);
                slot.setSize(SLOT_SIZE, SLOT_SIZE);
                addActor(slot);
            }
        }

        // Count label floats just above the grid
        countLabel = new Label("",
                new Label.LabelStyle(skin.getFontSmall(), InventorySkin.COL_TEXT_MUTED));
        countLabel.setAlignment(Align.right);
        countLabel.setPosition(0f, GRID_H + 4f);
        countLabel.setWidth(GRID_W);
        addActor(countLabel);

        setSize(GRID_W, PREF_H);
    }

    // ── Public ────────────────────────────────────────────────────────

    @Override public float getPrefWidth()  { return GRID_W; }
    @Override public float getPrefHeight() { return PREF_H; }

    public void refresh() {
        List<Item> items = inventory.getMainInventory();
        for (int i = 0; i < SLOT_COUNT; i++) {
            slots[i].setItem(i < items.size() ? items.get(i) : null);
        }
        int used = Math.min(items.size(), SLOT_COUNT);
        countLabel.setText("[" + used + "/" + SLOT_COUNT + " Slots]");
    }

    // ── EventBus listener ─────────────────────────────────────────────

    @Override public void onStatsChanged()                                    { refresh(); }
    @Override public void onItemMoved(InventorySlot f, InventorySlot t, Item i) { refresh(); }
    @Override public void onItemDropped(Item i)                               { refresh(); }
}
