package com.bpm.minotaur.managers;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class TormentManagerTest {

    private TormentManager tormentManager;

    @Before
    public void setUp() {
        tormentManager = TormentManager.getInstance();
        tormentManager.clearModifiers();
    }

    @Test
    public void testInitialState() {
        assertEquals(0, tormentManager.getHeatLevel());
        assertEquals(1.0f, tormentManager.getMonsterSpeedMultiplier(), 0.001f);
        assertEquals(1.0f, tormentManager.getMonsterDamageMultiplier(), 0.001f);
        assertEquals(1.0f, tormentManager.getLootSpawnMultiplier(), 0.001f);
        assertEquals(1.0f, tormentManager.getHealingMultiplier(), 0.001f);
        assertEquals(50, tormentManager.getMaxAllowedDeaths());
    }

    @Test
    public void testToggleModifiersAndHeatCalculation() {
        tormentManager.toggleModifier(TormentManager.TormentModifier.FRENZIED_FOES); // 1 heat
        assertTrue(tormentManager.hasModifier(TormentManager.TormentModifier.FRENZIED_FOES));
        assertEquals(1, tormentManager.getHeatLevel());
        assertEquals(1.25f, tormentManager.getMonsterSpeedMultiplier(), 0.001f);

        tormentManager.toggleModifier(TormentManager.TormentModifier.DEADLY_BLOWS); // +2 heat
        assertEquals(3, tormentManager.getHeatLevel());
        assertEquals(1.25f, tormentManager.getMonsterDamageMultiplier(), 0.001f);

        tormentManager.toggleModifier(TormentManager.TormentModifier.CRITICAL_BRIDGE); // +3 heat
        assertEquals(6, tormentManager.getHeatLevel());
        assertEquals(30, tormentManager.getMaxAllowedDeaths());

        // Toggle off FRENZIED_FOES
        tormentManager.toggleModifier(TormentManager.TormentModifier.FRENZIED_FOES);
        assertFalse(tormentManager.hasModifier(TormentManager.TormentModifier.FRENZIED_FOES));
        assertEquals(5, tormentManager.getHeatLevel());
        assertEquals(1.0f, tormentManager.getMonsterSpeedMultiplier(), 0.001f);
    }
}
