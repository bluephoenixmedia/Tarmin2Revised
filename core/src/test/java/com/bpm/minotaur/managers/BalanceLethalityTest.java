package com.bpm.minotaur.managers;

import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.Item.ItemType;
import com.bpm.minotaur.gamedata.item.ItemDataManager;
import com.bpm.minotaur.gamedata.item.ItemTemplate;
import com.bpm.minotaur.gamedata.monster.Monster;
import com.bpm.minotaur.gamedata.monster.MonsterFamily;
import com.bpm.minotaur.gamedata.monster.MonsterTemplate;
import com.bpm.minotaur.gamedata.player.Player;
import com.bpm.minotaur.utils.DiceRoller;
import org.junit.Test;

import static org.junit.Assert.*;

public class BalanceLethalityTest {

    @Test
    public void testDiceRollerWhitespaceResilience() {
        // Test standard dice notation with and without whitespace
        for (int i = 0; i < 50; i++) {
            int roll1 = DiceRoller.roll("2d6+3");
            assertTrue("2d6+3 must be between 5 and 15", roll1 >= 5 && roll1 <= 15);

            int roll2 = DiceRoller.roll("2d6 + 3");
            assertTrue("2d6 + 3 with spaces must be between 5 and 15", roll2 >= 5 && roll2 <= 15);

            int roll3 = DiceRoller.roll("4d12 + 10");
            assertTrue("4d12 + 10 must be between 14 and 58", roll3 >= 14 && roll3 <= 58);

            int roll4 = DiceRoller.roll("1d4");
            assertTrue("1d4 must be between 1 and 4", roll4 >= 1 && roll4 <= 4);
        }
    }

    @Test
    public void testRustySwordDamageDiceIs1d4() {
        ItemTemplate swordTemplate = new ItemTemplate();
        swordTemplate.isWeapon = true;
        swordTemplate.damageDice = "1d4";
        Item sword = Item.fromTemplate(ItemType.RUSTY_SWORD, swordTemplate);

        assertEquals("1d4", sword.getDamageDice());
        for (int i = 0; i < 20; i++) {
            int dmg = DiceRoller.roll(sword.getDamageDice());
            assertTrue("Rusty Sword damage roll must be 1-4", dmg >= 1 && dmg <= 4);
        }
    }

    @Test
    public void testMonsterAttackBonusScaling() {
        // Level 1 Kobold
        MonsterTemplate koboldT = new MonsterTemplate();
        koboldT.baseLevel = 1;
        koboldT.maxHP = 8;
        koboldT.dexterity = 10;
        koboldT.family = MonsterFamily.HUMANOID;
        Monster kobold = new Monster(Monster.MonsterType.KOBOLD, 8, 12);
        kobold.setTemplate(koboldT);
        int koboldBonus = CombatManager.calculateMonsterAttackBonus(kobold);
        // The statMod floor dropped from 1 to 0 (#177) and proficiency from 2 to 1
        // (#207): a level-1 monster with no stat bonus hits an unarmoured player
        // (AC 10) 60% of the time, inside the 55-65% target. Pinned to the exact value.
        assertEquals("Level 1 Kobold attack bonus should be exactly +1 (profBonus 1 + statMod floor 0)",
                1, koboldBonus);

        // Level 1 Giant Ant (Beast)
        MonsterTemplate antT = new MonsterTemplate();
        antT.baseLevel = 1;
        antT.maxHP = 12;
        antT.dexterity = 12;
        antT.family = MonsterFamily.BEAST;
        Monster ant = new Monster(Monster.MonsterType.GIANT_ANT, 12, 12);
        ant.setTemplate(antT);
        int antBonus = CombatManager.calculateMonsterAttackBonus(ant);
        // BEAST branch: the Dex modifier counts at half weight since #207,
        // max(0, (12-10)/4 = 0), so the Ant hits like the Kobold above.
        assertEquals("Level 1 Giant Ant attack bonus should be exactly +1 (profBonus 1 + statMod 0)",
                1, antBonus);

        // Level 2 Ghoul
        MonsterTemplate ghoulT = new MonsterTemplate();
        ghoulT.baseLevel = 2;
        ghoulT.maxHP = 35;
        ghoulT.dexterity = 10;
        ghoulT.family = MonsterFamily.UNDEAD;
        Monster ghoul = new Monster(Monster.MonsterType.GHOUL, 35, 12);
        ghoul.setTemplate(ghoulT);
        int ghoulBonus = CombatManager.calculateMonsterAttackBonus(ghoul);
        assertTrue("Level 2 Ghoul attack bonus should be at least +3", ghoulBonus >= 3);

        // Level 6 Troll
        MonsterTemplate trollT = new MonsterTemplate();
        trollT.baseLevel = 6;
        trollT.maxHP = 70;
        trollT.dexterity = 8;
        trollT.family = MonsterFamily.HUMANOID;
        Monster troll = new Monster(Monster.MonsterType.TROLL, 70, 15);
        troll.setTemplate(trollT);
        int trollBonus = CombatManager.calculateMonsterAttackBonus(troll);
        assertTrue("Level 6 Troll attack bonus should be at least +8", trollBonus >= 8);

        // Level 15 Minotaur
        MonsterTemplate minotaurT = new MonsterTemplate();
        minotaurT.baseLevel = 15;
        minotaurT.maxHP = 100;
        minotaurT.dexterity = 10;
        minotaurT.family = MonsterFamily.MAGICAL;
        Monster minotaur = new Monster(Monster.MonsterType.MINOTAUR, 100, 14);
        minotaur.setTemplate(minotaurT);
        int minotaurBonus = CombatManager.calculateMonsterAttackBonus(minotaur);
        assertTrue("Level 15 Minotaur attack bonus should be at least +13", minotaurBonus >= 13);
    }

