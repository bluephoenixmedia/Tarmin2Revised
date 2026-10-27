package com.bpm.minotaur.rendering;

import org.junit.Test;

import java.io.File;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Validates The Laughing Void rendering pipeline, asset integrity,
 * scaling, state transitions, and SkyState contract.
 */
public class TheLaughingVoidRenderingTest {

    @Test
    public void testLaughingVoidAssetExists() {
        File f = new File("assets/images/monsters/jester.png");
        if (!f.exists()) {
            f = new File("../assets/images/monsters/jester.png");
        }
        assertTrue("jester.png must exist in assets/images/monsters/", f.exists());
        assertTrue("jester.png must not be empty", f.length() > 0);
    }

    @Test
    public void testSkyStateVoidFields() {
        Skybox3DRenderer.SkyState state = new Skybox3DRenderer.SkyState();
        assertFalse("voidActive should default to false", state.voidActive);
        assertTrue("voidAlpha should default to Float.NaN", Float.isNaN(state.voidAlpha));
        assertFalse("isUnderground should default to false", state.isUnderground);

        state.voidActive = true;
        state.voidAlpha = 0.75f;
        state.isUnderground = true;

        assertTrue(state.voidActive);
        assertEquals(0.75f, state.voidAlpha, 0.001f);
        assertTrue(state.isUnderground);
    }

    @Test
    public void testVoidManifestationStates() {
        assertEquals(4, Skybox3DRenderer.VoidManifestationState.values().length);
        assertNotNull(Skybox3DRenderer.VoidManifestationState.valueOf("INACTIVE"));
        assertNotNull(Skybox3DRenderer.VoidManifestationState.valueOf("FADING_IN"));
        assertNotNull(Skybox3DRenderer.VoidManifestationState.valueOf("MANIFESTED"));
        assertNotNull(Skybox3DRenderer.VoidManifestationState.valueOf("FADING_OUT"));
    }

    @Test
    public void testVoidScaleRelativeToCastle() {
        float castleBaseHeight = 26.5f;
        float castleScale = 2.025f;
        float effectiveCastleHeight = castleBaseHeight * castleScale;

        float voidBaseHeight = Skybox3DRenderer.VOID_BASE_HEIGHT;
        float voidScale = Skybox3DRenderer.VOID_OVERLAND_SCALE;
        float effectiveVoidHeight = voidBaseHeight * voidScale;

        assertTrue("Void height (" + effectiveVoidHeight + ") must be at least 2x Castle height ("
                        + effectiveCastleHeight + ")",
                effectiveVoidHeight >= 2.0f * effectiveCastleHeight);
    }

    @Test
    public void testVoidParametersAndDistances() {
        assertEquals("Void fade duration must be 3.0 seconds", 3.0f, Skybox3DRenderer.VOID_FADE_SECONDS, 0.001f);
        assertEquals("Overland landmark distance must match horizon", 140f, Skybox3DRenderer.VOID_OVERLAND_DIST, 0.001f);
        assertEquals("Underground distance must bring apparition within subterranean sightlines", 32f, Skybox3DRenderer.VOID_UNDERGROUND_DIST, 0.001f);
    }

    @Test
    public void testVoidLeavesTheSkyWhenTheSurgeEnds() {
        // Regression: the lifecycle was fed "is the Void visible?", so once it had faded in it kept
        // itself wanted and never faded out. Driven by "is a surge running?" it must leave.
        Skybox3DRenderer.VoidManifestationState s = Skybox3DRenderer.VoidManifestationState.INACTIVE;
        s = Skybox3DRenderer.nextVoidState(s, true);
        assertEquals(Skybox3DRenderer.VoidManifestationState.FADING_IN, s);

        s = Skybox3DRenderer.VoidManifestationState.MANIFESTED; // updateSky finishes the fade
        assertEquals("stays while the surge runs", s, Skybox3DRenderer.nextVoidState(s, true));

        s = Skybox3DRenderer.nextVoidState(s, false);
        assertEquals("surge over: fades out", Skybox3DRenderer.VoidManifestationState.FADING_OUT, s);
        assertEquals("keeps fading", s, Skybox3DRenderer.nextVoidState(s, false));
    }

    @Test
    public void testVoidReturnsIfASurgeStartsWhileItFades() {
        assertEquals(Skybox3DRenderer.VoidManifestationState.FADING_IN,
                Skybox3DRenderer.nextVoidState(Skybox3DRenderer.VoidManifestationState.FADING_OUT, true));
        assertEquals(Skybox3DRenderer.VoidManifestationState.FADING_OUT,
                Skybox3DRenderer.nextVoidState(Skybox3DRenderer.VoidManifestationState.FADING_IN, false));
        assertEquals(Skybox3DRenderer.VoidManifestationState.INACTIVE,
                Skybox3DRenderer.nextVoidState(Skybox3DRenderer.VoidManifestationState.INACTIVE, false));
    }
}
