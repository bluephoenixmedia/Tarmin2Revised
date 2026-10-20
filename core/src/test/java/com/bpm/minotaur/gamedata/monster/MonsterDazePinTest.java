package com.bpm.minotaur.gamedata.monster;

import com.bpm.minotaur.managers.CombatManager;
import org.junit.Test;

import static org.junit.Assert.*;

/** Dazed and Pinned, the effects the Combat Arts need (#242). */
public class MonsterDazePinTest {

    private static Monster kobold() {
        MonsterTemplate t = new MonsterTemplate();
        t.baseLevel = 1;
        t.maxHP = 12;
        Monster m = new Monster(Monster.MonsterType.KOBOLD, 12, 10);
        m.setTemplate(t);
        return m;
    }

    @Test
    public void aDazedMonsterHitsTwoWorseAndActsSlower() {
        Monster m = kobold();
        int toHit = CombatManager.calculateMonsterAttackBonus(m);
        int speed = m.getEffectiveSpeed();

        assertEquals(Monster.DazeOutcome.DAZED, m.applyDaze());

        assertTrue(m.isDazed());
        assertEquals(toHit - 2, CombatManager.calculateMonsterAttackBonus(m));
        assertTrue("slightly slower: " + m.getEffectiveSpeed() + " < " + speed, m.getEffectiveSpeed() < speed);
    }

    @Test
    public void dazingADazedMonsterStunsIt() {
        Monster m = kobold();
        m.applyDaze();

        assertEquals(Monster.DazeOutcome.STUNNED, m.applyDaze());
        assertTrue(m.isStunned());
        assertFalse("the stun replaces the daze", m.isDazed());
    }

    @Test
    public void aMonsterImmuneToStunIsDazedInstead() {
        Monster m = kobold();
        m.applyDaze();
        m.setStunImmunityTurns(2);

        assertEquals(Monster.DazeOutcome.DAZED, m.applyDaze());
        assertFalse(m.isStunned());
        assertTrue(m.isDazed());
    }

    @Test
    public void aPinHoldsForThreeTurnsAndBothEffectsWearOff() {
        Monster m = kobold();
        m.applyPin();
        m.applyDaze();
        assertTrue(m.isPinned());

        for (int i = 0; i < Monster.PIN_TURNS - 1; i++) m.tickDazeAndPin();
        assertTrue("still pinned on the last turn", m.isPinned());
        m.tickDazeAndPin();
        assertFalse(m.isPinned());
        assertFalse(m.isDazed());
    }
}
