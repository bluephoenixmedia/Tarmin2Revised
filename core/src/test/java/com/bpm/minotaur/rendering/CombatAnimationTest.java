package com.bpm.minotaur.rendering;

import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.Item.ItemType;
import com.bpm.minotaur.gamedata.item.ItemTemplate;
import com.bpm.minotaur.rendering.animation.AnimationArchetype;
import com.bpm.minotaur.rendering.animation.CombatMotionProfile;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.*;

public class CombatAnimationTest {

    private Item createItem(ItemType type, String friendlyName, boolean isWeapon, boolean isRanged, boolean isShield) {
        ItemTemplate t = new ItemTemplate();
        t.friendlyName = friendlyName;
        t.isWeapon = isWeapon;
        t.isRanged = isRanged;
        t.isShield = isShield;
        return Item.fromTemplate(type, t);
    }

    @Test
    public void testArchetypeClassification() {
        Item sword = createItem(ItemType.SWORD, "Broad Sword", true, false, false);
        assertEquals(AnimationArchetype.SLASHING_1H, AnimationArchetype.fromItem(sword));

        Item twoHander = createItem(ItemType.TWO_HANDED_SWORD, "Two Handed Sword", true, false, false);
        assertEquals(AnimationArchetype.SLASHING_2H, AnimationArchetype.fromItem(twoHander));

        Item claymore = createItem(ItemType.SWORD, "Claymore", true, false, false);
        assertEquals(AnimationArchetype.SLASHING_2H, AnimationArchetype.fromItem(claymore));

        Item mace = createItem(ItemType.AXE, "Footman's Mace", true, false, false);
        assertEquals(AnimationArchetype.BLUNT_CRUSHING, AnimationArchetype.fromItem(mace));

        Item warhammer = createItem(ItemType.AXE, "Warhammer", true, false, false);
        assertEquals(AnimationArchetype.BLUNT_CRUSHING, AnimationArchetype.fromItem(warhammer));

        Item spear = createItem(ItemType.SPEAR, "Long Spear", true, false, false);
        assertEquals(AnimationArchetype.THRUSTING_PIERCE, AnimationArchetype.fromItem(spear));

        Item dagger = createItem(ItemType.KNIFE, "Bone Dagger", true, false, false);
        assertEquals(AnimationArchetype.THRUSTING_PIERCE, AnimationArchetype.fromItem(dagger));

        Item bow = createItem(ItemType.BOW, "Long Bow", true, true, false);
        assertEquals(AnimationArchetype.RANGED_BOW, AnimationArchetype.fromItem(bow));

        Item arquebus = createItem(ItemType.ARQUEBUS, "Arquebus", true, true, false);
        assertEquals(AnimationArchetype.RANGED_FIREARM, AnimationArchetype.fromItem(arquebus));

        Item shield = createItem(ItemType.SHIELD, "Spiked Buckler", false, false, true);
        assertEquals(AnimationArchetype.SHIELD, AnimationArchetype.fromItem(shield));

        Item cestus = createItem(ItemType.CESTUS, "Cestus", true, false, false);
        assertEquals(AnimationArchetype.BRAWLING, AnimationArchetype.fromItem(cestus));

        // Null item represents bare hands / unarmed brawling
        assertEquals(AnimationArchetype.BRAWLING, AnimationArchetype.fromItem(null));
    }

    @Test
    public void testTemplateArchetypeOverride() {
        Item customDagger = createItem(ItemType.SWORD, "Broad Sword", true, false, false);
        customDagger.getTemplate().animationArchetype = "THRUSTING_PIERCE";
        assertEquals("Explicit template override should take priority",
                AnimationArchetype.THRUSTING_PIERCE, AnimationArchetype.fromItem(customDagger));
    }

    @Test
    public void testComboChainBuilding() {
        Item sword = createItem(ItemType.SWORD, "Broad Sword", true, false, false);
        List<CombatMotionProfile> slashingChain = CombatMotionProfile.buildComboChain(AnimationArchetype.SLASHING_1H, sword, false);
        assertEquals(3, slashingChain.size());
        assertEquals("DIAGONAL CLEAVE", slashingChain.get(0).comboName);
        assertEquals(1.0f, slashingChain.get(0).damageMultiplier, 0.01f);
        assertFalse(slashingChain.get(0).isFinisher);

        assertEquals("RISING BACKSLASH", slashingChain.get(1).comboName);
        assertEquals(1.15f, slashingChain.get(1).damageMultiplier, 0.01f);
        assertFalse(slashingChain.get(1).isFinisher);

        assertEquals("EXECUTIONER'S SLAM", slashingChain.get(2).comboName);
        assertEquals(1.35f, slashingChain.get(2).damageMultiplier, 0.01f);
        assertTrue(slashingChain.get(2).isFinisher);

        // 2H Colossal chain
        List<CombatMotionProfile> colossalChain = CombatMotionProfile.buildComboChain(AnimationArchetype.SLASHING_2H, sword, false);
        assertEquals(2, colossalChain.size());
        assertTrue(colossalChain.get(1).isFinisher);
        assertEquals(1.45f, colossalChain.get(1).damageMultiplier, 0.01f);

        // Dual wielding chain
        List<CombatMotionProfile> dualChain = CombatMotionProfile.buildComboChain(AnimationArchetype.SLASHING_1H, sword, true);
        assertEquals(3, dualChain.size());
        assertTrue("Step 1 should be off-hand strike", dualChain.get(1).isOffHand);
        assertTrue("Step 2 should be scissor finisher", dualChain.get(2).isFinisher);
    }

