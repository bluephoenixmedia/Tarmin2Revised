package com.bpm.minotaur.gamedata.effects;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * How long a monster's venom lasts, and how hard it bites.
 *
 * <p>Before this existed, {@code POISONED} was a timer and nothing more: every application site
 * passed a hard-coded duration and {@code StatusManager.updateTurn} only counted it down. Being
 * poisoned cost the player nothing at all.
 *
 * <p>Duration scales with the level of whatever poisoned you, so a cave spider is an annoyance and
 * something from the deep strata is a genuine problem you need to answer before it kills you.
 */
public class PoisonDoseTest {

    @Test
    public void testLowLevelVenomIsShort() {
        for (int i = 0; i < 200; i++) {
            int ticks = PoisonDose.ticksFor(1);
            assertTrue("level 1 venom was " + ticks, ticks >= 3 && ticks <= 6);
        }
    }

    @Test
    public void testDurationGrowsWithLevel() {
        int lowTotal = 0;
        int highTotal = 0;
        for (int i = 0; i < 400; i++) {
            lowTotal += PoisonDose.ticksFor(1);
            highTotal += PoisonDose.ticksFor(8);
        }
        assertTrue("deep-strata venom must last longer on average", highTotal > lowTotal);
    }

    @Test
    public void testDurationIsCappedSoPoisonCannotBecomeUnsurvivable() {
        for (int level = 1; level <= 60; level++) {
            for (int i = 0; i < 40; i++) {
                int ticks = PoisonDose.ticksFor(level);
                assertTrue("level " + level + " produced " + ticks, ticks <= PoisonDose.MAX_TICKS);
            }
        }
    }

    @Test
    public void testDegenerateLevelsStillProduceAUsableDose() {
        for (int level : new int[] {0, -3, Integer.MIN_VALUE}) {
            int ticks = PoisonDose.ticksFor(level);
            assertTrue("level " + level + " produced " + ticks, ticks >= 3 && ticks <= 6);
        }
    }

    @Test
    public void testPotencyRisesInStepsRatherThanEveryLevel() {
        // Per-tick damage is what makes poison lethal, so it climbs far more slowly than duration.
        assertEquals(1, PoisonDose.potencyFor(1));
        assertEquals(1, PoisonDose.potencyFor(4));
        assertEquals(2, PoisonDose.potencyFor(5));
        assertEquals(3, PoisonDose.potencyFor(10));
        assertTrue(PoisonDose.potencyFor(999) <= PoisonDose.MAX_POTENCY);
    }
}
