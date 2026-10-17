package com.bpm.minotaur.gamedata.monster;

import com.bpm.minotaur.gamedata.DamageType;

import java.util.List;

/**
 * A data class representing a "variant" of a monster, typically a color,
 * that can spawn within a specific level range.
 * This class is loaded from the "variants" list in monsters.json.
 *
 * <p>A variant is a colour and a spawn window, and it may also carry its own look and element:
 * an optional sprite, ranged projectile, ranged damage type and spell list. A species with
 * colourways (a Specter that burns, or freezes) uses these so each reads as what it is. Anything
 * left out falls back to the species template. A species must give each of its variants a
 * different colour, because the colour is how a spawned monster finds its variant again.
 */
public class MonsterVariant {
    public MonsterColor color;
    public int minLevel;
    public int maxLevel;
    public int weight; // Used for weighted random selection

    /** Optional: this variant's own sprite. */
    public String texturePath;
    /** Optional: this variant's ranged projectile (ARROW, FIREBALL, ...). */
    public String rangedProjectile;
    /** Optional: this variant's ranged damage type. */
    public DamageType rangedDamageType;
    /** Optional: this variant's innate spells, replacing the species' own. */
    public List<String> innateSpells;

    public MonsterVariant() {
        // Default constructor for Json parsing
    }

    /** The variant that goes with a colour, or null if the species has none of that colour. */
    public static MonsterVariant forColor(List<MonsterVariant> variants, MonsterColor color) {
        if (variants == null || color == null) {
            return null;
        }
        for (MonsterVariant v : variants) {
            if (v != null && v.color == color) {
                return v;
            }
        }
        return null;
    }

    /** The sprite for a colourway: the variant's own if it has one, otherwise the species' sprite. */
    public static String textureFor(List<MonsterVariant> variants, MonsterColor color, String speciesTexture) {
        MonsterVariant v = forColor(variants, color);
        return (v != null && v.texturePath != null && !v.texturePath.isEmpty()) ? v.texturePath : speciesTexture;
    }
}
