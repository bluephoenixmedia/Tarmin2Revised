package com.bpm.minotaur.gamedata.player;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/** Hunger and thirst report one shared Severity scale, which the UI maps to colour once. */
public class SeverityTest {

    @Test
    public void hungerStages() {
        assertEquals(Severity.NONE, Hunger.Stage.SATIATED.severity());
        assertEquals(Severity.NONE, Hunger.Stage.NOT_HUNGRY.severity());
        assertEquals(Severity.WARNING, Hunger.Stage.HUNGRY.severity());
        assertEquals(Severity.DANGER, Hunger.Stage.WEAK.severity());
        assertEquals(Severity.DANGER, Hunger.Stage.FAINTING.severity());
    }

    @Test
    public void thirstStages() {
        assertEquals(Severity.NONE, Thirst.Stage.HYDRATED.severity());
        assertEquals(Severity.WARNING, Thirst.Stage.THIRSTY.severity());
        assertEquals(Severity.DANGER, Thirst.Stage.DEHYDRATED.severity());
        assertEquals(Severity.DANGER, Thirst.Stage.COLLAPSE.severity());
    }

    @Test
    public void bothMetersShareTheDeathLine() {
        assertEquals(Hunger.starvedBelow(10), Thirst.deadBelow(10), 0.0001f);
        assertEquals(-200f, SurvivalMeter.deathLine(100f, 10f, 10), 0.0001f);
    }
}