    @Test
    public void testMotionProfileKinematicPhases() {
        CombatMotionProfile p = new CombatMotionProfile();
        p.anticipationRatio = 0.20f;
        p.impactRatio = 0.40f;
        p.startRotation = -60f;
        p.endRotation = 60f;

        // At progress 0.0 (start)
        CombatMotionProfile.MotionState startState = p.evaluate(0f);
        assertEquals(-60f, startState.rotation, 0.5f);

        // At progress 0.10 (windup) - should pull back in opposite direction
        CombatMotionProfile.MotionState windupState = p.evaluate(0.10f);
        assertTrue("Windup should pull back beyond start rotation", windupState.rotation < -60f);

        // At progress 0.40 (impact) - should reach target strike angle
        CombatMotionProfile.MotionState impactState = p.evaluate(0.40f);
        assertEquals(60f, impactState.rotation, 1.0f);

        // At progress 1.0 (recovery) - should settle back towards start rotation
        CombatMotionProfile.MotionState endState = p.evaluate(1.0f);
        assertEquals(-60f, endState.rotation, 1.0f);
    }

    @Test
    public void testOverlayComboTransitionsAndWhiff() {
        FirstPersonWeaponOverlay overlay = new FirstPersonWeaponOverlay(null, null);
        Item sword = createItem(ItemType.SWORD, "Broad Sword", true, false, false);
        overlay.setEquipment(sword, null);

        // Strike 1
        overlay.triggerAttack(sword);
        assertTrue(overlay.isActive());
        assertEquals(0, overlay.getComboIndex());

        // Update past impact frame to trigger combo window
        overlay.update(0.15f);
        assertFalse(overlay.isMovementLocked()); // Unlocked after impact

        // Strike 2 within combo window
        overlay.triggerAttack(sword);
        assertEquals(1, overlay.getComboIndex());

        // Update past impact
        overlay.update(0.15f);

        // Strike 3 (Finisher)
        overlay.triggerAttack(sword);
        assertEquals(2, overlay.getComboIndex());
        assertTrue(overlay.getCurrentProfile().isFinisher);

        // Whiff resets combo
        overlay.triggerWhiff();
        overlay.triggerAttack(sword);
        assertEquals("Whiff should reset combo to opener", 0, overlay.getComboIndex());
    }

    @Test
    public void testHitFrameCallbackNotification() {
        FirstPersonWeaponOverlay overlay = new FirstPersonWeaponOverlay(null, null);
        Item sword = createItem(ItemType.SWORD, "Broad Sword", true, false, false);
        overlay.setEquipment(sword, null);

        AtomicBoolean hitFrameFired = new AtomicBoolean(false);
        overlay.setHitFrameCallback(profile -> {
            hitFrameFired.set(true);
            assertNotNull(profile);
            assertEquals("DIAGONAL CLEAVE", profile.comboName);
        });

        overlay.triggerAttack(sword);
        assertFalse("Should not fire immediately on trigger", hitFrameFired.get());

        // Update to just before impact
        overlay.update(0.05f);
        assertFalse("Should not fire during windup", hitFrameFired.get());

        // Update past impact ratio
        overlay.update(0.10f);
        assertTrue("Should fire exactly at impact milestone", hitFrameFired.get());
    }

    @Test
    public void testMovementMicroLock() {
        FirstPersonWeaponOverlay overlay = new FirstPersonWeaponOverlay(null, null);
        Item sword = createItem(ItemType.SWORD, "Broad Sword", true, false, false);
        overlay.setEquipment(sword, null);

        overlay.triggerAttack(sword);
        assertTrue("Movement should be micro-locked during windup", overlay.isMovementLocked());

        // Advance past impact ratio
        overlay.update(0.18f);
        assertFalse("Movement lock should release at impact for cancelable recovery", overlay.isMovementLocked());
    }
}
