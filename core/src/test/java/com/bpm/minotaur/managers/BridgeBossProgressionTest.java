package com.bpm.minotaur.managers;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Killing the bridge boss resets integrity to zero, so the boss has to get
 * harder each time it is summoned.
 *
 * <p>These are a pair, not two features. A full reset with a fixed-difficulty
 * boss permanently removes the 50-death cap for anyone who can win once, and
 * the apocalypse becomes unreachable. The escalating summon is what keeps an
 * ending in the game.
 *
 * <p>These exercise the scaling arithmetic directly; the counter's own
 * persistence needs a LibGDX Files backend the test classpath does not carry.
 */
public class BridgeBossProgressionTest {

    /** Mirrors DoomManager.getBridgeBossHpBonus. */
    private int hpBonusFor(int baseHpBonus, int summons) {
        int extra = Math.max(0, summons - 1);
        return baseHpBonus + Math.round(baseHpBonus * 0.5f * extra);
    }

    @Test
    public void theFirstSummoningUsesTheAuthoredDifficulty() {
        assertEquals("The first fight should be the one the theme data describes",
                120, hpBonusFor(120, 1));
    }

    @Test
    public void everyLaterSummoningIsStrictlyHarder() {
        int previous = hpBonusFor(120, 1);
        for (int summons = 2; summons <= 8; summons++) {
            int current = hpBonusFor(120, summons);
            assertTrue("Summon " + summons + " (" + current + ") must exceed summon "
                    + (summons - 1) + " (" + previous + ")", current > previous);
            previous = current;
        }
    }

    @Test
    public void escalationIsSteepEnoughToMatter() {
        // A token increase would leave the cap effectively removed, which is the
        // whole risk of resetting integrity to zero rather than cutting it.
        assertTrue("By the fourth summoning the boss should be at least double",
                hpBonusFor(120, 4) >= 240);
    }

    @Test
    public void aZeroOrNegativeSummonCountCannotWeakenTheBoss() {
        // Defensive: a corrupt or absent counter in an old save must not produce
        // a boss weaker than the authored one.
        assertEquals(120, hpBonusFor(120, 0));
        assertEquals(120, hpBonusFor(120, -3));
    }

    @Test
    public void theScalingMatchesTheProductionFormula() {
        // Guards the mirror above against drifting from DoomManager.
        DoomManager doom = DoomManager.getInstance();
        assertNotNull(doom);
        assertEquals("With no summons recorded, the authored value stands",
                hpBonusFor(120, doom.getBridgeBossSummons()),
                doom.getBridgeBossHpBonus(120));
    }
}
