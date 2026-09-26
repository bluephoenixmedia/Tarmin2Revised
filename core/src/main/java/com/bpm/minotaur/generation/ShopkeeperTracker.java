package com.bpm.minotaur.generation;

import java.util.HashSet;
import java.util.Set;

/**
 * Keeps the travelling merchant to one per dungeon level.
 *
 * <p>Merchants used to spawn on every eligible chunk with no roll at all. On a
 * nine-chunk maze that was tolerable; at the designed radius the maze is 441
 * chunks, so the player met a merchant almost everywhere and the encounter
 * stopped being an event.
 *
 * <p>Deliberately session state rather than save state: a merchant is a roaming
 * NPC, so re-meeting one after reloading is not a continuity problem, and
 * persisting it would mean a save that can never see a merchant again.
 */
public final class ShopkeeperTracker {

    private static final Set<Integer> levelsWithMerchant = new HashSet<>();

    private ShopkeeperTracker() {
    }

    /**
     * Claims the merchant slot for a level.
     *
     * @return true if this level had no merchant yet, meaning the caller may
     *         spawn one; false if the slot is already taken.
     */
    public static boolean claimLevel(int level) {
        return levelsWithMerchant.add(level);
    }

    /** True when this level already has its merchant. */
    public static boolean hasClaimed(int level) {
        return levelsWithMerchant.contains(level);
    }

    /** Clears every claim. Called when a run ends. */
    public static void reset() {
        levelsWithMerchant.clear();
    }

}
