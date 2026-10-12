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
        ItemTemplate t = new ItemTemplate();
        t.friendlyName = friendlyName;
        t.isWeapon = true;
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
}
