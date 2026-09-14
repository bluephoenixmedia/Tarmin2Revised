package com.bpm.minotaur.rendering;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.Item.ItemType;
import com.bpm.minotaur.gamedata.item.ItemTemplate;
import com.bpm.minotaur.rendering.animation.AnimationArchetype;
import com.bpm.minotaur.rendering.animation.CombatMotionProfile;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class WeaponRespawnAndArchetypeTest {

    private Item createItem(ItemType type, String friendlyName, boolean isWeapon, boolean isRanged, boolean isShield) {
        ItemTemplate t = new ItemTemplate();
        t.friendlyName = friendlyName;
        t.isWeapon = isWeapon;
        t.isRanged = isRanged;
        t.isShield = isShield;
        return Item.fromTemplate(type, t);
    }

    @Test
    public void testAxeArchetypeClassification() {
        Item battleAxe = createItem(ItemType.AXE, "Battle Axe", true, false, false);
        assertEquals(AnimationArchetype.AXE_CHOPPING, AnimationArchetype.fromItem(battleAxe));

        Item handAxe = createItem(ItemType.AXE, "Hand Axe", true, false, false);
        assertEquals(AnimationArchetype.AXE_CHOPPING, AnimationArchetype.fromItem(handAxe));

        Item hatchet = createItem(ItemType.AXE, "Iron Hatchet", true, false, false);
        assertEquals(AnimationArchetype.AXE_CHOPPING, AnimationArchetype.fromItem(hatchet));

        Item tomahawk = createItem(ItemType.AXE, "Tomahawk", true, false, false);
        assertEquals(AnimationArchetype.AXE_CHOPPING, AnimationArchetype.fromItem(tomahawk));

        Item greatAxe = createItem(ItemType.AXE, "Great Axe", true, false, false);
        assertEquals(AnimationArchetype.AXE_CHOPPING, AnimationArchetype.fromItem(greatAxe));
    }

    @Test
    public void testPolearmArchetypeClassification() {
        Item halberd = createItem(ItemType.SPEAR, "Halberd", true, false, false);
        assertEquals(AnimationArchetype.POLEARM_SWEEP, AnimationArchetype.fromItem(halberd));

        Item guisarme = createItem(ItemType.SPEAR, "Guisarme", true, false, false);
        assertEquals(AnimationArchetype.POLEARM_SWEEP, AnimationArchetype.fromItem(guisarme));

        Item naginata = createItem(ItemType.SPEAR, "Naginata", true, false, false);
        assertEquals(AnimationArchetype.POLEARM_SWEEP, AnimationArchetype.fromItem(naginata));

        Item glaive = createItem(ItemType.SPEAR, "Glaive", true, false, false);
        assertEquals(AnimationArchetype.POLEARM_SWEEP, AnimationArchetype.fromItem(glaive));

        Item polearm = createItem(ItemType.SPEAR, "Heavy Polearm", true, false, false);
        assertEquals(AnimationArchetype.POLEARM_SWEEP, AnimationArchetype.fromItem(polearm));

        Item bardiche = createItem(ItemType.SPEAR, "Bardiche", true, false, false);
        assertEquals(AnimationArchetype.POLEARM_SWEEP, AnimationArchetype.fromItem(bardiche));

        Item partisan = createItem(ItemType.SPEAR, "Partisan", true, false, false);
        assertEquals(AnimationArchetype.POLEARM_SWEEP, AnimationArchetype.fromItem(partisan));

        Item tetsubo = createItem(ItemType.AXE, "Iron Tetsubo", true, false, false);
        assertEquals(AnimationArchetype.POLEARM_SWEEP, AnimationArchetype.fromItem(tetsubo));
    }

    @Test
    public void testFlailWhipArchetypeClassification() {
        Item footmanFlail = createItem(ItemType.AXE, "Footman's Flail", true, false, false);
        assertEquals(AnimationArchetype.FLAIL_WHIP, AnimationArchetype.fromItem(footmanFlail));

        Item horsemanFlail = createItem(ItemType.AXE, "Horseman's Flail", true, false, false);
        assertEquals(AnimationArchetype.FLAIL_WHIP, AnimationArchetype.fromItem(horsemanFlail));

        Item morningStar = createItem(ItemType.AXE, "Morning Star", true, false, false);
        assertEquals(AnimationArchetype.FLAIL_WHIP, AnimationArchetype.fromItem(morningStar));

        Item whip = createItem(ItemType.SWORD, "Leather Whip", true, false, false);
        assertEquals(AnimationArchetype.FLAIL_WHIP, AnimationArchetype.fromItem(whip));

        Item nunchaku = createItem(ItemType.AXE, "Oak Nunchaku", true, false, false);
        assertEquals(AnimationArchetype.FLAIL_WHIP, AnimationArchetype.fromItem(nunchaku));

        Item chain = createItem(ItemType.AXE, "Spiked Chain", true, false, false);
        assertEquals(AnimationArchetype.FLAIL_WHIP, AnimationArchetype.fromItem(chain));
    }

    @Test
    public void testAxeComboChainAndKinematics() {
        Item axe = createItem(ItemType.AXE, "Battle Axe", true, false, false);
        List<CombatMotionProfile> chain = CombatMotionProfile.buildComboChain(AnimationArchetype.AXE_CHOPPING, axe, false);

        assertEquals(3, chain.size());
        assertEquals("OVERHEAD CHOP", chain.get(0).comboName);
        assertEquals("DIAGONAL HACK", chain.get(1).comboName);
        assertEquals("CLEAVING HOOK", chain.get(2).comboName);

        for (CombatMotionProfile profile : chain) {
            assertTrue("Duration should be positive", profile.duration > 0.1f);
            assertTrue("Damage multiplier should be positive", profile.damageMultiplier >= 1.0f);
            assertTrue("Impact ratio in range", profile.impactRatio > profile.anticipationRatio && profile.impactRatio < 1.0f);

            // Test kinematic trajectory at various points
            for (float progress = 0f; progress <= 1f; progress += 0.1f) {
                CombatMotionProfile.MotionState state = profile.evaluate(progress);
                assertFalse("X should be finite", Float.isNaN(state.xRel) || Float.isInfinite(state.xRel));
                assertFalse("Y should be finite", Float.isNaN(state.yRel) || Float.isInfinite(state.yRel));
                assertFalse("Rotation should be finite", Float.isNaN(state.rotation) || Float.isInfinite(state.rotation));
            }
        }

        // Finisher should deal the heaviest hit and screen trauma
        assertTrue("Cleaving Hook finisher has bonus damage", chain.get(2).damageMultiplier > chain.get(0).damageMultiplier);
        assertTrue("Finisher has heavy trauma", chain.get(2).screenTrauma >= 0.25f);
    }

    @Test
    public void testPolearmComboChainAndKinematics() {
        Item halberd = createItem(ItemType.SPEAR, "Halberd", true, false, false);
        List<CombatMotionProfile> chain = CombatMotionProfile.buildComboChain(AnimationArchetype.POLEARM_SWEEP, halberd, false);

        assertEquals(3, chain.size());
        assertEquals("WIDE REAP", chain.get(0).comboName);
        assertEquals("OVERHEAD HEFT", chain.get(1).comboName);
        assertEquals("VAULTING THRUST", chain.get(2).comboName);

        for (CombatMotionProfile profile : chain) {
            assertTrue("Duration should be positive", profile.duration > 0.1f);
            assertTrue("Damage multiplier should be positive", profile.damageMultiplier >= 1.0f);

            for (float progress = 0f; progress <= 1f; progress += 0.1f) {
                CombatMotionProfile.MotionState state = profile.evaluate(progress);
                assertFalse("X should be finite", Float.isNaN(state.xRel) || Float.isInfinite(state.xRel));
                assertFalse("Y should be finite", Float.isNaN(state.yRel) || Float.isInfinite(state.yRel));
                assertFalse("Rotation should be finite", Float.isNaN(state.rotation) || Float.isInfinite(state.rotation));
            }
        }
    }

    @Test
    public void testFlailComboChainAndKinematics() {
        Item flail = createItem(ItemType.AXE, "Footman's Flail", true, false, false);
        List<CombatMotionProfile> chain = CombatMotionProfile.buildComboChain(AnimationArchetype.FLAIL_WHIP, flail, false);

        assertEquals(3, chain.size());
        assertEquals("MOMENTUM SWIRL", chain.get(0).comboName);
        assertEquals("SIDE SNAP", chain.get(1).comboName);
        assertEquals("OVERHEAD CRUSH", chain.get(2).comboName);

        for (CombatMotionProfile profile : chain) {
            assertTrue("Duration should be positive", profile.duration > 0.1f);
            assertTrue("Damage multiplier should be positive", profile.damageMultiplier >= 1.0f);

            for (float progress = 0f; progress <= 1f; progress += 0.1f) {
                CombatMotionProfile.MotionState state = profile.evaluate(progress);
                assertFalse("X should be finite", Float.isNaN(state.xRel) || Float.isInfinite(state.xRel));
                assertFalse("Y should be finite", Float.isNaN(state.yRel) || Float.isInfinite(state.yRel));
                assertFalse("Rotation should be finite", Float.isNaN(state.rotation) || Float.isInfinite(state.rotation));
            }
        }
    }

    @Test
    public void testOverlayResetAndDecalClearanceOnRespawn() {
        FirstPersonWeaponOverlay overlay = new FirstPersonWeaponOverlay(null, null);

        Item axe = createItem(ItemType.AXE, "Battle Axe", true, false, false);
        overlay.setEquipment(axe, null);
        assertEquals(AnimationArchetype.AXE_CHOPPING, overlay.getMainHandArchetype());

        // Accumulate blood splatters during combat
        overlay.addBloodToWeapon();
        overlay.addBloodDecals(5, Color.RED, (TextureRegion) null);
        assertTrue("Decals present", overlay.getBloodDecalCount() > 0);

        // Trigger active swing
        overlay.triggerAttack(axe);
        assertTrue("Attack is active", overlay.isActive());

        // Simulate Shelter Respawn: clean weapon state
        overlay.reset();
        assertFalse("Attack is no longer active after reset", overlay.isActive());

        overlay.clearBloodDecals();
        assertEquals("Decals wiped on shelter awakening", 0, overlay.getBloodDecalCount());

        overlay.forceRefreshEquipment(axe, null);
        assertEquals("Archetype remains intact after refresh", AnimationArchetype.AXE_CHOPPING, overlay.getMainHandArchetype());
        assertFalse("Overlay is clean and idle ready", overlay.isActive());
    }
}
