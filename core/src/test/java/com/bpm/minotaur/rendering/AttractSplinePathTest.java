package com.bpm.minotaur.rendering;

import com.badlogic.gdx.math.Vector3;
import com.bpm.minotaur.generation.Biome;
import com.bpm.minotaur.rendering.attract.AttractSplinePath;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class AttractSplinePathTest {

    private AttractSplinePath spline;

    @Before
    public void setUp() {
        spline = new AttractSplinePath();
    }

    @Test
    public void testLoopClosureAndContinuity() {
        // Position at t = 0 should match position at t = loopDuration
        float duration = spline.getLoopDuration();
        assertTrue("Duration should be approximately 90 seconds", duration >= 85f && duration <= 95f);

        Vector3 startPos = spline.getPosition(0f, new Vector3());
        Vector3 endPos = spline.getPosition(duration, new Vector3());

        assertEquals("X must match at loop boundaries", startPos.x, endPos.x, 0.05f);
        assertEquals("Y must match at loop boundaries", startPos.y, endPos.y, 0.05f);
        assertEquals("Z must match at loop boundaries", startPos.z, endPos.z, 0.05f);
    }

    @Test
    public void testWorldBoundsContainment() {
        // A 10x10 chunk grid (36 tiles each) spans X: [0, 360], Z: [-360, 0]
        Vector3 pos = new Vector3();
        float step = 0.5f;
        float duration = spline.getLoopDuration();

        for (float t = 0; t <= duration; t += step) {
            spline.getPosition(t, pos);
            assertTrue("X must be inside 100-chunk world (got " + pos.x + ")", pos.x >= 10f && pos.x <= 350f);
            assertTrue("Z must be inside 100-chunk world (got " + pos.z + ")", pos.z <= -10f && pos.z >= -350f);
        }
    }

    @Test
    public void testAltitudeDynamics() {
        // Test that spline achieves low skim (< 2.5) and high apex (> 16.0)
        float minAltitude = Float.MAX_VALUE;
        float maxAltitude = Float.MIN_VALUE;
        Vector3 pos = new Vector3();
        float step = 0.5f;
        float duration = spline.getLoopDuration();

        for (float t = 0; t <= duration; t += step) {
            spline.getPosition(t, pos);
            if (pos.y < minAltitude) minAltitude = pos.y;
            if (pos.y > maxAltitude) maxAltitude = pos.y;
        }

        assertTrue("Must have low skimming altitude <= 2.5 (was " + minAltitude + ")", minAltitude <= 2.5f);
        assertTrue("Must have soaring spire apex altitude >= 16.0 (was " + maxAltitude + ")", maxAltitude >= 16.0f);
    }

    @Test
    public void testAerodynamicBankingRoll() {
        // Test that banking roll occurs during turns and is clamped reasonably
        float duration = spline.getLoopDuration();
        boolean observedNonZeroRoll = false;

        for (float t = 0; t <= duration; t += 1.0f) {
            float roll = spline.getRollDegrees(t);
            assertTrue("Roll must be bounded within +/- 15 degrees", Math.abs(roll) <= 15.0f);
            if (Math.abs(roll) > 1.0f) {
                observedNonZeroRoll = true;
            }
        }
        assertTrue("Spline must bank into turns", observedNonZeroRoll);
    }

    @Test
    public void testBiomeProgression() {
        // Test that the tour visits Lakelands, Forest, Desert, and Castle
        boolean sawLakelands = false;
        boolean sawForest = false;
        boolean sawDesert = false;
        boolean sawCastle = false;

        float duration = spline.getLoopDuration();
        for (float t = 0; t <= duration; t += 2.0f) {
            Biome b = spline.getBiomeAt(t);
            if (b == Biome.LAKELANDS) sawLakelands = true;
            if (b == Biome.FOREST) sawForest = true;
            if (b == Biome.DESERT) sawDesert = true;
            if (b == Biome.MAZE) sawCastle = true;
        }

        assertTrue("Tour must visit Lakelands", sawLakelands);
        assertTrue("Tour must visit Forest", sawForest);
        assertTrue("Tour must visit Desert", sawDesert);
        assertTrue("Tour must visit Castle", sawCastle);
    }

    @Test
    public void testExpeditionDiveTransition() {
        Vector3 startPos = spline.getPosition(10f, new Vector3());
        Vector3 currentPos = new Vector3();

        // At progress 0, dive starts at current camera pos
        spline.evaluateDive(startPos, 0f, currentPos);
        assertEquals(startPos.x, currentPos.x, 0.001f);
        assertEquals(startPos.y, currentPos.y, 0.001f);
        assertEquals(startPos.z, currentPos.z, 0.001f);

        // At progress 1.0, dive reaches castle entrance near (180, 1.5, -165)
        spline.evaluateDive(startPos, 1.0f, currentPos);
        assertEquals("Castle gate X is around 180", 180f, currentPos.x, 5.0f);
        assertTrue("Castle gate altitude is near ground level", currentPos.y <= 3.0f);
        assertEquals("Castle gate Z is around -165", -165f, currentPos.z, 10.0f);
    }
}
