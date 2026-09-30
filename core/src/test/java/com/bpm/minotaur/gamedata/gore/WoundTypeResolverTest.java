package com.bpm.minotaur.gamedata.gore;

import com.bpm.minotaur.gamedata.DamageType;
import com.bpm.minotaur.rendering.animation.AnimationArchetype;
import org.junit.Test;

import static com.bpm.minotaur.gamedata.gore.WoundDecal.WoundType.*;
import static org.junit.Assert.assertEquals;

public class WoundTypeResolverTest {

    @Test
    public void theWeaponsOwnDamageTypeWinsOverItsAnimation() {
        // A club swung with a slashing animation still bruises.
        assertEquals(CRUSH, WoundTypeResolver.resolve("BLUDGEONING", AnimationArchetype.SLASHING_1H, false, null));
        // A halberd animated as a sweep but piercing in the data stabs.
        assertEquals(STAB, WoundTypeResolver.resolve("PIERCING", AnimationArchetype.POLEARM_SWEEP, false, null));
        assertEquals(SLASH, WoundTypeResolver.resolve("SLASHING", AnimationArchetype.BLUNT_CRUSHING, false, null));
    }

    @Test
    public void finesseBladesLeaveNarrowCuts() {
        assertEquals(SLICE, WoundTypeResolver.resolve("SLASHING", AnimationArchetype.SLASHING_1H, true, null));
        assertEquals(STAB, WoundTypeResolver.resolve("PIERCING", AnimationArchetype.THRUSTING_PIERCE, true, null));
    }

    @Test
    public void bowsAndGunsPunctureWhateverTheDataSays() {
        assertEquals(PUNCTURE, WoundTypeResolver.resolve("PIERCING", AnimationArchetype.RANGED_FIREARM, false, null));
        assertEquals(PUNCTURE, WoundTypeResolver.resolve("PIERCING", AnimationArchetype.RANGED_BOW, false, null));
    }

    @Test
    public void fireAndMagicBurn() {
        assertEquals(SCORCH, WoundTypeResolver.resolve("SLASHING", AnimationArchetype.SLASHING_1H, false, DamageType.FIRE));
        assertEquals(SCORCH, WoundTypeResolver.resolve(null, null, false, DamageType.MAGICAL));
    }

    @Test
    public void aMissingDamageTypeFallsBackToTheAnimation() {
        assertEquals(CRUSH, WoundTypeResolver.resolve(null, AnimationArchetype.BLUNT_CRUSHING, false, null));
        assertEquals(STAB, WoundTypeResolver.resolve("SPIRITUAL", AnimationArchetype.THRUSTING_PIERCE, false, null));
        assertEquals(SLASH, WoundTypeResolver.resolve(null, null, false, null));
    }
}
