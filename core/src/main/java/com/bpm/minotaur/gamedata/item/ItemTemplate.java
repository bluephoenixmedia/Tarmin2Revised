package com.bpm.minotaur.gamedata.item;

import com.badlogic.gdx.graphics.Color;
import java.util.List;

public class ItemTemplate {

    public String friendlyName;
    public String texturePath;
    public String[] spriteData;
    public String description;

    // --- NEW: 3D Model Support ---
    public String modelPath;       // Path to .g3db file (e.g., "models/Sack2.g3db")
    public float modelScale = 1.0f; // Uniform scale (e.g., 0.01)
    public float modelYOffset = 0.0f; // Height adjustment if needed

    // Stats
    public int baseValue;
    public int warDamage;
    public int spiritDamage;
    public int armorDefense;

    public int accuracyModifier;

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
    public ScaleData scale;
    public boolean isPotionAppearance = false;
    public List<ItemVariant> variants;

    public ItemTemplate() { }

    public static class ScaleData {
        public float x;
        public float y;
    }

    public String unlockId;
}
