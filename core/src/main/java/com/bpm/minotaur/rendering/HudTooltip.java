package com.bpm.minotaur.rendering;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.bpm.minotaur.gamedata.item.Item;

/**
 * Floating dark-fantasy tooltip card displaying item name, category,
 * attributes/stats, and hotkey action prompts when hovering slots.
 */
public class HudTooltip extends Table {

    private final HudSkin skin;
    private final Label titleLabel;
    private final Label categoryLabel;
    private final Label statsLabel;
    private final Label promptLabel;

    public HudTooltip(HudSkin skin) {
        this.skin = skin;
        setBackground(skin.getTooltipBg());
        pad(8f, 12f, 8f, 12f);
        top().left();

        Label.LabelStyle titleStyle = new Label.LabelStyle(skin.getFontHeader(), HudSkin.COL_GOLD_BRIGHT);
        Label.LabelStyle catStyle = new Label.LabelStyle(skin.getFontSmall(), HudSkin.COL_EXP_AMBER);
        Label.LabelStyle statsStyle = new Label.LabelStyle(skin.getFontSmall(), Color.WHITE);
        Label.LabelStyle promptStyle = new Label.LabelStyle(skin.getFontSmall(), HudSkin.COL_GOLD_MUTED);

        titleLabel = new Label("", titleStyle);
        categoryLabel = new Label("", catStyle);
        statsLabel = new Label("", statsStyle);
        promptLabel = new Label("", promptStyle);

        add(titleLabel).left().row();
        add(categoryLabel).left().padTop(2f).row();
        add(statsLabel).left().padTop(4f).row();
        add(promptLabel).left().padTop(6f).row();

        setVisible(false);
    }

    public void show(Item item, float anchorX, float anchorY, String hotkeyHint) {
        if (item == null) {
            setVisible(false);
            return;
        }

        titleLabel.setText(item.getDisplayName());

        String catText = (item.getCategory() != null ? item.getCategory().name() : "ITEM");
        if (item.isModified()) {
            catText += " [Enchanted]";
        }
        categoryLabel.setText(catText);

        StringBuilder sb = new StringBuilder();
        if (item.getDamageDice() != null && !item.getDamageDice().isEmpty()) {
            sb.append("Damage: ").append(item.getDamageDice()).append("  ");
        }
        if (item.getArmorClassBonus() > 0) {
            sb.append("Armor: +").append(item.getArmorClassBonus()).append(" AC  ");
        }
        if (item.getNutrition() > 0) {
            sb.append("Food: +").append(item.getNutrition()).append("  ");
        }
        if (item.getHydrationValue() > 0) {
            sb.append("Water: +").append(item.getHydrationValue()).append("  ");
        }
        if (item.getGrantedDie() != null) {
            sb.append("Die: ").append(item.getGrantedDie().getName()).append("  ");
        }
        if (item.getModifiers() != null && !item.getModifiers().isEmpty()) {
            for (com.bpm.minotaur.gamedata.item.ItemModifier mod : item.getModifiers()) {
                if (mod.displayName != null && !mod.displayName.isEmpty()) {
                    sb.append(mod.displayName).append("  ");
                } else {
                    sb.append((mod.value >= 0 ? "+" : "")).append(mod.value).append(" ").append(mod.type != null ? mod.type.name() : "").append("  ");
                }
            }
        }
        if (sb.length() == 0) {
            if (item.isConsumableOrTool()) {
                sb.append("Consumable Tool");
            } else if (item.isTreasure()) {
                sb.append("Treasure (").append(item.getBaseValue()).append(" Gold)");
            } else {
                sb.append("Usable Item");
            }
        }
        statsLabel.setText(sb.toString().trim());

        if (hotkeyHint != null && !hotkeyHint.isEmpty()) {
            promptLabel.setText("[Left-Click to Use]  [" + hotkeyHint + "]");
        } else {
            promptLabel.setText("[Left-Click to Use]");
        }

        pack();

        // Position horizontally centered above the anchor point, Y=205f (above 200px HUD)
        float posX = Math.max(10f, Math.min(1920f - getWidth() - 10f, anchorX - getWidth() / 2f));
        float posY = 205f;
        setPosition(posX, posY);
        toFront();
        setVisible(true);
    }

    public void hide() {
        setVisible(false);
    }
}
