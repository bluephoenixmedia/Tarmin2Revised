package com.bpm.minotaur.screens.inventory;

import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.player.Player;
import com.bpm.minotaur.gamedata.spells.SpellType;

import java.util.List;

/**
 * Shows the player's known spells with their MP cost, matching the
 * "Spellbook:" section in the bottom-left of the left page.
 */
public class SpellbookPanel extends Table implements InventoryEventBus.Listener {

    private final Player      player;
    private final InventorySkin skin;
    private final Table       list;

    public SpellbookPanel(Player player, InventorySkin skin) {
        this.player = player;
        this.skin   = skin;

        Label header = new Label("Spellbook:",
                new Label.LabelStyle(skin.getFontBody(), InventorySkin.COL_TEXT_HEADER));
        add(header).left().padBottom(5).row();

        list = new Table();
        add(list).expandX().fillX().left();

        refresh();
    }

    public void refresh() {
        list.clear();
        List<SpellType> spells = player.getKnownSpells();
        if (spells == null || spells.isEmpty()) {
            list.add(new Label("None",
                    new Label.LabelStyle(skin.getFontSmall(), InventorySkin.COL_TEXT_MUTED))).left();
            return;
        }
        for (SpellType spell : spells) {
            String text = spell.getDisplayName() + "  [" + spell.getMpCost() + " MP]";
            list.add(new Label(text,
                    new Label.LabelStyle(skin.getFontSmall(), InventorySkin.COL_TEXT_BODY)))
                    .left().padBottom(2).row();
        }
    }

    @Override public void onSpellbookChanged() { refresh(); }
}
