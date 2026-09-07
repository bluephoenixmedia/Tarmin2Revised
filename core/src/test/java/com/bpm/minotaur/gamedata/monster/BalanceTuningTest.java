package com.bpm.minotaur.gamedata.monster;

import com.bpm.minotaur.gamedata.DamageType;
import com.bpm.minotaur.managers.DoomManager;
import com.bpm.minotaur.utils.DiceRoller;
import org.junit.Test;
import static org.junit.Assert.*;

public class BalanceTuningTest {

    @Test
    public void testArmorSoakThreshold() {
        // Monsters below AC 14 should have 0 soak (using Category.HORRIBLE neutral monsters to isolate soak)
        Monster ant = new Monster(Monster.MonsterType.GIANT_ANT, 20, 12);
        int taken1 = ant.takeDamage(6, DamageType.PHYSICAL);
        assertEquals(6, taken1); // AC 12: soak = 0, full 6 damage dealt

        Monster alligator = new Monster(Monster.MonsterType.ALLIGATOR, 20, 13);
        int taken2 = alligator.takeDamage(6, DamageType.PHYSICAL);
        assertEquals(6, taken2); // AC 13: soak = 0, full 6 damage dealt

        Monster minotaur = new Monster(Monster.MonsterType.MINOTAUR, 20, 14);
        int taken3 = minotaur.takeDamage(6, DamageType.PHYSICAL);
        assertEquals(6, taken3); // AC 14: (14-14)/2 = 0 soak, full 6 damage dealt

        // Heavy armor: AC 16 has 1 soak
        Monster armoredKnight = new Monster(Monster.MonsterType.MINOTAUR, 50, 16);
        int taken4 = armoredKnight.takeDamage(6, DamageType.PHYSICAL);
        assertEquals(5, taken4); // AC 16: (16-14)/2 = 1 soak, 6 - 1 = 5

        // Boss armor: AC 18 has 2 soak
        Monster demonLord = new Monster(Monster.MonsterType.FALL_ANGEL, 50, 18);
        int taken5 = demonLord.takeDamage(6, DamageType.PHYSICAL);
        assertEquals(4, taken5); // AC 18: (18-14)/2 = 2 soak, 6 - 2 = 4

        // Floor minimum: damage cannot drop below 1
        int taken6 = demonLord.takeDamage(1, DamageType.PHYSICAL);
        assertEquals(1, taken6);
    }

    @Test
    public void testCriticalHitBypassesSoak() {
        Monster heavilyArmored = new Monster(Monster.MonsterType.FALL_ANGEL, 50, 18);

        // Non-crit against AC 18: 2 soak absorbed
        int normalHit = heavilyArmored.takeDamage(6, DamageType.PHYSICAL, false);
        assertEquals(4, normalHit);

        // Crit against AC 18: soak is 0, full damage punches through
        int critHit = heavilyArmored.takeDamage(6, DamageType.PHYSICAL, true);
        assertEquals(6, critHit);
    }

    @Test
    public void testDoomScalingDepthGate() {
        DoomManager doom = DoomManager.getInstance();
        doom.reset();

        // Simulate 10 player deaths (+25% base death multiplier)
        for (int i = 0; i < 10; i++) {
            doom.incrementDeaths();
        }

        // Level 1: strictly 1.0f (0% bonus on Level 1 / starter chunk)
        assertEquals(1.0f, doom.getEnemyScalingMultiplier(1), 0.001f);
        assertEquals(1.0f, doom.getEnemyScalingMultiplier(0), 0.001f);

        // Level 2: partial scaling (25% of the penalty = +6.25%)
        assertEquals(1.0625f, doom.getEnemyScalingMultiplier(2), 0.001f);

        // Level 3: half scaling (50% of the penalty = +12.5%)
        assertEquals(1.125f, doom.getEnemyScalingMultiplier(3), 0.001f);

        // Level 5+: full scaling (100% of the penalty = +25%)
        assertEquals(1.25f, doom.getEnemyScalingMultiplier(5), 0.001f);
        assertEquals(1.25f, doom.getEnemyScalingMultiplier(10), 0.001f);

        // Reset doom manager state after test
        doom.reset();
    }

    @Test
    public void testRustySwordDamageDiceRange() {
        boolean sawAboveOne = false;
        for (int i = 0; i < 100; i++) {
            int roll = DiceRoller.roll("1d6");
            assertTrue("Roll must be >= 1", roll >= 1);
            assertTrue("Roll must be <= 6", roll <= 6);
            if (roll > 1) {
                sawAboveOne = true;
            }
        }
        assertTrue("1d6 should produce values greater than 1 across 100 rolls", sawAboveOne);
    }
}
