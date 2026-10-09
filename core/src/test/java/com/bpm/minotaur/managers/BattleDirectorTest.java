package com.bpm.minotaur.managers;

import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.gamedata.Gate;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.history.DoctrineCatalog;
import com.bpm.minotaur.gamedata.history.DoctrineCatalogTest;
import com.bpm.minotaur.gamedata.history.HistorySimulator;
import com.bpm.minotaur.gamedata.history.HistoryWorld;
import com.bpm.minotaur.gamedata.history.War;
import com.bpm.minotaur.gamedata.history.war.Front;
import com.bpm.minotaur.gamedata.monster.Faction;
import com.bpm.minotaur.gamedata.monster.Monster;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Random;

import static org.junit.Assert.*;

/** Plan T2.3-T2.7: a battle played out on a real maze, headless. */
public class BattleDirectorTest {

    private static DoctrineCatalog catalog;
    private static HistoryWorld world;
    private static War war;

    private Maze maze;
    private int spoilsDropped;
    private int spoilsLoser;

    @BeforeClass
    public static void load() throws IOException {
        catalog = DoctrineCatalogTest.loadCatalog();
        for (long seed = 0; war == null; seed++) {
            HistoryWorld w = HistorySimulator.prehistory(seed, catalog);
            if (!w.activeWars().isEmpty()) {
                world = w;
                war = w.activeWars().get(0);
            }
        }
    }

    @Before
    public void freshField() {
        maze = new Maze(1, new int[36][36]);
        maze.addGate(new Gate(18, 0));
        maze.addGate(new Gate(35, 18));
        spoilsDropped = 0;
        spoilsLoser = -1;
    }

    private final BattleDirector.Recruiter recruiter = (type, x, y) -> {
        Monster m = new Monster(Monster.MonsterType.valueOf(type), 20, 12);
        m.getPosition().set(x, y);
        return m;
    };

    private final BattleDirector.Spoils spoils = (maze, near, loser, lordFell) -> {
        spoilsDropped++;
        spoilsLoser = loser;
    };

    private BattleDirector director(long seed) {
        Front f = new Front(war.id, war.attackerId, war.defenderId, new GridPoint2(4, 4));
        return new BattleDirector(f, new GridPoint2(4, 4), 0, world, catalog, seed);
    }

    private void closeLines(BattleDirector d, Maze maze) {
        for (int i = 0; i < BattleDirector.WARNING_TURNS; i++) d.tick(maze, new GridPoint2(18, 18), recruiter, spoils);
    }

    private int warBand() {
        int n = 0;
        for (Monster m : maze.getMonsters().values()) if (m.isWarBand()) n++;
        return n;
    }

    @Test
    public void theHornsGiveTwelveTurnsBeforeTheLinesClose() {
        BattleDirector d = director(1);
        assertTrue(d.warning().contains(String.valueOf(BattleDirector.WARNING_TURNS)));
        for (int i = 0; i < BattleDirector.WARNING_TURNS - 1; i++) {
            d.tick(maze, new GridPoint2(18, 18), recruiter, spoils);
            assertEquals(BattleDirector.Phase.WARNING, d.phase());
            assertEquals(0, warBand());
        }
        d.tick(maze, new GridPoint2(18, 18), recruiter, spoils);
        assertEquals(BattleDirector.Phase.BATTLE, d.phase());
    }

    @Test
    public void theFieldHoldsThirtyToFortyAndEveryGateIsHeld() {
        BattleDirector d = director(2);
        for (int i = 0; i < BattleDirector.WARNING_TURNS; i++) d.tick(maze, new GridPoint2(18, 18), recruiter, spoils);
        int n = warBand();
        assertTrue("field " + n, n >= 30 && n <= 40);
        for (GridPoint2 gate : maze.getGates().keySet()) {
            int guards = 0;
            for (Monster m : maze.getMonsters().values()) {
                if (Math.abs(m.getPosition().x - gate.x) + Math.abs(m.getPosition().y - gate.y) <= 3) guards++;
                assertFalse("no soldier stands in a gate", gate.equals(new GridPoint2((int) m.getPosition().x, (int) m.getPosition().y)));
            }
            assertTrue("gate " + gate + " guards " + guards, guards >= 1);
        }
        for (Monster m : maze.getMonsters().values()) {
            assertEquals(Faction.MAZE_HOUSE, m.getFaction());
            assertTrue(m.getHouseId() == war.attackerId || m.getHouseId() == war.defenderId);
        }
        assertNotNull(d.captainOf(true));
        assertNotNull(d.captainOf(false).getDisplayName());
    }

    /** Kills about one soldier in twelve each turn, on both sides. */
    private void attrition(Random rng) {
        for (Monster m : new ArrayList<>(maze.getMonsters().values())) {
            if (m.isWarBand() && rng.nextInt(12) == 0) {
                m.setCurrentHP(0);
                maze.removeMonster(m);
            }
        }
    }

