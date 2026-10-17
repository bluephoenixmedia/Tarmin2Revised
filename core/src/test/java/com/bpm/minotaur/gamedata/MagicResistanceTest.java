package com.bpm.minotaur.gamedata;

import org.junit.Test;

import java.util.Random;

import static org.junit.Assert.*;

public class MagicResistanceTest {

    @Test
    public void aPercentageTakesThatShareOffSpellDamage() {
        assertEquals(20, MagicResistance.reduce(20, 0));
        assertEquals(10, MagicResistance.reduce(20, 50));
        assertEquals(15, MagicResistance.reduce(20, 25));
    }

    @Test
    public void aResistedSpellStillDoesAtLeastOneDamage() {
        assertEquals(1, MagicResistance.reduce(3, 75));
        assertEquals(1, MagicResistance.reduce(1, 90));
    }

    @Test
    public void noDamageStaysNoDamageAndNegativeResistanceIsIgnored() {
        assertEquals(0, MagicResistance.reduce(0, 50));
        assertEquals(20, MagicResistance.reduce(20, -30));
    }

    @Test
    public void resistanceIsCappedSoNothingBecomesImmune() {
        assertEquals(MagicResistance.PLAYER_CAP, MagicResistance.clampForPlayer(400));
        assertEquals(0, MagicResistance.clampForPlayer(-5));
        assertEquals(40, MagicResistance.clampForPlayer(40));
        assertEquals(MagicResistance.MONSTER_CAP, MagicResistance.clampForMonster(400));
        assertTrue(MagicResistance.PLAYER_CAP < MagicResistance.MONSTER_CAP);
    }

    @Test
    public void onlyTheElementsAreMagicalAndBladesAndBluntForceAreNot() {
        for (DamageType t : new DamageType[] { DamageType.FIRE, DamageType.ICE, DamageType.DARK,
                DamageType.LIGHT, DamageType.SORCERY, DamageType.MAGICAL }) {
            assertTrue(t + " is magical", MagicResistance.isMagical(t));
        }
        for (DamageType t : new DamageType[] { DamageType.PHYSICAL, DamageType.SPIRITUAL, DamageType.POISON,
                DamageType.BLEED, DamageType.DISEASE }) {
            assertFalse(t + " is not magical", MagicResistance.isMagical(t));
        }
        assertFalse(MagicResistance.isMagical(null));
    }

    @Test
    public void noResistanceNeverResistsAndFullResistanceAlwaysDoes() {
        for (int i = 0; i < 200; i++) {
            assertFalse(MagicResistance.resists(0));
            assertTrue(MagicResistance.resists(100));
        }
    }

    @Test
    public void aStatusEffectIsResistedAtTheSameRateAsDamageIsCut() {
        Random rng = new Random(11);
        int resisted = 0;
        int trials = 4000;
        for (int i = 0; i < trials; i++) {
            if (MagicResistance.resists(30, rng)) {
                resisted++;
            }
        }
        assertEquals(trials * 0.30, resisted, trials * 0.04);
        assertFalse(MagicResistance.resists(0, rng));
    }
}
