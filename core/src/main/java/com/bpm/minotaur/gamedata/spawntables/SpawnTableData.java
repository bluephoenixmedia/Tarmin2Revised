package com.bpm.minotaur.gamedata.spawntables;

import com.badlogic.gdx.utils.Array; // <-- ADD THIS IMPORT
import com.badlogic.gdx.utils.ObjectMap;
// import java.util.List; // <-- REMOVE THIS IMPORT

/**
 * Root class for parsing spawntables.json
 */
public class SpawnTableData {

    public Array<LevelBudget> levelBudgets;
    public LevelBudget defaultBudget;
    public Array<SpawnTableEntry> monsterSpawnTable;
    public Array<SpawnTableEntry> itemSpawnTable;
    public Array<SpawnTableEntry> containerSpawnTable;
    public Array<SpawnTableEntry> debrisSpawnTable; // <-- ADDED
    public ObjectMap<String, Array<SpawnTableEntry>> containerLoot;

}
