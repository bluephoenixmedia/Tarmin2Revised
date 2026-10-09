package com.bpm.minotaur.gamedata.history.war;

import org.junit.Test;

import java.util.Random;

import static org.junit.Assert.*;

/** Plan T2.4 and T2.6: a battle's bookkeeping, played out without a map. */
public class BattleModelTest {

    /** Each soldier lands a killing blow on an enemy soldier this often per turn. */
    private static final float KILL_RATE = 0.06f;

    private static int kills(Random rng, int attackers, int defenders) {
        int k = 0;
        for (int i = 0; i < attackers; i++) if (rng.nextFloat() < KILL_RATE) k++;
        return Math.min(k, defenders);
    }

    /** Plays a battle with abstract attrition until it ends; returns the turns it took. */
    private static int play(BattleModel m, Random rng, int killCaptainOfAAt) {
        int aliveA = 0, aliveB = 0;
        for (int turn = 1; turn <= BattleModel.TURN_CAP + 1; turn++) {
            BattleModel.Orders o = m.step(aliveA, aliveB, turn == killCaptainOfAAt, false);
            if (o.outcome != BattleModel.Outcome.ONGOING) return turn;
            aliveA += o.sendA;
            aliveB += o.sendB;
            int downA = kills(rng, aliveB, aliveA);
            int downB = kills(rng, aliveA, aliveB);
            aliveA -= downA;
            aliveB -= downB;
        }
        fail("battle never ended");
        return -1;
    }

    @Test
    public void fiftyBattlesAllEndInARoutOrAtTheCap() {
        Random rng = new Random(3);
        int routs = 0;
        for (int i = 0; i < 50; i++) {
            BattleModel m = new BattleModel(1, 5 + rng.nextInt(25), 2, 5 + rng.nextInt(25));
            int turns = play(m, rng, -1);
            assertTrue(turns <= BattleModel.TURN_CAP + 1);
            assertNotEquals(-1, m.winner());
            if (m.outcome() != BattleModel.Outcome.TURN_CAP) routs++;
        }
        assertTrue("most battles end in a rout: " + routs, routs >= 35);
    }

    @Test
    public void theFieldIsHeldAtThirtyToFortyCombatants() {
        BattleModel m = new BattleModel(1, 40, 2, 40);
        BattleModel.Orders o = m.step(0, 0, false, false);
        int field = o.sendA + o.sendB;
        for (int t = 0; t < 20; t++) {
            o = m.step(field / 2, field / 2, false, false);
            field += o.sendA + o.sendB;
        }
        assertTrue("field " + field, field >= 30 && field <= 40);
    }

    @Test
    public void killingTheWarCaptainBreaksThatSideSoon() {
        Random rng = new Random(9);
        int brokenFast = 0;
        for (int i = 0; i < 20; i++) {
            BattleModel m = new BattleModel(1, 30, 2, 30);
            int turns = play(m, rng, 10);
            if (m.outcome() == BattleModel.Outcome.A_ROUTED && turns <= 10 + BattleModel.CAPTAIN_BREAK_TURNS) brokenFast++;
        }
        assertTrue("captain's death routs within a few turns: " + brokenFast + "/20", brokenFast >= 15);
    }

    @Test
    public void aSideWithNothingLeftRoutsAtOnce() {
        BattleModel m = new BattleModel(1, 0, 2, 10);
        m.step(0, 0, false, false);
        BattleModel.Orders o = m.step(0, 5, false, false);
        assertEquals(BattleModel.Outcome.A_ROUTED, o.outcome);
        assertEquals(2, m.winner());
        assertEquals(1, m.loser());
    }

    @Test
    public void reserveComesFromHouseStrength() {
        assertEquals(5, BattleModel.reserveFor(10f));
        assertEquals(25, BattleModel.reserveFor(100f));
        assertEquals(30, BattleModel.reserveFor(200f));
    }
}
