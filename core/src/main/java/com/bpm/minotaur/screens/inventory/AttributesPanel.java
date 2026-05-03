package com.bpm.minotaur.screens.inventory;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.utils.Align;
import com.bpm.minotaur.gamedata.ModifierType;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.player.Player;

/**
 * Dense three-column attributes panel shown in the bottom-right of the right page.
 * Matches the "Player Attribute" section from the mockup.
 *
 * Rows are rebuilt on every {@link #refresh()} call — label objects are not
 * cached because column widths need to re-measure after stat changes.
 */
public class AttributesPanel extends Table implements InventoryEventBus.Listener {

    private final Player      player;
    private final InventorySkin skin;

    private final Table col1 = new Table();
    private final Table col2 = new Table();
    private final Table col3 = new Table();

    public AttributesPanel(Player player, InventorySkin skin) {
        this.player = player;
        this.skin   = skin;

        top().left();

        Label title = new Label("Player Attribute",
                new Label.LabelStyle(skin.getFontBody(), InventorySkin.COL_TEXT_HEADER));
        title.setAlignment(Align.center);
        add(title).colspan(3).center().padBottom(8).row();

        add(col1).top().left().expandY().padRight(14);
        add(col2).top().left().expandY().padRight(14);
        add(col3).top().left().expandY();

        buildRows();
    }

    // ── Row population ────────────────────────────────────────────────

    private void buildRows() {
        col1.clear();
        col2.clear();
        col3.clear();

        int bonusHP = player.getEquipment().getEquippedModifierSum(ModifierType.BONUS_MAX_HP);
        int bonusMP = player.getEquipment().getEquippedModifierSum(ModifierType.BONUS_MAX_MP);

        // ── Column 1 ────────────────────────────────────────────────
        stat(col1, "Level",   String.valueOf(player.getLevel()),                   Color.WHITE);
        stat(col1, "Exp",     String.valueOf(player.getExperience()),               Color.WHITE);
        stat(col1, "Armor Class", String.valueOf(player.getArmorClass()),           Color.WHITE);
        stat(col1, "HP",
                player.getCurrentHP() + " / " + (player.getStats().getMaxHP() + bonusHP), hpColor());
        stat(col1, "MP",
                player.getCurrentMP() + " / " + (player.getStats().getMaxMP() + bonusMP), Color.WHITE);
        stat(col1, "Strength",   String.valueOf(player.getStats().getStrength()),   Color.WHITE);
        stat(col1, "Dexterity",  String.valueOf(player.getEffectiveDexterity()),    Color.WHITE);
        stat(col1, "Luck",       String.valueOf(player.getLuck()),                  Color.WHITE);
        stat(col1, "Toxicity",   player.getStats().getToxicity() + "%",            toxColor());
        stat(col1, "Stamina",    player.getEffectiveStamina() + " dice",           Color.WHITE);

        // ── Column 2 ────────────────────────────────────────────────
        stat(col2, "Satiety",    player.getStats().getSatiety() + "%",             satColor());
        stat(col2, "Hydration",  player.getStats().getHydration() + "%",           hydColor());
        stat(col2, "Stamina",    String.valueOf(player.getEffectiveStamina()),      Color.WHITE);
        stat(col2, "Attack Spd", String.valueOf(player.getEffectiveSpeed()),        Color.WHITE);
        stat(col2, "Attack Spd", String.valueOf(player.getEffectiveSpeed()),        Color.WHITE);
        stat(col2, "Crit chance",Math.round(player.getCritChance() * 100) + "%",   Color.WHITE);
        stat(col2, "Crit damage",String.format("%.1f×", player.getCritMultiplier()), Color.WHITE);
        stat(col2, "Dodge",      Math.round(player.getDodgeChance() * 100) + "%",  Color.WHITE);
        stat(col2, "Spell Power","+" + player.getSpellPower(),                      Color.WHITE);
        stat(col2, "Spell Power","+" + player.getSpellPower(),                      Color.WHITE);

        // ── Column 3 ────────────────────────────────────────────────
        float temp = player.getStats().getBodyTemperature();
        stat(col3, "Body Temp",  String.format("%.0f", temp),                      tempColor(temp));
        stat(col3, "Arrows",     String.valueOf(player.getArrows()),               Color.WHITE);
        stat(col3, "Treasure",   String.valueOf(player.getTreasureScore()),         Color.GOLD);
        stat(col3, "Cook Skill", String.valueOf(player.getStats().getCookingSkill()), Color.WHITE);
        stat(col3, "Agility",    String.valueOf(player.getEffectiveAgility()),      Color.WHITE);
        stat(col3, "Intellect",  String.valueOf(player.getEffectiveIntelligence()), Color.WHITE);
        stat(col3, "Wisdom",     String.valueOf(player.getEffectiveWisdom()),       Color.WHITE);
        stat(col3, "Const.",     String.valueOf(player.getEffectiveConstitution()), Color.WHITE);
        stat(col3, "Charisma",   String.valueOf(player.getEffectiveCharisma()),     Color.WHITE);
    }

    /** Adds a single label:value row to a column table. */
    private void stat(Table col, String key, String value, Color valueColor) {
        col.add(new Label(key + ":",
                new Label.LabelStyle(skin.getFontSmall(), InventorySkin.COL_TEXT_BODY)))
                .left().padBottom(1).padRight(4);
        col.add(new Label(value,
                new Label.LabelStyle(skin.getFontSmall(), valueColor)))
                .right().padBottom(1).row();
    }

    // ── Colour helpers ────────────────────────────────────────────────

    private Color hpColor() {
        int cur = player.getCurrentHP();
        int max = player.getStats().getMaxHP();
        return cur < max * 0.3f ? Color.RED : cur < max * 0.6f ? Color.YELLOW : Color.WHITE;
    }

    private Color toxColor() {
        int t = player.getStats().getToxicity();
        return t >= 76 ? Color.RED : t >= 26 ? Color.ORANGE : Color.GREEN;
    }

    private Color satColor() {
        int s = player.getStats().getSatiety();
        return s < 20 ? Color.RED : s < 50 ? Color.YELLOW : Color.WHITE;
    }

    private Color hydColor() {
        int h = player.getStats().getHydration();
        return h < 20 ? Color.RED : h < 50 ? Color.YELLOW : Color.WHITE;
    }

    private Color tempColor(float t) {
        return (t < 33f || t > 41f) ? Color.RED : (t < 35f || t > 39f) ? Color.YELLOW : Color.WHITE;
    }

    // ── Public / EventBus ─────────────────────────────────────────────

    public void refresh() { buildRows(); }

    @Override public void onStatsChanged() { refresh(); }
}
