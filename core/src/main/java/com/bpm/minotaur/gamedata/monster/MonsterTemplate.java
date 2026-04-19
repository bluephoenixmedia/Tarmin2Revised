package com.bpm.minotaur.gamedata.monster;

import com.badlogic.gdx.utils.Array;
import com.bpm.minotaur.gamedata.Alignment; // New Import
import com.bpm.minotaur.gamedata.DamageType;
import com.bpm.minotaur.gamedata.effects.EffectApplicationData;
import com.bpm.minotaur.gamedata.effects.StatusEffectType;

import java.util.List;

// Note: Fields are public for easy parsing by libGDX Json.
// This class is just a data container.
public class MonsterTemplate {

    // --- Texture Offsets ---
    public float offsetX = 0.0f;
    public float offsetY = 0.0f;

    // This nested static class must also have public fields or a no-arg constructor
    public static class ScaleData {
        public float x;
        public float y;
    }

    // --- Refactored Stats ---
    public int baseLevel = 1; // 1-30+
    public int frequency = 0; // 0-7, weighted probability
    public int baseAC = 10; // Armor Class, descending scale (lower is better, 10 is base)
    public int magicResistance = 0; // 0-100%
    public int moveSpeed = 12; // Base speed (player usually 12)

    public Alignment alignment = Alignment.NEUTRAL; // Default

    // Flags (Bitmasks can be used, but for JSON ease, we might use lists of strings
    // or boolean flags)
    // For now, let's keep it simple with fields, but the plan mentioned bitmasks.
    // Let's use simple integers for flags if we want to stick to the C-style, or
    // specific booleans.
    // The design doc mentioned bitmasks. Let's provide an int for raw flags, but
    // maybe helper booleans?
    // Generation Flags
    public static final int G_UNIQ = 1; // Unique
    public static final int G_NOHELL = 2; // Not in Hell
    public static final int G_HELL = 4; // Only in Hell
    public static final int G_GENO = 8; // Geno'able
    public static final int G_LGROUP = 16; // Small Group
    public static final int G_SGROUP = 32; // Large Group
    public static final int G_NOGEN = 64; // Do not generate randomly

    public int generationFlags = 0; // G_GENO, G_HELL, etc.
    public int behaviorFlags = 0; // M1_FLY, M2_NASTY, etc.

    // Attacks
    public List<AttackDefinition> attacks;

    // ------------------------

    public int maxHP;
    public int maxMP; // New
    public int armorClass = 10; // Descending AC logic: Lower is good? Or Ascending? User said AD&D. Usually
                                // Ascending in modern checks, or THAC0.
    // Plan said: "Ascending AC. Unarmored = 10. Higher is better."

    public int baseExperience;
    public MonsterFamily family; // libGDX Json automatically converts "BEAST" string to MonsterFamily.BEAST
    public String texturePath;
    public String[] spriteData;
    public ScaleData scale;
    public String damageDice = "1d6";
    public DamageType damageType = DamageType.PHYSICAL;
    public int dexterity; // For Hit Chance calculation
    public boolean hasRangedAttack; // Can this monster shoot back?
    public int attackRange; // How far can they shoot?

    public int intelligence; // This will be loaded from JSON, defaulting to 0

    // --- AI & Combat Behavior ---
    public enum AiType {
        AGGRESSIVE, // Blindly attacks (Low INT)
        TACTICAL, // Uses items/spells if advantageous (Mid INT)
        CAUTIOUS, // Flees if low HP (High INT)
        HEALER // Prioritizes healing self/allies (Specific)
    }

    public AiType aiType = AiType.AGGRESSIVE; // Default
    public float healThreshold = 0.3f; // Heal if HP < 30%
    public int spellChance = 20; // 20% chance to cast spell if capable

    public Array<EffectApplicationData> onHitEffects;

    // --- Corpse Intrinsics ---
    public StatusEffectType corpseEffect;
    public int corpseEffectChance = 100;

    public List<MonsterVariant> variants;

    // --- Directional / Animated Sprites ---
    public static class DirectionTextures {
        public String north;
        public String east;
        public String west;
        public String[] southFrames; // Multiple frames = animated south
        public float frameDuration = 0.15f;
    }

    public DirectionTextures directionTextures;

    // A no-argument constructor is required for the Json parser
    public MonsterTemplate() {
    }
}
