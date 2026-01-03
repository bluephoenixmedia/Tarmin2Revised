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

    // --- Texture Offsets ---
    public float scaleX = 1f;
    public float scaleY = 1f;
    public float offsetX = 0f;
    public float offsetY = 0f;

    public RingEffectType ringEffect; // New: For JSON Loading

    public String unlockId;
    public boolean locked; // Matches JSON "locked": true

    public String[] spriteData;
    public int baseValue;

    public String damageDice = "1d4";
    public int armorClassBonus;
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
    public boolean isGauntlets; // New Field
    public boolean isBoots; // New Field
    public boolean isLegs; // New Field
    public boolean isTorso; // New Field
    public boolean isArms; // New Field
    public boolean isCloak; // New Field
    public boolean isAmulet; // New Field

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
