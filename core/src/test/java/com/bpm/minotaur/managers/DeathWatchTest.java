package com.bpm.minotaur.managers;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The central HP<=0 check.
 *
 * <p>Before this existed, every damage source was responsible for noticing it had killed the
 * player, and {@code InjuryManager} did not -- so bleeding out never actually killed anyone. These
 * tests pin the two properties that make a single central check safe to add alongside the existing
 * scattered ones: it fires exactly once per death, and it re-arms when the player is alive again.
 */
public class DeathWatchTest {

    private DeathWatch watch;

    @Before
    public void setUp() {
        watch = new DeathWatch();
    }

    @Test
    public void testLivingPlayerNeverTriggers() {
        assertFalse(watch.observe(20));
        assertFalse(watch.observe(1));
        assertFalse(watch.hasReportedDeath());
    }

    @Test
    public void testFiresExactlyOnceAtZero() {
        assertTrue("First observation at 0 HP must report death", watch.observe(0));
        assertFalse("Death must not be reported twice", watch.observe(0));
        assertFalse(watch.observe(0));
        assertTrue(watch.hasReportedDeath());
    }

    @Test
    public void testFiresOnNegativeHealth() {
        // Bleed and true-damage ticks can overshoot well past zero.
        assertTrue(watch.observe(-7));
        assertFalse(watch.observe(-7));
    }

    @Test
    public void testReArmsWhenPlayerIsAliveAgain() {
        assertTrue(watch.observe(0));
        // Respawn restores HP. The watch must re-arm on its own so a missed reset() cannot
        // silently make the player immortal for the rest of the session.
        assertFalse(watch.observe(16));
        assertFalse(watch.hasReportedDeath());
        assertTrue("A later death must report again", watch.observe(0));
    }

    @Test
    public void testExplicitResetReArms() {
        assertTrue(watch.observe(0));
        watch.reset();
        assertFalse(watch.hasReportedDeath());
        assertTrue(watch.observe(0));
    }
}
