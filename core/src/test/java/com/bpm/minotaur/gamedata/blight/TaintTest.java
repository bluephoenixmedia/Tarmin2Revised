package com.bpm.minotaur.gamedata.blight;

import com.bpm.minotaur.gamedata.player.PlayerStats;
import org.junit.Test;

import static org.junit.Assert.*;

/** Pins the Taint rules in section 8 of the Procedural World &amp; Blighted Marches requirements. */
public class TaintTest {

    @Test
    public void gainDoublesAtNightAddsInRotAndHalvesUnderTheWard() {
        float day = Taint.gainPerTurn(false, false, false);
        assertEquals(Taint.BASE_RATE, day, 1e-6f);
        assertEquals(day * 2f, Taint.gainPerTurn(true, false, false), 1e-6f);
        assertEquals(day + Taint.ROT_POOL_RATE, Taint.gainPerTurn(false, true, false), 1e-6f);
        assertEquals(day * Taint.WARD_MULT, Taint.gainPerTurn(false, false, true), 1e-6f);
    }

    @Test
    public void tiersFollowTheThresholds() {
        assertSame(Taint.Tier.CLEAN, Taint.tierOf(0f));
        assertSame(Taint.Tier.TAINTED, Taint.tierOf(0.5f));
        assertSame(Taint.Tier.FESTERING, Taint.tierOf(25f));
        assertSame(Taint.Tier.WASTING, Taint.tierOf(50f));
        assertSame(Taint.Tier.CONSUMED, Taint.tierOf(75f));
        assertSame(Taint.Tier.CLAIMED, Taint.tierOf(100f));
        for (Taint.Tier t : Taint.Tier.values()) {
            if (t != Taint.Tier.CLEAN) assertNotNull(t + " announces itself", Taint.onEnter(t));
        }
    }

    @Test
    public void regenSlowsAtTwentyFiveAndMaxHpShrinksAtFiftyAndSeventyFive() {
        assertEquals(1, Taint.regenIntervalMult(24.9f));
        assertEquals(2, Taint.regenIntervalMult(25f));
        assertEquals(1f, Taint.maxHpMult(49.9f), 0f);
        assertEquals(0.85f, Taint.maxHpMult(50f), 0f);
        assertEquals(0.70f, Taint.maxHpMult(75f), 0f);
        assertFalse(Taint.rousesLegion(99.9f));
        assertTrue(Taint.rousesLegion(100f));
    }

    @Test
    public void playerStatsClampTaintAndApplyItToMaxHp() {
        PlayerStats stats = new PlayerStats(com.bpm.minotaur.gamedata.Difficulty.MEDIUM);
        stats.setMaxHP(100);
        int base = stats.getMaxHP();

        stats.addTaint(500f);
        assertEquals(Taint.MAX, stats.getTaint(), 0f);
        assertEquals(Math.round(base * 0.70f), stats.getMaxHP());

        stats.addTaint(-Taint.ASHWATER_CLEANSE);
        assertEquals(60f, stats.getTaint(), 0f);
        assertEquals(Math.round(base * 0.85f), stats.getMaxHP());

        stats.addTaint(-1000f);
        assertEquals(0f, stats.getTaint(), 0f);
        assertEquals(base, stats.getMaxHP());
        assertEquals("the stored maximum is never touched", 100, stats.getBaseMaxHP());
    }
}
