package com.bpm.minotaur.screens.inventory;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.bpm.minotaur.gamedata.ModifierType;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.player.Player;

/**
 * Displays the four core stats below the paper-doll portrait:
 * Armor Class, HP, MP, and Treasure.
 */
public class CoreStatsPanel extends Table implements InventoryEventBus.Listener {

    private final Player player;
    private final Label  acVal;
    private final Label  hpVal;
    private final Label  mpVal;
    private final Label  goldVal;

    public CoreStatsPanel(Player player, InventorySkin skin) {
        this.player = player;

        Label.LabelStyle keyStyle = new Label.LabelStyle(skin.getFontSmall(), InventorySkin.COL_TEXT_BODY);
        Label.LabelStyle valStyle = new Label.LabelStyle(skin.getFontBody(),  InventorySkin.COL_TEXT_HEADER);

        acVal   = new Label("", valStyle);
        hpVal   = new Label("", valStyle);
        mpVal   = new Label("", valStyle);
        goldVal = new Label("", new Label.LabelStyle(skin.getFontBody(), Color.GOLD));

        iconRow(this, skin.getIconGray(),  "Armor Class:", acVal,   keyStyle);
        iconRow(this, skin.getIconRed(),   "HP:",          hpVal,   keyStyle);
        iconRow(this, skin.getIconBlue(),  "MP:",          mpVal,   keyStyle);
        iconRow(this, skin.getIconGold(),  "Treasure:",    goldVal, keyStyle);

        refresh();
    }

    private void iconRow(Table t, Drawable icon, String key, Label val, Label.LabelStyle ks) {
        Table keyCell = new Table();
        keyCell.add(new Image(icon)).size(12, 12).padRight(4);
        keyCell.add(new Label(key, ks)).left();
        t.add(keyCell).left().padRight(8).padBottom(4);
        t.add(val).right().padBottom(4).row();
    }

    public void refresh() {
        int bonusHP = player.getEquipment().getEquippedModifierSum(ModifierType.BONUS_MAX_HP);
        int bonusMP = player.getEquipment().getEquippedModifierSum(ModifierType.BONUS_MAX_MP);

        acVal.setText(String.valueOf(player.getArmorClass()));
        hpVal.setText(player.getCurrentHP() + " / " + (player.getStats().getMaxHP() + bonusHP));
        mpVal.setText(player.getCurrentMP() + " / " + (player.getStats().getMaxMP() + bonusMP));
        goldVal.setText(String.valueOf(player.getTreasureScore()));
    }

    @Override public void onStatsChanged()                                        { refresh(); }
    @Override public void onItemMoved(InventorySlot f, InventorySlot t, Item i)  { refresh(); }
}
