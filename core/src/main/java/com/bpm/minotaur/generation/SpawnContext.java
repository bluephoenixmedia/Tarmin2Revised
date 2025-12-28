package com.bpm.minotaur.generation;

import com.bpm.minotaur.gamedata.Alignment;
import java.util.Set;

/**
 * Context for generation, carrying environment state and constraints.
 *
 * @param depth            Current dungeon depth (1 = top)
 * @param playerLevel      Current player experience level
 * @param isGehennom       True if currently in Gehennom (Hell)
 * @param levelAlignment   Alignment of the current level
 * @param luck             Current player Luck
 * @param spawnedUniqueIds Set of IDs of unique monsters already spawned
 * @param isContainer      True if generating items inside a container (chest,
 *                         box)
 */
public record SpawnContext(
        int depth,
        int playerLevel,
        boolean isGehennom,
        Alignment levelAlignment,
        int luck,
        Set<String> spawnedUniqueIds,
        boolean isContainer) {
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
