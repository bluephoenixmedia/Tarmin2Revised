package com.bpm.minotaur.gamedata.player;

import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.ItemTemplate;
import com.bpm.minotaur.gamedata.item.Waterskin;
import com.bpm.minotaur.gamedata.liquid.LiquidType;
import com.bpm.minotaur.managers.GameEventManager;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/** Survival presses within an expedition (#312; the #261 grilling). */
public class SurvivalPaceTest {

    private static final int CON = 10;

    @Before
    public void setUp() {
        if (com.badlogic.gdx.Gdx.app == null) {
            com.badlogic.gdx.Gdx.app = (com.badlogic.gdx.Application) java.lang.reflect.Proxy.newProxyInstance(
                    com.badlogic.gdx.Application.class.getClassLoader(),
                    new Class<?>[]{com.badlogic.gdx.Application.class},
                    (proxy, method, args) -> null);
        }
    }

    /** The turn hunger first warns, eating {@code rations} rations each time it does. */
    private static int hungryWarningTurn(int rations) {
        return hungryWarningTurn(rations, 1f);
    }

    private static int hungryWarningTurn(int rations, float load) {
        float nutrition = Hunger.START;
        for (int turn = 1; turn < 5000; turn++) {
            nutrition -= Hunger.burn(load, 1f, 1f);
            if (Hunger.stageOf(nutrition, CON) == Hunger.Stage.HUNGRY) {
                if (rations == 0) return turn;
                rations--;
                nutrition += Hunger.RATION;
            }
        }
        return -1;
    }

    /** The turn thirst first warns, drinking the starting skin's draughts each time it does. */
    private static int thirstyWarningTurn(int draughts) {
        float hydration = Thirst.START;
        for (int turn = 1; turn < 5000; turn++) {
            hydration -= Thirst.burn(1f, 1f, 1f);
            if (Thirst.stageOf(hydration, CON) == Thirst.Stage.THIRSTY) {
                if (draughts == 0) return turn;
                draughts--;
                hydration += Thirst.DRINK_FULL;
            }
        }
        return -1;
    }

    @Test
    public void anUnfedBodyIsWarnedAroundTurnThreeFifty() {
        assertEquals(350, hungryWarningTurn(0), 25);
        assertEquals(350, thirstyWarningTurn(0), 25);
    }

    @Test
    public void theStartingKitCarriesItToAroundTurnSevenFifty() {
        assertEquals(750, hungryWarningTurn(Hunger.STARTING_RATIONS), 25);
        assertEquals(750, thirstyWarningTurn(Thirst.WATERSKIN_CAPACITY), 25);
    }

    @Test
    public void aStressedLoadBringsTheWarningForwardInProportion() {
        assertEquals(351 / 1.5f, hungryWarningTurn(0, 1.5f), 2);
    }

    @Test
    public void theLaterStagesKeepTheirSpacingAfterTheWarning() {
        assertEquals(100, Hunger.HUNGRY_BELOW - Hunger.WEAK_BELOW, 0f);
        assertEquals(100, Thirst.THIRSTY_BELOW - Thirst.DEHYDRATED_BELOW, 0f);
    }

    private static Item fullSkin() {
        ItemTemplate t = new ItemTemplate();
        Item skin = Item.fromTemplate(Item.ItemType.WATERSKIN, t);
        Waterskin.fill(skin, LiquidType.WATER, false);
        return skin;
    }

    @Test
    public void aDrinkAtFullIsRefusedAndCostsNothing() {
        Player player = new Player(3f, 3f);
        player.getStats().setHydration(Thirst.MAX_STORE);
        GameEventManager events = new GameEventManager();
        Item skin = fullSkin();
        int charges = skin.getCurrentCharges();

        assertFalse("no turn is spent", player.drinkFromWaterskin(skin, events));

        assertEquals("nothing is consumed", charges, skin.getCurrentCharges());
        assertTrue(events.getMessageHistory().toString(),
                events.getMessageHistory().stream().anyMatch(m -> m.startsWith("You are not thirsty")));
    }

    @Test
    public void withRoomToDrinkTheDraughtGoesDown() {
        Player player = new Player(3f, 3f);
        player.getStats().setHydration(Thirst.MAX_STORE - Thirst.DRINK_FULL);
        Item skin = fullSkin();
        int charges = skin.getCurrentCharges();

        assertTrue(player.drinkFromWaterskin(skin, new GameEventManager()));

        assertEquals(charges - 1, skin.getCurrentCharges());
    }
}
