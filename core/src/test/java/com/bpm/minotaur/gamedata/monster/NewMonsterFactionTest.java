package com.bpm.minotaur.gamedata.monster;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class NewMonsterFactionTest {

    @Test
    public void theExiledCastersJoinTheOutcastsNotTheBeasts() {
        assertEquals(Faction.OUTCASTS_AND_HERMITS, Faction.getDefaultFaction("SAGE", MonsterFamily.HUMANOID));
        assertEquals(Faction.OUTCASTS_AND_HERMITS, Faction.getDefaultFaction("JESTER", MonsterFamily.HUMANOID));
    }

    @Test
    public void theUndeadCastersJoinTheUndead() {
        assertEquals(Faction.UNDEAD, Faction.getDefaultFaction("SPECTER", MonsterFamily.UNDEAD));
        assertEquals(Faction.UNDEAD, Faction.getDefaultFaction("SKELETAL_WIZARD", MonsterFamily.UNDEAD));
    }

    @Test
    public void theVerminStayWithTheBeasts() {
        assertEquals(Faction.BEASTS_AND_VERMIN, Faction.getDefaultFaction("GIANT_BEE", MonsterFamily.BEAST));
        assertEquals(Faction.BEASTS_AND_VERMIN, Faction.getDefaultFaction("BAT", MonsterFamily.BEAST));
    }
}
