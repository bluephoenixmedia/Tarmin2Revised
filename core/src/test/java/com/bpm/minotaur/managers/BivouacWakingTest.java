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
        combat.freeRounds(Arrays.asList(a, b), 1, 0);
        assertEquals(Arrays.asList(a, b), struck);
    }

    /** Living War A64, through the real blow: the Shield stops the first 20, and only in the free rounds. */
    @Test
    public void aShieldTakesTheFirstBlowsOfTheFreeRoundsAndThenIsGone() {
        List<Integer> felt = new ArrayList<>();
        Player sleeper = new Player(5f, 5f) {
            @Override
            public int takeDamage(int amount, com.bpm.minotaur.gamedata.DamageType type) {
                felt.add(amount);
                return amount;
            }
        };
        CombatManager real = new CombatManager(sleeper, maze, null, new AnimationManager() {}, new GameEventManager(),
                new HeadlessSoundManager(), null, null, null, null, null);
        Monster striker = ghoul(6f);
        striker.setDamageDice("7d1");
        real.freeRounds(Arrays.asList(striker), 60, com.bpm.minotaur.gamedata.shelter.Ward.SHIELD_ABSORBS);
        assertTrue("it hit often enough to see: " + felt, felt.size() >= 2);
        assertEquals("7 + 7 + 6 stopped, and 1 through", Integer.valueOf(1), felt.get(0));
        assertEquals(Integer.valueOf(7), felt.get(1));
        felt.clear();
        real.monsterMeleeStrike(striker);
        real.freeRounds(Arrays.asList(striker), 60, 0);
        assertTrue(felt.size() >= 2);
        assertEquals("no Shield, no help", Integer.valueOf(7), felt.get(0));
    }

    /** Living War A64: an Alarm hears them at six tiles, and never has them adjoining. */
    @Test
    public void whatAnAlarmHearsIsSixTilesOffAndNeverBeside() {
        java.util.Random rng = new java.util.Random(5);
        for (int i = 0; i < 50; i++) {
            GridPoint2 tile = SpawnManager.tileWithin(maze, 8, 8, com.bpm.minotaur.gamedata.shelter.Ward.ALARM_TILES, rng);
            assertEquals("six steps", 6, Math.abs(tile.x - 8) + Math.abs(tile.y - 8));
        }
        Maze tiny = new Maze(2, new int[3][3]);
        GridPoint2 cramped = SpawnManager.tileWithin(tiny, 0, 0, com.bpm.minotaur.gamedata.shelter.Ward.ALARM_TILES, rng);
        assertEquals("as far as the ground goes", 4, cramped.x + cramped.y);
        assertNull("nothing but what adjoins", SpawnManager.tileWithin(new Maze(2, new int[1][2]), 0, 0, 6, rng));
    }

    /** A wall between two tiles is no way through: nothing is heard coming from behind one. */
    @Test
    public void whatAnAlarmHearsHasAWayToTheSleeper() {
        int eastWall = com.bpm.minotaur.gamedata.Direction.EAST.getWallMask();
        int westWall = com.bpm.minotaur.gamedata.Direction.WEST.getWallMask();
        int[][] walls = new int[16][16];
        for (int y = 0; y < 16; y++) {
            walls[y][8] = eastWall;
            walls[y][9] = westWall;
        }
        Maze divided = new Maze(2, walls);
        java.util.Random rng = new java.util.Random(6);
        for (int i = 0; i < 50; i++) {
            GridPoint2 tile = SpawnManager.tileWithin(divided, 8, 8, com.bpm.minotaur.gamedata.shelter.Ward.ALARM_TILES, rng);
            assertTrue("this side of the wall: " + tile, tile.x <= 8);
        }
    }

    @Test
    public void aFireDrawnAmbushStrikesTwice() {
        Monster a = ghoul(6f), b = ghoul(4f);
        combat.freeRounds(Arrays.asList(a, b), 2, 0);
        assertEquals(Arrays.asList(a, b, a, b), struck);
    }

    @Test
    public void theDeadDoNotStrikeNorIsADeadSleeperStruck() {
        Monster dead = ghoul(6f);
        dead.setCurrentHP(0);
        Monster live = ghoul(4f);
        combat.freeRounds(Arrays.asList(dead, live), 1, 0);
        assertEquals(Arrays.asList(live), struck);
        struck.clear();
        player.getStats().setCurrentHP(0);
        combat.freeRounds(Arrays.asList(live), 2, 0);
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
