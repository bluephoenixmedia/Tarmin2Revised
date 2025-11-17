package com.bpm.minotaur.gamedata.monster;

import com.bpm.minotaur.gamedata.monster.MonsterColor;

/**
 * A data class representing a "variant" of a monster, typically a color,
 * that can spawn within a specific level range.
 * This class is loaded from the "variants" list in monsters.json.
 */
public class MonsterVariant {
    public MonsterColor color;
    public int minLevel;
    public int maxLevel;
    public int weight; // Used for weighted random selection

    public MonsterVariant() {
        // Default constructor for Json parsing
    }
}
