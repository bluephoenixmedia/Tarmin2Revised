package com.bpm.minotaur.gamedata.events;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class EventOddsTest {

    private static final float EPS = 0.0001f;

    @Test
    public void averageAttributeAndNoLuckIsTheBaseChance() {
        assertEquals(0.5f, EventOdds.chance(0.5f, 10, 0), EPS);
    }

    @Test
    public void eachAttributePointAboveTenAddsFivePercent() {
        assertEquals(0.65f, EventOdds.chance(0.5f, 13, 0), EPS);
        assertEquals(0.40f, EventOdds.chance(0.5f, 8, 0), EPS);
    }

    @Test
    public void eachPointOfLuckAddsTwoPercent() {
        assertEquals(0.56f, EventOdds.chance(0.5f, 10, 3), EPS);
        assertEquals(0.44f, EventOdds.chance(0.5f, 10, -3), EPS);
    }

    @Test
    public void chanceIsClampedBetweenFiveAndNinetyFivePercent() {
        assertEquals(0.95f, EventOdds.chance(0.9f, 25, 13), EPS);
        assertEquals(0.05f, EventOdds.chance(0.1f, 3, -13), EPS);
    }

    @Test
    public void aLuckRollIgnoresAttributes() {
        assertEquals(0.31f, EventOdds.luckChance(0.25f, 3), EPS);
    }

    @Test
    public void percentRoundsToAWholeNumber() {
        assertEquals(56, EventOdds.percent(0.555f));
    }
}
