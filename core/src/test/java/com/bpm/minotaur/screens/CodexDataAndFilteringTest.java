package com.bpm.minotaur.screens;

import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.ItemTemplate;
import com.bpm.minotaur.gamedata.progression.ShelterAltar;
import com.bpm.minotaur.managers.UnlockManager;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class CodexDataAndFilteringTest {

    @Before
    public void setUp() {
        if (com.badlogic.gdx.Gdx.app == null) {
            com.badlogic.gdx.Gdx.app = (com.badlogic.gdx.Application) java.lang.reflect.Proxy.newProxyInstance(
                    com.badlogic.gdx.Application.class.getClassLoader(),
                    new Class<?>[]{com.badlogic.gdx.Application.class},
                    (proxy, method, args) -> null
            );
        }
        ShelterAltar.getInstance().reset();
    }

    @Test
    public void testStrataTierRequirementCalculation() {
        // Items scoring at the 310 threshold require Strata 1
        int scoreAtThreshold = 310;
        int strata1 = Math.max(1, (scoreAtThreshold - UnlockManager.UNLOCK_SCORE_THRESHOLD) / 250 + 1);
        assertEquals(1, strata1);

        // Items scoring 560 require Strata 2
        int scoreStrata2 = 560;
        int strata2 = Math.max(1, (scoreStrata2 - UnlockManager.UNLOCK_SCORE_THRESHOLD) / 250 + 1);
        assertEquals(2, strata2);

        // Items scoring 810 require Strata 3
        int scoreStrata3 = 810;
        int strata3 = Math.max(1, (scoreStrata3 - UnlockManager.UNLOCK_SCORE_THRESHOLD) / 250 + 1);
        assertEquals(3, strata3);
    }

    @Test
    public void testArcaneAttunementSpellSealing() {
        ShelterAltar altar = ShelterAltar.getInstance();

        // At Tier 0, all attunement spells are sealed
        assertTrue("Magic Missile should be sealed at Tier 0", altar.isSpellSealed("MAGIC_MISSILE"));
        assertTrue("Misty Step should be sealed at Tier 0", altar.isSpellSealed("MISTY_STEP"));
        assertTrue("Fireball should be sealed at Tier 0", altar.isSpellSealed("FIREBALL"));

        // Purchase Tier 1: Magic Missile unsealed, Misty Step & Fireball still sealed
        com.bpm.minotaur.managers.DivinityManager.getInstance().addDivinities(100);
        boolean purchased = altar.purchaseUpgrade(ShelterAltar.Tree.ARCANE_ATTUNEMENT);
        assertTrue("Purchase should succeed with banked divinities", purchased);
        assertEquals(1, altar.getTier(ShelterAltar.Tree.ARCANE_ATTUNEMENT));
        assertFalse("Magic Missile should be unsealed at Tier 1", altar.isSpellSealed("MAGIC_MISSILE"));
        assertFalse("Shield should be unsealed at Tier 1", altar.isSpellSealed("SHIELD"));
        assertTrue("Misty Step should remain sealed at Tier 1", altar.isSpellSealed("MISTY_STEP"));
        assertTrue("Fireball should remain sealed at Tier 1", altar.isSpellSealed("FIREBALL"));
    }

    @Test
    public void testItemScoreCalculationFormulas() {
        ItemTemplate weapon = new ItemTemplate();
        weapon.isWeapon = true;
        weapon.damageDice = "2d6";
        weapon.baseValue = 50;
        // score = baseValue (50) + numDice * dieSize * 50 (2 * 6 * 50 = 600) = 650
        assertEquals(650, UnlockManager.calculateItemScore(weapon));

        ItemTemplate armor = new ItemTemplate();
        armor.isArmor = true;
        armor.armorClassBonus = 4;
        armor.baseValue = 100;
        // score = baseValue (100) + armorClassBonus * 100 (4 * 100 = 400) = 500
        assertEquals(500, UnlockManager.calculateItemScore(armor));
    }
}
