package com.bpm.minotaur.rendering;

import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.Item.ItemType;
import com.bpm.minotaur.gamedata.item.ItemTemplate;
import com.bpm.minotaur.rendering.animation.AnimationArchetype;
import com.bpm.minotaur.rendering.animation.CombatMotionProfile;
import com.bpm.minotaur.rendering.animation.CombatMotionProfile.Quadrant;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class DirectionalContinuityTest {

    private Item createItem(ItemType type, String friendlyName) {
        return createItem(type, friendlyName, false);
    }

    private Item createItem(ItemType type, String friendlyName, boolean isRanged) {
        ItemTemplate t = new ItemTemplate();
        t.friendlyName = friendlyName;
        t.isWeapon = true;
        t.isRanged = isRanged;
        return Item.fromTemplate(type, t);
    }

    @Test
    public void testQuadrantChainingContinuity() {
        Item sword = createItem(ItemType.SWORD, "Broad Sword");
        AnimationArchetype arch = AnimationArchetype.SLASHING_1H;

        // Opener strike (no previous profile)
        CombatMotionProfile strike1 = CombatMotionProfile.getNextContinuousStrike(arch, sword, null, false, false, false, false);
        assertNotNull(strike1);
        assertNotNull(strike1.entryQuadrant);
        assertNotNull(strike1.exitQuadrant);

        // Next strike must match previous exitQuadrant
        CombatMotionProfile strike2 = CombatMotionProfile.getNextContinuousStrike(arch, sword, strike1, false, false, false, false);
        assertNotNull(strike2);
        assertEquals("Strike 2 entry quadrant must match Strike 1 exit quadrant",
                strike1.exitQuadrant, strike2.entryQuadrant);

        // Strike 3 must match Strike 2 exitQuadrant
        CombatMotionProfile strike3 = CombatMotionProfile.getNextContinuousStrike(arch, sword, strike2, false, false, false, false);
        assertNotNull(strike3);
        assertEquals("Strike 3 entry quadrant must match Strike 2 exit quadrant",
                strike2.exitQuadrant, strike3.entryQuadrant);
    }

    @Test
    public void testDynamicTempoWindows() {
        Item dagger = createItem(ItemType.KNIFE, "Stiletto Dagger");
        assertEquals("Daggers should have rapid 0.40s combo window",
                0.40f, CombatMotionProfile.getComboWindowMax(AnimationArchetype.THRUSTING_PIERCE, dagger), 0.01f);

        Item sword = createItem(ItemType.SWORD, "Broad Sword");
        assertEquals("1H weapons should have 0.55s combo window",
                0.55f, CombatMotionProfile.getComboWindowMax(AnimationArchetype.SLASHING_1H, sword), 0.01f);

        Item claymore = createItem(ItemType.TWO_HANDED_SWORD, "Claymore");
        assertEquals("2H colossal weapons should have 0.85s combo window",
                0.85f, CombatMotionProfile.getComboWindowMax(AnimationArchetype.SLASHING_2H, claymore), 0.01f);
    }

    @Test
    public void testDeflectionRiposteGeneration() {
        Item sword = createItem(ItemType.SWORD, "Broad Sword");
        CombatMotionProfile opener = CombatMotionProfile.getNextContinuousStrike(AnimationArchetype.SLASHING_1H, sword, null, false, false, false, false);

        CombatMotionProfile riposte = CombatMotionProfile.getNextContinuousStrike(AnimationArchetype.SLASHING_1H, sword, opener, false, false, false, true);
        assertNotNull(riposte);
        assertTrue("Strike generated after deflection must be flagged as riposte", riposte.isRiposte);
        assertTrue("Riposte should have amplified damage", riposte.damageMultiplier >= 1.20f);
        assertTrue("Riposte should have fast snappy duration", riposte.duration <= 0.28f);
    }

    @Test
    public void testCritAndFinisherGeneration() {
        Item axe = createItem(ItemType.AXE, "Battle Axe");
        CombatMotionProfile fin = CombatMotionProfile.getNextContinuousStrike(AnimationArchetype.AXE_CHOPPING, axe, null, true, false, false, false);
        assertNotNull(fin);
        assertTrue("Finisher flag must be set", fin.isFinisher);
        assertTrue("Finisher screen trauma must be high", fin.screenTrauma >= 0.40f);

        CombatMotionProfile crit = CombatMotionProfile.getNextContinuousStrike(AnimationArchetype.AXE_CHOPPING, axe, null, false, true, false, false);
        assertNotNull(crit);
        assertTrue("Crit damage multiplier must be high", crit.damageMultiplier >= 1.40f);
    }

    @Test
    public void testExpandedMotionPoolVariety() {
        Item sword = createItem(ItemType.SWORD, "Broad Sword");
        List<CombatMotionProfile> pool = CombatMotionProfile.getExpandedMotionPool(AnimationArchetype.SLASHING_1H, sword);
        assertTrue("Expanded motion pool must have at least 6 distinct kinetic profiles", pool.size() >= 6);

        long uniqueNames = pool.stream().map(p -> p.comboName).distinct().count();
        assertTrue("Expanded motion pool must have varied distinct move names", uniqueNames >= 6);
    }

    @Test
    public void testRangedBowAndCrossbowKinematics() {
        Item crossbow = createItem(ItemType.CROSSBOW, "Light Crossbow", true);

        CombatMotionProfile profile = CombatMotionProfile.getNextContinuousStrike(
                AnimationArchetype.RANGED_BOW, crossbow, null, false, false, false, false);
        assertNotNull(profile);
        assertTrue("Ranged bow must be flagged as isRangedDraw", profile.isRangedDraw);
        assertFalse("Ranged bow must not be a thrust", profile.isThrust);
        assertEquals("Ranged bow must not swing or rotate like a sword", 0f, profile.startRotation, 0.01f);
        assertEquals("Ranged bow must not swing or rotate like a sword", 0f, profile.endRotation, 0.01f);
        assertEquals("PRECISION RELEASE", profile.comboName);

        // Subsequent strike in chain should also remain ranged draw, not cycle into quadrant slashes
        CombatMotionProfile chainStrike = CombatMotionProfile.getNextContinuousStrike(
                AnimationArchetype.RANGED_BOW, crossbow, profile, false, false, false, false);
        assertNotNull(chainStrike);
        assertTrue("Chain strike for ranged bow must remain isRangedDraw", chainStrike.isRangedDraw);
        assertEquals(0f, chainStrike.startRotation, 0.01f);

        // Crit strike
        CombatMotionProfile critStrike = CombatMotionProfile.getNextContinuousStrike(
                AnimationArchetype.RANGED_BOW, crossbow, null, false, true, false, false);
        assertNotNull(critStrike);
        assertTrue(critStrike.isRangedDraw);
        assertEquals("DEADEYE SHOT", critStrike.comboName);
        assertTrue(critStrike.damageMultiplier >= 1.50f);
    }

    @Test
    public void testRangedFirearmKinematics() {
        Item musket = createItem(ItemType.MUSKET, "Musket", true);

        CombatMotionProfile profile = CombatMotionProfile.getNextContinuousStrike(
                AnimationArchetype.RANGED_FIREARM, musket, null, false, false, false, false);
        assertNotNull(profile);
        assertTrue("Firearm must be flagged as isRangedKick", profile.isRangedKick);
        assertEquals("POWDER BLAST", profile.comboName);
        assertEquals(0f, profile.startRotation, 0.01f);

        // Crit strike
        CombatMotionProfile crit = CombatMotionProfile.getNextContinuousStrike(
                AnimationArchetype.RANGED_FIREARM, musket, null, false, true, false, false);
        assertNotNull(crit);
        assertTrue(crit.isRangedKick);
        assertEquals("POINT-BLANK BLAST", crit.comboName);
        assertTrue(crit.damageMultiplier >= 1.60f);
    }

    @Test
    public void testRangedEffectiveRangeFallbacks() {
        Item crossbow = createItem(ItemType.CROSSBOW, "Crossbow", true);
        // Default range template is 1; effectiveRange must provide a robust 12-14 tile fallback
        int range = com.bpm.minotaur.managers.CombatManager.effectiveRange(crossbow);
        assertTrue("Crossbow must have effective range >= 10 tiles, was " + range, range >= 10);

        Item bow = createItem(ItemType.BOW_LONG, "Long Bow", true);
        int bowRange = com.bpm.minotaur.managers.CombatManager.effectiveRange(bow);
        assertTrue("Bow must have effective range >= 16 tiles, was " + bowRange, bowRange >= 16);
    }
}
