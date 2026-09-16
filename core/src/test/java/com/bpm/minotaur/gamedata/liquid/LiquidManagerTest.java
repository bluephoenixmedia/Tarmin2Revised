package com.bpm.minotaur.gamedata.liquid;

import com.bpm.minotaur.gamedata.player.Player;
import com.bpm.minotaur.managers.GameEventManager;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class LiquidManagerTest {

    private LiquidManager liquidManager;
    private Player player;
    private GameEventManager eventManager;

    @Before
    public void setUp() {
        if (com.badlogic.gdx.Gdx.app == null) {
            com.badlogic.gdx.Gdx.app = (com.badlogic.gdx.Application) java.lang.reflect.Proxy.newProxyInstance(
                    com.badlogic.gdx.Application.class.getClassLoader(),
                    new Class<?>[]{com.badlogic.gdx.Application.class},
                    (proxy, method, args) -> null
            );
        }
        liquidManager = new LiquidManager();
        player = new Player(5, 5);
        eventManager = new GameEventManager();
    }

    @Test
    public void testSetAndGetLiquid() {
        assertEquals(LiquidType.NONE, liquidManager.getLiquidAt(5, 5));
        assertFalse(liquidManager.hasLiquidAt(5, 5));

        liquidManager.setLiquidAt(5, 5, LiquidType.WATER);
        assertEquals(LiquidType.WATER, liquidManager.getLiquidAt(5, 5));
        assertTrue(liquidManager.hasLiquidAt(5, 5));

        liquidManager.setLiquidAt(6, 6, LiquidType.BLACK_MUCK);
        assertEquals(LiquidType.BLACK_MUCK, liquidManager.getLiquidAt(6, 6));

        liquidManager.setLiquidAt(7, 7, LiquidType.BLOOD);
        assertEquals(LiquidType.BLOOD, liquidManager.getLiquidAt(7, 7));
    }

    @Test
    public void testExposureAccumulationAndDrying() {
        liquidManager.setLiquidAt(5, 5, LiquidType.WATER);

        // Step 1 into water
        liquidManager.onPlayerStep(5, 5, player, eventManager);
        assertEquals(1, liquidManager.getExposureSteps());
        assertEquals(LiquidType.WATER, liquidManager.getCurrentExposureType());

        // Step 2 into water
        liquidManager.onPlayerStep(5, 5, player, eventManager);
        assertEquals(2, liquidManager.getExposureSteps());

        // Step onto dry tile (4, 4)
        liquidManager.onPlayerStep(4, 4, player, eventManager);
        assertEquals(1, liquidManager.getExposureSteps());

        // Step onto dry tile again -> dried off
        liquidManager.onPlayerStep(4, 4, player, eventManager);
        assertEquals(0, liquidManager.getExposureSteps());
        assertEquals(LiquidType.NONE, liquidManager.getCurrentExposureType());
    }

    @Test
    public void testBlackMuckToxicityThreshold() {
        liquidManager.setLiquidAt(5, 5, LiquidType.BLACK_MUCK);
        player.getStats().setToxicity(0);

        // Take 4 steps - not yet threshold
        for (int i = 0; i < 4; i++) {
            liquidManager.onPlayerStep(5, 5, player, eventManager);
        }
        assertEquals(4, liquidManager.getExposureSteps());
        assertEquals(0, player.getStats().getToxicity());

        // 5th step triggers toxicity increment (+3)
        liquidManager.onPlayerStep(5, 5, player, eventManager);
        assertEquals(5, liquidManager.getExposureSteps());
        assertEquals(3, player.getStats().getToxicity());
    }

    @Test
    public void testSerializationRoundTrip() {
        liquidManager.setLiquidAt(1, 2, LiquidType.WATER);
        liquidManager.setLiquidAt(10, 15, LiquidType.BLACK_MUCK);
        liquidManager.setLiquidAt(20, 25, LiquidType.BLOOD);

        String serialized = liquidManager.serialize();
        assertNotNull(serialized);
        assertFalse(serialized.isEmpty());

        LiquidManager restored = LiquidManager.deserialize(serialized);
        assertEquals(LiquidType.WATER, restored.getLiquidAt(1, 2));
        assertEquals(LiquidType.BLACK_MUCK, restored.getLiquidAt(10, 15));
        assertEquals(LiquidType.BLOOD, restored.getLiquidAt(20, 25));
        assertEquals(LiquidType.NONE, restored.getLiquidAt(0, 0));
    }
}
