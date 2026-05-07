package com.bpm.minotaur.screens.inventory;

import com.badlogic.gdx.scenes.scene2d.ui.Table;

/**
 * Left page of the open-book inventory.
 *
 * Layout (top → bottom):
 *   ┌─────────────────────────────┐
 *   │       PaperDollPanel        │  (expands to fill)
 *   ├─────────────────────────────┤
 *   │       CoreStatsPanel        │
 *   ├──────────────┬──────────────┤
 *   │ SpellbookPanel │ AlchemyPanel│
 *   └──────────────┴──────────────┘
 */
public class LeftPageTable extends Table {

    public LeftPageTable(PaperDollPanel doll,
                         CoreStatsPanel stats,
                         SpellbookPanel spells,
                         AlchemyPanel   alchemy,
                         InventorySkin  skin) {
        setBackground(skin.getLeftPageDrawable());
        top();
        pad(14, 10, 10, 10);

        add(doll).expand().fill().row();
        add(stats).expandX().fillX().padTop(8).padBottom(6).row();

        Table bottom = new Table();
        bottom.add(spells).expand().fill().padRight(8);
        bottom.add(alchemy).expand().fill();
        add(bottom).expandX().fillX();
    }
}
