package com.bpm.minotaur.gamedata.gore;

import org.junit.Test;

import static org.junit.Assert.*;

public class HitReactionTest {

    @Test
    public void aScratchFlinchesALittle() {
        float w = HitReaction.weight(1, 100, false);
        assertEquals(0.10f, HitReaction.recoilDistance(w), 0.01f);
    }

    @Test
    public void aCritOrAHeavyBlowStaggersFarAndFlashesLong() {
        assertEquals(1f, HitReaction.weight(1, 100, true), 0f);
        assertEquals(1f, HitReaction.weight(35, 100, false), 0.001f);
        assertEquals(0.35f, HitReaction.recoilDistance(1f), 0.001f);
        assertTrue(HitReaction.flashSeconds(1f) > HitReaction.flashSeconds(0f));
    }

    @Test
    public void recoilGrowsWithTheShareOfHealthTaken() {
        assertTrue(HitReaction.recoilDistance(HitReaction.weight(20, 100, false))
                > HitReaction.recoilDistance(HitReaction.weight(5, 100, false)));
    }

    @Test
    public void nothingTakenIsNoWeight() {
        assertEquals(0f, HitReaction.weight(0, 100, false), 0f);
        assertEquals(1f, HitReaction.weight(5, 0, false), 0f);
    }
}
