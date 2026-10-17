package com.bpm.minotaur.managers;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.liquid.LiquidType;
import com.bpm.minotaur.gamedata.monster.Monster;
import com.bpm.minotaur.gamedata.player.Exposure;
import com.bpm.minotaur.gamedata.player.Player;
import com.bpm.minotaur.gamedata.shelter.FieldFire;
import com.bpm.minotaur.generation.Stratum;
import com.bpm.minotaur.lighting.LightSource;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Proxy;

import static org.junit.Assert.*;

/** Exposure in the turn loop (#194): strata air, wetness, freezing water, field fires and regen. */
public class ExposureTurnTest {

    private TurnManager turnManager;
    private MonsterAiManager aiManager;
    private GameEventManager eventManager;
    private Player player;

    @Before
    public void setUp() {
        if (Gdx.app == null) {
            Gdx.app = (Application) Proxy.newProxyInstance(
                    Application.class.getClassLoader(),
                    new Class<?>[]{Application.class},
                    (proxy, method, args) -> null);
        }
        player = new Player(5f, 5f); // stands at the centre of tile (5, 5)
        eventManager = new GameEventManager();
        turnManager = new TurnManager();
        aiManager = new MonsterAiManager();
    }

    private Maze strata(Stratum stratum) {
        Maze maze = new Maze(2, new int[16][16]);
        maze.setStratum(stratum);
        return maze;
    }

    private void turns(Maze maze, int n) {
        for (int i = 0; i < n; i++) {
            turnManager.processTurn(maze, player, aiManager, null, null, eventManager);
        }
    }

    @Test
    public void theFloodedHallsChillAWetPlayerAndAFireWarmsAndDriesThem() {
        Maze halls = strata(Stratum.FLOODED_HALLS);
        player.getStats().setWetness(Exposure.WADING_WETNESS);
        turns(halls, 150);
        float chilled = player.getStats().getBodyTemperature();
        assertTrue("cooled to " + chilled, chilled < Exposure.COMFORTABLE_FROM);

        halls.addFieldFire(new FieldFire(6, 6));
        turns(halls, 40);
        assertEquals(Exposure.Band.WARM, player.getStats().getExposureBand());
        assertEquals(Exposure.Wetness.DRY, player.getStats().getWetnessLevel());
    }

    @Test
    public void temperateStrataLeaveADryPlayerComfortable() {
        Maze maze = strata(Stratum.MAZE);
        turns(maze, 300);
        assertEquals(Exposure.NORMAL, player.getStats().getBodyTemperature(), 0.01f);
    }

    @Test
    public void freezingSlushSoaksAndPlungesOnceOnTheWayIn() {
        Maze maze = strata(Stratum.MAZE);
        maze.getLiquidManager().setLiquidAt(5, 5, LiquidType.FREEZING_SLUSH);
        turns(maze, 1);
        assertEquals(Exposure.Wetness.SOAKED, player.getStats().getWetnessLevel());
        float afterPlunge = player.getStats().getBodyTemperature();
        assertTrue("plunged to " + afterPlunge, afterPlunge < Exposure.NORMAL - Exposure.FREEZING_WATER_PLUNGE + 0.1f);
        turns(maze, 1);
        assertTrue("no second plunge while standing in it",
                player.getStats().getBodyTemperature() > afterPlunge - 0.5f);
    }

    @Test
    public void wadingPlainWaterLeavesYouWet() {
        Maze maze = strata(Stratum.MAZE);
        maze.getLiquidManager().setLiquidAt(5, 5, LiquidType.WATER);
        turns(maze, 1);
        assertEquals(Exposure.Wetness.WET, player.getStats().getWetnessLevel());
    }

    @Test
    public void aFieldFireBurnsAboutAHundredTurnsThenGoesOut() {
        Maze maze = strata(Stratum.MAZE);
        FieldFire fire = new FieldFire(3, 3);
        maze.addFieldFire(fire);
        maze.addLight(new LightSource(fire.lightId(), 3.5f, 3.5f, com.badlogic.gdx.graphics.Color.ORANGE, 4f, 1f,
                LightSource.FlickerProfile.CAMPFIRE_FLICKER));
        for (int i = 0; i < FieldFire.BURN_TURNS - 1; i++) turnManager.tickFieldFires(maze, eventManager);
        assertEquals(1, maze.getFieldFires().size());
        assertNotNull(maze.fieldFireWarming(5, 5));
        assertNull("out of reach at three tiles", maze.fieldFireWarming(6, 6));
        turnManager.tickFieldFires(maze, eventManager);
        assertTrue(maze.getFieldFires().isEmpty());
        for (LightSource l : maze.getLights()) assertNotEquals(fire.lightId(), l.getId());
    }

    @Test
    public void monstersThatSeeTheFireComeToLook() {
        Maze maze = strata(Stratum.MAZE);
        FieldFire fire = new FieldFire(3, 3);
        Monster near = new Monster(Monster.MonsterType.GHOUL, 10, 5, 8.5f, 3.5f);
        near.setState(Monster.MonsterState.IDLE);
        Monster far = new Monster(Monster.MonsterType.GHOUL, 10, 5, 14.5f, 14.5f);
        far.setState(Monster.MonsterState.IDLE);
        maze.addMonster(near);
        maze.addMonster(far);
        assertEquals(1, TurnManager.drawMonstersToFire(maze, fire));
        assertEquals(Monster.MonsterState.HUNTING, near.getState());
        assertEquals(new GridPoint2(3, 3), near.getLastKnownTargetPos());
        assertEquals(Monster.MonsterState.IDLE, far.getState());
    }

    @Test
    public void freezingStopsNaturalRegen() {
        // No world and on the surface: Exposure leaves the body temperature where it is set.
        Maze surface = new Maze(1, new int[16][16]);
        player.getStats().setCurrentHP(player.getStats().getMaxHP() - 5);
        player.getStats().setBodyTemperature(33f);
        int before = player.getCurrentHP();
        turns(surface, 100);
        assertEquals(before, player.getCurrentHP());

        player.getStats().setBodyTemperature(Exposure.NORMAL);
        turns(surface, 100);
        assertTrue(player.getCurrentHP() > before);
    }
}
