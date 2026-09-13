package com.bpm.minotaur.managers;

import com.bpm.minotaur.gamedata.player.PlayerStats;
import com.bpm.minotaur.gamedata.Difficulty;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.ItemTemplate;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Verifies the bounded-accuracy combat math introduced by the NetHack
 * Progression Reboot: PlayerStats#getToHitBonus, and CombatManager's
 * hit / glancing-blow resolution at <= 3 delta below target AC.
 */
public class CombatAccuracyTest {

    @Test
    public void testToHitBonusFormula() {
        PlayerStats stats = new PlayerStats(Difficulty.MEDIUM);

        // Base bonus: 2 + (level / 2) + DEX modifier. DEX 10 = mod 0.
        stats.setLevel(1);
        stats.setDexterity(10);
        assertEquals(2, stats.getToHitBonus());

        stats.setLevel(4);
        assertEquals(4, stats.getToHitBonus());

        // DEX 16 -> mod +3
        stats.setDexterity(16);
        assertEquals(7, stats.getToHitBonus());
    }

    @Test
    public void testRegenIntervalScalesWithConstitution() {
        PlayerStats stats = new PlayerStats(Difficulty.MEDIUM);

        stats.setConstitution(10); // mod 0 -> 20 turns
        assertEquals(20, stats.getRegenIntervalTurns());

        stats.setConstitution(18); // mod +4 -> 20 - 8 = 12 turns
        assertEquals(12, stats.getRegenIntervalTurns());

        stats.setConstitution(30); // clamps at the 8-turn floor
        assertEquals(8, stats.getRegenIntervalTurns());
    }

    @Test
    public void testHitAndGlancingResolution() {
        int targetAC = 15;

        // Clean hit: attack roll meets AC exactly.
        assertTrue(CombatManager.isHit(15, targetAC, false));
        assertFalse(CombatManager.isGlancingBlow(15, targetAC, false));

        // Miss by 1-3: glancing blow, not a full hit.
        assertFalse(CombatManager.isHit(12, targetAC, false));
        assertTrue(CombatManager.isGlancingBlow(12, targetAC, false));
        assertTrue(CombatManager.isGlancingBlow(14, targetAC, false));

        // Miss by exactly the boundary (delta == -3) still glances.
        assertTrue(CombatManager.isGlancingBlow(targetAC - 3, targetAC, false));

        // Miss by more than 3: clean whiff, no glance.
        assertFalse(CombatManager.isHit(11, targetAC, false));
        assertFalse(CombatManager.isGlancingBlow(11, targetAC, false));

        // A natural crit always counts as a hit, never a glance, regardless of AC.
        assertTrue(CombatManager.isHit(1, targetAC, true));
        assertFalse(CombatManager.isGlancingBlow(1, targetAC, true));
    }

    @Test
    public void testGlancingBlowDamageIsThirtyFivePercentWithMinimumOne() {
        assertEquals(3, CombatManager.glancingBlowDamage(10));
        assertEquals(8, CombatManager.glancingBlowDamage(23));
        // Small hits still deal at least 1 damage.
        assertEquals(1, CombatManager.glancingBlowDamage(1));
        assertEquals(1, CombatManager.glancingBlowDamage(2));
    }

    @Test
    public void testIsBookWeaponDetection() {
        assertTrue(CombatManager.isBookWeapon(bookItem(Item.ItemType.WAR_BOOK)));
        assertTrue(CombatManager.isBookWeapon(bookItem(Item.ItemType.SPIRITUAL_BOOK)));
        assertTrue(CombatManager.isBookWeapon(bookItem(Item.ItemType.SPECIAL_BOOK)));

        ItemTemplate swordTemplate = new ItemTemplate();
        swordTemplate.friendlyName = "Rusty Sword";
        swordTemplate.isWeapon = true;
        Item sword = Item.fromTemplate(Item.ItemType.RUSTY_SWORD, swordTemplate);
        assertFalse(CombatManager.isBookWeapon(sword));

        assertFalse(CombatManager.isBookWeapon(null));
    }

    @Test
    public void testArcaneSparkDamageAddsIntModifierWithMinimumOne() {
        // 1d4 roll of 2 plus a +3 INT modifier
        assertEquals(5, CombatManager.arcaneSparkDamage(3, 2));
        // A heavily negative modifier still floors at 1 damage
        assertEquals(1, CombatManager.arcaneSparkDamage(-5, 1));
    }

    private Item bookItem(Item.ItemType type) {
        ItemTemplate template = new ItemTemplate();
        template.friendlyName = type.name();
        template.isWeapon = true;
        return Item.fromTemplate(type, template);
    }
}
