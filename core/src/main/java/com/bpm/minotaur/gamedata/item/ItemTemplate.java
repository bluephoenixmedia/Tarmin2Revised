package com.bpm.minotaur.gamedata.item;

import com.badlogic.gdx.graphics.Color;

import java.util.List;

// Note: Fields are public for easy parsing by libGDX Json.
// This class is just a data container.
public class ItemTemplate {

    public String friendlyName;
    public String texturePath;
    public String[] spriteData;
    public String description; // <-- ADD THIS LINE

    // Stats
    public int baseValue;
    public int warDamage;
    public int spiritDamage;
    public int armorDefense;

    // Booleans for type
    public boolean isWeapon;
    public boolean isRanged;
    public boolean isArmor;
    public boolean isPotion;
    public boolean isFood;
    public boolean isTreasure;
    public boolean isKey;
    public boolean isUsable;
    public boolean isContainer;
    public boolean isRing;
    public int range;
    public ScaleData scale; // <-- ADD THIS FIELD
    public boolean isPotionAppearance = false; // <-- ADD THIS LINE
    public List<ItemVariant> variants; // <-- ADD THIS LINE

    // A no-argument constructor is required for the Json parser
    public ItemTemplate() { }

    public static class ScaleData {
        public float x;
        public float y;
    }
}
