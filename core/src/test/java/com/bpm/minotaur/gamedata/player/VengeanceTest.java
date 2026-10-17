package com.bpm.minotaur.gamedata.player;

import com.bpm.minotaur.gamedata.player.Vengeance.Role;
import com.bpm.minotaur.telemetry.TelemetryManager;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/** Vengeance: the HP a kill gives back, scaled by the threat of what died (#190). */
public class VengeanceTest {

    @Before
    public void resetTelemetry() {
        TelemetryManager.getInstance().startNewRun();
    }

    @Test
    public void roleFallsBackOnLevelRelativeToThePlayer() {
        assertEquals(Role.TRIVIAL, Vengeance.roleByLevel(2, 5, false));
        assertEquals(Role.TRIVIAL, Vengeance.roleByLevel(1, 5, false));
        assertEquals(Role.FODDER, Vengeance.roleByLevel(3, 5, false));
        assertEquals(Role.FODDER, Vengeance.roleByLevel(4, 5, false));
        assertEquals(Role.SOLDIER, Vengeance.roleByLevel(5, 5, false));
        assertEquals(Role.SOLDIER, Vengeance.roleByLevel(7, 5, false));
        assertEquals(Role.HEAVY, Vengeance.roleByLevel(8, 5, false));
    }

    @Test
    public void anEliteIsHeavyUnlessItIsFarBelowThePlayer() {
        assertEquals(Role.HEAVY, Vengeance.roleByLevel(5, 5, true));
        assertEquals(Role.TRIVIAL, Vengeance.roleByLevel(2, 5, true));
    }

    @Test
    public void amountIsAShareOfMaxHp() {
        assertEquals(0, Vengeance.amount(Role.TRIVIAL, false, 100));
        assertEquals(2, Vengeance.amount(Role.FODDER, false, 100));
        assertEquals(8, Vengeance.amount(Role.SOLDIER, false, 100));
        assertEquals(20, Vengeance.amount(Role.HEAVY, false, 100));
        assertEquals(30, Vengeance.amount(Role.HEAVY, true, 100));
        assertEquals(12, Vengeance.amount(Role.SOLDIER, true, 100));
    }

    @Test
    public void aWorthwhileKillAlwaysGivesAtLeastOne() {
        assertEquals(1, Vengeance.amount(Role.FODDER, false, 22));
        assertEquals(0, Vengeance.amount(Role.TRIVIAL, true, 22));
    }

    @Test
    public void anOnLevelFightNetsAboutMinusFourAt22Hp() {
        // A fight costs 20-35% of 22 HP (combat targets, #158): 4 to 8 HP.
        int vengeance = Vengeance.amount(Role.SOLDIER, false, 22);
        assertEquals(2, vengeance);
        int lightFight = Math.round(22 * 0.20f) - vengeance;
        int hardFight = Math.round(22 * 0.35f) - vengeance;
        assertTrue(lightFight >= 2 && hardFight <= 6);
    }

    @Test
    public void outOfDepthFallsBackOnLevelWellAboveThePlayer() {
        assertFalse(Vengeance.outOfDepthByLevel(8, 4));
        assertTrue(Vengeance.outOfDepthByLevel(9, 4));
    }

    @Test
    public void grantHealsCappedAtMaxAndRecordsTelemetry() {
        Player player = new Player(0, 0);
        int max = player.getStats().getMaxHP();
        player.takeTrueDamage(max - 1);

        int healed = Vengeance.grant(player, Role.HEAVY, false);

        assertEquals(Vengeance.amount(Role.HEAVY, false, max), healed);
        assertEquals(1 + healed, player.getCurrentHP());
        assertEquals(healed, TelemetryManager.getInstance().getHpVengeance());

        player.setCurrentHP(max);
        assertEquals("nothing to restore at full HP", 0, Vengeance.grant(player, Role.HEAVY, false));
        assertEquals(healed, TelemetryManager.getInstance().getHpVengeance());
    }
}
