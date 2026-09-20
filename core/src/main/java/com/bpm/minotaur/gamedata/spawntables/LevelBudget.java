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
    /**
     * Upper bound on disguised mimics for this level; the actual count is rolled in
     * [0, mimicBudget]. Deliberately separate from containerBudget so mimic frequency
     * can be tuned without moving the loot economy. See MimicSpawnRule.
     */
    public int mimicBudget;

    public LevelBudget() {}
}
