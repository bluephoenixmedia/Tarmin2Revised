package com.bpm.minotaur.gamedata.player;

import com.bpm.minotaur.gamedata.Difficulty;
import com.bpm.minotaur.gamedata.monster.MonsterColor;
import com.bpm.minotaur.gamedata.monster.MonsterXp;
import org.junit.Test;

import static org.junit.Assert.*;

/** The front-loaded XP curve (#311; the #258 grilling): a typical life levels up. */
public class XpCurveTest {

    /** On-level kills of a common colour that the level after {@code level} costs. */
    private static double killsFor(PlayerStats stats) {
        int onLevelKill = MonsterXp.forKill(MonsterXp.forLevel(stats.getLevel()), MonsterColor.WHITE);
        return stats.getExperienceToNextLevel() / (double) onLevelKill;
    }

    @Test
    public void levelsTwoToFourCostAboutFourSixAndEightKills() {
        PlayerStats stats = new PlayerStats(Difficulty.MEDIUM);
        double[] expected = { 4, 6, 8 };
        for (double kills : expected) {
            assertEquals("level " + (stats.getLevel() + 1), kills, killsFor(stats), 0.5);
            stats.addExperience(stats.getExperienceToNextLevel());
        }
        assertEquals(4, stats.getLevel());
    }

    @Test
    public void fromLevelFiveTheCurveIsAsItWas() {
        PlayerStats stats = new PlayerStats(Difficulty.MEDIUM);
        while (stats.getLevel() < 4) stats.addExperience(stats.getExperienceToNextLevel());
        assertEquals("level 5 still costs 90 x 1.2^4", (int) Math.round(90 * Math.pow(1.2, 4)), stats.getExperienceToNextLevel());
    }

    @Test
    public void anOlderSaveHoldingMoreThanTheNewCostLevelsUpOnItsNextKill() {
        PlayerStats stats = new PlayerStats(Difficulty.MEDIUM);
        // As an older save restores it: level 1, 50 XP banked toward the old 108.
        stats.setLevel(1);
        stats.setExperience(50);
        stats.setExperienceToNextLevel(108);
        stats.recomputeExperienceToNextLevel();
        assertEquals(1, stats.getLevel());

        assertTrue(stats.addExperience(12));
        assertEquals(2, stats.getLevel());
    }
}
