package com.bpm.minotaur.gamedata.save;

import java.util.ArrayList;
import java.util.List;

/**
 * Serializable snapshot of global world state, time, seed, weather, and Torment modifiers.
 */
public class WorldSaveData {
    public long masterSeed = 1337L;
    public int currentLevel = 1;
    public int playerChunkX = 0;
    public int playerChunkY = 0;
    public long turnCount = 0;
    public float dayNightClock = 0f;
    public String weatherType = "CLEAR";
    public String weatherIntensity = "LIGHT";
    public String gameMode = "MODERN";
    public int tormentLevel = 0;
    public List<String> activeTormentModifiers = new ArrayList<>();

    public WorldSaveData() {
    }
}
