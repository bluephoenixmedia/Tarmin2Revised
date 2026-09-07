package com.bpm.minotaur.screens.inventory;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.utils.Align;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.ItemCategory;
import com.bpm.minotaur.gamedata.item.ItemModifier;
import com.bpm.minotaur.gamedata.player.Player;

/**
 * Dedicated item inspection & comparison card shown in the lower-right box.
 * Displays item name, category badge, primary stats, granted dice,
 * modifiers, and green/red comparison diffs against currently equipped gear.
 */
public class ItemInspectorPanel extends Table {

    private final Player player;
    private final InventorySkin skin;
    private final Table contentTable = new Table();

    private static final Color COL_INK_DARK = Color.valueOf("1C1208FF");
    private static final Color COL_INK_MUTED = Color.valueOf("5C4A30FF");
    private static final Color COL_UPGRADE = Color.valueOf("2E8B2EFF");
    private static final Color COL_DOWNGRADE = Color.valueOf("C82828FF");
    private static final Color COL_WAR = Color.valueOf("C0392BFF");
    private static final Color COL_SPIRITUAL = Color.valueOf("2980B9FF");
    private static final Color COL_ARMOR = Color.valueOf("7F8C8DFF");
    private static final Color COL_CONSUMABLE = Color.valueOf("27AE60FF");
    private static final Color COL_MAGIC = Color.valueOf("8E44ADFF");

    public ItemInspectorPanel(Player player, InventorySkin skin) {
        this.player = player;
        this.skin = skin;

        top().left();
        pad(8, 14, 8, 14);

        add(contentTable).expand().fill().top().left();
        showDefaultHint();
    }

    public void showDefaultHint() {
        contentTable.clear();
        Label hint = new Label("Hover or select an item to inspect details\nand compare with equipped gear.",
                new Label.LabelStyle(skin.getFontBody(), COL_INK_MUTED));
        hint.setAlignment(Align.center);
        contentTable.add(hint).expand().center();
    }

    public void inspect(Item item) {
        if (item == null) {
            showDefaultHint();
            return;
        }

        contentTable.clear();
        contentTable.top().left();

        // 1. Item Name Header
        String name = item.getDisplayName();
        if (name == null || name.isEmpty()) name = item.getFriendlyName();
        Label nameLabel = new Label(name, new Label.LabelStyle(skin.getFontHeader(), COL_INK_DARK));
        contentTable.add(nameLabel).left().padBottom(4).row();

        // 2. Category Badge & Type
        Table badgeRow = new Table();
        ItemCategory cat = item.getCategory();
        String badgeText = "[" + cat.name().replace('_', ' ') + "]";
        Color badgeColor = getCategoryColor(cat);

        Label badge = new Label(badgeText, new Label.LabelStyle(skin.getFontSmall(), badgeColor));
        badgeRow.add(badge).left().padRight(10);

        if (item.getGrantedDie() != null) {
            Label dieBadge = new Label("Die: " + item.getGrantedDie().getName(),
                    new Label.LabelStyle(skin.getFontSmall(), COL_MAGIC));
            badgeRow.add(dieBadge).left();
        }
        contentTable.add(badgeRow).left().padBottom(8).row();

        // 3. Stats & Details Table
        Table statsTable = new Table();
        statsTable.top().left();

        // Weapon stats & comparison
        if (cat == ItemCategory.WAR_WEAPON || cat == ItemCategory.SPIRITUAL_WEAPON || item.isWeapon()) {
            buildWeaponInspection(statsTable, item);
        }
        // Armor stats & comparison
        else if (cat == ItemCategory.ARMOR || item.isArmor()) {
            buildArmorInspection(statsTable, item);
        }
        // Consumable (food/potion/scroll)
        else if (item.isConsumableOrTool()) {
            buildConsumableInspection(statsTable, item);
        }
        // Rings & Accessories
        else if (cat == ItemCategory.RING || item.isRing()) {
            buildRingInspection(statsTable, item);
        }
        // General / Misc
        else {
            buildMiscInspection(statsTable, item);
        }

        contentTable.add(statsTable).expandX().fillX().left().padBottom(6).row();

        // 4. Modifiers / Enchantments (if any)
        if (item.getModifiers() != null && !item.getModifiers().isEmpty()) {
            Table modTable = new Table();
            modTable.top().left();
            for (ItemModifier mod : item.getModifiers()) {
                String modText = (mod.value >= 0 ? "+" : "") + mod.value + " " + (mod.type != null ? mod.type.name().replace('_', ' ') : "");
                Label modLabel = new Label("• " + modText, new Label.LabelStyle(skin.getFontSmall(), COL_MAGIC));
                modTable.add(modLabel).left().row();
            }
            contentTable.add(modTable).left().padBottom(4).row();
        }

        // 5. Instruction / Usage Footer
        String footerHint = getFooterHint(item);
        if (footerHint != null) {
            Label footer = new Label(footerHint, new Label.LabelStyle(skin.getFontSmall(), COL_INK_MUTED));
            contentTable.add(footer).left().padTop(4);
        }
    }

