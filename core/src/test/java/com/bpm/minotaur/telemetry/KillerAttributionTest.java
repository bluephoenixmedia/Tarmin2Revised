package com.bpm.minotaur.telemetry;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/** The epitaph names the monster that last drew blood, whatever the attack was (2026-10-19 run). */
public class KillerAttributionTest {

    private TelemetryManager telemetry;

    @Before
    public void setUp() {
        if (com.badlogic.gdx.Gdx.app == null) {
            com.badlogic.gdx.Gdx.app = (com.badlogic.gdx.Application) java.lang.reflect.Proxy.newProxyInstance(
                    com.badlogic.gdx.Application.class.getClassLoader(),
                    new Class<?>[]{com.badlogic.gdx.Application.class},
                    (proxy, method, args) -> null);
        }
        telemetry = TelemetryManager.getInstance();
        telemetry.startNewRun();
    }

    @Test
    public void aRangedHitAfterAMeleeHitNamesTheShooter() {
        Object skeleton = new Object(), bringer = new Object();
        telemetry.recordMonsterAttack(skeleton, "SKELETON", 1, true, 4);
        telemetry.recordMonsterAttack(bringer, "BRINGER_OF_DEATH", 18, true, 20);

        assertEquals("BRINGER_OF_DEATH", telemetry.getKillerMonster());
    }

    @Test
    public void aMissOrAHarmlessHitNamesNoOne() {
        telemetry.recordMonsterAttack(new Object(), "SKELETON", 1, true, 4);
        telemetry.recordMonsterAttack(new Object(), "BAT", 1, false, 0);
        telemetry.recordMonsterAttack(new Object(), "GOBLIN", 1, true, 0);

        assertEquals("SKELETON", telemetry.getKillerMonster());
    }
}
