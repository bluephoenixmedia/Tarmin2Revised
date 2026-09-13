package com.bpm.minotaur.generation;

import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.gamedata.Alignment;
import java.util.Set;

/**
 * Context for generation, carrying environment state, difficulty constraints, and spatial location.
 *
 * @param depth            Current dungeon depth (1 = top)
 * @param playerLevel      Current player experience level
 * @param isGehennom       True if currently in Gehennom (Hell)
 * @param levelAlignment   Alignment of the current level
 * @param luck             Current player Luck
 * @param spawnedUniqueIds Set of IDs of unique monsters already spawned
 * @param isContainer      True if generating items inside a container (chest, box)
 * @param edl              Effective Difficulty Level (NetHack scaled)
 * @param chunkId          Current chunk coordinates (X, Y)
 * @param doomStage        Current Doom Clock escalation stage (1..4)
 */
public record SpawnContext(
        int depth,
        int playerLevel,
        boolean isGehennom,
        Alignment levelAlignment,
        int luck,
        Set<String> spawnedUniqueIds,
        boolean isContainer,
        int edl,
        GridPoint2 chunkId,
        int doomStage) {

    /**
     * Backwards-compatible 7-arg constructor defaulting edl, chunkId, and doomStage.
     */
    public SpawnContext(
            int depth,
            int playerLevel,
            boolean isGehennom,
            Alignment levelAlignment,
            int luck,
            Set<String> spawnedUniqueIds,
            boolean isContainer) {
        this(depth, playerLevel, isGehennom, levelAlignment, luck, spawnedUniqueIds, isContainer,
                Math.max(1, (depth + playerLevel) / 2), new GridPoint2(0, 0), 1);
    }

    public boolean isSubterranean() {
        return depth >= 2;
    }

    public boolean isSurface() {
        return depth <= 1;
    }

    /**
     * Checks if a unique monster has already been generated.
     * 
     * @param id Monster Archetype ID (or internal ID)
     * @return true if already spawned
     */
    public boolean isUniqueSpawned(String id) {
        return spawnedUniqueIds != null && spawnedUniqueIds.contains(id);
    }
}
