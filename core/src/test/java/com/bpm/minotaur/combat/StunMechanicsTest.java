package com.bpm.minotaur.combat;

import com.bpm.minotaur.gamedata.monster.Monster;
import org.junit.Test;

import static org.junit.Assert.*;

public class StunMechanicsTest {

    @Test
    public void testBasicStunApplication() {
        Monster monster = new Monster(Monster.MonsterType.GOBLIN, 20, 12);
        assertFalse(monster.isStunned());
        assertFalse(monster.isStunImmune());

        boolean applied = monster.applyStun(1);
        assertTrue("Stun should apply to normal target", applied);
        assertTrue(monster.isStunned());
        assertEquals(1, monster.getStunTurns());
    }

    @Test
    public void testStunCannotStackWhileAlreadyStunned() {
        Monster monster = new Monster(Monster.MonsterType.ORC, 30, 13);
        assertTrue(monster.applyStun(1));
        assertTrue(monster.isStunned());

        // Attempting to re-stun while already stunned must fail
        boolean secondStun = monster.applyStun(1);
        assertFalse("Cannot re-apply stun while already stunned", secondStun);
        assertEquals(1, monster.getStunTurns());
    }

    @Test
    public void testConcussionResilienceGrantedUponWaking() {
        Monster monster = new Monster(Monster.MonsterType.SKELETON, 15, 11);
        monster.applyStun(1);
        assertTrue(monster.isStunned());

        // Waking up from stun
        monster.decrementStun();
        assertFalse("Monster should no longer be stunned", monster.isStunned());
        assertTrue("Monster should have concussion resilience", monster.isStunImmune());
        assertEquals(2, monster.getStunImmunityTurns());

        // Attempting to stun while resilient must fail
        assertFalse("Stun must fail during concussion resilience", monster.applyStun(1));
        assertFalse(monster.isStunned());

        // Turn 1 passes
        monster.decrementStunImmunity();
        assertTrue(monster.isStunImmune());
        assertEquals(1, monster.getStunImmunityTurns());
        assertFalse(monster.applyStun(1));

        // Turn 2 passes
        monster.decrementStunImmunity();
        assertFalse("Resilience should have expired", monster.isStunImmune());
        assertEquals(0, monster.getStunImmunityTurns());

        // Now stun can apply again
        assertTrue("Monster can be stunned again after resilience expires", monster.applyStun(1));
        assertTrue(monster.isStunned());
    }

    @Test
    public void testStaggerReducesEffectiveArmorClass() {
        Monster monster = new Monster(Monster.MonsterType.TROLL, 50, 14);
        int baseAc = monster.getEffectiveArmorClass();
        assertEquals(14, baseAc);

        monster.applyStagger(1);
        assertTrue(monster.isStaggered());
        assertEquals(12, monster.getEffectiveArmorClass());

        monster.decrementStagger();
        assertFalse(monster.isStaggered());
        assertEquals(14, monster.getEffectiveArmorClass());
    }
}
