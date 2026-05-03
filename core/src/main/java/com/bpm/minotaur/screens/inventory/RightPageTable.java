package com.bpm.minotaur.screens.inventory;

import com.badlogic.gdx.scenes.scene2d.ui.Table;

/**
 * Right page of the open-book inventory. Stacks the backpack grid, quick-slot
 * bar, and the dense player-attribute panel.
 */
public class RightPageTable extends Table {

    public RightPageTable(BackpackPanel backpack,
                          QuickSlotsPanel quickSlots,
                          AttributesPanel attributes,
                          InventorySkin skin) {
        setBackground(skin.getRightPageDrawable());
        top().left();
        pad(18, 14, 14, 14);

        add(backpack).expandX().fillX().padBottom(12).row();
        add(quickSlots).expandX().fillX().padBottom(12).row();
        add(attributes).expand().fill();
    }
}
