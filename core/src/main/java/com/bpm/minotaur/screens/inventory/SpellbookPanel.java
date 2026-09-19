package com.bpm.minotaur.screens.inventory;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.bpm.minotaur.gamedata.player.Player;
import com.bpm.minotaur.gamedata.spells.SpellDataManager;
import com.bpm.minotaur.gamedata.spells.SpellTemplate;
import com.bpm.minotaur.managers.SettingsManager;

/**
 * Read-only summary of the five Spell Slots on the inventory page. Spells are
 * read and assigned on the full Spellbook screen.
 */
public class SpellbookPanel extends Table {

    private final Player player;
    private final InventorySkin skin;

    public SpellbookPanel(Player player, InventorySkin skin) {
        this.player = player;
        this.skin   = skin;

        top().left();
        padTop(12);

        refresh();
    }

    public void refresh() {
        clear();

        add(new Label("SPELL SLOTS", new Label.LabelStyle(skin.getFontSmall(), InventorySkin.COL_TEXT_HEADER)))
                .left().padBottom(4).row();

        for (int i = 0; i < 5; i++) {
            String text;
            Color color;
            if (i >= player.getUnlockedSpellSlots()) {
                text = "LOCKED";
                color = InventorySkin.COL_TEXT_MUTED;
            } else {
                SpellTemplate spell = SpellDataManager.getSpell(player.getPreparedSpell(i));
                text = spell != null ? spell.getName() : "-- empty --";
                color = spell != null ? InventorySkin.COL_PAGE_LIGHT : InventorySkin.COL_TEXT_MUTED;
            }
            Label line = new Label("[" + com.bpm.minotaur.screens.SpellbookScreen.SLOT_KEYS[i] + "] " + text, new Label.LabelStyle(skin.getFontSmall(), color));
            line.setEllipsis(true);
            add(line).width(340).left().row();
        }

        String key = Input.Keys.toString(SettingsManager.getInstance().getKey("SPELLBOOK"));
        add(new Label(player.getKnownSpellIds().size() + " known -- Open Spellbook [" + key + "]",
                new Label.LabelStyle(skin.getFontSmall(), InventorySkin.COL_TEXT_HEADER))).left().padTop(6).row();
    }
}
