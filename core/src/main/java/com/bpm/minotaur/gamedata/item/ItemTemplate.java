package com.bpm.minotaur.gamedata.item;

import java.util.List;

public class ItemTemplate {
    public String friendlyName;
    public String description;
    public String texturePath;

    // --- 3D Model Properties ---
    public String modelPath;
    public float modelScale = 1.0f;
    public float modelYOffset = 0.0f;
    public float modelRotation = 0.0f;

    public String unlockId;

    public String[] spriteData;
    public int baseValue;

    public int warDamage;
    public int spiritDamage;
    public int armorDefense;
    public int accuracyModifier;

    public int range = 1;

    public boolean isWeapon;
    public boolean isRanged;
    public boolean isArmor;
    public boolean isPotion;
    public boolean isPotionAppearance;
    public boolean isScrollAppearance; // New
    public boolean isWandAppearance; // New
    public boolean isRingAppearance; // New
    public boolean isFood;
    public boolean isTreasure;
    public boolean isKey;
    public boolean isUsable;
    public boolean isContainer;
    public boolean isRing;
    public boolean isShield; // New Field
    public boolean isHelmet; // New Field

    // --- Refactored Stats ---
    public String material = "wood"; // wood, iron, leather, etc.
    public int baseCost = 0;
    public float weight = 1.0f;
    public int nutrition = 0;
    public int probability = 0; // Relative spawn weight

    // --- Collision Logic ---
    public boolean isImpassable = false; // New Flag

    public Vector2Wrapper scale;

    // --- Bone & Die System ---
    public List<com.bpm.minotaur.gamedata.dice.DieDefinition> grantedDice;

    public List<ItemVariant> variants;

    public static class Vector2Wrapper {
        public float x;
        public float y;
    }
}
