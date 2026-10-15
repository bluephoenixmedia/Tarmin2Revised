package com.bpm.minotaur.gamedata.item;

import org.junit.Test;

import static org.junit.Assert.*;

/** The Father's Sword returns with every Incarnation, and its perk deepens with the soul (#187). */
public class FathersSwordTest {

    @Test
    public void theSwordIsTheRustySword() {
        assertEquals(Item.ItemType.RUSTY_SWORD, FathersSword.TYPE);
        assertTrue(FathersSword.isFathersSword(new Item(Item.ItemType.RUSTY_SWORD, 0, 0, ItemColor.GRAY, null, null)));
        assertFalse(FathersSword.isFathersSword(null));
    }

    @Test
    public void aSoulWithNoMilestonesHasTheStartingPerk() {
        FathersSword.Perk perk = FathersSword.perkFor(0, 0);
        assertEquals(0, perk.steps);
        assertEquals(3, perk.maxHpBonus);
        assertEquals(0.05f, perk.critBonus, 1e-6f);
    }

    @Test
    public void deeperStrataDeepenThePerkSlowly() {
        assertEquals(0, FathersSword.milestoneSteps(FathersSword.STRATA_PER_STEP - 1, 0));
        assertEquals(1, FathersSword.milestoneSteps(FathersSword.STRATA_PER_STEP, 0));
        FathersSword.Perk perk = FathersSword.perkFor(FathersSword.STRATA_PER_STEP, 0);
        assertEquals(FathersSword.BASE_MAX_HP_BONUS + FathersSword.MAX_HP_PER_STEP, perk.maxHpBonus);
        assertEquals(FathersSword.BASE_CRIT_BONUS + FathersSword.CRIT_PER_STEP, perk.critBonus, 1e-6f);
    }

    @Test
    public void eachSealWonIsAStep() {
        assertEquals(2, FathersSword.milestoneSteps(0, 2));
        assertEquals(3, FathersSword.milestoneSteps(FathersSword.STRATA_PER_STEP, 2));
    }

    @Test
    public void theDeepeningIsCapped() {
        FathersSword.Perk perk = FathersSword.perkFor(999, 999);
        assertEquals(FathersSword.MAX_STEPS, perk.steps);
        assertEquals(FathersSword.BASE_MAX_HP_BONUS + FathersSword.MAX_STEPS * FathersSword.MAX_HP_PER_STEP,
                perk.maxHpBonus);
        assertTrue("still a small perk", perk.critBonus <= 0.10f + 1e-6f);
    }

    @Test
    public void negativeInputsCountAsNoMilestones() {
        assertEquals(0, FathersSword.milestoneSteps(-5, -1));
    }

    @Test
    public void deepestLevelIsCountedInStrataBelowTheSurface() {
        assertEquals(0, FathersSword.strataBelowSurface(0));
        assertEquals(0, FathersSword.strataBelowSurface(1));
        assertEquals(1, FathersSword.strataBelowSurface(2));
    }
}
