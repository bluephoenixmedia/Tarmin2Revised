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
    public boolean isFood;
    public boolean isTreasure;
    public boolean isKey;
    public boolean isUsable;
    public boolean isContainer;
    public boolean isRing;

    // --- Collision Logic ---
    public boolean isImpassable = false; // New Flag

    public Vector2Wrapper scale;

    public List<ItemVariant> variants;

    public static class Vector2Wrapper {
        public float x;
        public float y;
    }
}
