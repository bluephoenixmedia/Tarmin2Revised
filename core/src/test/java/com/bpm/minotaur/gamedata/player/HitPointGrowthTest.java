package com.bpm.minotaur.gamedata.player;

import com.bpm.minotaur.gamedata.Difficulty;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * A seeker's health keeps some pace with the strata (Houses of the Maze balance): the seal duels
 * found seekers of levels 13 to 23 at 47 to 77 hit points, against courts and beasts that strike
 * for twenty a blow. A level's worth of health is about five, not three.
 */
public class HitPointGrowthTest {

    private static int gainOverLevels(int constitution, int levels) {
        PlayerStats stats = new PlayerStats(Difficulty.MEDIUM);
        stats.setConstitution(constitution);
        int before = stats.getMaxHP();
        for (int i = 0; i < levels; i++) stats.addExperience(stats.getExperienceToNextLevel());
        return stats.getMaxHP() - before;
    }

    @Test
    public void anAverageSeekerGainsAboutFiveALevel() {
        int levels = 60;
        float perLevel = (float) gainOverLevels(10, levels) / levels;
        assertTrue("about five a level: " + perLevel, perLevel >= 4.6f && perLevel <= 5.4f);
    }

    @Test
    public void evenAFrailSeekerGainsSomething() {
        int levels = 100;
        assertTrue(gainOverLevels(3, levels) >= levels * PlayerStats.MIN_HP_PER_LEVEL);
    }
}
