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

    /** Width of the painted "Spellbook:" box on new_inventory.png, in stage units. */
    private static final float BOX_W = 370f;

    private final Player player;
    private final InventorySkin skin;

    public SpellbookPanel(Player player, InventorySkin skin) {
        this.player = player;
        this.skin   = skin;

        top().left();

        refresh();
    }

    public void refresh() {
        clear();

        // INV-2: this panel had seven rows -- a heading, five slots and a footer -- inside a
        // painted box with room for about six, so the footer was drawn below the frame. The
        // heading is already painted on the page ("Spellbook:") and the footer repeated a
        // shortcut that belongs in the page legend, so both come out and the five slots fit.
        for (int i = 0; i < 5; i++) {
            String text;
            Color color;
            if (i >= player.getUnlockedSpellSlots()) {
                // Five identical "LOCKED" lines told the player nothing. Each says when.
                text = "Locked - level " + com.bpm.minotaur.gamedata.player.Player.levelForSlot(i + 1);
                color = InventorySkin.COL_TEXT_MUTED;
            } else {
                SpellTemplate spell = SpellDataManager.getSpell(player.getPreparedSpell(i));
                text = spell != null ? spell.getName() : "Empty";
                color = spell != null ? InventorySkin.COL_TEXT_VALUE : InventorySkin.COL_TEXT_MUTED;
            }
            Label line = new Label(com.bpm.minotaur.screens.SpellbookScreen.SLOT_KEYS[i] + "  " + text,
                    new Label.LabelStyle(skin.getFontSmall(), color));
            line.setEllipsis(true);
            add(line).width(BOX_W).left().row();
        }

        String key = Input.Keys.toString(SettingsManager.getInstance().getKey("SPELLBOOK"));
        Label foot = new Label(player.getKnownSpellIds().size() + " known   -   " + key + " to open",
                new Label.LabelStyle(skin.getFontSmall(), InventorySkin.COL_TEXT_MUTED));
        foot.setEllipsis(true);
        add(foot).width(BOX_W).left().padTop(2).row();
    }
}
