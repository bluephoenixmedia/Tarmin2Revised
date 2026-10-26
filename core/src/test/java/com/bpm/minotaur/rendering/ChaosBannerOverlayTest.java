package com.bpm.minotaur.rendering;

import com.bpm.minotaur.managers.ChaosEventContext;
import com.bpm.minotaur.managers.ChaosManager;
import com.bpm.minotaur.managers.ChaosSurgeEvent;
import com.bpm.minotaur.managers.GameEventManager;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class ChaosBannerOverlayTest {

    private ChaosBannerOverlay overlay;
    private ChaosManager chaos;

    /** The Gdx.files stub this test installed, if it did; tearDown removes it. */
    private com.badlogic.gdx.Files installedFiles;

    @Before
    public void setUp() {
        if (com.badlogic.gdx.Gdx.files == null) {
            // Removed again in tearDown: a stub left behind breaks later tests that load real data.
            com.badlogic.gdx.Gdx.files = installedFiles = new com.badlogic.gdx.Files() {
                @Override public com.badlogic.gdx.files.FileHandle getFileHandle(String path, FileType type) { return new com.badlogic.gdx.files.FileHandle(path); }
                @Override public com.badlogic.gdx.files.FileHandle classpath(String path) { return new com.badlogic.gdx.files.FileHandle(path); }
                @Override public com.badlogic.gdx.files.FileHandle internal(String path) { return new com.badlogic.gdx.files.FileHandle(path); }
                @Override public com.badlogic.gdx.files.FileHandle external(String path) { return new com.badlogic.gdx.files.FileHandle(path); }
                @Override public com.badlogic.gdx.files.FileHandle absolute(String path) { return new com.badlogic.gdx.files.FileHandle(path); }
                @Override public com.badlogic.gdx.files.FileHandle local(String path) { return new com.badlogic.gdx.files.FileHandle(path); }
                @Override public String getExternalStoragePath() { return ""; }
                @Override public boolean isExternalStorageAvailable() { return false; }
                @Override public String getLocalStoragePath() { return ""; }
                @Override public boolean isLocalStorageAvailable() { return false; }
            };
        }

        ChaosManager.resetInstanceForTesting();
        chaos = ChaosManager.getInstance();
        overlay = new ChaosBannerOverlay(null);
    }

    @After
    public void tearDown() {
        if (overlay != null) {
            overlay.dispose();
        }
        if (installedFiles != null && com.badlogic.gdx.Gdx.files == installedFiles) {
            com.badlogic.gdx.Gdx.files = null;
        }
    }

    @Test
    public void testInitialState() {
        assertNotNull(overlay);
        assertFalse("Banner should not be visible initially", overlay.isBannerVisible());
        assertFalse("HUD pill should not be visible initially", overlay.isHudPillVisible());
    }

    @Test
    public void testSurgeTriggerDisplaysHeraldBanner() {
        ChaosSurgeEvent testEvent = new ChaosSurgeEvent() {
            @Override public String getId() { return "test_event"; }
            @Override public String getName() { return "Test Cosmic Surge"; }
            @Override public String getDescription() { return "Reality fractures into kaleidoscope mirrors."; }
            @Override public void onActivate(ChaosEventContext context) {}
            @Override public void onTurnTick(ChaosEventContext context) {}
            @Override public void onExpire(ChaosEventContext context) {}
        };

        chaos.registerEvent(testEvent);
        chaos.triggerSurge(testEvent, 20, null, null, null, null);

        // Overlay was registered as listener and receives trigger notification
        assertEquals("TEST COSMIC SURGE", overlay.getCurrentBannerTitle());

        // Step Scene2D actions
        overlay.act(0.6f);
        assertTrue("Banner should be visible during active hold", overlay.isBannerVisible());
        assertTrue("HUD Pill should be visible while surge is active", overlay.isHudPillVisible());
    }

    @Test
    public void testSurgeExpirationFadesBannerAndPill() {
        ChaosSurgeEvent testEvent = new ChaosSurgeEvent() {
            @Override public String getId() { return "fade_test"; }
            @Override public String getName() { return "Fade Surge"; }
            @Override public String getDescription() { return ""; }
            @Override public void onActivate(ChaosEventContext context) {}
            @Override public void onTurnTick(ChaosEventContext context) {}
            @Override public void onExpire(ChaosEventContext context) {}
        };

        chaos.registerEvent(testEvent);
        chaos.triggerSurge(testEvent, 5, null, null, null, null);

        overlay.act(0.6f);
        assertTrue(overlay.isHudPillVisible());

        chaos.endSurge(new ChaosEventContext(null, null, null, null, 0, 5));
        overlay.act(0.5f);

        assertFalse("HUD Pill should hide when surge ends", overlay.isHudPillVisible());
    }

    @Test
    public void testDisposalRemovesListener() {
        overlay.dispose();
        // Triggering after disposal should not affect disposed overlay
        ChaosSurgeEvent testEvent = new ChaosSurgeEvent() {
            @Override public String getId() { return "post_dispose"; }
            @Override public String getName() { return "Post Dispose"; }
            @Override public String getDescription() { return ""; }
            @Override public void onActivate(ChaosEventContext context) {}
            @Override public void onTurnTick(ChaosEventContext context) {}
            @Override public void onExpire(ChaosEventContext context) {}
        };
        chaos.registerEvent(testEvent);
        chaos.triggerSurge(testEvent, 5, null, null, null, null);

        // Banner title should remain empty
        assertEquals("", overlay.getCurrentBannerTitle());
    }
}
