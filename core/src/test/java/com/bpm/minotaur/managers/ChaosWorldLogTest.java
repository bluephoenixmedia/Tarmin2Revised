package com.bpm.minotaur.managers;

import com.bpm.minotaur.gamedata.history.Life;
import com.bpm.minotaur.gamedata.history.town.Allegiance;
import com.bpm.minotaur.gamedata.worldlog.Fact;
import com.bpm.minotaur.gamedata.worldlog.LifeRecord;
import com.bpm.minotaur.telemetry.TelemetryManager;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Tests for Issue #309: World Log Integration, Motley Reckonings, and Telemetry
 * for The Laughing Void / Chaos Surges.
 */
public class ChaosWorldLogTest {

    private ChaosManager chaos;
    private WorldLogManager worldLog;
    private TelemetryManager telemetry;

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

        worldLog = WorldLogManager.getInstance();
        worldLog.resetForNewGame();

        telemetry = TelemetryManager.getInstance();
        telemetry.startNewRun();
    }

    @After
    public void tearDown() {
        if (chaos != null) {
            chaos.endSurge(new ChaosEventContext(null, null, null, null, 0, 0));
        }
        if (installedFiles != null && com.badlogic.gdx.Gdx.files == installedFiles) {
            com.badlogic.gdx.Gdx.files = null;
        }
    }

    private static Life testLife(String name) {
        return new Life(name, 1, "the Low Shallows", Allegiance.REFUGEE_COUNCIL, Life.Origin.RAIDED,
                "the Antlered Host", null, null);
    }

    @Test
    public void testChaosSurgeTriggerRecordsTelemetryAndFact() {
        worldLog.keepUpWith(testLife("Varek Swift"));
        assertEquals(0, telemetry.getChaosSurgesTriggered());

        ChaosSurgeEvent event = new ChaosSurgeEvent() {
            @Override public String getId() { return "kaleidoscope_sky"; }
            @Override public String getName() { return "Kaleidoscope Sky"; }
            @Override public String getDescription() { return "The sky shatters into crystalline mirrors."; }
            @Override public void onActivate(ChaosEventContext context) {}
            @Override public void onTurnTick(ChaosEventContext context) {}
            @Override public void onExpire(ChaosEventContext context) {}
        };
        chaos.registerEvent(event);
        chaos.triggerSurge(event, 25, null, null, null, null);

        // 1. Telemetry incremented
        assertEquals(1, telemetry.getChaosSurgesTriggered());

        // 2. World Log Fact recorded
        LifeRecord life = worldLog.log().current();
        assertNotNull(life);
        boolean foundWitnessFact = false;
        for (Fact f : life.lines) {
            if (f.kind == Fact.Kind.CHAOS_SURGE_WITNESSED) {
                foundWitnessFact = true;
                assertEquals("Kaleidoscope Sky", f.subject);
                assertTrue(f.plain().contains("Kaleidoscope Sky"));
                assertTrue(f.mythic().contains("Kaleidoscope Sky"));
            }
        }
        assertTrue("Fact of kind CHAOS_SURGE_WITNESSED must be noted in World Log", foundWitnessFact);
    }

    @Test
    public void testDeathDuringSurgeRecordsMotleyReckoning() {
        worldLog.keepUpWith(testLife("Eldrin the Unlucky"));

        ChaosSurgeEvent event = new ChaosSurgeEvent() {
            @Override public String getId() { return "jester_dance"; }
            @Override public String getName() { return "Dance of the Fool"; }
            @Override public String getDescription() { return ""; }
            @Override public void onActivate(ChaosEventContext context) {}
            @Override public void onTurnTick(ChaosEventContext context) {}
            @Override public void onExpire(ChaosEventContext context) {}
        };
        chaos.registerEvent(event);
        chaos.triggerSurge(event, 20, null, null, null, null);

        assertTrue("Surge must be active before dying", chaos.isSurgeActive());

        worldLog.die("Fell to a Harpy", "a Harpy", "level 3", 140);

        LifeRecord deadLife = worldLog.log().last();
        assertNotNull(deadLife);
        assertTrue(deadLife.isDead());
        assertEquals("Dance of the Fool", deadLife.motleyReckoning);

        // Check Fact.Kind.MOTLEY_RECKONING
        boolean foundMotley = false;
        for (Fact f : deadLife.lines) {
            if (f.kind == Fact.Kind.MOTLEY_RECKONING) {
                foundMotley = true;
                assertEquals("Dance of the Fool", f.subject);
                assertTrue(f.plain().contains("Motley Reckoning"));
                assertTrue(f.plain().contains("Dance of the Fool"));
                assertTrue(f.mythic().contains("Motley Sovereign"));
            }
        }
        assertTrue("Life must record a MOTLEY_RECKONING fact", foundMotley);
    }

    @Test
    public void testDeathWithoutSurgeDoesNotRecordMotleyReckoning() {
        worldLog.keepUpWith(testLife("Serena Guard"));
        assertFalse(chaos.isSurgeActive());

        worldLog.die("Fell in the depths", "a Goblin", "level 1", 50);

        LifeRecord deadLife = worldLog.log().last();
        assertNotNull(deadLife);
        assertTrue(deadLife.isDead());
        assertNull(deadLife.motleyReckoning);

        for (Fact f : deadLife.lines) {
            assertFalse(f.kind == Fact.Kind.MOTLEY_RECKONING);
        }
    }

    @Test
    public void testTelemetryExportContainsChaosSurgesTriggered() {
        telemetry.recordChaosSurge();
        telemetry.recordChaosSurge();
        assertEquals(2, telemetry.getChaosSurgesTriggered());

        String json = telemetry.toJson();
        assertNotNull(json);
        assertTrue("Telemetry JSON should contain chaosSurgesTriggered key",
                json.contains("\"chaosSurgesTriggered\": 2") || json.contains("\"chaosSurgesTriggered\":2"));
    }
}
