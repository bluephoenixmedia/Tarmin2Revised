package com.bpm.minotaur.gamedata.gore;

import org.junit.Test;

import java.util.Random;

import static org.junit.Assert.*;

/** Blood trickles from a monster's wound, as it runs down a wall (#220). */
public class WoundDripTest {

    private static WoundDecal wound(float length, float v) {
        return new WoundDecal(WoundDecal.WoundType.SLASH, 0.5f, v, 0f, length, 0.05f, null);
    }

    private static WoundDrip drip(float length, float v, GoreProfile profile) {
        return WoundDrip.start(wound(length, v), profile, GoreLevel.NORMAL, new Random(7));
    }

    @Test
    public void aWoundLargeEnoughToBleedStartsATrickle() {
        WoundDrip d = drip(0.2f, 0.6f, GoreProfile.FLESH);
        assertNotNull(d);
        assertEquals(0f, d.length(), 0f);
        assertTrue(d.isRunning());
    }

    @Test
    public void aSmallWoundNeverDrips() {
        assertNull(drip(WoundDrip.MIN_WOUND_LENGTH - 0.01f, 0.6f, GoreProfile.FLESH));
    }

    @Test
    public void aBloodlessBodyNeverDrips() {
        for (GoreProfile p : GoreProfile.values()) {
            if (!p.hasBlood) assertNull(p.name(), drip(0.2f, 0.6f, p));
        }
    }

    @Test
    public void itRunsDownThenStops() {
        WoundDrip d = drip(0.2f, 0.6f, GoreProfile.FLESH);
        float last = 0f;
        for (int i = 0; i < 200 && d.isRunning(); i++) {
            d.update(0.05f);
            assertTrue("it only grows", d.length() >= last);
            last = d.length();
        }
        assertFalse("it stops", d.isRunning());
        assertEquals(d.target(), d.length(), 1e-4f);
        d.update(1f);
        assertEquals("and stays stopped", d.target(), d.length(), 1e-4f);
    }

    @Test
    public void itSlowsAsItThickens() {
        WoundDrip d = drip(0.2f, 0.9f, GoreProfile.FLESH);
        d.update(0.05f);
        float first = d.length();
        while (d.length() < d.target() * 0.8f) d.update(0.05f);
        float before = d.length();
        d.update(0.05f);
        assertTrue("a late step is shorter than the first", d.length() - before < first);
    }

    @Test
    public void itNeverRunsOffTheBottomOfTheSprite() {
        WoundDrip d = drip(0.2f, 0.05f, GoreProfile.FLESH);
        assertNotNull(d);
        assertEquals("clamped to the wound's height above the bottom", 0.05f, d.target(), 1e-6f);
    }

    @Test
    public void aGoreLevelWithoutDripsNeverDrips() {
        for (GoreLevel level : GoreLevel.values()) {
            WoundDrip d = WoundDrip.start(wound(0.2f, 0.6f), GoreProfile.FLESH, level, new Random(7));
            assertEquals(level.name(), level.drips(), d != null);
        }
    }

    @Test
    public void aFiveDamageWoundBleeds() {
        float fiveDamage = Math.min(0.22f, 0.10f + 5 * 0.002f); // CombatManager.applyCombatHitWound
        assertNotNull(drip(fiveDamage, 0.6f, GoreProfile.FLESH));
    }

    @Test
    public void theTrickleIsTheBodysBloodColour() {
        assertEquals(GoreProfile.SKELETAL.primaryColor, drip(0.2f, 0.6f, GoreProfile.SKELETAL).color());
    }

    @Test
    public void aSpriteDrawnTwiceInAFrameDripsOnce() {
        WoundDrip a = drip(0.2f, 0.9f, GoreProfile.FLESH);
        WoundDrip b = drip(0.2f, 0.9f, GoreProfile.FLESH);
        a.updateOnce(1, 0.1f);
        a.updateOnce(1, 0.1f);
        b.updateOnce(1, 0.1f);
        assertEquals(b.length(), a.length(), 0f);
    }

    @Test
    public void onlyAVisibleStepAsksForARecomposite() {
        WoundDrip d = drip(0.2f, 0.9f, GoreProfile.FLESH);
        int recomposites = 0;
        int frames = 0;
        while (d.isRunning()) {
            if (d.update(1f / 60f)) recomposites++;
            frames++;
        }
        assertTrue("far fewer re-composites than frames", recomposites * 4 < frames);
        assertTrue(recomposites <= Math.ceil(d.target() / WoundDrip.STEP) + 1);
        assertFalse("none once it has stopped", d.update(1f));
    }
}
