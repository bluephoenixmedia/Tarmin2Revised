package com.bpm.minotaur.gamedata;

import org.junit.Test;

import static org.junit.Assert.*;

/** #201: MEDIUM is the reference; the other difficulties are fixed multipliers on it. */
public class DifficultyTest {

    @Test
    public void mediumIsTheReference() {
        assertEquals(Difficulty.MEDIUM_START_HP, Difficulty.MEDIUM.startWarStrength);
        assertEquals(Difficulty.MEDIUM_START_MP, Difficulty.MEDIUM.startSpiritualStrength);
        assertEquals(Difficulty.MEDIUM_START_ARROWS, Difficulty.MEDIUM.startArrows);
        assertEquals(Difficulty.MEDIUM_VULNERABILITY, Difficulty.MEDIUM.vulnerabilityMultiplier, 0f);
        assertEquals(1f, Difficulty.MEDIUM.kitScale, 0f);
        assertEquals(1f, Difficulty.MEDIUM.damageTakenScale, 0f);
    }

    @Test
    public void everyDifficultyIsMediumTimesItsMultipliers() {
        for (Difficulty d : Difficulty.values()) {
            assertEquals(d.name(), Math.round(Difficulty.MEDIUM_START_HP * d.kitScale), d.startWarStrength);
            assertEquals(d.name(), Math.round(Difficulty.MEDIUM_START_MP * d.kitScale), d.startSpiritualStrength);
            assertEquals(d.name(), Math.round(Difficulty.MEDIUM_START_ARROWS * d.kitScale), d.startArrows);
            assertEquals(d.name(), Difficulty.MEDIUM_VULNERABILITY * d.damageTakenScale,
                    d.vulnerabilityMultiplier, 1e-6f);
        }
    }

    @Test
    public void startHpKeepsTheThreePointLadder() {
        assertEquals(27, Difficulty.EASIEST.startWarStrength);
        assertEquals(24, Difficulty.EASY.startWarStrength);
        assertEquals(21, Difficulty.MEDIUM.startWarStrength);
        assertEquals(18, Difficulty.HARD.startWarStrength);
    }

    @Test
    public void damageTakenKeepsItsQuarterSteps() {
        assertEquals(0.25f, Difficulty.EASIEST.vulnerabilityMultiplier, 1e-6f);
        assertEquals(0.50f, Difficulty.EASY.vulnerabilityMultiplier, 1e-6f);
        assertEquals(0.75f, Difficulty.MEDIUM.vulnerabilityMultiplier, 1e-6f);
        assertEquals(1.00f, Difficulty.HARD.vulnerabilityMultiplier, 1e-6f);
    }

    @Test
    public void easiestNoLongerStartsWithNinetyNineArrows() {
        assertEquals(9, Difficulty.EASIEST.startArrows);
    }

    @Test
    public void easierIsNeverStingierOrMorePunishing() {
        Difficulty[] all = Difficulty.values();
        for (int i = 1; i < all.length; i++) {
            Difficulty easier = all[i - 1], harder = all[i];
            assertTrue(harder.name(), easier.startWarStrength > harder.startWarStrength);
            assertTrue(harder.name(), easier.startSpiritualStrength >= harder.startSpiritualStrength);
            assertTrue(harder.name(), easier.startArrows >= harder.startArrows);
            assertTrue(harder.name(), easier.vulnerabilityMultiplier < harder.vulnerabilityMultiplier);
        }
    }
}
