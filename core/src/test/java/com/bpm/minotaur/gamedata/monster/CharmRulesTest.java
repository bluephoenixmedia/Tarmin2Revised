package com.bpm.minotaur.gamedata.monster;

import org.junit.Test;

import static org.junit.Assert.*;

public class CharmRulesTest {

    private static Monster monster(Monster.MonsterType type, MonsterFamily family, int level) {
        Monster m = new Monster(type, 20, 10, 5f, 5f);
        MonsterTemplate t = new MonsterTemplate();
        t.family = family;
        t.baseLevel = level;
        t.maxHP = 20;
        m.setTemplate(t);
        return m;
    }

    @Test
    public void everyCharmSpellIdMapsToAKindAndOthersDoNot() {
        assertEquals(CharmRules.Kind.CHARM_PERSON, CharmRules.Kind.forSpell("charm_person"));
        assertEquals(CharmRules.Kind.DOMINATE_BEAST, CharmRules.Kind.forSpell("DOMINATE_BEAST"));
        assertEquals(CharmRules.Kind.DOMINATE_PERSON, CharmRules.Kind.forSpell("DOMINATE_PERSON"));
        assertEquals(CharmRules.Kind.DOMINATE_MONSTER, CharmRules.Kind.forSpell("DOMINATE_MONSTER"));
        assertNull(CharmRules.Kind.forSpell("FIREBALL"));
        assertNull(CharmRules.Kind.forSpell(null));
    }

    @Test
    public void onlyDominateMonsterIsPermanent() {
        for (CharmRules.Kind k : CharmRules.Kind.values()) {
            assertEquals(k == CharmRules.Kind.DOMINATE_MONSTER, k.isPermanent());
        }
        assertEquals(CharmRules.PERMANENT, CharmRules.duration(CharmRules.Kind.DOMINATE_MONSTER, 20));
    }

    @Test
    public void timedCharmsLastLongerWithLevelAndDominateOutlastsCharm() {
        assertTrue(CharmRules.duration(CharmRules.Kind.CHARM_PERSON, 5) > CharmRules.duration(CharmRules.Kind.CHARM_PERSON, 1));
        assertTrue(CharmRules.duration(CharmRules.Kind.DOMINATE_BEAST, 1) > CharmRules.duration(CharmRules.Kind.CHARM_PERSON, 1));
        assertEquals(CharmRules.duration(CharmRules.Kind.DOMINATE_BEAST, 3), CharmRules.duration(CharmRules.Kind.DOMINATE_PERSON, 3));
    }

    @Test
    public void permanentAlliesAreCappedByCharismaWithAFloorOfOne() {
        assertEquals(1, CharmRules.permanentCap(-3));
        assertEquals(1, CharmRules.permanentCap(0));
        assertEquals(3, CharmRules.permanentCap(2));
    }

    @Test
    public void dominateBeastTakesBeastsOnly() {
        assertNull(CharmRules.refusal(CharmRules.Kind.DOMINATE_BEAST, monster(Monster.MonsterType.SPIDER, MonsterFamily.BEAST, 3), 5));
        assertNotNull(CharmRules.refusal(CharmRules.Kind.DOMINATE_BEAST, monster(Monster.MonsterType.ORC, MonsterFamily.HUMANOID, 3), 5));
    }

    @Test
    public void dominatePersonTakesHumanoidsOnly() {
        assertNull(CharmRules.refusal(CharmRules.Kind.DOMINATE_PERSON, monster(Monster.MonsterType.ORC, MonsterFamily.HUMANOID, 3), 5));
        assertNotNull(CharmRules.refusal(CharmRules.Kind.DOMINATE_PERSON, monster(Monster.MonsterType.SPIDER, MonsterFamily.BEAST, 3), 5));
    }

    @Test
    public void charmPersonOnlyHoldsMonstersNoStrongerThanTheCaster() {
        Monster weak = monster(Monster.MonsterType.ORC, MonsterFamily.HUMANOID, 3);
        assertNull(CharmRules.refusal(CharmRules.Kind.CHARM_PERSON, weak, 3));
        assertNotNull(CharmRules.refusal(CharmRules.Kind.CHARM_PERSON, weak, 2));
    }

    @Test
    public void undeadAndConstructsResistTheTimedSpellsButNotDominateMonster() {
        Monster undead = monster(Monster.MonsterType.SKELETON, MonsterFamily.UNDEAD, 3);
        Monster golem = monster(Monster.MonsterType.IRON_GOLEM, MonsterFamily.MAGICAL, 3);
        assertNotNull(CharmRules.refusal(CharmRules.Kind.DOMINATE_PERSON, undead, 9));
        assertNotNull(CharmRules.refusal(CharmRules.Kind.CHARM_PERSON, golem, 9));
        assertNull(CharmRules.refusal(CharmRules.Kind.DOMINATE_MONSTER, undead, 9));
        assertNull(CharmRules.refusal(CharmRules.Kind.DOMINATE_MONSTER, golem, 9));
    }

    @Test
    public void bossesAndExistingAlliesAreAlwaysRefused() {
        Monster boss = monster(Monster.MonsterType.IRON_GOLEM, MonsterFamily.MAGICAL, 3);
        boss.setBridgeBoss(true);
        assertNotNull(CharmRules.refusal(CharmRules.Kind.DOMINATE_MONSTER, boss, 30));

        Monster already = monster(Monster.MonsterType.ORC, MonsterFamily.HUMANOID, 3);
        already.setAllyTurns(CharmRules.PERMANENT);
        assertNotNull(CharmRules.refusal(CharmRules.Kind.DOMINATE_MONSTER, already, 30));
    }
}
