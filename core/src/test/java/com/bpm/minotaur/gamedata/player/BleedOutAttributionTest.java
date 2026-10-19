package com.bpm.minotaur.gamedata.player;

import com.bpm.minotaur.gamedata.DamageType;
import org.junit.Test;

import static org.junit.Assert.*;

/** A death a tick after the blow belongs to the monster, not to the bleeding (#236). */
public class BleedOutAttributionTest {

    private static Player woundedThenBled(int bleedTicks) {
        Player p = new Player(1, 1);
        p.getStats().setAgility(10); // no dodge: the blow must land
        p.takeDamage(3, DamageType.PHYSICAL);
        for (int i = 0; i < bleedTicks; i++) p.takeTrueDamage(1);
        return p;
    }

    @Test
    public void bleedingRightAfterTheBlowIsStillTheMonstersKill() {
        assertFalse(woundedThenBled(1).bledOutLongAfterTheLastBlow());
        assertFalse(woundedThenBled(Player.FRESH_WOUND_TICKS).bledOutLongAfterTheLastBlow());
    }

    @Test
    public void bleedingThatOutlastsTheFightIsABleedOut() {
        assertTrue(woundedThenBled(Player.FRESH_WOUND_TICKS + 1).bledOutLongAfterTheLastBlow());
    }

    @Test
    public void aFreshBlowRestartsTheCount() {
        Player p = woundedThenBled(Player.FRESH_WOUND_TICKS + 1);
        p.takeDamage(3, DamageType.PHYSICAL);
        p.takeTrueDamage(1);
        assertFalse(p.bledOutLongAfterTheLastBlow());
    }
}
