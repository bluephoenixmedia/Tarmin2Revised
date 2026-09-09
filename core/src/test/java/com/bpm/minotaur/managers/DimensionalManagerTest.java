package com.bpm.minotaur.managers;

import com.badlogic.gdx.math.GridPoint2;
import com.badlogic.gdx.math.Vector2;
import com.bpm.minotaur.gamedata.Direction;
import com.bpm.minotaur.gamedata.monster.Monster;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class DimensionalManagerTest {

    private DimensionalManager dimensionalManager;
    private DebugManager debugManager;

    @Before
    public void setUp() {
        dimensionalManager = DimensionalManager.getInstance();
        debugManager = DebugManager.getInstance();
        dimensionalManager.reset();
        debugManager.setRenderEngine(DebugManager.RenderEngine.PLANAR_3D);
        debugManager.setRenderModeDirect(DebugManager.RenderMode.MODERN);
    }

    @After
    public void tearDown() {
        dimensionalManager.reset();
        debugManager.setRenderEngine(DebugManager.RenderEngine.PLANAR_3D);
        debugManager.setRenderModeDirect(DebugManager.RenderMode.MODERN);
    }

    @Test
    public void testDefaultBootIsModern3D() {
        assertEquals("Default boot render engine should be PLANAR_3D",
                DebugManager.RenderEngine.PLANAR_3D, debugManager.getRenderEngine());
        assertEquals("Default boot render mode should be MODERN",
                DebugManager.RenderMode.MODERN, debugManager.getRenderMode());
        assertFalse("Should not start in the void", dimensionalManager.isInVoid());
        assertFalse("Should not start as hollow shade", dimensionalManager.isHollowShade());
        assertFalse("Weather should not be suppressed by default", dimensionalManager.isWeatherSuppressed());
        assertEquals(1.0f, dimensionalManager.getPhysicalDamageMultiplier(), 0.001f);
        assertEquals(1.0f, dimensionalManager.getSpiritualDamageMultiplier(), 0.001f);
    }

    @Test
    public void testEnterVoidSetsRetroRaycasterAndInversionMultipliers() {
        Vector2 playerPos = new Vector2(15f, 20f);
        GridPoint2 chunk = new GridPoint2(2, 3);
        int level = 1;

        dimensionalManager.enterVoid(false, playerPos, level, chunk);

        assertTrue("Should be in Void dimension", dimensionalManager.isInVoid());
        assertFalse("Voluntary entry should not set Hollow Shade", dimensionalManager.isHollowShade());
        assertEquals("Engine should be locked to RAYCASTER in Void",
                DebugManager.RenderEngine.RAYCASTER, debugManager.getRenderEngine());
        assertEquals("Mode should be locked to RETRO in Void",
                DebugManager.RenderMode.RETRO, debugManager.getRenderMode());

        // Weather suppression
        assertTrue("Weather should be suppressed in the Void", dimensionalManager.isWeatherSuppressed());

        // Spiritual Inversion multipliers
        assertEquals("Physical damage should be reduced by 60% (0.4x)",
                0.40f, dimensionalManager.getPhysicalDamageMultiplier(), 0.001f);
        assertEquals("Spiritual/Magic damage should be amplified by +250% (2.5x)",
                2.50f, dimensionalManager.getSpiritualDamageMultiplier(), 0.001f);

        // Mortal return coordinates saved
        assertEquals(15, dimensionalManager.getMortalReturnPos().x);
        assertEquals(20, dimensionalManager.getMortalReturnPos().y);
        assertEquals(1, dimensionalManager.getMortalReturnLevel());
        assertEquals(2, dimensionalManager.getMortalReturnChunk().x);
        assertEquals(3, dimensionalManager.getMortalReturnChunk().y);
    }

    @Test
    public void testDeathInversionPurgatoryAsHollowShade() {
        Vector2 playerPos = new Vector2(8f, 12f);
        GridPoint2 chunk = new GridPoint2(1, 1);
        int level = 2;

        dimensionalManager.enterVoid(true, playerPos, level, chunk);

        assertTrue("Should be in Void dimension", dimensionalManager.isInVoid());
        assertTrue("Should be marked as Hollow Shade", dimensionalManager.isHollowShade());
        assertNotNull("Soul husk position should be recorded", dimensionalManager.getSoulHuskPos());
        assertEquals(8, dimensionalManager.getSoulHuskPos().x);
        assertEquals(12, dimensionalManager.getSoulHuskPos().y);
        assertEquals(2, dimensionalManager.getSoulHuskLevel());
        assertEquals(1, dimensionalManager.getSoulHuskChunk().x);
        assertEquals(1, dimensionalManager.getSoulHuskChunk().y);

        // Exiting and recovering soul
        dimensionalManager.exitVoid(true);

        assertFalse("Should no longer be in Void dimension", dimensionalManager.isInVoid());
        assertFalse("Hollow Shade should be cleared upon resurrection", dimensionalManager.isHollowShade());
        assertNull("Soul husk should be cleared", dimensionalManager.getSoulHuskPos());
        assertEquals(DebugManager.RenderEngine.PLANAR_3D, debugManager.getRenderEngine());
        assertEquals(DebugManager.RenderMode.MODERN, debugManager.getRenderMode());
    }

    @Test
    public void testModeSwitchingLockedInVoidUnlessDebugOverlayActive() {
        dimensionalManager.enterVoid(false, new Vector2(5f, 5f), 1, new GridPoint2(0, 0));
        debugManager.setDebugOverlayVisible(false);

        // Attempting to toggle mode while in void
        debugManager.toggleRenderMode();
        assertEquals("Render mode should remain RETRO in Void",
                DebugManager.RenderMode.RETRO, debugManager.getRenderMode());

        debugManager.toggleRenderEngine();
        assertEquals("Render engine should remain RAYCASTER in Void",
                DebugManager.RenderEngine.RAYCASTER, debugManager.getRenderEngine());
    }

    @Test
    public void testGhostWallsOnlyActiveInVoid() {
        // Outside void
        assertFalse("Ghost walls should never be active outside the void",
                dimensionalManager.isGhostWall(0, 0, 10, 10, Direction.NORTH));

        // Enter void
        dimensionalManager.enterVoid(false, new Vector2(0f, 0f), 1, new GridPoint2(0, 0));

        // Deterministic check: there should exist some ghost walls in the chunk
        boolean foundGhostWall = false;
        for (int x = 0; x < 20; x++) {
            for (int y = 0; y < 20; y++) {
                if (dimensionalManager.isGhostWall(0, 0, x, y, Direction.NORTH)) {
                    foundGhostWall = true;
                    break;
                }
            }
            if (foundGhostWall) break;
        }
        assertTrue("Should have deterministic ghost walls throughout the void geometry", foundGhostWall);
    }

    @Test
    public void testVoidLoreGlyphsAlignWithStoryCycle() {
        dimensionalManager.enterVoid(false, new Vector2(0f, 0f), 1, new GridPoint2(0, 0));
        assertNotNull(DimensionalManager.VOID_LORE_GLYPHS);
        assertTrue("Should have 7 story lore glyphs matching the 7 story images",
                DimensionalManager.VOID_LORE_GLYPHS.length == 7);

        // Check each glyph contains story reference
        assertTrue(DimensionalManager.VOID_LORE_GLYPHS[0].contains("Story I"));
        assertTrue(DimensionalManager.VOID_LORE_GLYPHS[1].contains("Story II"));
        assertTrue(DimensionalManager.VOID_LORE_GLYPHS[2].contains("Story III"));
        assertTrue(DimensionalManager.VOID_LORE_GLYPHS[3].contains("Story IV"));
        assertTrue(DimensionalManager.VOID_LORE_GLYPHS[4].contains("Story V"));
        assertTrue(DimensionalManager.VOID_LORE_GLYPHS[5].contains("Story VI"));
        assertTrue(DimensionalManager.VOID_LORE_GLYPHS[6].contains("Story VII"));
    }

    @Test
    public void testDimensionalWarpTrigger() {
        assertFalse(debugManager.isDimensionalWarp());
        debugManager.triggerDimensionalWarp(true);
        assertTrue("Dimensional warp flag should be set", debugManager.isDimensionalWarp());
        assertTrue("Transition should be active", debugManager.isTransitioning());
    }
}
