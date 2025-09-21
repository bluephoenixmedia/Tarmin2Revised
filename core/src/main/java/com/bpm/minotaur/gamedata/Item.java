package com.bpm.minotaur.gamedata;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Vector2;

public class Item implements Renderable {

    public enum ItemType {
        POTION_STRENGTH,
        POTION_HEALING,
        KEY,
        PROJECTILE,
        BOW,
        SHIELD,
        HELMET
    }

    public enum ItemCategory {
        WEAPON,
        ARMOR,
        RING,
        USEFUL,
        TREASURE
    }

    public static class WeaponStats {
        public int damage;
        public int range;
        public boolean isRanged;

        public WeaponStats(int damage, int range, boolean isRanged) {
            this.damage = damage;
            this.range = range;
            this.isRanged = isRanged;
        }
    }

    public static class ArmorStats {
        public int defense;

        public ArmorStats(int defense) {
            this.defense = defense;
        }
    }


    private final ItemType type;
    private final ItemCategory category;
    private final Vector2 position;
    private final Color color;
    private Color liquidColor; // Restored this field
    private WeaponStats weaponStats;
    private ArmorStats armorStats;

    public Item(ItemType type, int x, int y) {
        this.type = type;
        this.position = new Vector2(x + 0.5f, y + 0.5f);

        switch (type) {
            case POTION_STRENGTH:
                this.category = ItemCategory.USEFUL;
                this.color = new Color(0.6f, 0.2f, 0.2f, 1);
                this.liquidColor = Color.RED;
                break;
            case POTION_HEALING:
                this.category = ItemCategory.USEFUL;
                this.color = new Color(0.2f, 0.6f, 0.2f, 1);
                this.liquidColor = Color.GREEN;
                break;
            case KEY:
                this.category = ItemCategory.USEFUL;
                this.color = Color.GOLD;
                this.liquidColor = Color.YELLOW;
                break;
            case PROJECTILE:
                this.category = ItemCategory.WEAPON;
                this.color = Color.WHITE;
                this.liquidColor = Color.WHITE;
                break;
            case BOW:
                this.category = ItemCategory.WEAPON;
                this.color = new Color(0.5f, 0.35f, 0.05f, 1); // Brown
                this.liquidColor = Color.TAN;
                this.weaponStats = new WeaponStats(8, 10, true);
                break;
            case SHIELD:
                this.category = ItemCategory.ARMOR;
                this.color = Color.GRAY;
                this.liquidColor = Color.DARK_GRAY;
                this.armorStats = new ArmorStats(5);
                break;
            case HELMET:
                this.category = ItemCategory.ARMOR;
                this.color = Color.LIGHT_GRAY;
                this.liquidColor = Color.WHITE;
                this.armorStats = new ArmorStats(3);
                break;
            default:
                this.category = ItemCategory.USEFUL;
                this.color = Color.MAGENTA;
                this.liquidColor = Color.PURPLE;
                break;
        }
    }

    // Added a getter for the liquidColor
    public Color getLiquidColor() {
        return liquidColor;
    }

    public ItemType getType() { return type; }
    public ItemCategory getCategory() { return category; }
    @Override public Vector2 getPosition() { return position; }
    @Override public Color getColor() { return color; }
    public WeaponStats getWeaponStats() { return weaponStats; }
    public ArmorStats getArmorStats() { return armorStats; }
}
