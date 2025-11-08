package com.bpm.minotaur.gamedata;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.math.Vector2;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class Item implements Renderable {


    public enum ItemType {
        // Weapons
        BOW,
        CROSSBOW,
        KNIFE,
        AXE,
        DART,
        SPEAR,
        PROJECTILE,
        SCROLL,
        BOOK,
        SMALL_FIREBALL,
        LARGE_FIREBALL,
        SMALL_LIGHTNING,
        LARGE_LIGHTNING,

        // Armor
        SMALL_SHIELD,
        LARGE_SHIELD,
        GAUNTLETS,
        HAUBERK,
        HELMET,
        BREASTPLATE,

        // Rings
        SMALL_RING,
        LARGE_RING,
        RING_BLUE,
        RING_PINK,
        RING_GREEN,
        RING_PURPLE,

        // Useful
        SMALL_POTION,
        LARGE_POTION,
        KEY,
        QUIVER,
        WAR_BOOK,
        SPIRITUAL_BOOK,
        SPECIAL_BOOK,
        FLOUR_SACK,

        // Containers
        MONEY_BELT,
        SMALL_BAG,
        BOX,
        MEDIUM_PACK,
        LARGE_BAG,
        REGULAR_CHEST,

        // Treasures
        COINS,
        NECKLACE,
        INGOT,
        LAMP,
        CHALICE,
        CROWN,
        TARMIN_TREASURE
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

    private String[] spriteData = new String[0]; // Add this line
    private Texture texture = null; // Add this field to store the item's texture
    public Vector2 scale; // Changed from float to Vector2

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

    public static class RingStats {
        public int defense;

        public RingStats(int defense) {
            this.defense = defense;
        }
    }


    private final ItemType type;
    private final ItemCategory category;
    private final Vector2 position;
    private final ItemColor itemColor; // Changed from Color to ItemColor
    private final Color color;
    private Color liquidColor;
    private WeaponStats weaponStats;
    private SpiritualWeaponStats spiritualWeaponStats;
    private ArmorStats armorStats;
    private RingStats ringStats;
    private final List<Item> contents = new ArrayList<>();
    private boolean isLocked = false;
    private int value = 0;
    private boolean vanishesOnUse = false;
    private ItemColor requiredKeyColor; // Add this field
    private final List<ItemModifier> modifiers = new ArrayList<>();


// In Item.java

    public Item(ItemType type, int x, int y, ItemColor itemColor) {
        this.type = type;
        this.position = new Vector2(x + 0.5f, y + 0.5f);
        this.scale = new Vector2(1.0f, 1.0f);
        this.itemColor = itemColor;
        this.color = itemColor.getColor();
        this.spriteData = spriteData;
        if (isLocked) {
            if (itemColor == ItemColor.CONTAINER_TAN) {
                this.requiredKeyColor = ItemColor.CONTAINER_TAN;
            } else if (itemColor == ItemColor.CONTAINER_ORANGE) {
                this.requiredKeyColor = ItemColor.CONTAINER_ORANGE;
            } else if (itemColor == ItemColor.CONTAINER_BLUE) {
                this.requiredKeyColor = ItemColor.CONTAINER_BLUE;
            }
        }

        switch (type) {
            // --- WEAPONS ---
            case BOW:
                this.category = ItemCategory.WAR_WEAPON;
                switch (this.itemColor) {
                    case TAN:
                        this.weaponStats = new WeaponStats(4, 10, true);
                        this.spiritualWeaponStats = new SpiritualWeaponStats(1);
                        break;
                    case ORANGE:
                        this.weaponStats = new WeaponStats(5, 10, true);
                        this.spiritualWeaponStats = new SpiritualWeaponStats(2);
                        break;
                    case BLUE:
                        this.weaponStats = new WeaponStats(6, 20, true);
                        this.spiritualWeaponStats = new SpiritualWeaponStats(3);
                        break;
                    case GRAY:
                        this.weaponStats = new WeaponStats(7, 30, true);
                        this.spiritualWeaponStats = new SpiritualWeaponStats(4);
                        break;
                    case YELLOW:
                        this.weaponStats = new WeaponStats(10, 50, true);
                        this.spiritualWeaponStats = new SpiritualWeaponStats(5);
                        break;
                    case WHITE:
                        this.weaponStats = new WeaponStats(15, 100, true);
                        this.spiritualWeaponStats = new SpiritualWeaponStats(10);
                        break;
                    default:
                        this.weaponStats = new WeaponStats(4, 10, true); // fallback
                        break;
                }
                this.spriteData = ItemSpriteData.BOW;

                this.texture = new Texture(Gdx.files.internal("images/items/bow.png")); // Load the texture
                this.scale.set(0.5f, 0.5f);
                break;
            case CROSSBOW:
                this.category = ItemCategory.WAR_WEAPON;
              //  this.color = new Color(0.6f, 0.4f, 0.1f, 1);
                switch (this.itemColor) {
                    case TAN:
                        this.weaponStats = new WeaponStats(5, 10, true);
                        this.spiritualWeaponStats = new SpiritualWeaponStats(2);
                        break;
                    case ORANGE:
                        this.weaponStats = new WeaponStats(6, 10, true);
                        this.spiritualWeaponStats = new SpiritualWeaponStats(3);
                        break;
                    case BLUE:
                        this.weaponStats = new WeaponStats(7, 20, true);
                        this.spiritualWeaponStats = new SpiritualWeaponStats(4);
                        break;
                    case GRAY:
                        this.weaponStats = new WeaponStats(8, 30, true);
                        this.spiritualWeaponStats = new SpiritualWeaponStats(5);
                        break;
                    case YELLOW:
                        this.weaponStats = new WeaponStats(15, 50, true);
                        this.spiritualWeaponStats = new SpiritualWeaponStats(8);
                        break;
                    case WHITE:
                        this.weaponStats = new WeaponStats(20, 100, true);
                        this.spiritualWeaponStats = new SpiritualWeaponStats(10);
                        break;
                    default:
                        this.weaponStats = new WeaponStats(5, 10, true); // fallback
                        break;
                }
                this.spriteData = ItemSpriteData.CROSSBOW;
                this.texture = new Texture(Gdx.files.internal("images/items/bow.png"));
                this.scale.set(0.9f, 1.3f);
                break;
            case KNIFE:
                this.category = ItemCategory.WAR_WEAPON;
             //   this.color = Color.GRAY;
                switch (this.itemColor) {
                    case TAN:
                        this.weaponStats = new WeaponStats(5, 10, false);
                        this.spiritualWeaponStats = new SpiritualWeaponStats(2);
                        break;
                    case ORANGE:
                        this.weaponStats = new WeaponStats(6, 10, false);
                        this.spiritualWeaponStats = new SpiritualWeaponStats(3);
                        break;
                    case BLUE:
                        this.weaponStats = new WeaponStats(7, 20, false);
                        this.spiritualWeaponStats = new SpiritualWeaponStats(4);
                        break;
                    case GRAY:
                        this.weaponStats = new WeaponStats(8, 30, false);
                        this.spiritualWeaponStats = new SpiritualWeaponStats(5);
                        break;
                    case YELLOW:
                        this.weaponStats = new WeaponStats(15, 50, false);
                        this.spiritualWeaponStats = new SpiritualWeaponStats(8);
                        break;
                    case WHITE:
                        this.weaponStats = new WeaponStats(20, 100, false);
                        this.spiritualWeaponStats = new SpiritualWeaponStats(10);
                        break;
                    default:
                        this.weaponStats = new WeaponStats(5, 10, false); // fallback
                        break;
                }
                this.spriteData = ItemSpriteData.KNIFE;
                this.texture = new Texture(Gdx.files.internal("images/items/bow.png"));
                this.scale.set(0.5f, 0.5f);
                this.vanishesOnUse = true;
                break;
            case AXE:
                this.category = ItemCategory.WAR_WEAPON;
               // this.color = Color.DARK_GRAY;
                switch (this.itemColor) {
                    case TAN:
                        this.weaponStats = new WeaponStats(6, 10, false);
                        this.spiritualWeaponStats = new SpiritualWeaponStats(2);
                        break;
                    case ORANGE:
                        this.weaponStats = new WeaponStats(7, 10, false);
                        this.spiritualWeaponStats = new SpiritualWeaponStats(3);
                        break;
                    case BLUE:
                        this.weaponStats = new WeaponStats(8, 20, false);
                        this.spiritualWeaponStats = new SpiritualWeaponStats(4);
                        break;
                    case GRAY:
                        this.weaponStats = new WeaponStats(9, 30, false);
                        this.spiritualWeaponStats = new SpiritualWeaponStats(5);
                        break;
                    case YELLOW:
                        this.weaponStats = new WeaponStats(18, 50, false);
                        this.spiritualWeaponStats = new SpiritualWeaponStats(10);
                        break;
                    case WHITE:
                        this.weaponStats = new WeaponStats(22, 100, false);
                        this.spiritualWeaponStats = new SpiritualWeaponStats(15);
                        break;
                    default:
                        this.weaponStats = new WeaponStats(6, 10, false); // fallback
                        break;
                }
                this.spriteData = ItemSpriteData.AXE;
                this.texture = new Texture(Gdx.files.internal("images/items/bow.png"));
                this.scale.set(0.3f, 0.3f);
                this.vanishesOnUse = true;
                break;
            case DART:
                this.category = ItemCategory.WAR_WEAPON;
               // this.color = Color.LIGHT_GRAY;
                switch (this.itemColor) {
                    case TAN:
                        this.weaponStats = new WeaponStats(4, 10, false);
                        this.spiritualWeaponStats = new SpiritualWeaponStats(1);
                        break;
                    case ORANGE:
                        this.weaponStats = new WeaponStats(5, 10, false);
                        this.spiritualWeaponStats = new SpiritualWeaponStats(2);
                        break;
                    case BLUE:
                        this.weaponStats = new WeaponStats(6, 20, false);
                        this.spiritualWeaponStats = new SpiritualWeaponStats(3);
                        break;
                    case GRAY:
                        this.weaponStats = new WeaponStats(7, 30, false);
                        this.spiritualWeaponStats = new SpiritualWeaponStats(4);
                        break;
                    case YELLOW:
                        this.weaponStats = new WeaponStats(10, 50, false);
                        this.spiritualWeaponStats = new SpiritualWeaponStats(5);
                        break;
                    case WHITE:
                        this.weaponStats = new WeaponStats(15, 100, false);
                        this.spiritualWeaponStats = new SpiritualWeaponStats(10);
                        break;
                    default:
                        this.weaponStats = new WeaponStats(4, 10, false); // fallback
                        break;
                }                this.spriteData = ItemSpriteData.DART;
                this.texture = new Texture(Gdx.files.internal("images/items/bow.png"));
                this.scale.set(0.3f, 0.3f);
                this.vanishesOnUse = true;
                break;
            case SPEAR:
                this.category = ItemCategory.WAR_WEAPON;
               // this.color = new Color(0.4f, 0.25f, 0.05f, 1);
                switch (this.itemColor) {
                    case TAN:
                        this.weaponStats = new WeaponStats(6, 10, false);
                        this.spiritualWeaponStats = new SpiritualWeaponStats(2);
                        break;
                    case ORANGE:
                        this.weaponStats = new WeaponStats(7, 10, false);
                        this.spiritualWeaponStats = new SpiritualWeaponStats(3);
                        break;
                    case BLUE:
                        this.weaponStats = new WeaponStats(9, 20, false);
                        this.spiritualWeaponStats = new SpiritualWeaponStats(4);
                        break;
                    case GRAY:
                        this.weaponStats = new WeaponStats(10, 30, false);
                        this.spiritualWeaponStats = new SpiritualWeaponStats(5);
                        break;
                    case YELLOW:
                        this.weaponStats = new WeaponStats(20, 50, false);
                        this.spiritualWeaponStats = new SpiritualWeaponStats(10);
                        break;
                    case WHITE:
                        this.weaponStats = new WeaponStats(25, 100, false);
                        this.spiritualWeaponStats = new SpiritualWeaponStats(15);
                        break;
                    default:
                        this.weaponStats = new WeaponStats(6, 10, false); // fallback
                        break;
                }
                this.spriteData = ItemSpriteData.SPEAR;
                this.texture = new Texture(Gdx.files.internal("images/items/bow.png"));
                this.scale.set(1.0f, 1.5f);
                this.vanishesOnUse = true;
                break;
            case SCROLL:
                this.category = ItemCategory.SPIRITUAL_WEAPON;
               // this.color = Color.TAN;
                switch (this.itemColor) {
                    case BLUE:
                        this.spiritualWeaponStats = new SpiritualWeaponStats(10);
                        break;
                    case GRAY_SPIRITUAL:
                        this.spiritualWeaponStats = new SpiritualWeaponStats(12);
                        break;
                    case WHITE_SPIRITUAL:
                        this.spiritualWeaponStats = new SpiritualWeaponStats(15);
                        break;
                    case PINK:
                        this.spiritualWeaponStats = new SpiritualWeaponStats(18);
                        break;
                    case RED:
                        this.spiritualWeaponStats = new SpiritualWeaponStats(25);
                        break;
                    case PURPLE:
                        this.spiritualWeaponStats = new SpiritualWeaponStats(30);
                        break;
                    default:
                        this.spiritualWeaponStats = new SpiritualWeaponStats(10); // fallback
                        break;
                }
                this.spriteData = ItemSpriteData.SCROLL;
                this.texture = new Texture(Gdx.files.internal("images/items/scroll.png")); // Load the texture
                this.scale.set(0.7f, 0.7f);
                this.vanishesOnUse = false;
                break;
            case BOOK:
                this.category = ItemCategory.SPIRITUAL_WEAPON;
                //this.color = Color.BLUE;
                switch (this.itemColor) {
                    case BLUE:
                        this.spiritualWeaponStats = new SpiritualWeaponStats(18);
                        break;
                    case GRAY_SPIRITUAL:
                        this.spiritualWeaponStats = new SpiritualWeaponStats(20);
                        break;
                    case WHITE_SPIRITUAL:
                        this.spiritualWeaponStats = new SpiritualWeaponStats(25);
                        break;
                    case PINK:
                        this.spiritualWeaponStats = new SpiritualWeaponStats(28);
                        break;
                    case RED:
                        this.spiritualWeaponStats = new SpiritualWeaponStats(35);
                        break;
                    case PURPLE:
                        this.spiritualWeaponStats = new SpiritualWeaponStats(50);
                        break;
                    default:
                        this.spiritualWeaponStats = new SpiritualWeaponStats(18); // fallback
                        break;
                }
                this.spriteData = ItemSpriteData.BOOK;
                this.texture = new Texture(Gdx.files.internal("images/items/book.png")); // Load the texture
                this.scale.set(0.7f, 0.7f);
                this.vanishesOnUse = false;

                break;
            case SMALL_FIREBALL:
                this.category = ItemCategory.SPIRITUAL_WEAPON;
               // this.color = Color.RED;
                switch (this.itemColor) {
                    case BLUE:
                        this.spiritualWeaponStats = new SpiritualWeaponStats(9);
                        break;
                    case GRAY_SPIRITUAL:
                        this.spiritualWeaponStats = new SpiritualWeaponStats(10);
                        break;
                    case WHITE_SPIRITUAL:
                        this.spiritualWeaponStats = new SpiritualWeaponStats(16);
                        break;
                    case PINK:
                        this.spiritualWeaponStats = new SpiritualWeaponStats(19);
                        break;
                    case RED:
                        this.spiritualWeaponStats = new SpiritualWeaponStats(21);
                        break;
                    case PURPLE:
                        this.spiritualWeaponStats = new SpiritualWeaponStats(25);
                        break;
                    default:
                        this.spiritualWeaponStats = new SpiritualWeaponStats(28); // fallback
                        break;
                }
                this.spriteData = ItemSpriteData.SMALL_FIREBALL;
                this.texture = new Texture(Gdx.files.internal("images/items/firenball.png")); // Load the texture
                this.scale.set(0.7f, 0.7f);
                this.vanishesOnUse = true;


                break;
            case LARGE_FIREBALL:
                this.category = ItemCategory.SPIRITUAL_WEAPON;
              //  this.color = Color.RED;
                switch (this.itemColor) {
                    case BLUE:
                        this.spiritualWeaponStats = new SpiritualWeaponStats(11);
                        break;
                    case GRAY_SPIRITUAL:
                        this.spiritualWeaponStats = new SpiritualWeaponStats(13);
                        break;
                    case WHITE_SPIRITUAL:
                        this.spiritualWeaponStats = new SpiritualWeaponStats(15);
                        break;
                    case PINK:
                        this.spiritualWeaponStats = new SpiritualWeaponStats(20);
                        break;
                    case RED:
                        this.spiritualWeaponStats = new SpiritualWeaponStats(22);
                        break;
                    case PURPLE:
                        this.spiritualWeaponStats = new SpiritualWeaponStats(26);
                        break;
                    default:
                        this.spiritualWeaponStats = new SpiritualWeaponStats(29); // fallback
                        break;
                }
                this.spriteData = ItemSpriteData.LARGE_FIREBALL;
                this.texture = new Texture(Gdx.files.internal("images/items/big_fireball.png")); // Load the texture
                this.scale.set(0.8f, 0.8f);
                this.vanishesOnUse = true;

                break;
            case SMALL_LIGHTNING:
                this.category = ItemCategory.SPIRITUAL_WEAPON;
             //   this.color = Color.CYAN;
                switch (this.itemColor) {
                    case BLUE:
                        this.spiritualWeaponStats = new SpiritualWeaponStats(9);
                        break;
                    case GRAY_SPIRITUAL:
                        this.spiritualWeaponStats = new SpiritualWeaponStats(10);
                        break;
                    case WHITE_SPIRITUAL:
                        this.spiritualWeaponStats = new SpiritualWeaponStats(16);
                        break;
                    case PINK:
                        this.spiritualWeaponStats = new SpiritualWeaponStats(19);
                        break;
                    case RED:
                        this.spiritualWeaponStats = new SpiritualWeaponStats(21);
                        break;
                    case PURPLE:
                        this.spiritualWeaponStats = new SpiritualWeaponStats(25);
                        break;
                    default:
                        this.spiritualWeaponStats = new SpiritualWeaponStats(28); // fallback
                        break;
                }
                this.spriteData = ItemSpriteData.SMALL_LIGHTNING;
                this.texture = new Texture(Gdx.files.internal("images/items/lightning_bolt.png")); // Load the texture
                this.scale.set(0.7f, 0.7f);
                this.vanishesOnUse = true;

                break;
            case LARGE_LIGHTNING:
                this.category = ItemCategory.SPIRITUAL_WEAPON;
               // this.color = Color.CYAN;
                switch (this.itemColor) {
                    case BLUE:
                        this.spiritualWeaponStats = new SpiritualWeaponStats(11);
                        break;
                    case GRAY_SPIRITUAL:
                        this.spiritualWeaponStats = new SpiritualWeaponStats(13);
                        break;
                    case WHITE_SPIRITUAL:
                        this.spiritualWeaponStats = new SpiritualWeaponStats(15);
                        break;
                    case PINK:
                        this.spiritualWeaponStats = new SpiritualWeaponStats(20);
                        break;
                    case RED:
                        this.spiritualWeaponStats = new SpiritualWeaponStats(22);
                        break;
                    case PURPLE:
                        this.spiritualWeaponStats = new SpiritualWeaponStats(26);
                        break;
                    default:
                        this.spiritualWeaponStats = new SpiritualWeaponStats(29); // fallback
                        break;
                }
                this.spriteData = ItemSpriteData.LARGE_LIGHTNING;
                this.texture = new Texture(Gdx.files.internal("images/items/big_bolt.png")); // Load the texture
                this.scale.set(0.9f, 0.9f);
                this.vanishesOnUse = true;

                break;

            // --- ARMOR ---
            case SMALL_SHIELD:
                this.category = ItemCategory.ARMOR;
              //  this.color = Color.GRAY;
                switch (this.itemColor) {
                    case BLUE:
                        this.armorStats = new ArmorStats(5);
                        break;
                    case GRAY_SPIRITUAL:
                        this.armorStats = new ArmorStats(6);
                        break;
                    case WHITE_SPIRITUAL:
                        this.armorStats = new ArmorStats(7);
                        break;
                    case PINK:
                        this.armorStats = new ArmorStats(8);
                        break;
                    case RED:
                        this.armorStats = new ArmorStats(10);
                        break;
                    case PURPLE:
                        this.armorStats = new ArmorStats(12);
                        break;
                    default:
                        this.armorStats = new ArmorStats(15);
                        break;
                }
               // this.armorStats = new ArmorStats(5);
                this.spriteData = ItemSpriteData.SMALL_SHIELD; // Using SMALL_SHIELD for now
                this.texture = new Texture(Gdx.files.internal("images/items/shield.png")); // Load the texture
                this.scale.set(0.7f, 0.7f);
                break;
            case LARGE_SHIELD:
                this.category = ItemCategory.ARMOR;
             //   this.color = Color.DARK_GRAY;
                switch (this.itemColor) {
                    case BLUE:
                        this.armorStats = new ArmorStats(8);
                        break;
                    case GRAY_SPIRITUAL:
                        this.armorStats = new ArmorStats(9);
                        break;
                    case WHITE_SPIRITUAL:
                        this.armorStats = new ArmorStats(10);
                        break;
                    case PINK:
                        this.armorStats = new ArmorStats(12);
                        break;
                    case RED:
                        this.armorStats = new ArmorStats(15);
                        break;
                    case PURPLE:
                        this.armorStats = new ArmorStats(20);
                        break;
                    default:
                        this.armorStats = new ArmorStats(8);
                        break;
                }
                this.spriteData = ItemSpriteData.LARGE_SHIELD;
                this.texture = new Texture(Gdx.files.internal("images/items/shield.png"));
                this.scale.set(0.9f, 0.9f);
                break;
            case GAUNTLETS:
                this.category = ItemCategory.ARMOR;
             //   this.color = Color.LIGHT_GRAY;
                switch (this.itemColor) {
                    case BLUE:
                        this.armorStats = new ArmorStats(2);
                        break;
                    case GRAY_SPIRITUAL:
                        this.armorStats = new ArmorStats(3);
                        break;
                    case WHITE_SPIRITUAL:
                        this.armorStats = new ArmorStats(4);
                        break;
                    case PINK:
                        this.armorStats = new ArmorStats(5);
                        break;
                    case RED:
                        this.armorStats = new ArmorStats(10);
                        break;
                    case PURPLE:
                        this.armorStats = new ArmorStats(12);
                        break;
                    default:
                        this.armorStats = new ArmorStats(2);
                        break;
                }
                this.spriteData = ItemSpriteData.GAUNTLETS;
                this.texture = new Texture(Gdx.files.internal("images/items/helmet.png"));
                this.scale.set(0.6f, 0.6f);
                break;
            case HAUBERK:
                this.category = ItemCategory.ARMOR;
              //  this.color = Color.GRAY;
                switch (this.itemColor) {
                    case BLUE:
                        this.armorStats = new ArmorStats(8);
                        break;
                    case GRAY_SPIRITUAL:
                        this.armorStats = new ArmorStats(9);
                        break;
                    case WHITE_SPIRITUAL:
                        this.armorStats = new ArmorStats(10);
                        break;
                    case PINK:
                        this.armorStats = new ArmorStats(12);
                        break;
                    case RED:
                        this.armorStats = new ArmorStats(15);
                        break;
                    case PURPLE:
                        this.armorStats = new ArmorStats(20);
                        break;
                    default:
                        this.armorStats = new ArmorStats(8);
                        break;
                }
                this.spriteData = ItemSpriteData.HAUBERK;
                this.texture = new Texture(Gdx.files.internal("images/items/helmet.png"));
                this.scale.set(1.0f, 1.0f);
                break;
            case HELMET:
                this.category = ItemCategory.ARMOR;
             //   this.color = Color.LIGHT_GRAY;
                switch (this.itemColor) {
                    case BLUE:
                        this.armorStats = new ArmorStats(3);
                        break;
                    case GRAY_SPIRITUAL:
                        this.armorStats = new ArmorStats(4);
                        break;
                    case WHITE_SPIRITUAL:
                        this.armorStats = new ArmorStats(5);
                        break;
                    case PINK:
                        this.armorStats = new ArmorStats(7);
                        break;
                    case RED:
                        this.armorStats = new ArmorStats(10);
                        break;
                    case PURPLE:
                        this.armorStats = new ArmorStats(12);
                        break;
                    default:
                        this.armorStats = new ArmorStats(2);
                        break;
                }
                this.spriteData = ItemSpriteData.HELMET;
                this.texture = new Texture(Gdx.files.internal("images/items/helmet.png")); // Load the texture
                this.scale.set(0.7f, 0.7f);
                break;
            case BREASTPLATE:
                this.category = ItemCategory.ARMOR;
            //    this.color = Color.DARK_GRAY;
                switch (this.itemColor) {
                    case BLUE:
                        this.armorStats = new ArmorStats(12);
                        break;
                    case GRAY_SPIRITUAL:
                        this.armorStats = new ArmorStats(13);
                        break;
                    case WHITE_SPIRITUAL:
                        this.armorStats = new ArmorStats(15);
                        break;
                    case PINK:
                        this.armorStats = new ArmorStats(20);
                        break;
                    case RED:
                        this.armorStats = new ArmorStats(25);
                        break;
                    case PURPLE:
                        this.armorStats = new ArmorStats(30);
                        break;
                    default:
                        this.armorStats = new ArmorStats(12);
                        break;
                }
                this.spriteData = ItemSpriteData.BREASTPLATE;
                this.texture = new Texture(Gdx.files.internal("images/items/helmet.png"));
                this.scale.set(1.0f, 1.0f);
                break;

            // --- RINGS ---
            case LARGE_RING:
                this.category = ItemCategory.RING;
            //    this.color = Color.BLUE;
                switch (this.itemColor) {
                    case BLUE:
                        this.ringStats = new RingStats(2);
                        break;
                    case GRAY_SPIRITUAL:
                        this.ringStats = new RingStats(3);
                        break;
                    case WHITE_SPIRITUAL:
                        this.ringStats = new RingStats(4);
                        break;
                    case PINK:
                        this.ringStats = new RingStats(5);
                        break;
                    case RED:
                        this.ringStats = new RingStats(10);
                        break;
                    case PURPLE:
                        this.ringStats = new RingStats(20);
                        break;
                    default:
                        this.ringStats = new RingStats(2);
                        break;
                }
                this.spriteData = ItemSpriteData.LARGE_RING;
                this.texture = new Texture(Gdx.files.internal("images/items/blue_ring.png")); // Load the texture
                this.scale.set(0.8f, 0.8f);
                break;

            case SMALL_RING:
                this.category = ItemCategory.RING;
                switch (this.itemColor) {
                    case BLUE:
                        this.ringStats = new RingStats(1);
                        break;
                    case GRAY_SPIRITUAL:
                        this.ringStats = new RingStats(2);
                        break;
                    case WHITE_SPIRITUAL:
                        this.ringStats = new RingStats(3);
                        break;
                    case PINK:
                        this.ringStats = new RingStats(4);
                        break;
                    case RED:
                        this.ringStats = new RingStats(5);
                        break;
                    case PURPLE:
                        this.ringStats = new RingStats(10);
                        break;
                    default:
                        this.ringStats = new RingStats(1);
                        break;
                }
                this.spriteData = ItemSpriteData.SMALL_RING;
                this.texture = new Texture(Gdx.files.internal("images/items/blue_ring.png")); // Load the texture
                this.scale.set(0.5f, 0.5f);
                break;



            // --- USEFUL ---
            case SMALL_POTION:
                this.category = ItemCategory.USEFUL;
            //    this.color = new Color(0.6f, 0.2f, 0.2f, 1);
                this.liquidColor = Color.RED;
                this.spriteData = ItemSpriteData.SMALL_POTION;
                this.texture = new Texture(Gdx.files.internal("images/items/potion_gold.png")); // Load the texture
                this.scale.set(1.0f, 1.0f);
                this.vanishesOnUse = true;
                break;
            case LARGE_POTION:
                this.category = ItemCategory.USEFUL;
            //    this.color = new Color(0.2f, 0.6f, 0.2f, 1);
                this.liquidColor = Color.GREEN;
                this.spriteData = ItemSpriteData.LARGE_POTION;
                this.texture = new Texture(Gdx.files.internal("images/items/potion_blue.png")); // Load the texture
                this.scale.set(.6f, .6f);
                this.vanishesOnUse = true;
                break;
            case KEY:
                this.category = ItemCategory.USEFUL;
            //    this.color = Color.GOLD;
                this.spriteData = ItemSpriteData.KEY;
                this.texture = new Texture(Gdx.files.internal("images/items/key.png")); // Load the texture
                this.scale.set(0.2f, 0.2f);
                break;
            case QUIVER:
                this.category = ItemCategory.USEFUL;
            //    this.color = new Color(0.6f, 0.3f, 0.1f, 1);
                this.spriteData = ItemSpriteData.QUIVER;
                this.texture = new Texture(Gdx.files.internal("images/items/quiver.png")); // Load the texture
                this.scale.set(1.0f, 1.0f);
                break;
            case FLOUR_SACK:
                this.category = ItemCategory.USEFUL;
            //   this.color = Color.WHITE;
                this.spriteData = ItemSpriteData.FLOUR_SACK;
                this.texture = new Texture(Gdx.files.internal("images/items/small_bag.png"));
                this.scale.set(0.6f, 0.6f);
                break;
            case WAR_BOOK:
                this.category = ItemCategory.USEFUL;
            //    this.color = Color.RED;
                this.spriteData = ItemSpriteData.WAR_BOOK;
                this.texture = new Texture(Gdx.files.internal("images/items/book.png"));
                this.scale.set(0.7f, 0.7f);
                this.vanishesOnUse = false;
                break;
            case SPIRITUAL_BOOK:
                this.category = ItemCategory.USEFUL;
            //    this.color = Color.PURPLE;
                this.spriteData = ItemSpriteData.SPIRITUAL_BOOK;
                this.texture = new Texture(Gdx.files.internal("images/items/book.png"));
                this.scale.set(0.7f, 0.7f);
                this.vanishesOnUse = false;
                break;
            case SPECIAL_BOOK:
                this.category = ItemCategory.USEFUL;
             //   this.color = Color.CYAN;
                this.spriteData = ItemSpriteData.SPECIAL_BOOK;
                this.texture = new Texture(Gdx.files.internal("images/items/book.png"));
                this.scale.set(0.7f, 0.7f);
                break;

            // --- CONTAINERS ---
            case MONEY_BELT:
                this.category = ItemCategory.CONTAINER;
            //    this.color = Color.TAN;
                this.spriteData = ItemSpriteData.MONEY_BELT;
                this.texture = new Texture(Gdx.files.internal("images/items/money_belt.png")); // Load the texture

                break;
            case SMALL_BAG:
                this.category = ItemCategory.CONTAINER;
            //    this.color = Color.TAN;
                this.spriteData = ItemSpriteData.SMALL_BAG;
                this.texture = new Texture(Gdx.files.internal("images/items/small_bag.png")); // Load the texture

                break;
            case BOX:
                this.category = ItemCategory.CONTAINER;
            //    this.color = Color.ORANGE;
                this.isLocked = true;
                this.spriteData = ItemSpriteData.BOX;
                this.texture = new Texture(Gdx.files.internal("images/items/box_.png")); // Load the texture

                break;
            case MEDIUM_PACK:
                this.category = ItemCategory.CONTAINER;
             //   this.color = Color.ORANGE;
                this.isLocked = true;
                this.spriteData = ItemSpriteData.MEDIUM_PACK;
                this.texture = new Texture(Gdx.files.internal("images/items/pack.png")); // Load the texture

                break;
            case LARGE_BAG:
                this.category = ItemCategory.CONTAINER;
            //    this.color = Color.TAN;
                this.spriteData = ItemSpriteData.LARGE_BAG;
                this.texture = new Texture(Gdx.files.internal("images/items/large_bag.png")); // Load the texture

                break;
            case REGULAR_CHEST:
                this.category = ItemCategory.CONTAINER;
             //   this.color = Color.BLUE;
                this.isLocked = true;
                this.spriteData = ItemSpriteData.REGULAR_CHEST;
                this.texture = new Texture(Gdx.files.internal("images/items/box.png")); // Load the texture

                break;

            // --- TREASURES ---
            case COINS:
                this.category = ItemCategory.TREASURE;
             //   this.color = Color.GOLD;
                this.value = 10;
                this.spriteData = ItemSpriteData.COINS;
                this.texture = new Texture(Gdx.files.internal("images/items/coins.png")); // Load the texture

                break;
            case NECKLACE:
                this.category = ItemCategory.TREASURE;
             //   this.color = Color.YELLOW;
                this.value = 20;
                this.spriteData = ItemSpriteData.NECKLACE;
                this.texture = new Texture(Gdx.files.internal("images/items/necklace.png")); // Load the texture

                break;
            case INGOT:
                this.category = ItemCategory.TREASURE;
            //    this.color = Color.GOLD;
                this.value = 50;
                this.spriteData = ItemSpriteData.INGOT;
                this.texture = new Texture(Gdx.files.internal("images/items/ingot.png")); // Load the texture

                break;
            case LAMP:
                this.category = ItemCategory.TREASURE;
            //    this.color = Color.GOLD;
                this.value = 100;
                this.spriteData = ItemSpriteData.LAMP;
                this.texture = new Texture(Gdx.files.internal("images/items/lamp.png")); // Load the texture

                break;
            case CHALICE:
                this.category = ItemCategory.TREASURE;
            //    this.color = Color.GOLD;
                this.value = 120;
                this.spriteData = ItemSpriteData.CHALICE;
                this.texture = new Texture(Gdx.files.internal("images/items/chalice.png")); // Load the texture


                break;
            case CROWN:
                this.category = ItemCategory.TREASURE;
            //    this.color = Color.GOLD;
                this.value = 300;
                this.spriteData = ItemSpriteData.CROWN;
                this.texture = new Texture(Gdx.files.internal("images/items/crown.png")); // Load the texture

                break;
            case TARMIN_TREASURE:
                this.category = ItemCategory.TREASURE;
            //    this.color = Color.YELLOW;
                this.value = 1000;
                this.spriteData = ItemSpriteData.TARMIN_TREASURE;
                this.texture = new Texture(Gdx.files.internal("images/items/crown.png"));
                this.scale.set(1.2f, 1.2f);
                break;

            // --- PROJECTILE: No sprite needed ---
            case PROJECTILE:
                this.category = ItemCategory.WAR_WEAPON;
            //    this.color = Color.WHITE;
                this.spriteData = null;
                break;

            default:
                this.category = ItemCategory.USEFUL;
            //    this.color = Color.MAGENTA;
                this.spriteData = null;
                break;
        }
    }

    public void addItem(Item item) {
        if (this.category == ItemCategory.CONTAINER) {
            contents.add(item);
        }
    }

    public Vector2 getScale() {
        return scale;
    }

    public List<Item> getContents() {
        return contents;
    }

    public boolean isLocked() {
        return isLocked;
    }

    public Texture getTexture() {
        return texture;
    }

    public void unlock() {
        isLocked = false;
    }

    public Color getLiquidColor() {
        return liquidColor;
    }

    public String[] getSpriteData() {
        return spriteData;
    }

    public boolean vanishesOnUse() {
        return vanishesOnUse;
    }

    public void setValue(int value) {
        if (this.category == ItemCategory.TREASURE) {
            this.value = value;
        }
    }
    public int getValue() { return value; }
    public ItemType getType() { return type; }
    public String getTypeName() { return type.name(); }
    public ItemCategory getCategory() { return category; }
    @Override public Vector2 getPosition() { return position; }
   // @Override public Color getColor() { return color; }
    public WeaponStats getWeaponStats() { return weaponStats; }
    public SpiritualWeaponStats getSpiritualWeaponStats() { return spiritualWeaponStats; }
    public ArmorStats getArmorStats() { return armorStats; }
    public RingStats getRingStats() { return ringStats; }

    public boolean unlocks(Item key) {
        if (key.getType() != ItemType.KEY || this.requiredKeyColor == null) {
            return false;
        }

        ItemColor keyColor = key.itemColor;

        if (this.requiredKeyColor == ItemColor.CONTAINER_TAN) {
            return keyColor == ItemColor.CONTAINER_TAN || keyColor == ItemColor.CONTAINER_ORANGE || keyColor == ItemColor.CONTAINER_BLUE;
        } else if (this.requiredKeyColor == ItemColor.CONTAINER_ORANGE) {
            return keyColor == ItemColor.CONTAINER_ORANGE || keyColor == ItemColor.CONTAINER_BLUE;
        } else if (this.requiredKeyColor == ItemColor.CONTAINER_BLUE) {
            return keyColor == ItemColor.CONTAINER_BLUE;
        }

        return false;
    }

    @Override
    public Color getColor() {
        return itemColor.getColor();
    }

    public void addModifier(ItemModifier modifier) {
        this.modifiers.add(modifier);
    }

    public List<ItemModifier> getModifiers() {
        return modifiers;
    }

    public boolean isModified() {
        return !modifiers.isEmpty();
    }

    /**
     * Gets the full display name of the item, including modifiers.
     * e.g., "Fiery Bow +1"
     * @return The formatted display name.
     */
    public String getDisplayName() {
        if (!isModified()) {
            // Just return the base name, formatted nicely
            String name = getTypeName().replace('_', ' ');
            return name.substring(0, 1).toUpperCase() + name.substring(1).toLowerCase();
        }

        StringBuilder nameBuilder = new StringBuilder();
        String prefix = null;
        String suffix = null;
        String bonus = null;

        // This logic builds the name. e.g. [Prefix] [Base Name] [Bonus] [Suffix]
        for (ItemModifier mod : modifiers) {
            if (mod.type == ModifierType.BONUS_DAMAGE || mod.type == ModifierType.BONUS_DEFENSE) {
                bonus = mod.displayName; // e.g., "+1"
            } else if (mod.displayName.startsWith("of ")) {
                suffix = mod.displayName; // e.g., "of Brawn"
            } else {
                prefix = mod.displayName; // e.g., "Fiery"
            }
        }

        if (prefix != null) {
            nameBuilder.append(prefix).append(" ");
        }

        // Add base name
        String name = getTypeName().replace('_', ' ');
        nameBuilder.append(name.substring(0, 1).toUpperCase() + name.substring(1).toLowerCase());

        if (bonus != null) {
            nameBuilder.append(" ").append(bonus);
        }

        if (suffix != null) {
            nameBuilder.append(" ").append(suffix);
        }

        return nameBuilder.toString();
    }



    public ItemColor getItemColor() {
        return itemColor;
    }
}
