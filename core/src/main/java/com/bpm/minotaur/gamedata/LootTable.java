package com.bpm.minotaur.gamedata;

import java.util.Arrays;
import java.util.List;

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
        public final int minLevel;    // Min dungeon level for this mod to appear (integrates with TIER system)
        public final int maxLevel;    // Max dungeon level
        public final int minBonus;
        public final int maxBonus;
        public final String displayName; // e.g., "Fiery", "of Brawn", "+1"
        public final Item.ItemCategory category; // The item category this mod can apply to

        public ModInfo(ModifierType type, int minLevel, int maxLevel, int minBonus, int maxBonus, String displayName, Item.ItemCategory category) {
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
        new ModInfo(ModifierType.BONUS_DAMAGE, 1, 10, 1, 1, "+1", Item.ItemCategory.WAR_WEAPON),
        new ModInfo(ModifierType.BONUS_DAMAGE, 1, 10, 1, 1, "+1", Item.ItemCategory.SPIRITUAL_WEAPON),
        new ModInfo(ModifierType.BONUS_DEFENSE, 1, 10, 1, 1, "+1", Item.ItemCategory.ARMOR),
        new ModInfo(ModifierType.BONUS_DEFENSE, 1, 10, 1, 1, "+1", Item.ItemCategory.RING),
        new ModInfo(ModifierType.BONUS_WAR_STRENGTH, 3, 12, 1, 3, "of Brawn", Item.ItemCategory.ARMOR),
        new ModInfo(ModifierType.BONUS_SPIRITUAL_STRENGTH, 3, 12, 1, 3, "of Spirit", Item.ItemCategory.RING),

        // Simple Elemental Resistances
        new ModInfo(ModifierType.RESIST_FIRE, 4, 12, 1, 3, "Warm", Item.ItemCategory.ARMOR),
        new ModInfo(ModifierType.RESIST_ICE, 4, 12, 1, 3, "Insulated", Item.ItemCategory.ARMOR),

        // Material "Tags" (value is just 1 to confirm presence)
        new ModInfo(ModifierType.MATERIAL_IRON, 1, 8, 1, 1, "Iron", Item.ItemCategory.WAR_WEAPON),
        new ModInfo(ModifierType.MATERIAL_IRON, 1, 8, 1, 1, "Iron", Item.ItemCategory.ARMOR),
        new ModInfo(ModifierType.MATERIAL_BRONZE, 1, 8, 1, 1, "Bronze", Item.ItemCategory.WAR_WEAPON),
        new ModInfo(ModifierType.MATERIAL_BRONZE, 1, 8, 1, 1, "Bronze", Item.ItemCategory.ARMOR),

        // Simple Bane
        new ModInfo(ModifierType.BANE_ANIMAL, 3, 10, 2, 4, "Hunter's", Item.ItemCategory.WAR_WEAPON),


        // --- TIER 2 (Levels 6-12) ---
        // Upgraded numerical bonuses
        new ModInfo(ModifierType.BONUS_DAMAGE, 6, 15, 2, 3, "+2", Item.ItemCategory.WAR_WEAPON),
        new ModInfo(ModifierType.BONUS_DAMAGE, 6, 15, 2, 3, "+2", Item.ItemCategory.SPIRITUAL_WEAPON),
        new ModInfo(ModifierType.BONUS_DEFENSE, 6, 15, 2, 3, "+2", Item.ItemCategory.ARMOR),
        new ModInfo(ModifierType.BONUS_DEFENSE, 6, 15, 2, 3, "+2", Item.ItemCategory.RING),

        // Elemental Damage
        new ModInfo(ModifierType.ADD_FIRE_DAMAGE, 7, 15, 2, 5, "Fiery", Item.ItemCategory.WAR_WEAPON),
        new ModInfo(ModifierType.ADD_ICE_DAMAGE, 7, 15, 2, 5, "Icy", Item.ItemCategory.WAR_WEAPON),
        new ModInfo(ModifierType.ADD_POISON_DAMAGE, 8, 16, 1, 3, "Venomous", Item.ItemCategory.WAR_WEAPON),

        // More Resistances
        new ModInfo(ModifierType.RESIST_POISON, 8, 16, 2, 4, "Antidotal", Item.ItemCategory.RING),
        new ModInfo(ModifierType.RESIST_DISEASE, 9, 18, 2, 4, "Cleansing", Item.ItemCategory.RING),

        // Better Materials
        new ModInfo(ModifierType.MATERIAL_STEEL, 7, 15, 1, 1, "Steel", Item.ItemCategory.WAR_WEAPON),
        new ModInfo(ModifierType.MATERIAL_STEEL, 7, 15, 1, 1, "Steel", Item.ItemCategory.ARMOR),
        new ModInfo(ModifierType.MATERIAL_SILVER, 9, 18, 1, 1, "Silver", Item.ItemCategory.WAR_WEAPON),

        // More Bane Types
        new ModInfo(ModifierType.BANE_UNDEAD, 6, 15, 4, 8, "Disrupting", Item.ItemCategory.WAR_WEAPON),
        new ModInfo(ModifierType.BANE_UNDEAD, 6, 15, 4, 8, "Disrupting", Item.ItemCategory.SPIRITUAL_WEAPON),
        new ModInfo(ModifierType.BANE_HUMANOID, 7, 16, 3, 6, "Slaying", Item.ItemCategory.WAR_WEAPON),


        // --- TIER 3 (Levels 13+) ---
        // Top-tier numerical bonuses
        new ModInfo(ModifierType.BONUS_DAMAGE, 13, 99, 4, 6, "+3", Item.ItemCategory.WAR_WEAPON),
        new ModInfo(ModifierType.BONUS_DAMAGE, 13, 99, 4, 6, "+3", Item.ItemCategory.SPIRITUAL_WEAPON),
        new ModInfo(ModifierType.BONUS_DEFENSE, 13, 99, 4, 6, "+3", Item.ItemCategory.ARMOR),
        new ModInfo(ModifierType.BONUS_DEFENSE, 13, 99, 4, 6, "+3", Item.ItemCategory.RING),

        // Advanced Elemental/Effect Damage
        new ModInfo(ModifierType.ADD_BLEED_DAMAGE, 13, 99, 3, 6, "Wounding", Item.ItemCategory.WAR_WEAPON),
        new ModInfo(ModifierType.ADD_DARK_DAMAGE, 15, 99, 4, 8, "Void", Item.ItemCategory.SPIRITUAL_WEAPON),
        new ModInfo(ModifierType.ADD_LIGHT_DAMAGE, 15, 99, 4, 8, "Holy", Item.ItemCategory.SPIRITUAL_WEAPON),

        // Advanced Resistances
        new ModInfo(ModifierType.RESIST_DARK, 15, 99, 5, 10, "Hallowed", Item.ItemCategory.ARMOR),
        new ModInfo(ModifierType.RESIST_LIGHT, 15, 99, 5, 10, "Shadow", Item.ItemCategory.ARMOR),

        // Top-tier Materials
        new ModInfo(ModifierType.MATERIAL_MITHRIL, 14, 99, 1, 1, "Mithril", Item.ItemCategory.WAR_WEAPON),
        new ModInfo(ModifierType.MATERIAL_MITHRIL, 14, 99, 1, 1, "Mithril", Item.ItemCategory.ARMOR),
        new ModInfo(ModifierType.MATERIAL_TITANIUM, 16, 99, 1, 1, "Titanium", Item.ItemCategory.WAR_WEAPON),
        new ModInfo(ModifierType.MATERIAL_PLATINUM, 18, 99, 1, 1, "Platinum", Item.ItemCategory.WAR_WEAPON),

        // Top-tier Bane
        new ModInfo(ModifierType.BANE_MYTHICAL, 15, 99, 8, 15, "Godslayer", Item.ItemCategory.WAR_WEAPON)
    );
}
