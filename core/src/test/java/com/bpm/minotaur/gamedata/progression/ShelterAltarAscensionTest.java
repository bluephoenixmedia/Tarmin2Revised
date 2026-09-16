package com.bpm.minotaur.gamedata.progression;

import com.bpm.minotaur.gamedata.Difficulty;
import com.bpm.minotaur.gamedata.player.PlayerStats;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class ShelterAltarAscensionTest {

    private ShelterAltar altar;

    @Before
    public void setUp() {
        if (com.badlogic.gdx.Gdx.app == null) {
            com.badlogic.gdx.Gdx.app = (com.badlogic.gdx.Application) java.lang.reflect.Proxy.newProxyInstance(
                    com.badlogic.gdx.Application.class.getClassLoader(),
                    new Class<?>[]{com.badlogic.gdx.Application.class},
                    (proxy, method, args) -> null
            );
        }
        altar = ShelterAltar.getInstance();
        // Reset ascension state for testing
        altar.setCrestsOfValor(0);
        for (ShelterAltar.StatType stat : ShelterAltar.StatType.values()) {
            altar.setAscensionTier(stat, 0);
        }
    }

    @Test
    public void testCrestsOfValorAccumulation() {
        assertEquals(0, altar.getCrestsOfValor());

        altar.addCrestsOfValor(3);
        assertEquals(3, altar.getCrestsOfValor());

        altar.addCrestsOfValor(2);
        assertEquals(5, altar.getCrestsOfValor());
    }

    @Test
    public void testEscalatingCostsAndTierProgression() {
        ShelterAltar.StatType stat = ShelterAltar.StatType.STRENGTH;
        int[] expectedCosts = {1, 2, 4, 7, 10};

        for (int tier = 0; tier < 5; tier++) {
            assertEquals("Cost mismatch at tier " + tier, expectedCosts[tier], altar.getAscensionCost(stat));
            // Add exact crests needed
            altar.addCrestsOfValor(expectedCosts[tier]);
            boolean upgraded = altar.purchaseAscension(stat);
            assertTrue("Expected upgrade to succeed at tier " + tier, upgraded);
            assertEquals(tier + 1, altar.getAscensionTier(stat));
            assertEquals(tier + 1, altar.getAscensionBonus(stat));
        }

        // At Tier 5 (MAX)
        assertEquals(5, altar.getAscensionTier(stat));
        assertEquals(5, altar.getAscensionBonus(stat));
        assertEquals(-1, altar.getAscensionCost(stat));

        // Attempting another upgrade should fail
        altar.addCrestsOfValor(999);
        boolean maxUpgrade = altar.purchaseAscension(stat);
        assertFalse(maxUpgrade);
        assertEquals(5, altar.getAscensionTier(stat));
    }

    @Test
    public void testInsufficientCrestsFailsUpgrade() {
        ShelterAltar.StatType stat = ShelterAltar.StatType.AGILITY;
        altar.setCrestsOfValor(0);

        boolean result = altar.purchaseAscension(stat);
        assertFalse(result);
        assertEquals(0, altar.getAscensionTier(stat));
    }

    @Test
    public void testPlayerStatsReflectsAscensionBonus() {
        PlayerStats stats = new PlayerStats(Difficulty.MEDIUM);
        int baseStr = stats.getStrength();
        int baseAgi = stats.getAgility();

        altar.addCrestsOfValor(1);
        altar.purchaseAscension(ShelterAltar.StatType.STRENGTH);

        assertEquals(baseStr + 1, stats.getStrength());
        assertEquals(baseAgi, stats.getAgility()); // AGI unaffected

        altar.addCrestsOfValor(1);
        altar.purchaseAscension(ShelterAltar.StatType.AGILITY);
        assertEquals(baseAgi + 1, stats.getAgility());
    }
}
