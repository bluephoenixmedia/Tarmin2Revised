package com.bpm.minotaur.rendering;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The death cinematic's timeline.
 *
 * <p>The sequence is watched dozens of times per playthrough (fifty deaths is a designed
 * quantity), so its timing is a contract rather than a detail. These tests pin the beats, the
 * skip lockout that stops a player who died mid-attack from skipping their own death before
 * seeing it, and the handover point -- the screen swap must happen under fully opaque blood or
 * the player sees it.
 */
public class DeathSequenceTest {

    private DeathSequence seq;

    @Before
    public void setUp() {
        seq = new DeathSequence();
    }

    /** Advances in small steps, the way a real frame loop would. */
    private void advance(float seconds) {
        float step = 1f / 120f;
        for (float t = 0; t < seconds; t += step) {
            seq.update(step);
        }
    }

    @Test
    public void testInactiveUntilBegun() {
        assertFalse(seq.isActive());
        assertFalse(seq.shouldHandOver());
        assertEquals(0f, seq.getBloodAlpha(), 0.001f);
    }

    @Test
    public void testCollapseDropsEyeHeightAndPitchesForward() {
        seq.begin(true, 40f);
        assertEquals("Eye height starts at standing height",
                DeathSequence.EYE_HEIGHT_STANDING, seq.getEyeHeight(), 0.001f);
        assertEquals("No pitch at the instant of death", 0f, seq.getPitchDegrees(), 0.001f);

        advance(0.55f);
        assertTrue("Eye height must have dropped by the end of the collapse",
                seq.getEyeHeight() < DeathSequence.EYE_HEIGHT_STANDING);
        assertTrue("View must pitch downward, never up", seq.getPitchDegrees() > 0f);

        advance(0.60f); // past impact
        assertTrue("Eye height reaches the floor", seq.getEyeHeight() <= 0.12f);
        assertTrue("Pitch keeps going down into the floor", seq.getPitchDegrees() > 40f);
    }

    @Test
    public void testBloodStartsAtImpactNotBefore() {
        seq.begin(true, 40f);
        advance(1.05f);
        assertEquals("No blood before the body lands", 0f, seq.getBloodAlpha(), 0.001f);

        advance(0.40f);
        assertTrue("Blood begins at impact", seq.getBloodAlpha() > 0f);
        assertTrue("...but is not yet opaque", seq.getBloodAlpha() < 1f);
    }

    @Test
    public void testHandoverOnlyUnderOpaqueBlood() {
        seq.begin(true, 40f);
        advance(1.85f);
        assertFalse("Must not hand over while the world is still visible", seq.shouldHandOver());

        advance(0.15f);
        assertEquals("Blood must be fully opaque at handover", 1f, seq.getBloodAlpha(), 0.001f);
        assertTrue(seq.shouldHandOver());
    }

    @Test
    public void testHudFadesOutEarly() {
        seq.begin(true, 40f);
        assertEquals(1f, seq.getHudAlpha(), 0.001f);
        advance(0.55f);
        assertEquals("HUD is gone well before the body lands", 0f, seq.getHudAlpha(), 0.001f);
    }

    @Test
    public void testSkipIsLockedOutAtTheStart() {
        seq.begin(true, 40f);
        assertFalse("A player who died mid-attack must not skip instantly", seq.canSkip());
        advance(0.20f);
        assertFalse(seq.canSkip());
        advance(0.25f);
        assertTrue("Skip opens once the grunt and first tip have landed", seq.canSkip());
    }

    @Test
    public void testSkipFastForwardsToHandoverWithoutCutting() {
        seq.begin(true, 40f);
        advance(0.50f);
        assertTrue(seq.canSkip());
        seq.skip();

        // Skipping must still play a wipe -- an instant cut to a static screen reads as a crash.
        assertFalse("Skip must not hand over on the same frame", seq.shouldHandOver());

        // Without the skip, blood would not begin until impact at 1.10s; one frame after skipping
        // at 0.50s it must already be rising.
        advance(1f / 120f);
        assertTrue("Skip starts the wipe immediately rather than waiting for impact",
                seq.getBloodAlpha() > 0f);

        advance(0.20f);
        assertEquals(1f, seq.getBloodAlpha(), 0.001f);
        assertTrue(seq.shouldHandOver());
    }

    @Test
    public void testSkipBeforeLockoutExpiresIsIgnored() {
        seq.begin(true, 40f);
        advance(0.10f);
        seq.skip();
        advance(0.10f);
        assertFalse("Ignored skip must not have started the wipe", seq.getBloodAlpha() > 0f);
    }

    @Test
    public void testRollBuildsTowardTheRequestedAngle() {
        seq.begin(true, 50f);
        assertEquals("No roll at the instant of death", 0f, seq.getRollDegrees(), 0.001f);
        advance(1.20f);
        float mid = seq.getRollDegrees();
        assertTrue("Roll has begun by impact", mid > 0f);
        advance(1.00f);
        assertTrue("Roll continues toward its target", seq.getRollDegrees() >= mid);
        assertTrue("Roll never exceeds the requested angle", seq.getRollDegrees() <= 50.001f);
    }

    @Test
    public void testSequenceEndsAndStaysEnded() {
        seq.begin(true, 40f);
        advance(3.0f);
        assertTrue(seq.shouldHandOver());
        assertEquals(1f, seq.getBloodAlpha(), 0.001f);
    }

    @Test
    public void testViolentFlagIsReported() {
        seq.begin(false, 30f);
        assertFalse("Attrition deaths must be reported as not violent", seq.isViolentDeath());
        seq.begin(true, 30f);
        assertTrue(seq.isViolentDeath());
    }
}
