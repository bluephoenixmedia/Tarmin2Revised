package com.bpm.minotaur.gamedata;

/**
 * Defines all possible modifier effects that can be applied to items.
 * This is used by combat and player systems to query for bonuses.
 */
public enum ModifierType {
    // --- Basic Stats ---
    /** +N to base damage (War & Spiritual) */
    BONUS_DAMAGE,
    /** +N to base armor (Armor & Rings) */
    BONUS_DEFENSE,
    /** +N to player's max War Strength */
    BONUS_WAR_STRENGTH,
    /** +N to player's max Spiritual Strength */
    BONUS_SPIRITUAL_STRENGTH,

    // --- Elemental/Effect Damage (Weapons) ---
    ADD_FIRE_DAMAGE,
    ADD_ICE_DAMAGE,
    ADD_POISON_DAMAGE,
    ADD_BLEED_DAMAGE,
    ADD_DARK_DAMAGE,
    ADD_LIGHT_DAMAGE,
    ADD_SORCERY_DAMAGE, // Sorcery could be a special type monsters are weak to

    // --- Elemental/Effect Resistance (Armor/Rings) ---
    RESIST_FIRE,
    RESIST_ICE,
    RESIST_POISON,
    RESIST_BLEED,
    RESIST_DISEASE,
    RESIST_DARK,
    RESIST_LIGHT,
    RESIST_SORCERY,

    // --- Material Tags (Value is 0 or 1, just for identification) ---
    MATERIAL_IRON,
    MATERIAL_STEEL,
    MATERIAL_BRONZE,
    MATERIAL_SILVER,
    MATERIAL_MITHRIL,
    MATERIAL_PLATINUM,
    MATERIAL_TITANIUM,

    // --- Bane Damage (vs. Specific Monster Families) ---
    BANE_BEAST,
    BANE_HUMANOID,
    BANE_UNDEAD,
    BANE_MYTHICAL,
    BANE_ANIMAL, // Kept separate from BEAST for things like snakes/spiders
    BANE_HUMAN,  // Kept separate from HUMANOID for... humans
}
