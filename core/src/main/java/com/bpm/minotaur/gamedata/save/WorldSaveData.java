package com.bpm.minotaur.gamedata.save;

import com.bpm.minotaur.gamedata.monster.Monster;
import com.bpm.minotaur.gamedata.monster.MonsterColor;
import java.util.ArrayList;
import java.util.List;

/**
 * Serializable snapshot of global world state, time, seed, weather, and Torment modifiers.
 */
public class WorldSaveData {
    public long masterSeed = 1337L;
    /**
     * The algorithm that laid out this world (WorldConstants.WORLD_GEN_*). Saves written
     * before versions existed have no such field and keep this default: legacy.
     */
    public int worldGenVersion = com.bpm.minotaur.generation.WorldConstants.WORLD_GEN_LEGACY;
    public int currentLevel = 1;
    public int playerChunkX = 0;
    public int playerChunkY = 0;
    public long turnCount = 0;
    public float dayNightClock = com.bpm.minotaur.managers.DayNightManager.DAWN_SUNRISE;
    public String weatherType = "CLEAR";
    public String weatherIntensity = "LIGHT";
    public String gameMode = "MODERN";
    public int tormentLevel = 0;
    public List<String> activeTormentModifiers = new ArrayList<>();
    /** Choice events already placed this run, so a reload does not place them again. */
    public List<String> seenChoiceEvents = new ArrayList<>();
    public String factionMatrix;
    /** The Maze's live history as a replay (ADR 0004). Null in saves written before it existed. */
    public com.bpm.minotaur.gamedata.history.HistorySaveData history;
    /**
     * True from the moment the player dies until they awaken in the Shelter. A save written in
     * that window holds the stripped character, and loading it must finish the respawn rather
     * than resume a corpse at 0 HP.
     */
    public boolean respawnPending = false;

    public static class PendingPursuerSaveData {
        public Monster.MonsterType monsterType;
        public MonsterColor color;
        public int currentHP;
        public int currentMP;
        public String pursuitType; // GATE or LADDER
        public int originLevel;
        public int targetLevel;
        public int targetChunkX;
        public int targetChunkY;
        public int arrivalTileX;
        public int arrivalTileY;
        public int turnsRemaining;
        public int searchTurnsRemaining;
        public boolean isDescending;
        public boolean warned;
        /**
         * Preserved so a boss caught mid-chase by a save/quit comes back as the
         * boss. Rebuilding uses the template constructor, which would otherwise
         * return an ordinary monster of the same type.
         */
        public boolean bridgeBoss = false;
        public int maxHP = 0;
        public int moveSpeed = 0;
        public float scaleX = 0f;
        public float scaleY = 0f;

        public PendingPursuerSaveData() {}
    }

    public List<PendingPursuerSaveData> pendingPursuers = new ArrayList<>();

    public WorldSaveData() {
    }
}
