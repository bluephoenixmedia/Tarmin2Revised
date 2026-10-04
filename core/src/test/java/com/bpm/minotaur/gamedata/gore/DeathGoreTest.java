package com.bpm.minotaur.gamedata.gore;

import com.bpm.minotaur.gamedata.spells.VisualArchetype;
import com.bpm.minotaur.rendering.animation.AnimationArchetype;
import org.junit.Test;

import static org.junit.Assert.*;

public class DeathGoreTest {

    // --- Overkill tiers ---

    @Test
    public void tiersStartAtFifteenAndThirtyFivePercentOverkill() {
        assertEquals(0, DeathGore.overkillTier(14, 100, false, GoreLevel.NORMAL));
        assertEquals(1, DeathGore.overkillTier(15, 100, false, GoreLevel.NORMAL));
        assertEquals(1, DeathGore.overkillTier(34, 100, false, GoreLevel.NORMAL));
        assertEquals(2, DeathGore.overkillTier(35, 100, false, GoreLevel.NORMAL));
    }

    @Test
    public void critsAndFinishersAlwaysReachTierOne() {
        assertEquals(1, DeathGore.overkillTier(0, 100, true, GoreLevel.NORMAL));
        assertEquals(2, DeathGore.overkillTier(40, 100, true, GoreLevel.NORMAL));
    }

    @Test
    public void brutalHalvesTheFullGibThreshold() {
        assertEquals(1, DeathGore.overkillTier(17, 100, false, GoreLevel.NORMAL));
        assertEquals(2, DeathGore.overkillTier(18, 100, false, GoreLevel.BRUTAL));
    }

    @Test
    public void goreOffNeverGibs() {
        assertEquals(0, DeathGore.overkillTier(500, 100, true, GoreLevel.OFF));
    }

    @Test
    public void aZeroHpMonsterDoesNotDivideByZero() {
        assertEquals(2, DeathGore.overkillTier(5, 0, false, GoreLevel.NORMAL));
    }

    // --- Weapon deaths ---

    @Test
    public void aCleanKillPlaysTheDeathArtAndLeavesABody() {
        DeathGore.Plan p = DeathGore.plan(KillCause.weapon(AnimationArchetype.SLASHING_1H, false), 0, GoreLevel.NORMAL);
        assertEquals(DeathGore.Style.CLEAN, p.style);
        assertTrue(p.playDeathArt);
        assertFalse(p.gorePile);
        assertEquals(CorpseFinish.NONE, p.finish);
    }

    @Test
    public void tierOnePlaysTheDeathArtWithTheWeaponsGore() {
        DeathGore.Plan p = DeathGore.plan(KillCause.weapon(AnimationArchetype.BLUNT_CRUSHING, false), 1, GoreLevel.NORMAL);
        assertEquals(DeathGore.Style.CRUSH, p.style);
        assertTrue(p.playDeathArt);
    }

    @Test
    public void tierTwoReplacesTheDeathArtWithAGibDeath() {
        DeathGore.Plan slash = DeathGore.plan(KillCause.weapon(AnimationArchetype.SLASHING_2H, false), 2, GoreLevel.NORMAL);
        assertEquals(DeathGore.Style.SLICE, slash.style);
        assertFalse(slash.playDeathArt);
        assertTrue(slash.gorePile);

        assertEquals(DeathGore.Style.CRUSH,
                DeathGore.plan(KillCause.weapon(AnimationArchetype.FLAIL_WHIP, false), 2, GoreLevel.NORMAL).style);
        assertEquals(DeathGore.Style.FOUNTAIN,
                DeathGore.plan(KillCause.weapon(AnimationArchetype.THRUSTING_PIERCE, false), 2, GoreLevel.NORMAL).style);
    }

    @Test
    public void aHeavyRangedOverkillBlowsOutTheFarSide() {
        assertEquals(DeathGore.Style.RANGED_BURST,
                DeathGore.plan(KillCause.weapon(AnimationArchetype.RANGED_FIREARM, false), 2, GoreLevel.NORMAL).style);
        assertEquals(DeathGore.Style.RANGED_BURST,
                DeathGore.plan(KillCause.weapon(AnimationArchetype.RANGED_BOW, false), 2, GoreLevel.NORMAL).style);
        assertEquals(DeathGore.Style.FOUNTAIN,
                DeathGore.plan(KillCause.weapon(AnimationArchetype.RANGED_FIREARM, false), 1, GoreLevel.NORMAL).style);
    }

    @Test
    public void aSlashingCritTakesTheHeadAndWinsOverDeathArt() {
        DeathGore.Plan p = DeathGore.plan(KillCause.weapon(AnimationArchetype.AXE_CHOPPING, true), 1, GoreLevel.NORMAL);
        assertEquals(DeathGore.Style.DECAPITATE, p.style);
        assertFalse(p.playDeathArt);
        assertFalse(p.gorePile);
        assertEquals(CorpseFinish.HEADLESS, p.finish);
    }