    private int parseMaxDamage(Item item) {
        if (item == null) return 0;
        String dd = item.getDamageDice();
        if (dd != null && dd.contains("d")) {
            try {
                String[] parts = dd.toLowerCase().split("d");
                int count = Integer.parseInt(parts[0].trim());
                int sides = Integer.parseInt(parts[1].split("[+-]")[0].trim());
                return count * sides;
            } catch (Exception ignored) {}
        }
        return 6;
    }

    private void buildWeaponInspection(Table table, Item item) {
        String diceFormula = item.getDamageDice() != null ? item.getDamageDice() : "1d6";
        addStatRow(table, "Damage Formula:", diceFormula, COL_INK_DARK);
        addStatRow(table, "Base Value:", item.getBaseValue() + " Gold", COL_INK_DARK);

        // Compare against currently equipped Right Hand weapon
        Item equipped = player.getInventory().getRightHand();
        Table compTable = new Table();
        compTable.top().left();

        if (equipped == null) {
            Label comp = new Label("Equip: Right Hand empty (Upgrade!)",
                    new Label.LabelStyle(skin.getFontSmall(), COL_UPGRADE));
            compTable.add(comp).left();
        } else if (equipped == item) {
            Label comp = new Label("Currently Equipped in Right Hand",
                    new Label.LabelStyle(skin.getFontSmall(), COL_MAGIC));
            compTable.add(comp).left();
        } else {
            int thisMax = parseMaxDamage(item);
            int eqMax = parseMaxDamage(equipped);
            int diff = thisMax - eqMax;

            String diffStr = (diff > 0 ? "(+" + diff + " Max Dmg Upgrade)" : diff < 0 ? "(" + diff + " Max Dmg)" : "(= Same Dmg)");
            Color diffCol = diff > 0 ? COL_UPGRADE : diff < 0 ? COL_DOWNGRADE : COL_INK_MUTED;

            Label eqLabel = new Label("vs " + equipped.getDisplayName() + ": ",
                    new Label.LabelStyle(skin.getFontSmall(), COL_INK_MUTED));
            Label diffLabel = new Label(diffStr,
                    new Label.LabelStyle(skin.getFontSmall(), diffCol));
            compTable.add(eqLabel).left();
            compTable.add(diffLabel).left();
        }
        table.add(compTable).colspan(2).left().padTop(4).row();
    }

    private void buildArmorInspection(Table table, Item item) {
        int itemAC = item.getArmorClassBonus();
        addStatRow(table, "Armor Class Bonus:", "+" + itemAC, COL_INK_DARK);
        addStatRow(table, "Base Value:", item.getBaseValue() + " Gold", COL_INK_DARK);

        // Determine slot & compare against equipped armor
        Item equipped = getEquippedArmorForSlot(item);
        Table compTable = new Table();
        compTable.top().left();

        if (equipped == null) {
            Label comp = new Label("Equip: Slot currently empty (+" + itemAC + " AC Upgrade!)",
                    new Label.LabelStyle(skin.getFontSmall(), COL_UPGRADE));
            compTable.add(comp).left();
        } else if (equipped == item) {
            Label comp = new Label("Currently Equipped",
                    new Label.LabelStyle(skin.getFontSmall(), COL_MAGIC));
            compTable.add(comp).left();
        } else {
            int diff = itemAC - equipped.getArmorClassBonus();
            String diffStr = (diff > 0 ? "(+" + diff + " AC Upgrade)" : diff < 0 ? "(" + diff + " AC Downgrade)" : "(= Same AC)");
            Color diffCol = diff > 0 ? COL_UPGRADE : diff < 0 ? COL_DOWNGRADE : COL_INK_MUTED;

            Label eqLabel = new Label("vs " + equipped.getDisplayName() + ": ",
                    new Label.LabelStyle(skin.getFontSmall(), COL_INK_MUTED));
            Label diffLabel = new Label(diffStr,
                    new Label.LabelStyle(skin.getFontSmall(), diffCol));
            compTable.add(eqLabel).left();
            compTable.add(diffLabel).left();
        }
        table.add(compTable).colspan(2).left().padTop(4).row();
    }

