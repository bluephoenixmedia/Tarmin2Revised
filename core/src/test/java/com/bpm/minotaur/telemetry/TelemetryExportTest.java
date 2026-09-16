package com.bpm.minotaur.telemetry;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Verifies TelemetryManager tracks hit/glance/miss and other run stats
 * correctly and exports a clean, valid-looking JSON payload on run end.
 */
public class TelemetryExportTest {

    @Before
    public void setUp() {
        if (com.badlogic.gdx.Gdx.app == null) {
            com.badlogic.gdx.Gdx.app = (com.badlogic.gdx.Application) java.lang.reflect.Proxy.newProxyInstance(
                    com.badlogic.gdx.Application.class.getClassLoader(),
                    new Class<?>[]{com.badlogic.gdx.Application.class},
                    (proxy, method, args) -> null
            );
        }
        TelemetryManager.getInstance().startNewRun();
    }

    @Test
    public void testAccuracyAndCountersAccumulateCorrectly() {
        TelemetryManager telemetry = TelemetryManager.getInstance();

        telemetry.recordAttack(TelemetryManager.HitType.HIT, 10);
        telemetry.recordAttack(TelemetryManager.HitType.GLANCING, 3);
        telemetry.recordAttack(TelemetryManager.HitType.MISS, 0);
        telemetry.recordAttack(TelemetryManager.HitType.MISS, 0);

        // 2 of 4 attacks landed (1 full hit + 1 glance) -> 50% accuracy
        assertEquals(50.0f, telemetry.getAccuracyPercent(), 0.01f);

        telemetry.recordKill("GOBLIN");
        telemetry.recordKill("GOBLIN");
        telemetry.recordKill("SKELETON");
        assertEquals(3, telemetry.getTotalMonstersKilled());

        telemetry.recordDivinitiesEarned(5);
        telemetry.recordDivinitiesEarned(3);
        assertEquals(8, telemetry.getDivinitiesEarned());

        telemetry.setStrataReached(4);
        telemetry.setStrataReached(2); // must never regress
        assertEquals(4, telemetry.getStrataReached());
    }

    @Test
    public void testExportProducesValidLookingJsonWithHitGlanceMissStats() {
        TelemetryManager telemetry = TelemetryManager.getInstance();

        telemetry.recordAttack(TelemetryManager.HitType.HIT, 8);
        telemetry.recordAttack(TelemetryManager.HitType.GLANCING, 2);
        telemetry.recordAttack(TelemetryManager.HitType.MISS, 0);
        telemetry.recordKill("HOBGOBLIN");
        telemetry.setTurnsLived(412);
        telemetry.setStrataReached(3);
        telemetry.setLastDamageSource("UMBER_HULK");

        String json = telemetry.exportRun("Fell to an Umber Hulk");

        assertNotNull(json);
        assertTrue("JSON should be a well-formed object", json.trim().startsWith("{") && json.trim().endsWith("}"));
        assertTrue(json.contains("\"attacksHit\": 1"));
        assertTrue(json.contains("\"attacksGlanced\": 1"));
        assertTrue(json.contains("\"attacksMissed\": 1"));
        assertTrue(json.contains("\"turnsLived\": 412"));
        assertTrue(json.contains("\"strataReached\": 3"));
        assertTrue(json.contains("\"killerMonster\": \"UMBER_HULK\""));
        assertTrue(json.contains("\"causeOfDeath\": \"Fell to an Umber Hulk\""));
        assertTrue(json.contains("\"HOBGOBLIN\": 1"));
    }

    @Test
    public void testStartNewRunResetsAllCounters() {
        TelemetryManager telemetry = TelemetryManager.getInstance();
        telemetry.recordAttack(TelemetryManager.HitType.HIT, 5);
        telemetry.recordKill("RAT");
        telemetry.setTurnsLived(50);

        String previousRunId = telemetry.getRunId();
        telemetry.startNewRun();

        assertNotEquals("A new run must get a fresh run ID", previousRunId, telemetry.getRunId());
        assertEquals(0f, telemetry.getAccuracyPercent(), 0.01f);
        assertEquals(0, telemetry.getTotalMonstersKilled());
        assertEquals(0, telemetry.getTurnsLived());
    }

    /**
     * Added as part of the "way off again" balance investigation: the old
     * rest-to-heal action left no telemetry trace of its own, which is part of
     * why its removal went undiagnosed for as long as it did. The reintroduced
     * field Rest (H) is now visible in every run export, the same way bleed
     * damage and injury counts already are.
     */
    @Test
    public void testFieldRestUsageIsCountedAndResetsOnNewRun() {
        TelemetryManager telemetry = TelemetryManager.getInstance();
        assertEquals(0, telemetry.getFieldRestUses());

        telemetry.recordFieldRestUsed();
        telemetry.recordFieldRestUsed();
        assertEquals(2, telemetry.getFieldRestUses());

        String json = telemetry.toJson();
        assertTrue("Export must include the new counter", json.contains("\"fieldRestUses\": 2"));

        telemetry.startNewRun();
        assertEquals(0, telemetry.getFieldRestUses());
    }
}