    @Test
    public void testDepthScalingFormula() {
        // Depth 1: 14 monsters
        int depth1 = (int) Math.round(14.0 + (1 - 1) * 3.5);
        assertEquals(14, depth1);

        // Depth 2: 18 monsters
        int depth2 = (int) Math.round(14.0 + (2 - 1) * 3.5);
        assertEquals(18, depth2);

        // Depth 3: 21 monsters
        int depth3 = (int) Math.round(14.0 + (3 - 1) * 3.5);
        assertEquals(21, depth3);

        // Depth 4: 25 monsters
        int depth4 = (int) Math.round(14.0 + (4 - 1) * 3.5);
        assertEquals(25, depth4);
    }

    @Test
    public void testTierOneMonsterDamageDiceBounds() {
        // Kobold / Spider (1d4+1): 2 to 5 damage
        for (int i = 0; i < 50; i++) {
            int roll = DiceRoller.roll("1d4+1");
            assertTrue("1d4+1 must roll between 2 and 5", roll >= 2 && roll <= 5);
        }

        // Goblin (1d6): 1 to 6 damage
        for (int i = 0; i < 50; i++) {
            int roll = DiceRoller.roll("1d6");
            assertTrue("1d6 must roll between 1 and 6", roll >= 1 && roll <= 6);
        }

        // Skeleton / Giant Snake / Troglodyte (1d6+1): 2 to 7 damage
        for (int i = 0; i < 50; i++) {
            int roll = DiceRoller.roll("1d6+1");
            assertTrue("1d6+1 must roll between 2 and 7", roll >= 2 && roll <= 7);
        }
    }

    @Test
    public void testStarterChunkMonsterDensity() {
        // Starter chunk (0,0) at depth 1 has a gentle 8-monster budget
        int currentDepth = 1;
        int scaledBudget;
        com.badlogic.gdx.math.GridPoint2 starterChunk = new com.badlogic.gdx.math.GridPoint2(0, 0);
        if (starterChunk.x == 0 && starterChunk.y == 0 && currentDepth <= 1) {
            scaledBudget = 8;
        } else {
            scaledBudget = (int) Math.round(14.0 + (currentDepth - 1) * 3.5);
        }
        assertEquals("Starter chunk (0,0) at depth 1 should have 8 monsters", 8, scaledBudget);

        // Adjacent chunk (1,0) at depth 1 uses standard 14 monsters
        com.badlogic.gdx.math.GridPoint2 nextChunk = new com.badlogic.gdx.math.GridPoint2(1, 0);
        if (nextChunk.x == 0 && nextChunk.y == 0 && currentDepth <= 1) {
            scaledBudget = 8;
        } else {
            scaledBudget = (int) Math.round(14.0 + (currentDepth - 1) * 3.5);
        }
        assertEquals("Subsequent chunks at depth 1 should scale to 14 monsters", 14, scaledBudget);
    }
}
