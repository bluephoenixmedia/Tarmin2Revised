package com.bpm.minotaur.gamedata.spells;

import com.bpm.minotaur.gamedata.spells.SpellbookStudy.Failure;
import com.bpm.minotaur.gamedata.spells.SpellbookStudy.Mishap;
import org.junit.Test;

import java.util.EnumSet;
import java.util.Random;
import java.util.Set;

import static org.junit.Assert.*;

public class SpellbookStudyTest {

    @Test
    public void abilityFollowsTheNetHackFormula() {
        // INT 10, level 4, book level 3: 10 + 4 + 2 - 6 = 10
        assertEquals(10, SpellbookStudy.ability(10, 4, 3));
        assertEquals(0.5f, SpellbookStudy.failureChance(10, 4, 3), 0.001f);
    }

    @Test
    public void aBrilliantReaderNeverFailsAndAHopelessOneAlwaysDoes() {
        assertEquals(0f, SpellbookStudy.failureChance(18, 20, 1), 0.001f);
        assertEquals(1f, SpellbookStudy.failureChance(8, 1, 9), 0.001f);
    }

    @Test
    public void moreIntelligenceAndExperienceLowerTheRisk() {
        float base = SpellbookStudy.failureChance(10, 3, 4);
        assertTrue(SpellbookStudy.failureChance(14, 3, 4) < base);
        assertTrue(SpellbookStudy.failureChance(10, 9, 4) < base);
        assertTrue("a harder book is riskier", SpellbookStudy.failureChance(10, 3, 6) > base);
    }

    @Test
    public void onlyABookAboveTheReadersLevelIsRisky() {
        assertFalse(SpellbookStudy.isRisky(3, 3));
        assertFalse(SpellbookStudy.isRisky(3, 7));
        assertTrue(SpellbookStudy.isRisky(5, 4));
    }

    @Test
    public void mishapsGrowWorseWithTheBooksLevel() {
        Set<Mishap> low = seen(2);
        assertTrue(EnumSet.of(Mishap.STING, Mishap.CONFUSION).containsAll(low));
        Set<Mishap> mid = seen(4);
        assertTrue(EnumSet.of(Mishap.BLINDNESS, Mishap.HALLUCINATION).containsAll(mid));
        Set<Mishap> high = seen(6);
        assertTrue(EnumSet.of(Mishap.WOUND, Mishap.POISON).containsAll(high));
        assertEquals(EnumSet.of(Mishap.EXPLOSION), seen(8));
    }

    @Test
    public void aFailedReadingCostsTurnsAndAnExplosionAlwaysDestroysTheBook() {
        Random rng = new Random(5);
        for (int i = 0; i < 200; i++) {
            Failure f = SpellbookStudy.rollFailure(9, rng);
            assertEquals(Mishap.EXPLOSION, f.mishap);
            assertTrue(f.destroysBook);
            assertTrue(f.delayTurns >= 1 && f.delayTurns <= SpellbookStudy.MAX_DELAY_TURNS);
        }
        assertTrue("a higher book keeps the reader down longer",
                SpellbookStudy.rollFailure(6, new Random(1)).delayTurns
                        > SpellbookStudy.rollFailure(1, new Random(1)).delayTurns);
    }

    @Test
    public void roughlyAThirdOfOrdinaryMishapsDestroyTheBook() {
        Random rng = new Random(9);
        int destroyed = 0;
        int trials = 3000;
        for (int i = 0; i < trials; i++) {
            if (SpellbookStudy.rollFailure(3, rng).destroysBook) {
                destroyed++;
            }
        }
        assertEquals(trials / 3.0, destroyed, trials * 0.05);
    }

    private static Set<Mishap> seen(int bookLevel) {
        Random rng = new Random(2);
        Set<Mishap> seen = EnumSet.noneOf(Mishap.class);
        for (int i = 0; i < 100; i++) {
            seen.add(SpellbookStudy.rollFailure(bookLevel, rng).mishap);
        }
        return seen;
    }
}
