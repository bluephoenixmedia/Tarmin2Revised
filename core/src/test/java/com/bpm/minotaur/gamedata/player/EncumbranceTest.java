package com.bpm.minotaur.gamedata.player;

import org.junit.Test;

import static org.junit.Assert.*;

public class EncumbranceTest {

    @Test
    public void capacityGrowsWithStrength() {
        assertEquals(75f, Encumbrance.capacity(10), 0.001f);
        assertEquals(25f + 5f * 18, Encumbrance.capacity(18), 0.001f);
        assertTrue(Encumbrance.capacity(14) > Encumbrance.capacity(10));
    }

    @Test
    public void tiersStartAtOneOneAndAHalfAndTwoTimesTheCapacity() {
        float cap = 80f;
        assertEquals(Encumbrance.Tier.UNENCUMBERED, Encumbrance.tier(0f, cap));
        assertEquals(Encumbrance.Tier.UNENCUMBERED, Encumbrance.tier(79.9f, cap));
        assertEquals(Encumbrance.Tier.BURDENED, Encumbrance.tier(80f, cap));
        assertEquals(Encumbrance.Tier.BURDENED, Encumbrance.tier(119.9f, cap));
        assertEquals(Encumbrance.Tier.STRESSED, Encumbrance.tier(120f, cap));
        assertEquals(Encumbrance.Tier.STRESSED, Encumbrance.tier(159.9f, cap));
        assertEquals(Encumbrance.Tier.OVERLOADED, Encumbrance.tier(160f, cap));
        assertEquals(Encumbrance.Tier.OVERLOADED, Encumbrance.tier(500f, cap));
    }

    @Test
    public void everyTierSlowsMovementAndTheHeavierTwoAlsoDrainFaster() {
        assertEquals(1f, Encumbrance.Tier.UNENCUMBERED.speedFactor, 0.001f);
        assertTrue(Encumbrance.Tier.BURDENED.speedFactor < 1f);
        assertTrue(Encumbrance.Tier.STRESSED.speedFactor < Encumbrance.Tier.BURDENED.speedFactor);
        assertTrue(Encumbrance.Tier.OVERLOADED.speedFactor < Encumbrance.Tier.STRESSED.speedFactor);

        assertEquals(1f, Encumbrance.Tier.UNENCUMBERED.drainFactor, 0.001f);
        assertEquals("Burdened only slows", 1f, Encumbrance.Tier.BURDENED.drainFactor, 0.001f);
        assertTrue(Encumbrance.Tier.STRESSED.drainFactor > 1f);
        assertTrue(Encumbrance.Tier.OVERLOADED.drainFactor > Encumbrance.Tier.STRESSED.drainFactor);
    }

    @Test
    public void anyMoveSpeedStaysAtLeastOne() {
        assertEquals(1, Encumbrance.Tier.OVERLOADED.apply(1));
        assertEquals(9, Encumbrance.Tier.BURDENED.apply(12));
        assertEquals(12, Encumbrance.Tier.UNENCUMBERED.apply(12));
    }

    @Test
    public void goldWeighsOneUnitPerHundredCoins() {
        assertEquals(0f, Encumbrance.goldWeight(99), 0.001f);
        assertEquals(1f, Encumbrance.goldWeight(100), 0.001f);
        assertEquals(25f, Encumbrance.goldWeight(2500), 0.001f);
        assertEquals(0f, Encumbrance.goldWeight(-5), 0.001f);
    }
}
