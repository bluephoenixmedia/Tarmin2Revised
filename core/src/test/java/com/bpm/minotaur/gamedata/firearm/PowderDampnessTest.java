package com.bpm.minotaur.gamedata.firearm;

import org.junit.Test;

import java.util.Random;

import static org.junit.Assert.*;

/**
 * Wet powder misfires.
 *
 * <p>Dampness is carried on the player rather than read from the weather directly. The
 * weather's own wetness is outdoor-only, so reading it live would mean a musket is
 * perfectly reliable in every dungeon in the game -- which is where nearly all play
 * happens, so the mechanic would never be seen. Carrying it means walking in from a
 * storm leaves your powder damp for a while, and the surface/underground transition
 * finally has a consequence.
 *
 * <p>A misfire wastes the shot but not the reload. Losing both would stack a random
 * failure on top of a three-turn commitment, which reads as the game cheating.
 */
public class PowderDampnessTest {

    /** nextFloat() == 0 clears any chance above zero. */
    private static Random alwaysMisfires() {
        return new Random() {
            @Override
            public float nextFloat() {
                return 0f;
            }
        };
    }

    /** nextFloat() just under 1 fails every chance below certainty. */
    private static Random neverMisfires() {
        return new Random() {
            @Override
            public float nextFloat() {
                return 0.999f;
            }
        };
    }

    @Test
    public void testDryPowderNeverMisfires() {
        assertEquals(0f, PowderDampness.misfireChance(0f), 0.0001f);
        assertFalse(PowderDampness.rollMisfire(0f, alwaysMisfires()));
    }

    @Test
    public void testSoakedPowderMisfiresAboutAQuarterOfTheTime() {
        assertEquals(0.25f, PowderDampness.misfireChance(1f), 0.0001f);
    }

    @Test
    public void testChanceScalesWithDampness() {
        assertEquals(0.125f, PowderDampness.misfireChance(0.5f), 0.0001f);
        assertTrue(PowderDampness.misfireChance(0.8f) > PowderDampness.misfireChance(0.3f));
    }

    @Test
    public void testDampnessIsClampedToASaneRange() {
        assertEquals(0f, PowderDampness.misfireChance(-2f), 0.0001f);
        assertEquals(0.25f, PowderDampness.misfireChance(5f), 0.0001f);
    }

    @Test
    public void testASoakedGunCanStillFire() {
        assertFalse("even soaked, three shots in four should go off",
                PowderDampness.rollMisfire(1f, neverMisfires()));
    }

    @Test
    public void testSoakedPowderDoesFailWhenTheRollSaysSo() {
        assertTrue(PowderDampness.rollMisfire(1f, alwaysMisfires()));
    }

    @Test
    public void testExposureSoaksThePlayerImmediately() {
        assertEquals("stepping into water soaks you outright", 1f,
                PowderDampness.afterExposure(0f), 0.0001f);
        assertEquals(1f, PowderDampness.afterExposure(0.5f), 0.0001f);
    }

    @Test
    public void testDampnessDriesOverAPredictableNumberOfTurns() {
        float damp = 1f;
        int turns = 0;
        while (damp > 0f && turns < 100) {
            damp = PowderDampness.afterDryTurn(damp);
            turns++;
        }

        assertEquals("soaked to dry should take about fifteen turns", 15, turns);
        assertEquals(0f, damp, 0.0001f);
    }

    @Test
    public void testDryingNeverGoesNegative() {
        assertEquals(0f, PowderDampness.afterDryTurn(0f), 0.0001f);
        assertEquals(0f, PowderDampness.afterDryTurn(-1f), 0.0001f);
    }

    @Test
    public void testWeatherOnlySoaksYouWhenItIsActuallyRaining() {
        // Ambient weather wetness below the threshold is drizzle on the flagstones, not
        // enough to spoil powder in a closed flask.
        assertFalse(PowderDampness.isSoakingWeather(0.2f));
        assertTrue(PowderDampness.isSoakingWeather(0.6f));
    }
}
