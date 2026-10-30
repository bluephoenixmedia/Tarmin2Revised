package com.bpm.minotaur.managers;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.GridPoint2;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.history.DoctrineCatalog;
import com.bpm.minotaur.gamedata.history.DoctrineCatalogTest;
import com.bpm.minotaur.gamedata.monster.Monster;
import com.bpm.minotaur.gamedata.player.Player;
import com.bpm.minotaur.headless.HeadlessSoundManager;
import com.bpm.minotaur.rendering.AnimationManager;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.io.FileReader;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Living War A22's acceptance test: a battle kills at about the same rate whether anyone is
 * watching. The same battles are run in numbers, and on a real field through the real monster AI
 * and combat, with real soldiers' stats, the player off in a corner taking no part.
 */
public class BattleEquivalenceTest {

    private static final int FIGHTS = 24;
    private static final int TURNS = 120;
    private static final int SOLDIERS = 60;
    private static final float WITHIN = 0.25f;

    private static DoctrineCatalog catalog;
    private static JsonValue monsters;

    @BeforeClass
    public static void load() throws Exception {
        catalog = DoctrineCatalogTest.loadCatalog();
        File f = new File("../assets/data/monsters.json");
        monsters = new JsonReader().parse(new FileReader(f.exists() ? f : new File("assets/data/monsters.json")));
        if (Gdx.app == null) {
            Gdx.app = (Application) Proxy.newProxyInstance(Application.class.getClassLoader(),
                    new Class<?>[]{Application.class}, (proxy, method, args) -> null);
        }
    }

    private static Monster real(String type, int x, int y) {
        JsonValue t = monsters.get(type);
        Monster m = new Monster(Monster.MonsterType.valueOf(type), t.getInt("maxHP", 20), t.getInt("armorClass", 10));
        m.getPosition().set(x + 0.5f, y + 0.5f);
        m.setDamageDice(t.getString("damageDice", "1d4"));
        m.setLevel(t.getInt("baseLevel", 1));
        return m;
    }

    private static int dead(BattleFixture f) {
        return 2 * SOLDIERS - f.layer.aliveOn(f.battle, true) - f.layer.aliveOn(f.battle, false);
    }

    private static boolean under(BattleFixture f) {
        return f.history.roster().battles.contains(f.battle);
    }

    /** Kills and turns, with no one watching. */
    private static int[] offScreen(long seed) {
        BattleFixture f = new BattleFixture(seed, catalog, SOLDIERS);
        int turns = 0;
        int killed = 0;
        for (int i = 0; i < TURNS && under(f); i++) {
            f.history.tickWarClock();
            f.layer.tick(f.history.warClock());
            turns++;
            if (under(f)) killed = dead(f);
        }
        return new int[] {killed, turns};
    }

    /** Kills and turns on the field, each soldier taking its turns as the TurnManager gives them. */
    private static int[] onTheMap(long seed) {
        BattleFixture f = new BattleFixture(seed, catalog, SOLDIERS);
        Maze maze = new Maze(1, new int[36][36]);
        Player player = new Player(0.5f, 0.5f);
        MonsterAiManager ai = new MonsterAiManager();
        ai.getFactionMatrix().setHouseRelations(f.history.world().houseRelations());
        CombatManager combat = new CombatManager(player, maze, null, new AnimationManager() {}, new GameEventManager(),
                new HeadlessSoundManager(), null, null, null, null, null);
        BattleDirector d = new BattleDirector(f.battle, f.layer, new GridPoint2(BattleFixture.X, BattleFixture.Y), 0,
                f.history.world(), catalog, seed, true);
        BattleDirector.Spoils none = (m, near, loser, lordFell) -> { };
        GridPoint2 corner = new GridPoint2(0, 0);
        long clock = 1;
        d.tick(maze, null, BattleEquivalenceTest::real, none, clock++);
        int turns = 0;
        int killed = 0;
        for (int i = 0; i < TURNS && d.phase() == BattleDirector.Phase.BATTLE; i++) {
            List<Monster> all = new ArrayList<>(maze.getMonsters().values());
            for (Monster m : all) {
                if (!m.isAlive() || m.getCurrentHP() <= 0) continue;
                m.addEnergy(TurnManager.BASE_TURN_COST / 12f * m.getEffectiveSpeed());
                int guard = 0;
                while (m.getEnergy() >= TurnManager.BASE_TURN_COST && guard++ < 10 && m.getCurrentHP() > 0) {
                    m.setEnergy(m.getEnergy() - TurnManager.BASE_TURN_COST);
                    ai.updateMonster(m, maze, player, true, combat);
                }
            }
            player.setPosition(corner.x + 0.5f, corner.y + 0.5f);
            d.tick(maze, null, BattleEquivalenceTest::real, none, clock++);
            turns++;
            if (under(f)) killed = dead(f);
        }
        return new int[] {killed, turns};
    }

    @Test
    public void aBattleKillsAtTheSameRateWatchedOrNot() {
        int[] onMap = new int[2], unseen = new int[2];
        for (int i = 0; i < FIGHTS; i++) {
            int[] n = offScreen(300L + i), m = onTheMap(300L + i);
            for (int k = 0; k < 2; k++) {
                unseen[k] += n[k];
                onMap[k] += m[k];
            }
        }
        float map = onMap[0] / (float) onMap[1], numbers = unseen[0] / (float) unseen[1];
        System.out.printf("kills a turn: on the map %.3f, in the numbers %.3f%n", map, numbers);
        float off = Math.abs(map - numbers) / numbers;
        assertTrue("on the map " + map + ", in the numbers " + numbers + ": off by " + off, off <= WITHIN);
    }
}
