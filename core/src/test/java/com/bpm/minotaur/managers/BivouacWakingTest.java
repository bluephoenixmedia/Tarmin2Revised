package com.bpm.minotaur.managers;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.Scenery;
import com.bpm.minotaur.gamedata.history.war.EncounterLedger;
import com.bpm.minotaur.gamedata.monster.Monster;
import com.bpm.minotaur.gamedata.player.Player;
import com.bpm.minotaur.headless.HeadlessSoundManager;
import com.bpm.minotaur.rendering.AnimationManager;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

/** Living War A60, A63: waking under attack, and what is left of the tent. */
public class BivouacWakingTest {

    private Player player;
    private Maze maze;
    private final List<Monster> struck = new ArrayList<>();
    private CombatManager combat;

    @Before
    public void setUp() {
        if (Gdx.app == null) {
            Gdx.app = (Application) Proxy.newProxyInstance(Application.class.getClassLoader(),
                    new Class<?>[]{Application.class}, (proxy, method, args) -> null);
        }
        player = new Player(5f, 5f);
        maze = new Maze(2, new int[16][16]);
        combat = new CombatManager(player, maze, null, new AnimationManager() {}, new GameEventManager(),
                new HeadlessSoundManager(), null, null, null, null, null) {
            @Override
            public void monsterMeleeStrike(Monster attacker) {
                struck.add(attacker);
            }
        };
    }

    private static Monster ghoul(float x) {
        return new Monster(Monster.MonsterType.GHOUL, 100, 30, x, 5f);
    }

    @Test
    public void eachAmbusherStrikesOnceARound() {
        Monster a = ghoul(6f), b = ghoul(4f);
        combat.freeRounds(Arrays.asList(a, b), 1);
        assertEquals(Arrays.asList(a, b), struck);
    }

    @Test
    public void aFireDrawnAmbushStrikesTwice() {
        Monster a = ghoul(6f), b = ghoul(4f);
        combat.freeRounds(Arrays.asList(a, b), 2);
        assertEquals(Arrays.asList(a, b, a, b), struck);
    }

    @Test
    public void theDeadDoNotStrikeNorIsADeadSleeperStruck() {
        Monster dead = ghoul(6f);
        dead.setCurrentHP(0);
        Monster live = ghoul(4f);
        combat.freeRounds(Arrays.asList(dead, live), 1);
        assertEquals(Arrays.asList(live), struck);
        struck.clear();
        player.getStats().setCurrentHP(0);
        combat.freeRounds(Arrays.asList(live), 2);
        assertTrue(struck.isEmpty());
    }

    private static Scenery prop(String id, int x, int y) {
        Scenery s = new Scenery(Scenery.SceneryType.PROP, x, y);
        s.setPropId(id);
        return s;
    }

    private void pitch(EncounterLedger ledger, int clearAt) {
        EncounterDirector.pitchBivouac(ledger, maze, new GridPoint2(1, 1), 2, new GridPoint2(8, 8), BivouacWakingTest::prop, clearAt);
    }

    @Test
    public void aTentStandsClearOfTheSleeperAndIsOfOneLevel() {
        EncounterLedger ledger = new EncounterLedger();
        pitch(ledger, 2);
        assertEquals(1, ledger.dressings.size());
        assertEquals(2, ledger.dressings.get(0).level);
        assertEquals(EncounterDirector.BIVOUAC, ledger.dressings.get(0).kind);
        assertEquals(2, maze.getScenery().size());
        assertFalse(maze.getScenery().containsKey(new GridPoint2(8, 8)));
    }

    @Test
    public void pitchingAgainStrikesTheOldTent() {
        EncounterLedger ledger = new EncounterLedger();
        pitch(ledger, 2);
        pitch(ledger, 3);
        assertEquals(1, ledger.dressings.size());
        assertEquals(2, maze.getScenery().size());
        assertEquals(3, ledger.dressings.get(0).clearAtSleep);
    }

    @Test
    public void noTentIsPitchedInACorridor() {
        // Every tile walled north and south: nothing here is open ground.
        int walled = com.bpm.minotaur.gamedata.Direction.NORTH.getWallMask() | com.bpm.minotaur.gamedata.Direction.SOUTH.getWallMask();
        int[][] walls = new int[16][16];
        for (int[] row : walls) java.util.Arrays.fill(row, walled);
        Maze narrow = new Maze(2, walls);
        EncounterLedger ledger = new EncounterLedger();
        EncounterDirector.pitchBivouac(ledger, narrow, new GridPoint2(1, 1), 2, new GridPoint2(8, 8), BivouacWakingTest::prop, 2);
        assertTrue(ledger.dressings.isEmpty());
        assertTrue(narrow.getScenery().isEmpty());
    }

    @Test
    public void aTentBelowIsStruckBelowAndNotByTheSurfaceAboveIt() throws java.io.IOException {
        com.bpm.minotaur.gamedata.history.DoctrineCatalog catalog = com.bpm.minotaur.gamedata.history.DoctrineCatalogTest.loadCatalog();
        HistoryManager history = HistoryManager.create(9L, catalog);
        EncounterLedger ledger = history.encounterLedger();
        GridPoint2 chunk = new GridPoint2(1, 1);
        pitch(ledger, ledger.sleeps + 1);
        ledger.newExpedition(true);
        EncounterDirector stages = new EncounterDirector(catalog);
        stages.onTurn(history, chunk, 1, false, new Maze(1, new int[16][16]), new GridPoint2(8, 8), new ArrayList<>(), new ArrayList<>(),
                null, BivouacWakingTest::prop, null);
        assertEquals("the surface above is another place", 1, ledger.dressings.size());
        stages.onTurn(history, chunk, 2, false, maze, new GridPoint2(8, 8), new ArrayList<>(), new ArrayList<>(),
                null, BivouacWakingTest::prop, null);
        assertTrue(ledger.dressings.isEmpty());
        assertTrue(maze.getScenery().isEmpty());
    }
}
