package com.bpm.minotaur.gamedata;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Vector2;

import java.util.ArrayList;
import java.util.List;

public class Item implements Renderable {

    public enum ItemType {
        // Weapons
        BOW,
        PROJECTILE,
        SCROLL,
        BOOK,
        SMALL_FIREBALL,
        LARGE_FIREBALL,
        SMALL_LIGHTNING_BOLT,
        LARGE_LIGHTNING_BOLT,
        QUIVER, // Add this line


        // Armor
        SHIELD,
        HELMET,

        // Useful
        POTION_STRENGTH,
        POTION_HEALING,
        KEY,

        // Containers
        MONEY_BELT,
        SMALL_BAG,
        BOX,
        PACK,
        LARGE_BAG,
        CHEST,

        // Treasures
        COINS,
        NECKLACE,
        INGOT,
        LAMP,
        CHALICE,
        CROWN
    }

    public enum ItemCategory {
        WAR_WEAPON,
        SPIRITUAL_WEAPON,
        ARMOR,
        RING,
        USEFUL,
        TREASURE,
        CONTAINER
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

    public static class SpiritualWeaponStats {
        public int damage;

        public SpiritualWeaponStats(int damage) {
            this.damage = damage;
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
    private Color liquidColor;
    private WeaponStats weaponStats;
    private SpiritualWeaponStats spiritualWeaponStats;
    private ArmorStats armorStats;
    private final List<Item> contents = new ArrayList<>();
    private boolean isLocked = false;
    private int value = 0;

    public Item(ItemType type, int x, int y) {
        this.type = type;
        this.position = new Vector2(x + 0.5f, y + 0.5f);

        switch (type) {
            // --- WEAPONS ---
            case BOW:
                this.category = ItemCategory.WAR_WEAPON;
                this.color = new Color(0.5f, 0.35f, 0.05f, 1); // Brown
                this.weaponStats = new WeaponStats(8, 10, true);
                break;

            case QUIVER:
                this.category = ItemCategory.USEFUL;
                this.color = new Color(0.6f, 0.3f, 0.1f, 1); // A leathery brown color
                break;

            case SCROLL:
                this.category = ItemCategory.SPIRITUAL_WEAPON;
                this.color = Color.TAN;
                this.spiritualWeaponStats = new SpiritualWeaponStats(10);
                break;
            case BOOK:
                this.category = ItemCategory.SPIRITUAL_WEAPON;
                this.color = Color.BLUE;
                this.spiritualWeaponStats = new SpiritualWeaponStats(15);
                break;
            case SMALL_FIREBALL:
            case LARGE_FIREBALL:
                this.category = ItemCategory.SPIRITUAL_WEAPON;
                this.color = Color.RED;
                this.spiritualWeaponStats = (type == ItemType.SMALL_FIREBALL) ? new SpiritualWeaponStats(20) : new SpiritualWeaponStats(30);
                break;
            case SMALL_LIGHTNING_BOLT:
            case LARGE_LIGHTNING_BOLT:
                this.category = ItemCategory.SPIRITUAL_WEAPON;
                this.color = Color.CYAN;
                this.spiritualWeaponStats = (type == ItemType.SMALL_LIGHTNING_BOLT) ? new SpiritualWeaponStats(25) : new SpiritualWeaponStats(35);
                break;

            // --- ARMOR ---
            case SHIELD:
                this.category = ItemCategory.ARMOR;
                this.color = Color.GRAY;
                this.armorStats = new ArmorStats(5);
                break;
            case HELMET:
                this.category = ItemCategory.ARMOR;
                this.color = Color.LIGHT_GRAY;
                this.armorStats = new ArmorStats(3);
                break;

            // --- USEFUL ---
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
                break;

            // --- CONTAINERS ---
            case MONEY_BELT:
            case SMALL_BAG:
            case LARGE_BAG:
                this.category = ItemCategory.CONTAINER;
                this.color = Color.TAN;
                break;
            case BOX:
            case PACK:
                this.category = ItemCategory.CONTAINER;
                this.color = Color.ORANGE;
                this.isLocked = true;
                break;
            case CHEST:
                this.category = ItemCategory.CONTAINER;
                this.color = Color.BLUE;
                this.isLocked = true;
                break;

            // --- TREASURES ---
            case COINS:
                this.category = ItemCategory.TREASURE;
                this.color = Color.GOLD;
                this.value = 10;
                break;
            case NECKLACE:
                this.category = ItemCategory.TREASURE;
                this.color = Color.YELLOW;
                this.value = 20;
                break;
            case INGOT:
                this.category = ItemCategory.TREASURE;
                this.color = Color.GOLD;
                this.value = 50;
                break;
            case LAMP:
                this.category = ItemCategory.TREASURE;
                this.color = Color.GOLD;
                this.value = 100;
                break;
            case CHALICE:
                this.category = ItemCategory.TREASURE;
                this.color = Color.GOLD;
                this.value = 120;
                break;
            case CROWN:
                this.category = ItemCategory.TREASURE;
                this.color = Color.GOLD;
                this.value = 300;
                break;

            default:
                this.category = ItemCategory.USEFUL;
                this.color = Color.MAGENTA;
                break;
        }
    }

    public void addItem(Item item) {
        if (this.category == ItemCategory.CONTAINER) {
            contents.add(item);
        }
    }

    public List<Item> getContents() {
        return contents;
    }

    public boolean isLocked() {
        return isLocked;
    }

    public void unlock() {
        isLocked = false;
    }


    public Color getLiquidColor() {
        return liquidColor;
    }
    public int getValue() { return value; }
    public ItemType getType() { return type; }
    public ItemCategory getCategory() { return category; }
    @Override public Vector2 getPosition() { return position; }
    @Override public Color getColor() { return color; }
    public WeaponStats getWeaponStats() { return weaponStats; }
    public SpiritualWeaponStats getSpiritualWeaponStats() { return spiritualWeaponStats; }
    public ArmorStats getArmorStats() { return armorStats; }
}
