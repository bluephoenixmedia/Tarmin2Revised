package com.bpm.minotaur.combat;

import com.bpm.minotaur.gamedata.Difficulty;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.ItemColor;
import com.bpm.minotaur.gamedata.player.Player;
import com.bpm.minotaur.gamedata.player.PlayerStats;
import com.bpm.minotaur.gamedata.progression.SkillId;
import com.bpm.minotaur.rendering.animation.AnimationArchetype;
import com.bpm.minotaur.rendering.animation.CombatMotionProfile;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class DualWieldCombatTest {

    @Before
    public void setUp() {
        if (com.badlogic.gdx.Gdx.app == null) {
            com.badlogic.gdx.Gdx.app = (com.badlogic.gdx.Application) java.lang.reflect.Proxy.newProxyInstance(
                    com.badlogic.gdx.Application.class.getClassLoader(),
                    new Class<?>[]{com.badlogic.gdx.Application.class},
                    (proxy, method, args) -> null
            );
        }
    }

    @Test
    public void testDualWieldComboChainSequence() {
        // Single wield weapon combo: no offhand steps
        List<CombatMotionProfile> singleChain = CombatMotionProfile.buildComboChain(
                AnimationArchetype.SLASHING_1H, null, false);
        assertNotNull(singleChain);
        for (CombatMotionProfile profile : singleChain) {
            assertFalse("Single-wield combo must not contain offhand attacks", profile.isOffHand);
            assertFalse("Single-wield combo must not contain dual strikes", profile.isDualStrike);
        }

        // Dual wield weapon combo: 3-hit sequence (Main -> Off-Hand -> Scissor Finisher)
        List<CombatMotionProfile> dualChain = CombatMotionProfile.buildComboChain(
                AnimationArchetype.SLASHING_1H, null, true);
        assertEquals("Dual-wield combo chain must have exactly 3 steps", 3, dualChain.size());

        // Step 0: Main Hand strike
        CombatMotionProfile step0 = dualChain.get(0);
        assertEquals(0, step0.comboStep);
        assertFalse(step0.isOffHand);
        assertFalse(step0.isDualStrike);
        assertFalse(step0.isFinisher);

        // Step 1: Off-Hand sweep
        CombatMotionProfile step1 = dualChain.get(1);
        assertEquals(1, step1.comboStep);
        assertTrue("Step 1 must be flagged as off-hand strike", step1.isOffHand);
        assertFalse(step1.isDualStrike);
        assertFalse(step1.isFinisher);

        // Step 2: Scissor Finisher
        CombatMotionProfile step2 = dualChain.get(2);
        assertEquals(2, step2.comboStep);
        assertTrue("Step 2 must be flagged as dual strike", step2.isDualStrike);
        assertTrue("Step 2 must be flagged as finisher", step2.isFinisher);
        assertEquals("SCISSOR FINISHER", step2.comboName);
        assertEquals(1.4f, step2.damageMultiplier, 0.01f);
    }

    @Test
    public void testPlayerDualWieldProgressionGating() {
        Player player = new Player(0, 0);
        assertFalse("New player cannot dual-wield by default", player.canDualWield());

        // Level up and learn Dual Wielder
        player.getStats().setStrength(12);
        player.getStats().addExperience(player.getStats().getExperienceToNextLevel());
        assertTrue(player.learnSkill(SkillId.DUAL_WIELDER));

        assertTrue("Player can dual wield after unlocking the perk", player.canDualWield());
        assertTrue(player.hasSkill(SkillId.DUAL_WIELDER));
    }

    @Test
    public void testHeavyArmorMasteryDetection() {
        Player player = new Player(0, 0);
        assertFalse("Player does not wear heavy armor initially", player.isWearingHeavyArmor());

        // Equip leather armor (light)
        Item leather = new Item(Item.ItemType.LEATHER_ARMOR, 0, 0, ItemColor.TAN, null, null);
        player.getEquipment().setWornChest(leather);
        assertFalse("Leather armor is not heavy armor", player.isWearingHeavyArmor());

        // Equip plate mail (heavy)
        Item plate = new Item(Item.ItemType.PLATE_MAIL, 0, 0, ItemColor.GRAY, null, null);
        player.getEquipment().setWornChest(plate);
        assertTrue("Plate mail must be recognized as heavy armor", player.isWearingHeavyArmor());
    }
}
