package com.bpm.minotaur.rendering.vfx;

import org.junit.Test;

import static org.junit.Assert.*;

public class DamageFlashTest {

    @Test
    public void quietUntilThePlayerIsHurt() {
        DamageFlash flash = new DamageFlash();
        assertFalse(flash.isActive());
        assertEquals(0f, flash.alpha(), 0.0001f);
    }

    @Test
    public void aHitFlashesThenFadesAwayWithinAFractionOfASecond() {
        DamageFlash flash = new DamageFlash();
        flash.trigger(0.2f);
        float start = flash.alpha();
        assertTrue(start > 0f);

        flash.update(DamageFlash.DURATION / 2f);
        assertTrue("fading", flash.alpha() < start && flash.alpha() > 0f);

        flash.update(DamageFlash.DURATION);
        assertFalse(flash.isActive());
        assertEquals(0f, flash.alpha(), 0.0001f);
    }

    @Test
    public void aBiggerBlowFlashesHarderButNeverPastTheCap() {
        DamageFlash small = new DamageFlash();
        small.trigger(0.05f);
        DamageFlash big = new DamageFlash();
        big.trigger(0.5f);
        DamageFlash huge = new DamageFlash();
        huge.trigger(5f);

        assertTrue(big.alpha() > small.alpha());
        assertTrue("the centre of the screen is never hidden", huge.alpha() <= DamageFlash.MAX_ALPHA + 0.0001f);
    }

    @Test
    public void aSecondHitNeverDimsAFlashStillShowing() {
        DamageFlash flash = new DamageFlash();
        flash.trigger(0.5f);
        float strong = flash.alpha();
        flash.trigger(0.01f);
        assertTrue(flash.alpha() >= strong - 0.0001f);
    }
}
