package com.bpm.minotaur.gamedata.events;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class EventTriggerRuleTest {

    @Test
    public void clearWithNoThreats() {
        assertTrue(EventTriggerRule.isClear(10, 10, Collections.emptyList()));
    }

    @Test
    public void aHostileWithinFourTilesBlocksEvenWithoutLineOfSight() {
        assertFalse(EventTriggerRule.isClear(10, 10,
                Collections.singletonList(new EventTriggerRule.Threat(14, 13, false))));
    }

    @Test
    public void aHostileFiveTilesAwayWithoutLineOfSightDoesNotBlock() {
        assertTrue(EventTriggerRule.isClear(10, 10,
                Collections.singletonList(new EventTriggerRule.Threat(15, 10, false))));
    }

    @Test
    public void aHostileThatCanSeeThePlayerWithinEightTilesBlocks() {
        assertFalse(EventTriggerRule.isClear(10, 10,
                Collections.singletonList(new EventTriggerRule.Threat(18, 10, true))));
    }

    @Test
    public void aHostileThatCanSeeThePlayerFromBeyondEightTilesDoesNotBlock() {
        assertTrue(EventTriggerRule.isClear(10, 10,
                Collections.singletonList(new EventTriggerRule.Threat(19, 10, true))));
    }

    @Test
    public void anyOneBlockingThreatIsEnough() {
        assertFalse(EventTriggerRule.isClear(10, 10, Arrays.asList(
                new EventTriggerRule.Threat(30, 30, true),
                new EventTriggerRule.Threat(11, 10, false))));
    }
}
