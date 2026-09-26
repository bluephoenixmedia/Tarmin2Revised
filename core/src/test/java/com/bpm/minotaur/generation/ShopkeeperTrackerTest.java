package com.bpm.minotaur.generation;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * The merchant is one per level, not one per chunk.
 *
 * <p>It used to spawn on every eligible level 1-2 chunk with no roll at all,
 * which on a 441-chunk maze meant meeting one almost everywhere.
 */
public class ShopkeeperTrackerTest {

    @Before
    public void setUp() {
        ShopkeeperTracker.reset();
    }

    @Test
    public void onlyTheFirstChunkOnALevelClaimsTheMerchant() {
        assertTrue("The first chunk generated on a level gets the merchant",
                ShopkeeperTracker.claimLevel(1));

        for (int chunk = 0; chunk < 20; chunk++) {
            assertFalse("Every later chunk on that level must be refused",
                    ShopkeeperTracker.claimLevel(1));
        }
    }

    @Test
    public void eachLevelGetsItsOwnMerchant() {
        assertTrue(ShopkeeperTracker.claimLevel(1));
        assertTrue("A different level has its own slot", ShopkeeperTracker.claimLevel(2));
        assertFalse(ShopkeeperTracker.claimLevel(1));
        assertFalse(ShopkeeperTracker.claimLevel(2));
    }

    @Test
    public void aResetLetsANewRunSeeMerchantsAgain() {
        // Without a reset on every run-start path, a second game in the same
        // session would inherit the first run's claims and never spawn one.
        ShopkeeperTracker.claimLevel(1);
        ShopkeeperTracker.claimLevel(2);

        ShopkeeperTracker.reset();

        assertTrue("A new run must be able to claim level 1 again",
                ShopkeeperTracker.claimLevel(1));
        assertTrue("A new run must be able to claim level 2 again",
                ShopkeeperTracker.claimLevel(2));
    }
}
