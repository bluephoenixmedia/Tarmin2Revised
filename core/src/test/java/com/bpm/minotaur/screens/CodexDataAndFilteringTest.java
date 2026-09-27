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
        // Anchored on real item scores, not on the threshold constant. An
        // earlier rewrite expressed everything relative to the threshold, which
        // made every assertion reduce to (base - base) / 250 + 1 and touch no
        // production value at all.
        assertEquals("An item at the threshold requires Strata 1",
                1, strataFor(UnlockManager.UNLOCK_SCORE_THRESHOLD));
        assertEquals("An item just under one band up is still Strata 1",
                1, strataFor(UnlockManager.UNLOCK_SCORE_THRESHOLD + 249));
        assertEquals("One full band above the threshold is Strata 2",
                2, strataFor(UnlockManager.UNLOCK_SCORE_THRESHOLD + 250));
        assertEquals("Two bands above is Strata 3",
                3, strataFor(UnlockManager.UNLOCK_SCORE_THRESHOLD + 500));
        assertEquals("An item below the threshold never exceeds Strata 1",
                1, strataFor(0));
    }

    /** Mirrors the Codex's tier rule: one strata per 250 points above the gate. */
    private int strataFor(int itemScore) {
        return Math.max(1, (itemScore - UnlockManager.UNLOCK_SCORE_THRESHOLD) / 250 + 1);
    }

    @Test
    public void testArcaneAttunementSpellSealing() {
        ShelterAltar altar = ShelterAltar.getInstance();

        // The 9-spell hard gate was retired in the spell system overhaul: isSpellSealed always returns false
        assertFalse("Magic Missile is unsealed", altar.isSpellSealed("MAGIC_MISSILE"));
        assertFalse("Misty Step is unsealed", altar.isSpellSealed("MISTY_STEP"));
        assertFalse("Fireball is unsealed", altar.isSpellSealed("FIREBALL"));
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
