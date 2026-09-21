package com.bpm.minotaur.generation;

import org.junit.Test;

import java.util.Random;

import static org.junit.Assert.*;

/**
 * Mimics spawn from their own budget rather than riding the container pool.
 *
 * <p>At the container tables' own rates, REGULAR_CHEST carries weight 4 against
 * BOX(10)/MEDIUM_PACK(8)/LARGE_BAG(6) and only containerBudget x 0.5 containers spawn
 * per level, so "a percentage of chests are mimics" yields 0.11-0.40 mimics per level --
 * roughly one every nine levels early on. Since the wandering MIMIC spawn was removed,
 * that would make the creature effectively absent.
 */
public class MimicSpawnRuleTest {

    @Test
    public void testMimicsDoNotAppearBeforeChestsDo() {
        assertFalse(MimicSpawnRule.isEligible(1));
        assertFalse(MimicSpawnRule.isEligible(7));
    }

    @Test
    public void testMimicsAppearFromTheChestBandOnward() {
        assertTrue("REGULAR_CHEST has minLevel 8; the disguise must match", MimicSpawnRule.isEligible(8));
        assertTrue(MimicSpawnRule.isEligible(20));
    }

    @Test
    public void testIneligibleLevelsSpawnNoMimicsWhateverTheBudget() {
        assertEquals(0, MimicSpawnRule.rollCount(7, 5, new Random(1)));
    }

    @Test
    public void testZeroBudgetSpawnsNoMimics() {
        assertEquals(0, MimicSpawnRule.rollCount(10, 0, new Random(1)));
        assertEquals(0, MimicSpawnRule.rollCount(10, -3, new Random(1)));
    }

    @Test
    public void testCountIsRolledInclusivelyFromZeroToBudget() {
        int budget = 2;
        boolean sawZero = false;
        boolean sawMax = false;
        Random random = new Random(20260920L);

        for (int i = 0; i < 500; i++) {
            int count = MimicSpawnRule.rollCount(10, budget, random);
            assertTrue("count must never be negative", count >= 0);
            assertTrue("count must never exceed the budget", count <= budget);
            if (count == 0) sawZero = true;
            if (count == budget) sawMax = true;
        }

        assertTrue("a level with no mimic at all must be possible", sawZero);
        assertTrue("the full budget must be reachable", sawMax);
    }

    @Test
    public void testAverageLandsNearHalfTheBudget() {
        Random random = new Random(20260920L);
        int total = 0;
        int runs = 20000;
        for (int i = 0; i < runs; i++) {
            total += MimicSpawnRule.rollCount(10, 2, random);
        }
        double mean = (double) total / runs;
        assertEquals("budget 2 should average about one mimic per eligible level", 1.0, mean, 0.05);
    }
}
