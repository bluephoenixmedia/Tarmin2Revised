package com.bpm.minotaur.gamedata.player;

import com.bpm.minotaur.gamedata.Difficulty;
import com.bpm.minotaur.gamedata.progression.ShelterAltar;
import com.bpm.minotaur.gamedata.progression.ShelterAltar.StatType;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * 2026-10-03: the level-up screen capped attributes on the value <i>with</i> Ascension
 * bonuses, while allocation caps the trained value only. With a few Ascensions every +
 * went dead -- disabled and untouchable -- with points still to spend, and the screen
 * looked as if it had lost the mouse.
 */
public class AttributeCapTest {

    @Test
    public void theCapReadsTheTrainedValueNotTheAscendedOne() {
        PlayerStats stats = new PlayerStats(Difficulty.values()[0]);
        stats.setStrength(15);
        int ascension = ShelterAltar.getInstance().getAscensionTier(StatType.STRENGTH);

        assertEquals("the screen shows trained + Ascension", 15 + ascension, stats.getEffectiveStat(StatType.STRENGTH));
        assertEquals("the cap reads the trained value alone", 15, stats.getBaseStat(StatType.STRENGTH));
    }

    @Test
    public void allocationStopsAtTheCapOnTheTrainedValue() {
        PlayerStats stats = new PlayerStats(Difficulty.values()[0]);
        stats.setStrength(PlayerStats.MAX_ATTRIBUTE_CAP - 1);
        stats.setUnallocatedAttributePoints(2);

        assertTrue(stats.allocateAttribute(StatType.STRENGTH));
        assertEquals(PlayerStats.MAX_ATTRIBUTE_CAP, stats.getBaseStat(StatType.STRENGTH));
        assertFalse("no point past the cap", stats.allocateAttribute(StatType.STRENGTH));
        assertEquals(1, stats.getUnallocatedAttributePoints());
    }
}
