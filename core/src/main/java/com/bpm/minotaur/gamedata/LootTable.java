package com.bpm.minotaur.gamedata;

import java.util.Arrays;
import java.util.List;
import com.bpm.minotaur.gamedata.item.ItemCategory;

/**
 * A static data class containing the "recipes" for all item modifiers.
 * This table is used by the SpawnManager to determine which modifiers
 * can be rolled for a given item, at a given level.
 */
public class LootTable {

    /**
     * Defines a single potential modifier that can be applied to an item.
     */
    public static class ModInfo {
        public final ModifierType type;
        public final int minLevel; // Min dungeon level for this mod to appear (integrates with TIER system)
        public final int maxLevel; // Max dungeon level
        public final int minBonus;
        public final int maxBonus;
        public final String displayName; // e.g., "Fiery", "of Brawn", "+1"
        public final ItemCategory category; // The item category this mod can apply to

        public ModInfo(ModifierType type, int minLevel, int maxLevel, int minBonus, int maxBonus, String displayName,
                ItemCategory category) {
            this.type = type;
            this.minLevel = minLevel;
            this.maxLevel = maxLevel;
            this.minBonus = minBonus;
            this.maxBonus = maxBonus;
            this.displayName = displayName;
            this.category = category;
        }
    }

    // --- MASTER MODIFIER POOL ---
    // This is the list of all possible modifiers that can be generated.
    // We can add or balance these entries at any time.
    public static final List<ModInfo> MODIFIER_POOL = Arrays.asList(

            // --- TIER 1 (Levels 1-5) ---
            // Basic numerical bonuses
            new ModInfo(ModifierType.BONUS_DAMAGE, 1, 10, 1, 1, "+1", ItemCategory.WAR_WEAPON),
            new ModInfo(ModifierType.BONUS_DAMAGE, 1, 10, 1, 1, "+1", ItemCategory.SPIRITUAL_WEAPON),
            new ModInfo(ModifierType.BONUS_AC, 1, 10, 1, 1, "+1", ItemCategory.ARMOR),
            new ModInfo(ModifierType.BONUS_AC, 1, 10, 1, 1, "+1", ItemCategory.RING),
            new ModInfo(ModifierType.BONUS_MAX_HP, 3, 12, 1, 3, "of Brawn", ItemCategory.ARMOR),
            new ModInfo(ModifierType.BONUS_MAX_MP, 3, 12, 1, 3, "of Spirit", ItemCategory.RING),

            // Simple Elemental Resistances
            new ModInfo(ModifierType.RESIST_FIRE, 4, 12, 1, 3, "Warm", ItemCategory.ARMOR),
            new ModInfo(ModifierType.RESIST_ICE, 4, 12, 1, 3, "Insulated", ItemCategory.ARMOR),

            // Material "Tags" (value is just 1 to confirm presence)
            new ModInfo(ModifierType.MATERIAL_IRON, 1, 8, 1, 1, "Iron", ItemCategory.WAR_WEAPON),
            new ModInfo(ModifierType.MATERIAL_IRON, 1, 8, 1, 1, "Iron", ItemCategory.ARMOR),
            new ModInfo(ModifierType.MATERIAL_BRONZE, 1, 8, 1, 1, "Bronze", ItemCategory.WAR_WEAPON),
            new ModInfo(ModifierType.MATERIAL_BRONZE, 1, 8, 1, 1, "Bronze", ItemCategory.ARMOR),

            // Simple Bane
            new ModInfo(ModifierType.BANE_ANIMAL, 3, 10, 2, 4, "Hunter's", ItemCategory.WAR_WEAPON),

            // --- TIER 2 (Levels 6-12) ---
            // Upgraded numerical bonuses
            new ModInfo(ModifierType.BONUS_DAMAGE, 6, 15, 2, 3, "+2", ItemCategory.WAR_WEAPON),
            new ModInfo(ModifierType.BONUS_DAMAGE, 6, 15, 2, 3, "+2", ItemCategory.SPIRITUAL_WEAPON),
            new ModInfo(ModifierType.BONUS_AC, 6, 15, 2, 3, "+2", ItemCategory.ARMOR),
            new ModInfo(ModifierType.BONUS_AC, 6, 15, 2, 3, "+2", ItemCategory.RING),

            // Elemental Damage
            new ModInfo(ModifierType.ADD_FIRE_DAMAGE, 7, 15, 2, 5, "Fiery", ItemCategory.WAR_WEAPON),
            new ModInfo(ModifierType.ADD_ICE_DAMAGE, 7, 15, 2, 5, "Icy", ItemCategory.WAR_WEAPON),
            new ModInfo(ModifierType.ADD_POISON_DAMAGE, 8, 16, 1, 3, "Venomous", ItemCategory.WAR_WEAPON),

            // More Resistances
            new ModInfo(ModifierType.RESIST_POISON, 8, 16, 2, 4, "Antidotal", ItemCategory.RING),
            new ModInfo(ModifierType.RESIST_DISEASE, 9, 18, 2, 4, "Cleansing", ItemCategory.RING),

            // Better Materials
            new ModInfo(ModifierType.MATERIAL_STEEL, 7, 15, 1, 1, "Steel", ItemCategory.WAR_WEAPON),
            new ModInfo(ModifierType.MATERIAL_STEEL, 7, 15, 1, 1, "Steel", ItemCategory.ARMOR),
            new ModInfo(ModifierType.MATERIAL_SILVER, 9, 18, 1, 1, "Silver", ItemCategory.WAR_WEAPON),

            // More Bane Types
            new ModInfo(ModifierType.BANE_UNDEAD, 6, 15, 4, 8, "Disrupting", ItemCategory.WAR_WEAPON),
            new ModInfo(ModifierType.BANE_UNDEAD, 6, 15, 4, 8, "Disrupting", ItemCategory.SPIRITUAL_WEAPON),
            new ModInfo(ModifierType.BANE_HUMANOID, 7, 16, 3, 6, "Slaying", ItemCategory.WAR_WEAPON),

            // --- TIER 3 (Levels 13+) ---
            // Top-tier numerical bonuses
            new ModInfo(ModifierType.BONUS_DAMAGE, 13, 99, 4, 6, "+3", ItemCategory.WAR_WEAPON),
            new ModInfo(ModifierType.BONUS_DAMAGE, 13, 99, 4, 6, "+3", ItemCategory.SPIRITUAL_WEAPON),
            new ModInfo(ModifierType.BONUS_AC, 13, 99, 4, 6, "+3", ItemCategory.ARMOR),
            new ModInfo(ModifierType.BONUS_AC, 13, 99, 4, 6, "+3", ItemCategory.RING),

            // Advanced Elemental/Effect Damage
            new ModInfo(ModifierType.ADD_BLEED_DAMAGE, 13, 99, 3, 6, "Wounding", ItemCategory.WAR_WEAPON),
            new ModInfo(ModifierType.ADD_DARK_DAMAGE, 15, 99, 4, 8, "Void", ItemCategory.SPIRITUAL_WEAPON),
            new ModInfo(ModifierType.ADD_LIGHT_DAMAGE, 15, 99, 4, 8, "Holy", ItemCategory.SPIRITUAL_WEAPON),

            // Advanced Resistances
            new ModInfo(ModifierType.RESIST_DARK, 15, 99, 5, 10, "Hallowed", ItemCategory.ARMOR),
            new ModInfo(ModifierType.RESIST_LIGHT, 15, 99, 5, 10, "Shadow", ItemCategory.ARMOR),

            // Top-tier Materials
            new ModInfo(ModifierType.MATERIAL_MITHRIL, 14, 99, 1, 1, "Mithril", ItemCategory.WAR_WEAPON),
            new ModInfo(ModifierType.MATERIAL_MITHRIL, 14, 99, 1, 1, "Mithril", ItemCategory.ARMOR),
            new ModInfo(ModifierType.MATERIAL_TITANIUM, 16, 99, 1, 1, "Titanium", ItemCategory.WAR_WEAPON),
            new ModInfo(ModifierType.MATERIAL_PLATINUM, 18, 99, 1, 1, "Platinum", ItemCategory.WAR_WEAPON),

            // Top-tier Bane
            new ModInfo(ModifierType.BANE_MYTHICAL, 15, 99, 8, 15, "Godslayer", ItemCategory.WAR_WEAPON),

            // --- PRIMARY ATTRIBUTE BONUSES ---
            // Tier 1: +2 to stat (= +1 modifier)
            new ModInfo(ModifierType.BONUS_STRENGTH,     1, 10, 2, 2, "Strong",      ItemCategory.ARMOR),
            new ModInfo(ModifierType.BONUS_DEXTERITY,    1, 10, 2, 2, "Nimble",      ItemCategory.ARMOR),
            new ModInfo(ModifierType.BONUS_CONSTITUTION, 1, 10, 2, 2, "Hardy",       ItemCategory.ARMOR),
            new ModInfo(ModifierType.BONUS_INTELLIGENCE, 1, 10, 2, 2, "Brilliant",   ItemCategory.RING),
            new ModInfo(ModifierType.BONUS_WISDOM,       1, 10, 2, 2, "Sage",        ItemCategory.RING),
            new ModInfo(ModifierType.BONUS_AGILITY,      1, 10, 2, 2, "Swift",       ItemCategory.ARMOR),
            new ModInfo(ModifierType.BONUS_LUCK,         1, 99, 1, 1, "Lucky",       ItemCategory.RING),
            // Tier 2: +4 to stat (= +2 modifier)
            new ModInfo(ModifierType.BONUS_STRENGTH,     6, 15, 4, 4, "of Giants",    ItemCategory.ARMOR),
            new ModInfo(ModifierType.BONUS_DEXTERITY,    6, 15, 4, 4, "of Precision", ItemCategory.ARMOR),
            new ModInfo(ModifierType.BONUS_CONSTITUTION, 6, 15, 4, 4, "of the Ox",    ItemCategory.ARMOR),
            new ModInfo(ModifierType.BONUS_INTELLIGENCE, 6, 15, 4, 4, "of Insight",   ItemCategory.RING),
            new ModInfo(ModifierType.BONUS_WISDOM,       6, 15, 4, 4, "of the Seer",  ItemCategory.RING),
            new ModInfo(ModifierType.BONUS_AGILITY,      6, 15, 4, 4, "of Shadows",   ItemCategory.ARMOR),
            new ModInfo(ModifierType.BONUS_LUCK,         6, 15, 2, 2, "Charmed",      ItemCategory.RING),
            // Tier 3: +6 to stat (= +3 modifier)
            new ModInfo(ModifierType.BONUS_STRENGTH,     13, 99, 6, 6, "of Titans",    ItemCategory.ARMOR),
            new ModInfo(ModifierType.BONUS_DEXTERITY,    13, 99, 6, 6, "of the Blade", ItemCategory.ARMOR),
            new ModInfo(ModifierType.BONUS_CONSTITUTION, 13, 99, 6, 6, "of Endurance", ItemCategory.ARMOR),
            new ModInfo(ModifierType.BONUS_INTELLIGENCE, 13, 99, 6, 6, "of Mastery",   ItemCategory.RING),
            new ModInfo(ModifierType.BONUS_WISDOM,       13, 99, 6, 6, "of the Oracle",ItemCategory.RING),
            new ModInfo(ModifierType.BONUS_AGILITY,      13, 99, 6, 6, "of Swiftness", ItemCategory.ARMOR),
            new ModInfo(ModifierType.BONUS_LUCK,         13, 99, 3, 3, "Blessed",      ItemCategory.RING),

            // --- ABSORB (flat damage mitigation on physical hits) ---
            new ModInfo(ModifierType.BONUS_ABSORB,  3, 10, 1, 1, "Iron",   ItemCategory.ARMOR),
            new ModInfo(ModifierType.BONUS_ABSORB,  8, 15, 2, 2, "Steel",  ItemCategory.ARMOR),
            new ModInfo(ModifierType.BONUS_ABSORB, 14, 99, 3, 3, "Titan",  ItemCategory.ARMOR),

            // --- CRIT CHANCE (integer percentage points added to crit roll) ---
            new ModInfo(ModifierType.BONUS_CRIT_CHANCE,  6, 14, 3, 3, "Precise", ItemCategory.WAR_WEAPON),
            new ModInfo(ModifierType.BONUS_CRIT_CHANCE, 13, 99, 5, 5, "Lethal",  ItemCategory.WAR_WEAPON),

            // --- CRIT MULTIPLIER (tenths: 2 = +0.2x, 5 = +0.5x) ---
            new ModInfo(ModifierType.BONUS_CRIT_MULTIPLIER,  8, 15, 2, 2, "Keen",   ItemCategory.WAR_WEAPON),
            new ModInfo(ModifierType.BONUS_CRIT_MULTIPLIER, 14, 99, 5, 5, "Savage", ItemCategory.WAR_WEAPON),

            // --- DODGE (integer percentage points added to dodge roll) ---
            new ModInfo(ModifierType.BONUS_DODGE,  5, 13, 3, 3, "Evasive", ItemCategory.ARMOR),
            new ModInfo(ModifierType.BONUS_DODGE, 12, 99, 5, 5, "Ghostly", ItemCategory.ARMOR),

            // --- SPELL POWER (flat addition to offensive spell damage) ---
            new ModInfo(ModifierType.BONUS_SPELL_POWER,  1, 10, 1, 1, "Arcane",  ItemCategory.SPIRITUAL_WEAPON),
            new ModInfo(ModifierType.BONUS_SPELL_POWER,  1, 10, 1, 1, "Arcane",  ItemCategory.RING),
            new ModInfo(ModifierType.BONUS_SPELL_POWER,  6, 15, 2, 2, "Potent",  ItemCategory.SPIRITUAL_WEAPON),
            new ModInfo(ModifierType.BONUS_SPELL_POWER,  6, 15, 2, 2, "Potent",  ItemCategory.RING),
            new ModInfo(ModifierType.BONUS_SPELL_POWER, 13, 99, 3, 3, "Supreme", ItemCategory.SPIRITUAL_WEAPON),
            new ModInfo(ModifierType.BONUS_SPELL_POWER, 13, 99, 3, 3, "Supreme", ItemCategory.RING),

            // --- STAMINA (extra dice selectable per combat round) ---
            new ModInfo(ModifierType.BONUS_STAMINA,  5, 14, 1, 1, "Vigorous", ItemCategory.ARMOR),
            new ModInfo(ModifierType.BONUS_STAMINA, 13, 99, 2, 2, "Tireless", ItemCategory.ARMOR),

            // --- TOXICITY THRESHOLD (shifts poison tier boundaries upward) ---
            new ModInfo(ModifierType.BONUS_TOXICITY_THRESHOLD,  6, 14, 10, 10, "Alchemical", ItemCategory.ARMOR),
            new ModInfo(ModifierType.BONUS_TOXICITY_THRESHOLD,  6, 14, 10, 10, "Alchemical", ItemCategory.RING),
            new ModInfo(ModifierType.BONUS_TOXICITY_THRESHOLD, 13, 99, 20, 20, "Venomwoven", ItemCategory.ARMOR),
            new ModInfo(ModifierType.BONUS_TOXICITY_THRESHOLD, 13, 99, 20, 20, "Venomwoven", ItemCategory.RING));
}
