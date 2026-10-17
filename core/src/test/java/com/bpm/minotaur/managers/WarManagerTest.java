package com.bpm.minotaur.managers;

import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.history.DoctrineCatalog;
import com.bpm.minotaur.gamedata.history.DoctrineCatalogTest;
import com.bpm.minotaur.gamedata.history.EventType;
import com.bpm.minotaur.gamedata.history.HistoryEvent;
import com.bpm.minotaur.gamedata.history.War;
import com.bpm.minotaur.gamedata.history.war.Front;
import com.bpm.minotaur.gamedata.history.war.SeatMap;
import com.bpm.minotaur.gamedata.monster.Monster;
import com.bpm.minotaur.generation.ShelterRoads;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Random;

import static org.junit.Assert.*;

/** Plan T2.3: the horns, and the choice to stay or go. */
public class WarManagerTest {

    private static DoctrineCatalog catalog;
    private HistoryManager history;
    private War war;
    private WarManager wars;
    private WarManager.Ground ground;

    private final BattleDirector.Recruiter recruiter = (type, x, y) -> {
        Monster m = new Monster(Monster.MonsterType.valueOf(type), 20, 12);
        m.getPosition().set(x, y);
        return m;
    };
    private final BattleDirector.Spoils spoils = (maze, near, loser, lordFell) -> { };

    @BeforeClass
    public static void load() throws IOException {
        catalog = DoctrineCatalogTest.loadCatalog();
    }

    @Before
    public void atWar() {
        for (long seed = 50; war == null; seed++) {
            history = HistoryManager.create(seed, catalog);
            if (!history.world().activeWars().isEmpty()) war = history.world().activeWars().get(0);
        }
        wars = new WarManager(catalog);
        ground = new WarManager.Ground();
        ground.chunk = new GridPoint2(5, 5);
        ground.level = 1;
        ground.front = new Front(war.id, war.attackerId, war.defenderId, ground.chunk);
        ground.seats = SeatMap.of(history.world(), new ShelterRoads(1L, new GridPoint2(0, 50)), new GridPoint2(0, 50), 1L);
        ground.maze = new Maze(1, new int[36][36]);
        ground.playerTile = new GridPoint2(18, 18);
    }

    private WarManager.Turn turn() {
        history.tickWarClock();
        return wars.onTurn(history, ground, recruiter, spoils);
    }

    @Test
    public void aFrontReachingThePlayerSoundsTheHorns() {
        WarManager.Turn t = turn();
        assertNotNull(wars.active());
        assertTrue(t.messages.get(0).startsWith("War-horns"));
        assertTrue("and the horns are heard (plan T2.9)", t.cues.contains(WarManager.Cue.HORNS));
    }

    @Test
    public void leavingBeforeTheLinesCloseMeansNoBattle() {
        turn();
        for (int i = 0; i < 5; i++) turn();
        ground.chunk = new GridPoint2(6, 5);
        ground.front = null;
        WarManager.Turn t = turn();
        assertNull(wars.active());
        assertTrue(t.messages.get(0).contains("slip away"));
        assertTrue(ground.maze.getMonsters().isEmpty());

        ground.chunk = new GridPoint2(5, 5);
        ground.front = new Front(war.id, war.attackerId, war.defenderId, ground.chunk);
        turn();
        assertNull("the same ground stays quiet for a while", wars.active());
    }

    @Test
    public void goingBelowDeclinesItToo() {
        turn();
        ground.level = 2;
        turn();
        assertNull(wars.active());
    }

    @Test
    public void noBattleOnSanctuaryGround() {
        ground.sanctuary = true;
        turn();
        assertNull(wars.active());
    }

    @Test
    public void stayingFightsItAndTheOutcomeEntersTheHistory() {
        turn();
        Random rng = new Random(5);
        int before = history.world().events().size();
        java.util.List<WarManager.Cue> heard = new ArrayList<>();
        for (int i = 0; i < 400 && wars.active() != null; i++) {
            heard.addAll(turn().cues);
            for (Monster m : new ArrayList<>(ground.maze.getMonsters().values())) {
                if (m.isWarBand() && rng.nextInt(10) == 0) {
                    m.setCurrentHP(0);
                    ground.maze.removeMonster(m);
                }
            }
        }
        assertNull("the battle is over", wars.active());
        boolean recorded = false;
        for (HistoryEvent e : history.world().events().subList(before, history.world().events().size())) {
            if (e.type == EventType.BATTLE && e.detail == 1) recorded = true;
        }
        assertTrue(recorded);
        assertEquals("the drums once as the lines close, and the rout once (plan T2.9)",
                java.util.Arrays.asList(WarManager.Cue.JOINED, WarManager.Cue.ROUT), heard);
    }
}