    @Test
    public void aBluntCritIsNotADecapitation() {
        assertEquals(DeathGore.Style.CRUSH,
                DeathGore.plan(KillCause.weapon(AnimationArchetype.BLUNT_CRUSHING, true), 1, GoreLevel.NORMAL).style);
    }

    @Test
    public void aKillWithNoWeaponOrSpellGoesByTier() {
        assertEquals(DeathGore.Style.CLEAN, DeathGore.plan(KillCause.none(), 1, GoreLevel.NORMAL).style);
        assertEquals(DeathGore.Style.GIB, DeathGore.plan(KillCause.none(), 2, GoreLevel.NORMAL).style);
        assertEquals(DeathGore.Style.GIB, DeathGore.plan(null, 2, GoreLevel.NORMAL).style);
    }

    // --- Elemental deaths ---

    @Test
    public void fireCharsTheBodyAtAnyTier() {
        DeathGore.Plan p = DeathGore.plan(KillCause.spell(VisualArchetype.FLAME_BOLT), 0, GoreLevel.NORMAL);
        assertEquals(DeathGore.Style.CHARRED, p.style);
        assertTrue(p.playDeathArt);
        assertEquals(CorpseFinish.CHARRED, p.finish);
        assertFalse(p.gibs);
        assertFalse(p.blood);
    }

    @Test
    public void anExplosionCharsAndGibsFromTierOne() {
        assertFalse(DeathGore.plan(KillCause.spell(VisualArchetype.EXPLOSIVE_BURST), 0, GoreLevel.NORMAL).gibs);
        DeathGore.Plan p = DeathGore.plan(KillCause.spell(VisualArchetype.EXPLOSIVE_BURST), 1, GoreLevel.NORMAL);
        assertEquals(DeathGore.Style.CHARRED, p.style);
        assertTrue(p.gibs);
        assertEquals(CorpseFinish.CHARRED, p.finish);
    }

    @Test
    public void frostShattersLightningAshesToxinMelts() {
        DeathGore.Plan frost = DeathGore.plan(KillCause.spell(VisualArchetype.FROST_RAY), 0, GoreLevel.NORMAL);
        assertEquals(DeathGore.Style.FROST_SHATTER, frost.style);
        assertFalse(frost.playDeathArt);
        assertEquals(CorpseFinish.FROZEN, frost.finish);

        assertEquals(DeathGore.Style.ASH,
                DeathGore.plan(KillCause.spell(VisualArchetype.LIGHTNING_ARC), 0, GoreLevel.NORMAL).style);
        assertEquals(DeathGore.Style.ASH,
                DeathGore.plan(KillCause.spell(VisualArchetype.THUNDER_CONCUSSION), 0, GoreLevel.NORMAL).style);
        assertEquals(CorpseFinish.ASH,
                DeathGore.plan(KillCause.spell(VisualArchetype.LIGHTNING_ARC), 0, GoreLevel.NORMAL).finish);

        DeathGore.Plan melt = DeathGore.plan(KillCause.spell(VisualArchetype.TOXIC_CLOUD), 0, GoreLevel.NORMAL);
        assertEquals(DeathGore.Style.MELT, melt.style);
        assertEquals(CorpseFinish.MELTED, melt.finish);
    }

    @Test
    public void forceAndPsychicHitOneTierHarder() {
        DeathGore.Plan p = DeathGore.plan(KillCause.spell(VisualArchetype.FORCE_MISSILE), 1, GoreLevel.NORMAL);
        assertEquals(2, p.tier);
        assertEquals(DeathGore.Style.GIB, p.style);
        assertEquals(1, DeathGore.plan(KillCause.spell(VisualArchetype.PSYCHIC_SHOCK), 0, GoreLevel.NORMAL).tier);
    }

    @Test
    public void otherSpellsGoreByTier() {
        assertEquals(DeathGore.Style.CLEAN,
                DeathGore.plan(KillCause.spell(VisualArchetype.NECROTIC_DRAIN), 1, GoreLevel.NORMAL).style);
        assertEquals(DeathGore.Style.GIB,
                DeathGore.plan(KillCause.spell(VisualArchetype.HOLY_RADIANCE), 2, GoreLevel.NORMAL).style);
    }

    @Test
    public void goreOffIsAlwaysACleanDeathWithItsArt() {
        DeathGore.Plan p = DeathGore.plan(KillCause.weapon(AnimationArchetype.SLASHING_1H, true), 2, GoreLevel.OFF);
        assertEquals(DeathGore.Style.CLEAN, p.style);
        assertTrue(p.playDeathArt);
        assertFalse(p.gorePile);
        assertEquals(CorpseFinish.NONE, p.finish);
        assertEquals(DeathGore.Style.CLEAN,
                DeathGore.plan(KillCause.spell(VisualArchetype.FROST_RAY), 0, GoreLevel.OFF).style);
    }
}
