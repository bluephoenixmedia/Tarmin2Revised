package com.bpm.minotaur.screens.inventory;

import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.utils.Align;
import com.bpm.minotaur.gamedata.Inventory;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.ItemDataManager;

import java.util.List;

public class BackpackPanel extends Table implements InventoryEventBus.Listener {

    private static final int   COLS       = 8;
    private static final int   ROWS       = 5;
    private static final int   SLOT_COUNT = COLS * ROWS;
    private static final float SLOT_SIZE  = 56f;
    private static final float SLOT_PAD   = 3f;

    private final InventorySlot[] slots = new InventorySlot[SLOT_COUNT];
    private final Label           countLabel;
    private final Inventory       inventory;

    public BackpackPanel(Inventory inventory, InventorySkin skin,
                         ItemDataManager idm, InventoryDragDropHandler dnd) {
        this.inventory = inventory;

        // ── Header ────────────────────────────────────────────────────
        Label title = new Label("Backpack Storage  (Right-Click to Drop)",
                new Label.LabelStyle(skin.getFontBody(), InventorySkin.COL_TEXT_HEADER));
        title.setAlignment(Align.left);

        countLabel = new Label("", new Label.LabelStyle(skin.getFontSmall(), InventorySkin.COL_TEXT_MUTED));
        countLabel.setAlignment(Align.right);

        Table header = new Table();
        header.add(title).expandX().left();
        header.add(countLabel).right();
        add(header).expandX().fillX().padBottom(6).row();

        // ── Slot grid ─────────────────────────────────────────────────
        Table grid = new Table();
        for (int i = 0; i < SLOT_COUNT; i++) {
            InventorySlot slot = new InventorySlot(
                    InventorySlot.SlotCategory.BACKPACK, i, null, null, skin, idm);
            slots[i] = slot;
            dnd.register(slot);
            grid.add(slot).size(SLOT_SIZE, SLOT_SIZE).pad(SLOT_PAD);
            if ((i + 1) % COLS == 0) grid.row();
        }
        add(grid).expandX().fillX();
    }

    // ── Public ────────────────────────────────────────────────────────

    public void refresh() {
        List<Item> items = inventory.getMainInventory();
        for (int i = 0; i < SLOT_COUNT; i++) {
            slots[i].setItem(i < items.size() ? items.get(i) : null);
        }
        int used = Math.min(items.size(), SLOT_COUNT);
        countLabel.setText("[" + used + "/" + SLOT_COUNT + " Slots]");
    }

    // ── EventBus listener ─────────────────────────────────────────────

    @Override public void onStatsChanged()                               { refresh(); }
    @Override public void onItemMoved(InventorySlot f, InventorySlot t, Item i) { refresh(); }
    @Override public void onItemDropped(Item i)                          { refresh(); }
}
