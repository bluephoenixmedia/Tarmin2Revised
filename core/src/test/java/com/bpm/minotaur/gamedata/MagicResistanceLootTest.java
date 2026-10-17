package com.bpm.minotaur.gamedata;

import com.bpm.minotaur.gamedata.item.ItemCategory;
import org.junit.Test;

import static org.junit.Assert.*;

/** Magic resistance has to be findable: armour, rings and blades can roll it, in steps that grow. */
public class MagicResistanceLootTest {

    private static LootTable.ModInfo[] resistanceMods() {
        return LootTable.MODIFIER_POOL.stream()
                .filter(m -> m.type == ModifierType.BONUS_MAGIC_RESISTANCE)
                .toArray(LootTable.ModInfo[]::new);
    }

    @Test
    public void armourRingsAndBladesCanAllRollIt() {
        boolean armour = false;
        boolean ring = false;
        boolean blade = false;
        for (LootTable.ModInfo m : resistanceMods()) {
            armour |= m.category == ItemCategory.ARMOR;
            ring |= m.category == ItemCategory.RING;
            blade |= m.category == ItemCategory.WAR_WEAPON;
        }
        assertTrue("armour", armour);
        assertTrue("ring", ring);
        assertTrue("blade", blade);
    }

    @Test
    public void everyStepIsAFivePercentToFifteenPercentWardAndNoSingleItemBreaksTheCap() {
        for (LootTable.ModInfo m : resistanceMods()) {
            assertTrue(m.displayName + " " + m.minBonus, m.minBonus >= 5 && m.maxBonus <= 15);
            assertTrue(m.minBonus <= m.maxBonus);
            assertTrue("one ward must stay well below the " + MagicResistance.PLAYER_CAP + "% cap",
                    m.maxBonus < MagicResistance.PLAYER_CAP);
        }
    }

    @Test
    public void deeperFloorsOfferStrongerWardsOnArmour() {
        int shallow = 0;
        int deep = 0;
        for (LootTable.ModInfo m : resistanceMods()) {
            if (m.category != ItemCategory.ARMOR) {
                continue;
            }
            if (m.minLevel <= 3) {
                shallow = Math.max(shallow, m.maxBonus);
            }
            if (m.maxLevel >= 99 && m.minLevel >= 14) {
                deep = Math.max(deep, m.maxBonus);
            }
        }
        assertTrue(deep > shallow);
    }
}
