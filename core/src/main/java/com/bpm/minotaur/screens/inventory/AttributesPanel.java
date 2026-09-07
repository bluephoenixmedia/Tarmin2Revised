package com.bpm.minotaur.screens.inventory;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.bpm.minotaur.gamedata.ModifierType;
import com.bpm.minotaur.gamedata.player.Player;

/**
 * Three-column attributes panel formatted cleanly for the lower-right box.
 * Categorized into Combat Core, Primary Attributes, and Survival & Economy
 * with crisp, legible typography on parchment.
 */
public class AttributesPanel extends Table implements InventoryEventBus.Listener {

    private final Player player;
    private final InventorySkin skin;

    private final Table col1 = new Table();
    private final Table col2 = new Table();
    private final Table col3 = new Table();

    private static final Color COL_HEADER = Color.valueOf("4A2E10FF");
    private static final Color COL_KEY    = Color.valueOf("2C1A08FF");
    private static final Color COL_VAL    = Color.valueOf("111111FF");
    private static final Color COL_GREEN  = Color.valueOf("1E7E34FF");
    private static final Color COL_RED    = Color.valueOf("BD2130FF");
    private static final Color COL_BLUE   = Color.valueOf("1B4F72FF");
    private static final Color COL_GOLD   = Color.valueOf("8A6D05FF");

    public AttributesPanel(Player player, InventorySkin skin) {
        this.player = player;
        this.skin = skin;

        top().left();
        pad(4, 12, 4, 12);

        add(col1).top().left().width(250f).padRight(14);
        add(col2).top().left().width(225f).padRight(14);
        add(col3).top().left().width(250f);

        buildRows();
    }

    private void buildRows() {
        col1.clear();
        col2.clear();
        col3.clear();

        int bonusHP = player.getEquipment().getEquippedModifierSum(ModifierType.BONUS_MAX_HP);
        int bonusMP = player.getEquipment().getEquippedModifierSum(ModifierType.BONUS_MAX_MP);

        // ── Column 1: Combat Core ──────────────────────────────────
        addSectionHeader(col1, "COMBAT CORE");
        stat(col1, "Level", String.valueOf(player.getLevel()), COL_VAL);
        stat(col1, "Exp", String.valueOf(player.getExperience()), COL_VAL);
        stat(col1, "HP", player.getCurrentHP() + " / " + (player.getStats().getMaxHP() + bonusHP), hpColor());
        stat(col1, "MP", player.getCurrentMP() + " / " + (player.getStats().getMaxMP() + bonusMP), COL_BLUE);
        stat(col1, "Armor Class", String.valueOf(player.getArmorClass()), COL_VAL);
        stat(col1, "Attack Spd", String.valueOf(player.getEffectiveSpeed()), COL_VAL);
        stat(col1, "Crit Chance", Math.round(player.getCritChance() * 100) + "%", COL_VAL);
        stat(col1, "Crit Dmg", String.format("%.1f×", player.getCritMultiplier()), COL_VAL);
        stat(col1, "Dodge", Math.round(player.getDodgeChance() * 100) + "%", COL_VAL);
        stat(col1, "Spell Power", "+" + player.getSpellPower(), COL_BLUE);

        // ── Column 2: Attributes ───────────────────────────────────
        addSectionHeader(col2, "ATTRIBUTES");
        stat(col2, "Strength", String.valueOf(player.getStats().getStrength()), COL_VAL);
        stat(col2, "Dexterity", String.valueOf(player.getEffectiveDexterity()), COL_VAL);
        stat(col2, "Agility", String.valueOf(player.getEffectiveAgility()), COL_VAL);
        stat(col2, "Constitution", String.valueOf(player.getEffectiveConstitution()), COL_VAL);
        stat(col2, "Intellect", String.valueOf(player.getEffectiveIntelligence()), COL_VAL);
        stat(col2, "Wisdom", String.valueOf(player.getEffectiveWisdom()), COL_VAL);
        stat(col2, "Charisma", String.valueOf(player.getEffectiveCharisma()), COL_VAL);
        stat(col2, "Luck", String.valueOf(player.getLuck()), COL_VAL);

        // ── Column 3: Survival & Economy ───────────────────────────
        addSectionHeader(col3, "SURVIVAL & RES");
        stat(col3, "Satiety", player.getStats().getSatiety() + "%", satColor());
        stat(col3, "Hydration", player.getStats().getHydration() + "%", hydColor());
        stat(col3, "Toxicity", player.getStats().getToxicity() + "%", toxColor());
        float temp = player.getStats().getBodyTemperature();
        stat(col3, "Body Temp", String.format("%.0f°C", temp), tempColor(temp));
        stat(col3, "Stamina Pool", player.getEffectiveStamina() + " Dice", COL_VAL);
        stat(col3, "Arrows", String.valueOf(player.getArrows()), COL_VAL);
        stat(col3, "Treasure", String.valueOf(player.getTreasureScore()), COL_GOLD);
        stat(col3, "Cook Skill", String.valueOf(player.getStats().getCookingSkill()), COL_VAL);
    }

    private void addSectionHeader(Table col, String headerText) {
        Label header = new Label(headerText, new Label.LabelStyle(skin.getFontSmall(), COL_HEADER));
        col.add(header).colspan(2).left().padBottom(5).row();
    }

    private void stat(Table col, String key, String value, Color valueColor) {
        Label k = new Label(key + ":", new Label.LabelStyle(skin.getFontSmall(), COL_KEY));
        Label v = new Label(value, new Label.LabelStyle(skin.getFontSmall(), valueColor));
        col.add(k).left().width(130f).padBottom(1);
        col.add(v).right().width(95f).padBottom(1).row();
    }

    // ── Colour helpers ────────────────────────────────────────────────

    private Color hpColor() {
        int cur = player.getCurrentHP();
        int max = player.getStats().getMaxHP();
        return cur < max * 0.3f ? COL_RED : cur < max * 0.6f ? Color.ORANGE : COL_GREEN;
    }

    private Color toxColor() {
        int t = player.getStats().getToxicity();
        return t >= 76 ? COL_RED : t >= 26 ? Color.ORANGE : COL_GREEN;
    }

    private Color satColor() {
        int s = player.getStats().getSatiety();
        return s < 20 ? COL_RED : s < 50 ? Color.ORANGE : COL_GREEN;
    }

    private Color hydColor() {
        int h = player.getStats().getHydration();
        return h < 20 ? COL_RED : h < 50 ? Color.ORANGE : COL_GREEN;
    }

    private Color tempColor(float t) {
        return (t < 33f || t > 41f) ? COL_RED : (t < 35f || t > 39f) ? Color.ORANGE : COL_GREEN;
    }

    // ── Public / EventBus ─────────────────────────────────────────────

    public void refresh() {
        buildRows();
    }

    @Override
    public void onStatsChanged() {
        refresh();
    }
}