    private void buildConsumableInspection(Table table, Item item) {
        if (item.isFood()) {
            int nut = item.getNutrition() > 0 ? item.getNutrition() : 5;
            addStatRow(table, "Nutrition:", "+" + nut + " Satiety", COL_UPGRADE);
            if (item.getHydrationValue() > 0) {
                addStatRow(table, "Hydration:", "+" + item.getHydrationValue() + " Water", COL_SPIRITUAL);
            }
        } else if (item.isPotion()) {
            if (item.isIdentified() && item.getTrueEffect() != null) {
                addStatRow(table, "Identified Effect:", item.getTrueEffect().getBaseName(), COL_MAGIC);
            } else {
                addStatRow(table, "Effect:", "Unknown Potion (Drink to identify)", COL_INK_MUTED);
            }
        } else if (item.getType() != null && item.getType().name().contains("SCROLL")) {
            addStatRow(table, "Type:", "Magical Parchment Scroll", COL_MAGIC);
        } else {
            addStatRow(table, "Type:", "Consumable / Usable Utility", COL_INK_DARK);
        }
        addStatRow(table, "Base Value:", item.getBaseValue() + " Gold", COL_INK_DARK);
    }

    private void buildRingInspection(Table table, Item item) {
        addStatRow(table, "Slot:", "Finger Ring", COL_INK_DARK);
        addStatRow(table, "Base Value:", item.getBaseValue() + " Gold", COL_INK_DARK);
        Item wornRing = player.getEquipment().getWornRing();
        if (wornRing == item) {
            Label eq = new Label("Currently Worn on Ring Slot", new Label.LabelStyle(skin.getFontSmall(), COL_MAGIC));
            table.add(eq).colspan(2).left().padTop(4).row();
        }
    }

    private void buildMiscInspection(Table table, Item item) {
        String typeName = item.getType() != null ? item.getType().name().replace('_', ' ') : "Item";
        addStatRow(table, "Item Type:", typeName, COL_INK_DARK);
        addStatRow(table, "Base Value:", item.getBaseValue() + " Gold", COL_INK_DARK);
    }

    private void addStatRow(Table table, String label, String val, Color valColor) {
        Label l = new Label(label, new Label.LabelStyle(skin.getFontSmall(), COL_INK_MUTED));
        Label v = new Label(val, new Label.LabelStyle(skin.getFontSmall(), valColor));
        table.add(l).left().padRight(8).padBottom(2);
        table.add(v).left().padBottom(2).row();
    }

    private Item getEquippedArmorForSlot(Item item) {
        if (item.isHelmet()) return player.getEquipment().getWornHelmet();
        if (item.isShield()) return player.getEquipment().getWornShield();
        if (item.isGauntlets()) return player.getEquipment().getWornGauntlets();
        if (item.isBoots()) return player.getEquipment().getWornBoots();
        if (item.isLegs()) return player.getEquipment().getWornLegs();
        if (item.isArms()) return player.getEquipment().getWornArms();
        if (item.isCloak()) return player.getEquipment().getWornBack();
        if (item.isTorso()) return player.getEquipment().getWornChest();
        return null;
    }

    private Color getCategoryColor(ItemCategory cat) {
        if (cat == null) return COL_INK_MUTED;
        switch (cat) {
            case WAR_WEAPON: return COL_WAR;
            case SPIRITUAL_WEAPON: return COL_SPIRITUAL;
            case ARMOR: return COL_ARMOR;
            case FOOD:
            case USEFUL: return COL_CONSUMABLE;
            case RING: return COL_MAGIC;
            default: return COL_INK_MUTED;
        }
    }

    private String getFooterHint(Item item) {
        if (item.isConsumableOrTool()) {
            return "Tip: Place in Quick Slots (1-6) to use with number keys.";
        }
        if (item.isWeapon() || item.isArmor() || item.isRing()) {
            return "Tip: Drag onto paper doll to equip, or right-click to drop.";
        }
        return "Tip: Right-click in backpack to drop on the ground.";
    }
}
