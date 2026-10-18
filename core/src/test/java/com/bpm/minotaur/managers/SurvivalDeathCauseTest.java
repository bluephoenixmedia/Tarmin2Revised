package com.bpm.minotaur.managers;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.player.Player;
import com.bpm.minotaur.gamedata.player.Thirst;
import com.bpm.minotaur.telemetry.TelemetryManager;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Proxy;

import static org.junit.Assert.*;

/** A death of thirst is not credited to the last monster that hurt the player. */
public class SurvivalDeathCauseTest {

    @Before
    public void setUp() {
        if (Gdx.app == null) {
            Gdx.app = (Application) Proxy.newProxyInstance(
                    Application.class.getClassLoader(),
                    new Class<?>[]{Application.class},
                    (proxy, method, args) -> null);
        }
        TelemetryManager.getInstance().startNewRun();
    }

    @Test
    public void dyingOfThirstClearsAnEarlierAttacker() {
        TelemetryManager telemetry = TelemetryManager.getInstance();
        telemetry.setLastDamageSource("GIANT_BEE");
        Player player = new Player(5f, 5f);
        int con = player.getStats().getConstitution();
        player.getStats().setHydration(Thirst.floor(con));

        new TurnManager().processTurn(new Maze(2, new int[16][16]), player, new MonsterAiManager(), null, null,
                new GameEventManager());

        assertEquals(0, player.getCurrentHP());
        assertEquals("thirst", telemetry.getSurvivalDeath());
        assertNull("the bee did not kill them", telemetry.getKillerMonster());
    }

    @Test
    public void aNewRunForgetsTheSurvivalDeath() {
        TelemetryManager telemetry = TelemetryManager.getInstance();
        telemetry.setSurvivalDeath("starvation");
        telemetry.startNewRun();
        assertNull(telemetry.getSurvivalDeath());
    }
}
