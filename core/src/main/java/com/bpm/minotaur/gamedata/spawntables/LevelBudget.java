package com.bpm.minotaur.gamedata.spawntables;

/**
 * Data class for level-specific spawn budgets.
 */
public class LevelBudget {
    public int level;
    public int monsterBudget;
    public int itemBudget;
    public int containerBudget;
    public int debrisBudget; // <-- ADDED

    public LevelBudget() {}
}
