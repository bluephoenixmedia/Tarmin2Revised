package com.bpm.minotaur.gamedata.player;

import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonWriter;
import com.bpm.minotaur.gamedata.Difficulty;
import com.bpm.minotaur.gamedata.history.DoctrineCatalogTest;
import com.bpm.minotaur.gamedata.history.HistorySimulator;
import com.bpm.minotaur.gamedata.history.HistoryWorld;
import com.bpm.minotaur.gamedata.history.LifeForge;
import com.bpm.minotaur.gamedata.save.PlayerSaveData;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.*;

/** Each Incarnation wakes in a prisoner, with one reroll, and the save keeps who (#279). */
public class PlayerLifeTest {

    private static HistoryWorld world;
    private Player player;

    @BeforeClass
    public static void load() throws IOException {
        world = HistorySimulator.prehistory(21L, DoctrineCatalogTest.loadCatalog());
    }

    @Before
    public void setUp() {
        if (com.badlogic.gdx.Gdx.app == null) {
            com.badlogic.gdx.Gdx.app = (com.badlogic.gdx.Application) java.lang.reflect.Proxy.newProxyInstance(
                    com.badlogic.gdx.Application.class.getClassLoader(),
                    new Class<?>[]{com.badlogic.gdx.Application.class},
                    (proxy, method, args) -> null);
        }
        player = new Player(1, 1);
    }

    @Test
    public void aNewBodyWaitsForItsLifeAndWakesInTheForgedPrisoner() {
        assertTrue(player.needsLife());
        player.beginLife(world, 7L);
        assertFalse(player.needsLife());
        assertEquals(LifeForge.forge(world, 7L).summary(), player.getLife().summary());
        assertTrue(player.isLifeRevealPending());
    }

    @Test
    public void theWholeIdentityCanBeRerolledOnce() {
        player.beginLife(world, 7L);
        String first = player.getLife().summary();
        assertTrue(player.canRerollLife());
        assertTrue(player.rerollLife(world));
        String second = player.getLife().summary();
        assertNotEquals(first, second);
        assertFalse(player.canRerollLife());
        assertFalse(player.rerollLife(world));
        assertEquals(second, player.getLife().summary());
    }

    @Test
    public void risingClosesTheRevealAndDeathEndsTheLife() {
        player.beginLife(world, 7L);
        player.acknowledgeLife();
        assertFalse(player.isLifeRevealPending());
        player.beginNewIncarnation(Difficulty.values()[0]);
        assertTrue(player.needsLife());
    }

    @Test
    public void theSaveKeepsWhoTheyWereAndTheSpentReroll() {
        player.beginLife(world, 7L);
        player.rerollLife(world);
        Json json = new Json();
        json.setOutputType(JsonWriter.OutputType.json);
        PlayerSaveData restored = json.fromJson(PlayerSaveData.class, json.toJson(new PlayerSaveData(player)));
        Player loaded = new Player(2, 2);
        restored.applyToPlayer(loaded, null, null);

        assertEquals(player.getLife().summary(), loaded.getLife().summary());
        assertEquals(player.getLife().settlementId, loaded.getLife().settlementId);
        assertFalse(loaded.canRerollLife());
        assertTrue(loaded.isLifeRevealPending());
    }

    @Test
    public void aSaveFromBeforeLivesWaitsForOne() {
        Player loaded = new Player(2, 2);
        new PlayerSaveData().applyToPlayer(loaded, null, null);
        assertTrue(loaded.needsLife());
    }

    @Test
    public void aSaveFromBeforeLivesIsGivenItsPrisonerWithoutAWakingMidExpedition() {
        Player loaded = new Player(2, 2);
        new PlayerSaveData().applyToPlayer(loaded, null, null);
        loaded.beginLife(world, 7L);
        assertNotNull(loaded.getLife());
        assertFalse(loaded.isLifeRevealPending());

        loaded.beginNewIncarnation(Difficulty.values()[0]);
        loaded.beginLife(world, 8L);
        assertTrue("the next rebirth is shown as usual", loaded.isLifeRevealPending());
    }

    @Test
    public void aSaveWrittenBetweenDeathAndWakingStillShowsTheWaking() {
        player.beginLife(world, 7L);
        player.beginNewIncarnation(Difficulty.values()[0]);
        Json json = new Json();
        PlayerSaveData restored = json.fromJson(PlayerSaveData.class, json.toJson(new PlayerSaveData(player)));
        Player loaded = new Player(2, 2);
        restored.applyToPlayer(loaded, null, null);
        loaded.beginLife(world, 8L);
        assertTrue(loaded.isLifeRevealPending());
    }
}