    @Test
    public void aBattleRoutsLeavesSpoilsAndTheVictorsMarchOn() {
        BattleDirector d = director(3);
        Random rng = new Random(3);
        BattleDirector.Report result = null;
        for (int turn = 0; turn < 400 && d.phase() != BattleDirector.Phase.DONE; turn++) {
            BattleDirector.Report r = d.tick(maze, new GridPoint2(18, 18), recruiter, spoils);
            if (r.winner >= 0) result = r;
            if (d.phase() == BattleDirector.Phase.BATTLE) attrition(rng);
        }
        assertNotNull("the battle broke", result);
        assertEquals(BattleDirector.Phase.DONE, d.phase());
        assertEquals(1, spoilsDropped);
        assertEquals(result.loser, spoilsLoser);
        for (Monster m : maze.getMonsters().values()) {
            assertNotEquals("the broken side fled", result.loser, m.getHouseId());
        }
        assertTrue("the victors marched on", warBand() <= 4);
    }

    @Test
    public void cuttingDownACaptainBreaksThatSide() {
        int brokeA = 0;
        for (long seed = 10; seed < 20; seed++) {
            freshField();
            BattleDirector d = director(seed);
            closeLines(d, maze);
            Monster captain = d.captainOf(true);
            captain.setCurrentHP(0);
            maze.removeMonster(captain);
            BattleDirector.Report broke = null;
            for (int t = 0; t < 20 && broke == null; t++) {
                BattleDirector.Report r = d.tick(maze, new GridPoint2(18, 18), recruiter, spoils);
                if (r.winner >= 0) broke = r;
                attrition(new Random(seed * 31 + t));
            }
            if (broke != null && broke.loser == war.attackerId) brokeA++;
        }
        assertTrue("the leaderless side broke " + brokeA + "/10", brokeA >= 8);
    }

    @Test
    public void aVolleyIsMarkedOneTurnAndLandsTheNext() {
        BattleDirector d = director(4);
        closeLines(d, maze);
        GridPoint2 standing = new GridPoint2(18, 18);
        boolean marked = false, landed = false;
        for (int t = 0; t < 30 && !landed; t++) {
            BattleDirector.Report r = d.tick(maze, standing, recruiter, spoils);
            if (marked && r.volleyDamage > 0) landed = true;
            for (String msg : r.messages) if (msg.contains("sky darkens")) marked = true;
        }
        assertTrue("marked", marked);
        assertTrue("and it hit a player who stood still", landed);
    }

    @Test
    public void attackersComeFromTheEdgeFacingTheirSeat() {
        assertEquals(0, BattleDirector.edgeToward(new GridPoint2(0, 0), new GridPoint2(-20, 3)));
        assertEquals(1, BattleDirector.edgeToward(new GridPoint2(0, 0), new GridPoint2(20, -3)));
        assertEquals(2, BattleDirector.edgeToward(new GridPoint2(0, 0), new GridPoint2(2, -30)));
        assertEquals(3, BattleDirector.edgeToward(new GridPoint2(0, 0), new GridPoint2(2, 30)));
    }

    @Test
    public void soldiersPreferRivalsToThePlayer() {
        assertFalse(MonsterAiManager.warBandTurnsOnPlayer(3, false, 0.5f));
        assertTrue("adjacent", MonsterAiManager.warBandTurnsOnPlayer(1, false, 0.5f));
        assertTrue("struck by the player", MonsterAiManager.warBandTurnsOnPlayer(5, true, 0.5f));
        assertTrue("a melee spill", MonsterAiManager.warBandTurnsOnPlayer(4, false, 0.01f));
    }

    @Test
    public void aBattleThePlayerWalksAwayFromIsNotSavedWithTheChunk() {
        BattleDirector d = director(5);
        closeLines(d, maze);
        assertTrue(warBand() >= 30);
        assertTrue(new com.bpm.minotaur.gamedata.ChunkData(maze).monsters.isEmpty());
    }

    @Test
    public void aChargeIsMarkedAcrossTheFieldAndLandsTheNextTurn() {
        BattleDirector d = director(6);
        closeLines(d, maze);
        GridPoint2 standing = new GridPoint2(18, 18);
        boolean marked = false, landed = false;
        for (int t = 0; t < 40 && !landed; t++) {
            BattleDirector.Report r = d.tick(maze, standing, recruiter, spoils);
            if (marked && r.volleyDamage > 0) landed = true;
            for (String msg : r.messages) if (msg.contains("charge is coming")) marked = true;
        }
        assertTrue("marked", marked);
        assertTrue("and it rode down a player who stood still", landed);
    }
}
