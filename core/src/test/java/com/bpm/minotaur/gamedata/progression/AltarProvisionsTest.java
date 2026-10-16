package com.bpm.minotaur.gamedata.progression;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * The Provisions tree pays out at the start of every expedition, not only after a
 * death (#101). An expedition starts on the first turn outside the shelter; the next
 * pack is only earned by venturing out properly, so ducking in and out of the door
 * farms nothing.
 */
public class AltarProvisionsTest {

    private ShelterAltar altar;

    @Before
    public void setUp() {
        if (com.badlogic.gdx.Gdx.app == null) {
            com.badlogic.gdx.Gdx.app = (com.badlogic.gdx.Application) java.lang.reflect.Proxy.newProxyInstance(
                    com.badlogic.gdx.Application.class.getClassLoader(),
                    new Class<?>[]{com.badlogic.gdx.Application.class},
                    (proxy, method, args) -> null);
        }
        altar = ShelterAltar.getInstance();
        altar.reset();
        com.bpm.minotaur.managers.DivinityManager.getInstance().addDivinities(10_000);
        assertTrue(altar.purchaseUpgrade(ShelterAltar.Tree.PROVISIONS));
        assertTrue(altar.purchaseUpgrade(ShelterAltar.Tree.PROVISIONS));
    }

    @org.junit.After
    public void tearDown() {
        altar.reset(); // the singleton outlives this class; leave no Provisions tier behind
    }

    @Test
    public void theFirstStepOutOfTheShelterHandsOverThePack() {
        altar.onShelterTurn();
        assertEquals(2, altar.claimExpeditionProvisions());
        assertEquals("one pack per expedition", 0, altar.claimExpeditionProvisions());
    }

    @Test
    public void duckingBackInsideWithoutVenturingEarnsNothing() {
        assertEquals(2, altar.claimExpeditionProvisions());
        altar.onShelterTurn();
        assertEquals(0, altar.claimExpeditionProvisions());
    }

    @Test
    public void returningFromARealTripPacksTheNextExpedition() {
        assertEquals(2, altar.claimExpeditionProvisions());
        altar.noteVenturedOut();
        assertEquals("still out on the same expedition", 0, altar.claimExpeditionProvisions());
        altar.onShelterTurn();
        assertEquals(2, altar.claimExpeditionProvisions());
    }

    @Test
    public void aRespawnStartsANewExpedition() {
        assertEquals(2, altar.claimExpeditionProvisions());
        altar.packProvisionsForNewExpedition();
        assertEquals(2, altar.claimExpeditionProvisions());
    }

    @Test
    public void withoutTheTreeThereIsNothingToHandOver() {
        altar.reset();
        assertEquals(0, altar.claimExpeditionProvisions());
    }
}
