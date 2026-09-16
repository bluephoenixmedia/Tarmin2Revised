package com.bpm.minotaur.gamedata.player;

import com.bpm.minotaur.managers.GameEventManager;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * The field Rest action (H): a bounded, deliberately non-spammable restoration
 * of what the old R-bound rest used to do before it was repurposed to open the
 * First Aid modal (see the "way off again" balance investigation). Reintroduced
 * with real teeth against the abuse the original had -- a cooldown, not just an
 * interruption risk -- since the old version let a player camp a cleared room
 * and top off to full for the cost of a fraction of their hunger meter.
 */
public class FieldRestTest {

    private Player player;
    private GameEventManager eventManager;

    @Before
    public void setUp() {
        player = new Player(0, 0); // MEDIUM difficulty: maxHP 14, satiety(food) 7, CON 10
        eventManager = new GameEventManager();
    }

    @Test
    public void testRestHealsAFixedAmountAndCostsOneFood() {
        player.takeTrueDamage(8);
        assertEquals(6, player.getCurrentHP());
        int foodBefore = player.getStats().getFood();

        Player.FieldRestResult result = player.attemptFieldRest(eventManager);

        assertTrue(result.success);
        assertEquals(6 + Player.FIELD_REST_HEAL_AMOUNT, player.getCurrentHP());
        assertEquals("Rest costs exactly one point of satiety, matching the original mechanic",
                foodBefore - 1, player.getStats().getFood());
    }

    @Test
    public void testRestNeverOverhealsPastMaxHp() {
        player.takeTrueDamage(2); // just 2 short of max
        Player.FieldRestResult result = player.attemptFieldRest(eventManager);
        assertTrue(result.success);
        assertEquals(player.getStats().getMaxHP(), player.getCurrentHP());
    }

    @Test
    public void testRestRefusesAtFullHealthWithoutSpendingFood() {
        int foodBefore = player.getStats().getFood();
        Player.FieldRestResult result = player.attemptFieldRest(eventManager);

        assertFalse("Already at full strength -- nothing to spend food on", result.success);
        assertEquals("A refused rest must not cost anything", foodBefore, player.getStats().getFood());
    }

    @Test
    public void testRestRefusesWhenStarving() {
        player.takeTrueDamage(5);
        player.getStats().setFood(0);
        Player.FieldRestResult result = player.attemptFieldRest(eventManager);

        assertFalse("No food in the tank means nothing to spend", result.success);
        assertEquals("An HP loss must not have occurred on a refused rest", 9, player.getCurrentHP());
    }

    /**
     * A refused rest is not a silent no-op: the player pressed a key and got
     * nothing for it, so they need to be told why (still recovering, already at
     * full strength, or too famished) exactly as the successful case tells them
     * what happened.
     */
    @Test
    public void testEveryRefusalReasonReachesThePlayer() {
        // Full health: refused, and the reason reaches the player.
        assertTrue(player.attemptFieldRest(eventManager).success == false
                && !eventManager.getMessageHistory().isEmpty());
        assertTrue(eventManager.getMessageHistory().get(0).toLowerCase().contains("full"));

        // Starving: refused, and the reason reaches the player.
        player.takeTrueDamage(5);
        player.getStats().setFood(0);
        player.attemptFieldRest(eventManager);
        assertTrue(eventManager.getMessageHistory().get(0).toLowerCase().contains("famish"));

        // On cooldown: refused, and the reason reaches the player.
        player.getStats().setFood(5);
        player.attemptFieldRest(eventManager); // succeeds, starts the cooldown
        player.takeTrueDamage(1);
        player.attemptFieldRest(eventManager); // refused: still on cooldown
        assertTrue(eventManager.getMessageHistory().get(0).toLowerCase().contains("breath"));
    }

    /**
     * GameScreen advances FIELD_REST_TICKS_PER_PRESS world-ticks per rest via the
     * same per-turn hook that decrements this cooldown for every other cause of
     * turn advancement -- so a rest's own ticks would otherwise chip away at the
     * cooldown it just set, undermining the anti-spam design (at the shortest
     * regen interval, most of the cooldown could evaporate before the player
     * even regains control). restoreFieldRestCooldown() is how the caller
     * compensates for exactly what its own ticks consumed.
     */
    @Test
    public void testRestoreFieldRestCooldownCompensatesForItsOwnSelfTicks() {
        player.takeTrueDamage(5);
        player.attemptFieldRest(eventManager);
        int freshCooldown = player.getFieldRestCooldownTurns();

        int selfTicks = 5;
        for (int i = 0; i < selfTicks; i++) {
            player.tickFieldRestCooldown();
        }
        player.restoreFieldRestCooldown(selfTicks);

        assertEquals("The rest's own tick advancement must not erode the cooldown it just set",
                freshCooldown, player.getFieldRestCooldownTurns());
    }

    /**
     * The teeth the old mechanic never had: once used, Rest is on a cooldown
     * matching the same Constitution-scaled interval natural regen already
     * uses, so this reads as one coherent healing model instead of a second,
     * disconnected system, and a full hunger bar can no longer buy unlimited
     * back-to-back heals.
     */
    @Test
    public void testRestEntersCooldownMatchingTheRegenInterval() {
        player.takeTrueDamage(10);
        int expectedCooldown = player.getStats().getRegenIntervalTurns();

        Player.FieldRestResult first = player.attemptFieldRest(eventManager);
        assertTrue(first.success);
        assertEquals(expectedCooldown, player.getFieldRestCooldownTurns());

        player.takeTrueDamage(1); // still hurt, still has food: 14 - 10 + 5 - 1 = 8
        Player.FieldRestResult second = player.attemptFieldRest(eventManager);
        assertFalse("Cannot rest again while the cooldown is still counting down", second.success);
        assertEquals("A refused rest must not heal", 8, player.getCurrentHP());

        for (int i = 0; i < expectedCooldown; i++) {
            player.tickFieldRestCooldown();
        }
        assertEquals(0, player.getFieldRestCooldownTurns());
        Player.FieldRestResult third = player.attemptFieldRest(eventManager);
        assertTrue("Once the cooldown fully elapses, resting works again", third.success);
    }
}
