package com.bpm.minotaur.generation;

import java.util.Random;

/**
 * Decides how many mimics a level gets.
 *
 * <p>Mimics draw from their own budget rather than converting chests from the container
 * pool. Tying the rate to chest frequency would make them vanishingly rare -- REGULAR_CHEST
 * carries weight 4 against BOX(10)/MEDIUM_PACK(8)/LARGE_BAG(6), and only
 * {@code containerBudget * 0.5} containers spawn per level, so a per-chest percentage
 * yields well under half a mimic per level. A separate budget also means mimic frequency
 * can be tuned without disturbing the loot economy.
 */
public final class MimicSpawnRule {

    /**
     * Mimics only appear where chests do. REGULAR_CHEST has minLevel 8 in the container
     * spawn table; a mimic shallower than that would be impersonating something the
     * player has never seen.
     */
    public static final int MIN_LEVEL = 8;

    private MimicSpawnRule() {
    }

    /** True if this depth is deep enough for chests, and therefore for mimics. */
    public static boolean isEligible(int level) {
        return level >= MIN_LEVEL;
    }

    /**
     * Rolls the number of mimics for a level, uniformly across {@code [0, budget]}.
     *
     * <p>Rolling rather than spawning the budget outright keeps mimic-free levels
     * possible, so a player can never conclude from a chest's existence that the level's
     * mimic has already been found.
     */
    public static int rollCount(int level, int budget, Random random) {
        if (!isEligible(level) || budget <= 0) {
            return 0;
        }
        return random.nextInt(budget + 1);
    }
}
