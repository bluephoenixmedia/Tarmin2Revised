package com.bpm.minotaur.gamedata.monster;

import com.bpm.minotaur.gamedata.item.Item;
import org.junit.Test;

import java.util.Random;

import static org.junit.Assert.*;

/**
 * The codebase's first perception check. Wisdom is the stat -- ShelterAltar already
 * describes it as "Spiritual energy, divine boons, and secret perception", and it has
 * an ascension tree where Luck does not.
 */
public class MimicDetectionTest {

    @Test
    public void testBaseWisdomGivesTheDesignedChance() {
        assertEquals(25, MimicDetection.chancePercent(10));
    }

    @Test
    public void testChanceScalesFivePercentPerPointOfWisdom() {
        assertEquals(50, MimicDetection.chancePercent(15));
        assertEquals(75, MimicDetection.chancePercent(20));
        assertEquals(15, MimicDetection.chancePercent(8));
    }

    @Test
    public void testChanceIsNeverImpossibleOrCertain() {
        assertEquals("a dullard still gets a sliver of a chance", 5, MimicDetection.chancePercent(0));
        assertEquals(5, MimicDetection.chancePercent(-20));
        assertEquals("no amount of Wisdom disarms the trap entirely", 95, MimicDetection.chancePercent(30));
        assertEquals(95, MimicDetection.chancePercent(100));
    }

    @Test
    public void testAttemptIgnoresOrdinaryChests() {
        Item chest = new Item();
        assertFalse(MimicDetection.attempt(chest, 100, alwaysSucceeds()));
        assertFalse(chest.isMimicSeen());
    }

    @Test
    public void testAttemptIgnoresNull() {
        assertFalse(MimicDetection.attempt(null, 100, alwaysSucceeds()));
    }

    @Test
    public void testSuccessfulAttemptMarksTheMimicSeen() {
        Item mimic = mimicChest();
        assertTrue(MimicDetection.attempt(mimic, 10, alwaysSucceeds()));
        assertTrue(mimic.isMimicSeen());
    }

    @Test
    public void testFailedAttemptLeavesTheDisguiseIntact() {
        Item mimic = mimicChest();
        assertFalse(MimicDetection.attempt(mimic, 10, alwaysFails()));
        assertFalse(mimic.isMimicSeen());
    }

    @Test
    public void testAttemptIsRolledOnlyOncePerMimic() {
        Item mimic = mimicChest();
        assertTrue("first look through the disguise", MimicDetection.attempt(mimic, 10, alwaysSucceeds()));
        assertFalse("already seen, so no second detection event",
                MimicDetection.attempt(mimic, 10, alwaysSucceeds()));
        assertTrue(mimic.isMimicSeen());
    }

    @Test
    public void testAnAlreadyFailedMimicIsNotRerolled() {
        Item mimic = mimicChest();
        MimicDetection.attempt(mimic, 10, alwaysFails());
        assertTrue("the roll is spent", mimic.isMimicRollSpent());

        assertFalse("re-entering the chunk must not buy a second roll",
                MimicDetection.attempt(mimic, 10, alwaysSucceeds()));
        assertFalse(mimic.isMimicSeen());
    }

    private static Item mimicChest() {
        Item item = new Item();
        item.setMimic(true);
        return item;
    }

    /** nextInt(100) == 0 always clears any chance above zero. */
    private static Random alwaysSucceeds() {
        return new Random() {
            @Override
            public int nextInt(int bound) {
                return 0;
            }
        };
    }

    /** nextInt(100) == 99 always fails any chance at or below 95. */
    private static Random alwaysFails() {
        return new Random() {
            @Override
            public int nextInt(int bound) {
                return bound - 1;
            }
        };
    }
}
