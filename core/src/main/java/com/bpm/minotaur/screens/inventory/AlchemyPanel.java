package com.bpm.minotaur.screens.inventory;

import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.bpm.minotaur.gamedata.item.ItemDataManager;

/**
 * 3×3 alchemy crafting grid with an output slot, matching the
 * "Alchemy Crafting:" section in the bottom-right of the left page.
 */
public class AlchemyPanel extends Table {

    private static final int   GRID   = 3;
    private static final float SLOT_S = 46f;

    private final InventorySlot[] inputSlots = new InventorySlot[GRID * GRID];
    private final InventorySlot   outputSlot;

    public AlchemyPanel(InventorySkin skin, ItemDataManager idm, InventoryDragDropHandler dnd) {
        Label header = new Label("Alchemy Crafting:",
                new Label.LabelStyle(skin.getFontBody(), InventorySkin.COL_TEXT_HEADER));
        add(header).left().padBottom(5).colspan(3).row();

        // 3×3 input grid
        Table grid = new Table();
        for (int i = 0; i < GRID * GRID; i++) {
            InventorySlot slot = new InventorySlot(
                    InventorySlot.SlotCategory.ALCHEMY_INPUT, i, null, null, skin, idm);
            inputSlots[i] = slot;
            dnd.register(slot);
            grid.add(slot).size(SLOT_S, SLOT_S).pad(2f);
            if ((i + 1) % GRID == 0) grid.row();
        }

        // Arrow
        Label arrow = new Label("→",
                new Label.LabelStyle(skin.getFontHeader(), InventorySkin.COL_TEXT_HEADER));

        // Output slot (drag-from only; alchemy_output rejects drops in accepts())
        outputSlot = new InventorySlot(
                InventorySlot.SlotCategory.ALCHEMY_OUTPUT, 0, null, null, skin, idm);
        dnd.register(outputSlot);

        add(grid);
        add(arrow).center().padLeft(6).padRight(6);
        add(outputSlot).size(SLOT_S, SLOT_S);
    }

    public InventorySlot[] getInputSlots() { return inputSlots; }
    public InventorySlot   getOutputSlot() { return outputSlot; }
}
