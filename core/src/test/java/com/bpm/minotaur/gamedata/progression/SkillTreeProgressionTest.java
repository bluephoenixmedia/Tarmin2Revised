package com.bpm.minotaur.gamedata.progression;

import com.bpm.minotaur.gamedata.Difficulty;
import com.bpm.minotaur.gamedata.player.Player;
import com.bpm.minotaur.gamedata.player.PlayerStats;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class SkillTreeProgressionTest {

    private PlayerStats stats;
    private ShelterAltar altar;

    @Before
    public void setUp() {
        if (com.badlogic.gdx.Gdx.app == null) {
            com.badlogic.gdx.Gdx.app = (com.badlogic.gdx.Application) java.lang.reflect.Proxy.newProxyInstance(
                    com.badlogic.gdx.Application.class.getClassLoader(),
                    new Class<?>[]{com.badlogic.gdx.Application.class},
                    (proxy, method, args) -> null
            );
        }
        stats = new PlayerStats(Difficulty.MEDIUM);
        altar = ShelterAltar.getInstance();
        altar.reset();
    }

    @Test
    public void testAutomaticLevelUpOnExperienceThreshold() {
        assertEquals(1, stats.getLevel());
        assertEquals(0, stats.getUnallocatedAttributePoints());
        assertEquals(0, stats.getUnallocatedSkillPoints());

        int needed = stats.getExperienceToNextLevel();
        assertTrue("Needed XP should be positive", needed > 0);

        // Add enough XP to level up directly without bed rest
        boolean leveled = stats.addExperience(needed);
        assertTrue("Player should level up automatically", leveled);
        assertEquals("Player should now be Level 2", 2, stats.getLevel());
        assertEquals("Should receive 2 Attribute Points per level", 2, stats.getUnallocatedAttributePoints());
        assertEquals("Should receive 1 Skill Point per level", 1, stats.getUnallocatedSkillPoints());
    }

    @Test
    public void testAttributeAllocationAndCap() {
        stats.addExperience(stats.getExperienceToNextLevel()); // Level 2, 2 AP
        assertEquals(2, stats.getUnallocatedAttributePoints());
        assertEquals(10, stats.getStrength());

        // Allocate 1 STR
        boolean allocated = stats.allocateAttribute(ShelterAltar.StatType.STRENGTH);
        assertTrue("Allocating STR should succeed", allocated);
        assertEquals(11, stats.getStrength());
        assertEquals(1, stats.getUnallocatedAttributePoints());

        // Test CON allocation grants retroactive HP
        int initialHp = stats.getMaxHP();
        stats.allocateAttribute(ShelterAltar.StatType.CONSTITUTION);
        assertEquals(11, stats.getConstitution());
        assertEquals(initialHp + 2, stats.getMaxHP());
        assertEquals(0, stats.getUnallocatedAttributePoints());

        // Cannot allocate with 0 points
        assertFalse(stats.allocateAttribute(ShelterAltar.StatType.AGILITY));

        // Test max cap of 20
        stats.setStrength(20);
        stats.addExperience(stats.getExperienceToNextLevel() * 3); // More points
        assertFalse("Cannot allocate stat past cap of 20", stats.allocateAttribute(ShelterAltar.StatType.STRENGTH));
    }

    @Test
    public void testDualWielderGatingAndProgression() {
        assertFalse("Player cannot dual wield by default", stats.canDualWield());

        // Dual Wielder requires STR 11
        stats.setStrength(10);
        stats.addExperience(stats.getExperienceToNextLevel()); // 1 Skill Point
        assertFalse("Cannot learn Dual Wielder with STR 10",
                SkillRegistry.getInstance().canLearn(stats, SkillId.DUAL_WIELDER));

        // Allocate STR to 11
        stats.allocateAttribute(ShelterAltar.StatType.STRENGTH);
        assertEquals(11, stats.getStrength());
        assertTrue("Can learn Dual Wielder with STR 11 and 1 SP",
                SkillRegistry.getInstance().canLearn(stats, SkillId.DUAL_WIELDER));

        // Learn Dual Wielder
        boolean learned = stats.learnSkill(SkillId.DUAL_WIELDER);
        assertTrue("Learning Dual Wielder should succeed", learned);
        assertTrue("Player can now dual wield", stats.canDualWield());
        assertTrue(stats.hasSkill(SkillId.DUAL_WIELDER));
        assertEquals(0, stats.getUnallocatedSkillPoints());

        // Cannot learn twice
        assertFalse(stats.learnSkill(SkillId.DUAL_WIELDER));
    }

    @Test
    public void testSkillPrerequisitesAndDisciplineProgression() {
        // Brutal Cleave requires Dual Wielder + STR 13
        stats.setStrength(13);
        stats.addExperience(stats.getExperienceToNextLevel()); // 1 SP

        assertFalse("Cannot learn Brutal Cleave without Dual Wielder prerequisite",
                SkillRegistry.getInstance().canLearn(stats, SkillId.BRUTAL_CLEAVE));

        // Learn Dual Wielder first
        stats.learnSkill(SkillId.DUAL_WIELDER);

        // Earn another point for Brutal Cleave
        stats.addExperience(stats.getExperienceToNextLevel());
        assertTrue("Can learn Brutal Cleave once Dual Wielder is unlocked and STR >= 13",
                SkillRegistry.getInstance().canLearn(stats, SkillId.BRUTAL_CLEAVE));

        assertTrue(stats.learnSkill(SkillId.BRUTAL_CLEAVE));
        assertTrue(stats.hasSkill(SkillId.BRUTAL_CLEAVE));
    }

    @Test
    public void testTrainingDummyStationInShelter() {
        assertNotNull(ShelterAltar.Station.TRAINING_DUMMY);
        assertEquals(25, ShelterAltar.Station.TRAINING_DUMMY.getCost());

        altar.reset();
        assertFalse("Skill tree training should be locked before building Training Grounds",
                altar.isSkillTreeUnlocked());

        altar.setStationUnlocked(ShelterAltar.Station.TRAINING_DUMMY, true);
        assertTrue("Skill tree training should be unlocked once Training Grounds is built",
                altar.isSkillTreeUnlocked());
    }
}
