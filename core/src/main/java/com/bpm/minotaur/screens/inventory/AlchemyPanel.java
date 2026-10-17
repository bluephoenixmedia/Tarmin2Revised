package com.bpm.minotaur.screens.inventory;

import com.badlogic.gdx.scenes.scene2d.ui.WidgetGroup;
import com.bpm.minotaur.gamedata.item.ItemDataManager;

/**
 * The 3x3 alchemy grid and its output, laid over the still and the mug painted on the left page.
 *
 * <p>INV-2: this was a {@code Table} of grid, an arrow and an output slot laid out left to right,
 * which came to about 236 units and sat at a position tuned for something narrower. Two things
 * followed. The whole row was taller than the painted box allowed, so the grid was drawn across
 * the "Alchemy Crafting:" title above it. And the output slot, being last in a row that started
 * at the box's left edge, ended up outside the box entirely, over the page gutter.
 *
 * <p>The page is painted art with a 3x3 grid and a mug drawn into it, so the slots have exactly
 * one correct place each and a layout container can only approximate it. Like
 * {@link BackpackPanel} and {@link QuickSlotsPanel}, this is a {@code WidgetGroup} with each slot
 * placed on the thing it stands for: the nine inputs on the painted grid, the output on the mug.
 * The arrow is gone -- the still pouring into the mug already says which way it flows.
 *
 * <p>Coordinates are in stage units, measured off {@code new_inventory.png} (2676x1568 scaled to
 * the 1920x1080 stage). They are relative to the panel's own origin, which
 * {@code InventoryLayoutConfig} places at the bottom-left of the painted box.
 */
public class AlchemyPanel extends WidgetGroup {

    private static final int GRID = 3;

    /** One input cell, matching the painted grid's cell pitch. */
    private static final float SLOT_S = 42f;
    private static final float CELL = 44f;

    /** The painted 3x3 grid's bottom-left corner, relative to this panel's origin. */
    private static final float GRID_X = 0f;
    private static final float GRID_Y = 0f;

    /** The painted mug, which is where a finished brew appears. */
    private static final float OUTPUT_X = -72f;
    private static final float OUTPUT_Y = 4f;
    private static final float OUTPUT_S = 46f;

    private static final float PREF_W = GRID * CELL;
    private static final float PREF_H = GRID * CELL;

    private final InventorySlot[] inputSlots = new InventorySlot[GRID * GRID];
    private final InventorySlot outputSlot;

    public AlchemyPanel(InventorySkin skin, ItemDataManager idm, InventoryDragDropHandler dnd) {
        // Row 0 is the top row of the painted grid; stage y counts up, so it sits highest.
        for (int row = 0; row < GRID; row++) {
            for (int col = 0; col < GRID; col++) {
                int i = row * GRID + col;
                InventorySlot slot = new InventorySlot(
                        InventorySlot.SlotCategory.ALCHEMY_INPUT, i, null, null, skin, idm);
                inputSlots[i] = slot;
                dnd.register(slot);
                slot.setSize(SLOT_S, SLOT_S);
                slot.setPosition(GRID_X + col * CELL, GRID_Y + (GRID - 1 - row) * CELL);
                addActor(slot);
            }
        }

        // Drag-from only; alchemy_output rejects drops in accepts().
        outputSlot = new InventorySlot(
                InventorySlot.SlotCategory.ALCHEMY_OUTPUT, 0, null, null, skin, idm);
        dnd.register(outputSlot);
        outputSlot.setSize(OUTPUT_S, OUTPUT_S);
        outputSlot.setPosition(OUTPUT_X, OUTPUT_Y);
        addActor(outputSlot);

        setSize(PREF_W, PREF_H);
    }

    @Override
    public float getPrefWidth() {
        return PREF_W;
    }

    @Override
    public float getPrefHeight() {
        return PREF_H;
    }

    public InventorySlot[] getInputSlots() { return inputSlots; }

    public InventorySlot getOutputSlot() { return outputSlot; }
}
