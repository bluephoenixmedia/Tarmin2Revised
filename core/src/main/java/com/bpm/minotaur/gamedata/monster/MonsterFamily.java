package com.bpm.minotaur.gamedata.monster;

/**
 * Classifies monsters into broad "families" for the
 * purpose of "Bane" type weapon modifiers.
 */
public enum MonsterFamily {
    BEAST,          // e.g., Giant Ant, Giant Scorpion, Giant Snake
    HUMANOID,       // e.g., Dwarf, Giant, Orc
    UNDEAD,         // e.g., Ghoul, Skeleton, Cloaked Skeleton, Wraith
    MYTHICAL,
    MAGICAL,// e.g., Dragon, Minotaur
    NONE            // Default for any unclassified monster
}
