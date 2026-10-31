package com.bpm.minotaur.managers;

import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.gamedata.Gate;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.history.DoctrineCatalog;
import com.bpm.minotaur.gamedata.history.DoctrineCatalogTest;
import com.bpm.minotaur.gamedata.monster.Faction;
import com.bpm.minotaur.gamedata.monster.Monster;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Random;

import static org.junit.Assert.*;

/** Plan T2.3-T2.7, Living War A21: a battle of real armies played out on a real maze, headless. */
public class BattleDirectorTest {

    private static DoctrineCatalog catalog;

    private BattleFixture fixture;
    private Maze maze;
    private int spoilsDropped;
    private int spoilsLoser;
    private long clock;

    @BeforeClass
    public static void load() throws IOException {
        catalog = DoctrineCatalogTest.loadCatalog();
    }

    @Before
    public void freshField() {
        maze = new Maze(1, new int[36][36]);
        maze.addGate(new Gate(18, 0));
        maze.addGate(new Gate(35, 18));
        spoilsDropped = 0;
        spoilsLoser = -1;
    }

    private final BattleDirector.Recruiter recruiter = BattleFixture::soldier;

    private final BattleDirector.Spoils spoils = (maze, near, loser, lordFell) -> {
        spoilsDropped++;
        spoilsLoser = loser;
    };

    private BattleDirector director(long seed) {
        fixture = new BattleFixture(1000 + seed, catalog, 40);
        return new BattleDirector(fixture.battle, fixture.layer, new GridPoint2(BattleFixture.X, BattleFixture.Y), 0,
                fixture.history.world(), catalog, seed, false);
    }

    private BattleDirector.Report tick(BattleDirector d, GridPoint2 at) {
        return d.tick(maze, at, recruiter, spoils, ++clock);
    }

    private void closeLines(BattleDirector d) {
        for (int i = 0; i < BattleDirector.WARNING_TURNS; i++) tick(d, new GridPoint2(18, 18));
    }

    private int warBand() {
        int n = 0;
        for (Monster m : maze.getMonsters().values()) if (m.isWarBand()) n++;
        return n;
    }

    /** Turns a fleeing player may take to cut through a line and out by a gate. */
    static final int FLEE_TURN_CAP = 120;

    /**
     * Plan T2.5: once the battle is joined the edges are lines of soldiers, but the player is never
     * soft-locked. A scripted flee from the centre to each edge's gate, one tile a turn, cuts down
     * whoever stands in the way while the battle goes on, and must get out.
     */
    @Test
    public void aScriptedFleeCutsThroughTheLineToEveryEdge() {
        GridPoint2[] exits = {new GridPoint2(18, 0), new GridPoint2(35, 18), new GridPoint2(18, 35), new GridPoint2(0, 18)};
        for (int e = 0; e < exits.length; e++) {
            maze = new Maze(1, new int[36][36]);
            for (GridPoint2 g : exits) maze.addGate(new Gate(g.x, g.y));
            BattleDirector d = director(40 + e);
            GridPoint2 at = new GridPoint2(18, 18);
            for (int i = 0; i < BattleDirector.WARNING_TURNS; i++) tick(d, at);
            assertEquals(BattleDirector.Phase.BATTLE, d.phase());

            GridPoint2 exit = exits[e];
            int turns = 0;
            int cut = 0;
            while (!at.equals(exit) && turns < FLEE_TURN_CAP) {
                GridPoint2 next = at.x != exit.x
                        ? new GridPoint2(at.x + Integer.signum(exit.x - at.x), at.y)
                        : new GridPoint2(at.x, at.y + Integer.signum(exit.y - at.y));
                Monster blocking = null;
                for (Monster m : maze.getMonsters().values()) {
                    if ((int) m.getPosition().x == next.x && (int) m.getPosition().y == next.y) blocking = m;
                }
                if (blocking != null) {
                    blocking.setCurrentHP(0); // bump-to-attack, a blow at a time in the game; one here
                    maze.removeMonster(blocking);
                    cut++;
                } else {
                    at = next;
                }
                tick(d, at);
                turns++;
            }
            assertEquals("fled by edge " + e + " in " + turns + " turns, cutting down " + cut, exit, at);
            assertTrue("the gate was held: the flight cut through the line (edge " + e + ")", cut > 0);
        }
    }

    @Test
    public void theHornsGiveTwelveTurnsBeforeTheLinesClose() {
        BattleDirector d = director(1);
        assertTrue(d.warning().contains(String.valueOf(BattleDirector.WARNING_TURNS)));
        for (int i = 0; i < BattleDirector.WARNING_TURNS - 1; i++) {
            tick(d, new GridPoint2(18, 18));
            assertEquals(BattleDirector.Phase.WARNING, d.phase());
            assertEquals(0, warBand());
        }
        tick(d, new GridPoint2(18, 18));
        assertEquals(BattleDirector.Phase.BATTLE, d.phase());
    }

    @Test
    public void theFieldHoldsThirtyToFortyAndEveryGateIsHeld() {
        BattleDirector d = director(2);
        closeLines(d);
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
            assertTrue(m.getHouseId() == fixture.battle.houseA || m.getHouseId() == fixture.battle.houseB);
        }
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
            BattleDirector.Report r = tick(d, new GridPoint2(18, 18));
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
    public void aVolleyIsMarkedOneTurnAndLandsTheNext() {
        BattleDirector d = director(4);
        closeLines(d);
        GridPoint2 standing = new GridPoint2(18, 18);
        boolean marked = false, landed = false;
        for (int t = 0; t < 30 && !landed; t++) {
            BattleDirector.Report r = tick(d, standing);
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
        assertFalse(MonsterAiManager.warBandTurnsOnPlayer(3, false));
        assertTrue("adjacent", MonsterAiManager.warBandTurnsOnPlayer(1, false));
        assertTrue("struck by the player", MonsterAiManager.warBandTurnsOnPlayer(5, true));
        // Living War W14: no melee spill -- a soldier at a distance never breaks off for the player.
        assertFalse("no spill", MonsterAiManager.warBandTurnsOnPlayer(4, false));
    }

    @Test
    public void aBattleThePlayerWalksAwayFromIsNotSavedWithTheChunk() {
        BattleDirector d = director(5);
        closeLines(d);
        assertTrue(warBand() >= 30);
        assertTrue(new com.bpm.minotaur.gamedata.ChunkData(maze).monsters.isEmpty());
    }

    @Test
    public void aChargeIsMarkedAcrossTheFieldAndLandsTheNextTurn() {
        BattleDirector d = director(6);
        closeLines(d);
        GridPoint2 standing = new GridPoint2(18, 18);
        boolean marked = false, landed = false;
        for (int t = 0; t < 40 && !landed; t++) {
            BattleDirector.Report r = tick(d, standing);
            if (marked && r.volleyDamage > 0) landed = true;
            for (String msg : r.messages) if (msg.contains("charge is coming")) marked = true;
        }
        assertTrue("marked", marked);
        assertTrue("and it rode down a player who stood still", landed);
    }
}
