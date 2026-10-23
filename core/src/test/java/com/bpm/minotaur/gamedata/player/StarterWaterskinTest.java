package com.bpm.minotaur.gamedata.player;

import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.ItemTemplate;
import com.bpm.minotaur.gamedata.item.Waterskin;
import com.bpm.minotaur.gamedata.liquid.LiquidType;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/** Every life sets out with a full waterskin, not only the first (playtest 2026-10-22). */
public class StarterWaterskinTest {

    @Before
    public void setUp() {
        if (com.badlogic.gdx.Gdx.app == null) {
            com.badlogic.gdx.Gdx.app = (com.badlogic.gdx.Application) java.lang.reflect.Proxy.newProxyInstance(
                    com.badlogic.gdx.Application.class.getClassLoader(),
                    new Class<?>[]{com.badlogic.gdx.Application.class},
                    (proxy, method, args) -> null);
        }
    }

    @Test
    public void aRebornLifeCarriesAFullSkinOfCleanWater() {
        Player player = new Player(1, 1);
        Item skin = Item.fromTemplate(Item.ItemType.WATERSKIN, new ItemTemplate());
        assertTrue(player.giveStarterWaterskin(skin));
        assertTrue(player.getInventory().getMainInventory().contains(skin));
        assertTrue(Waterskin.isFull(skin));
        assertEquals(LiquidType.WATER, skin.getLiquidContent());
        assertFalse("clean surface water, no strata sickness", skin.isStrataSource());
        assertFalse(player.giveStarterWaterskin(null));
    }
}
