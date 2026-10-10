package com.bpm.minotaur.managers;

import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.gamedata.Gate;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.Scenery;
import com.bpm.minotaur.gamedata.history.DoctrineCatalog;
import com.bpm.minotaur.gamedata.history.DoctrineCatalogTest;
import com.bpm.minotaur.gamedata.history.EventType;
import com.bpm.minotaur.gamedata.history.War;
import com.bpm.minotaur.gamedata.history.war.Encounter;
import com.bpm.minotaur.gamedata.monster.Monster;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

/** Living War W2, W12: encounters put into a real maze, headless. */
public class EncounterDirectorTest {

    private static DoctrineCatalog catalog;
    private static final GridPoint2 HERE = new GridPoint2(4, 4);
    private static final GridPoint2 PLAYER = new GridPoint2(18, 18);

    private HistoryManager history;
    private War war;
    private Maze maze;
    private EncounterDirector director;

    private final BattleDirector.Recruiter recruiter = (type, x, y) -> {
        Monster m = new Monster(Monster.MonsterType.valueOf(type), 20, 12);
        m.getPosition().set(x, y);
        return m;
    };

    private final EncounterDirector.Props props = (id, x, y) -> {
        Scenery s = new Scenery(Scenery.SceneryType.PROP, x, y);
        s.setPropId(id);
        return s;
    };

    @BeforeClass
    public static void load() throws IOException {
        catalog = DoctrineCatalogTest.loadCatalog();
    }

    @Before
    public void fresh() {
        history = HistoryManager.create(41L, catalog);
        war = history.world().activeWars().get(0);
        maze = new Maze(1, new int[36][36]);
        maze.addGate(new Gate(18, 0));
        director = new EncounterDirector(catalog);
    }

    private Encounter skirmish(long end) {
        return new Encounter(Encounter.Kind.SKIRMISH, 3, war.id, war.attackerId, war.defenderId, 0, end, HERE, HERE);
    }

    private EncounterDirector.Turn turn(List<Encounter> encounters) {
        return director.onTurn(history, HERE, 1, false, maze, PLAYER, encounters, Collections.emptyList(),
                recruiter, props, null);
    }

    private List<Monster> band(int house) {
        List<Monster> out = new ArrayList<>();
        for (Monster m : maze.getMonsters().values()) if (m.isWarBand() && m.getHouseId() == house) out.add(m);
        return out;
    }

    private static void kill(Monster m) {
        m.setCurrentHP(0);
    }

    @Test
    public void aSkirmishPutsTwoWarBandsOnTheGround() {
        EncounterDirector.Turn t = turn(List.of(skirmish(1000)));
        int a = band(war.attackerId).size();
        int b = band(war.defenderId).size();
        assertTrue("6-10 a side: " + a + " and " + b, a >= 6 && a <= 10 && b >= 6 && b <= 10);
        assertTrue(t.cues.contains(EncounterDirector.Cue.CLASH));
        turn(List.of(skirmish(1000)));
        assertEquals("staged once", a + b, band(war.attackerId).size() + band(war.defenderId).size());
    }

    @Test
    public void aBandThatLosesMostOfItsMenRoutsAndLeavesABattlefield() {
        Encounter e = skirmish(1000);
        turn(List.of(e));
        List<Monster> losers = band(war.defenderId);
        for (int i = 0; i < losers.size() - 1; i++) kill(losers.get(i));
        EncounterDirector.Turn t = turn(List.of(e));
        assertTrue(t.cues.contains(EncounterDirector.Cue.ROUT));
        assertTrue(history.encounterLedger().isSpent(e));
        assertFalse("a battlefield is left", history.encounterLedger().dressings.isEmpty());
        assertFalse(maze.getScenery().isEmpty());
        for (int i = 0; i < EncounterDirector.LINGER + 1; i++) turn(List.of(e));
        assertTrue("the victors march on", band(war.attackerId).isEmpty());
    }

    @Test
    public void unwatchedItWritesNoHistory() {
        Encounter e = skirmish(1000);
        turn(List.of(e));
        float before = history.world().house(war.attackerId).strength;
        for (Monster m : band(war.defenderId)) kill(m);
        turn(List.of(e));
        assertEquals(before, history.world().house(war.attackerId).strength, 0.001f);
    }

    @Test
    public void foughtInItMovesTheHistoryAndAFallenCaptainIsChronicled() {
        Encounter e = skirmish(1000);
        turn(List.of(e));
        Monster captain = null;
        for (Monster m : maze.getMonsters().values()) if (m.isWarBand() && m.getFigureId() >= 0) captain = m;
        assertNotNull("a band has a named captain", captain);
        for (Monster m : band(captain.getHouseId())) {
            m.markSeekerDrewBlood();
            kill(m);
        }
        int events = history.world().events().size();
        turn(List.of(e));
        assertTrue(history.world().events().size() > events);
        assertEquals(EventType.BATTLE, history.world().events().get(history.world().events().size() - 1).type);
    }

    @Test
    public void leavingMidFightMeansItGoesOnWithoutYou() {
        Encounter e = skirmish(1000);
        turn(List.of(e));
        director.onTurn(history, new GridPoint2(5, 4), 1, false, maze, PLAYER, List.of(e), Collections.emptyList(),
                recruiter, props, null);
        assertTrue(history.encounterLedger().isSpent(e));
    }

    @Test
    public void aColumnMarchesAcrossAndLeaves() {
        Encounter col = new Encounter(Encounter.Kind.COLUMN, 4, war.id, war.attackerId, -1, -500, 500,
                new GridPoint2(2, 4), new GridPoint2(6, 4));
        EncounterDirector.Turn t = turn(List.of(col));
        assertTrue(t.cues.contains(EncounterDirector.Cue.DRUMS));
        List<Monster> marchers = band(war.attackerId);
        assertTrue(marchers.size() >= 8);
        for (Monster m : marchers) {
            assertNotNull(m.getMarchTarget());
            assertEquals("bound east", 34, m.getMarchTarget().x);
            assertTrue("entering from the west", m.getPosition().x < 12);
        }
        for (Monster m : marchers) {
            maze.removeMonster(m);
            m.getPosition().set(m.getMarchTarget().x, m.getMarchTarget().y);
            maze.addMonster(m);
        }
        turn(List.of(col));
        assertTrue("they leave at the far edge", band(war.attackerId).isEmpty());
        assertFalse("passing through is not destroying", history.encounterLedger().isSpent(col));
    }

    @Test
    public void raidersLightFiresAndMoveOff() {
        Encounter raid = new Encounter(Encounter.Kind.RAID, 5, war.id, war.attackerId, war.defenderId, 0, 10, HERE, HERE);
        history.tickWarClock();
        turn(List.of(raid));
        assertTrue(band(war.attackerId).size() >= 4);
        int fires = 0;
        for (Scenery s : maze.getScenery().values()) if ("campfire".equals(s.getPropId())) fires++;
        assertTrue(fires >= 1);
        for (int i = 0; i < 12; i++) history.tickWarClock();
        turn(List.of(raid));
        assertTrue(band(war.attackerId).isEmpty());
        assertTrue(history.encounterLedger().isSpent(raid));
    }

    @Test
    public void aBattlefieldIsClearedAfterThreeSleeps() {
        Encounter e = skirmish(1000);
        turn(List.of(e));
        for (Monster m : band(war.defenderId)) kill(m);
        turn(List.of(e));
        int scars = maze.getScenery().size();
        assertTrue(scars > 0);
        // Leave, sleep three times, come back.
        director.onTurn(history, new GridPoint2(9, 9), 1, false, maze, PLAYER, List.of(), Collections.emptyList(), recruiter, props, null);
        for (int i = 0; i < EncounterDirector.AFTERMATH_SLEEPS - 1; i++) history.onSleep();
        turn(List.of());
        assertEquals("still there after two sleeps", scars, maze.getScenery().size());
        director.onTurn(history, new GridPoint2(9, 9), 1, false, maze, PLAYER, List.of(), Collections.emptyList(), recruiter, props, null);
        history.onSleep();
        turn(List.of());
        assertTrue("cleared after three", maze.getScenery().isEmpty());
    }

    @Test
    public void aCampStandsWhileItsWarDoes() {
        Encounter camp = new Encounter(Encounter.Kind.CAMP, -2, war.id, war.attackerId, -1, 0, Long.MAX_VALUE, HERE, HERE);
        director.onTurn(history, HERE, 1, false, maze, PLAYER, List.of(), List.of(camp), recruiter, props, null);
        int tents = 0;
        for (Scenery s : maze.getScenery().values()) if ("camp_tent".equals(s.getPropId())) tents++;
        assertTrue(tents >= 1);
        assertTrue("sentries", band(war.attackerId).size() >= 3);
        // The war ends: next time the player comes, the camp has been struck.
        director.onTurn(history, new GridPoint2(9, 9), 1, false, maze, PLAYER, List.of(), List.of(), recruiter, props, null);
        director.onTurn(history, HERE, 1, false, maze, PLAYER, List.of(), List.of(), recruiter, props, null);
        for (Scenery s : maze.getScenery().values()) assertNotEquals("camp_tent", s.getPropId());
    }

    @Test
    public void aCampCutDownStaysUnguarded() {
        Encounter camp = new Encounter(Encounter.Kind.CAMP, -2, war.id, war.attackerId, -1, 0, Long.MAX_VALUE, HERE, HERE);
        director.onTurn(history, HERE, 1, false, maze, PLAYER, List.of(), List.of(camp), recruiter, props, null);
        for (Monster m : band(war.attackerId)) kill(m);
        director.onTurn(history, HERE, 1, false, maze, PLAYER, List.of(), List.of(camp), recruiter, props, null);
        assertTrue(history.encounterLedger().isSpent(camp));
        director.onTurn(history, new GridPoint2(9, 9), 1, false, maze, PLAYER, List.of(), List.of(camp), recruiter, props, null);
        director.onTurn(history, HERE, 1, false, maze, PLAYER, List.of(), List.of(camp), recruiter, props, null);
        int alive = 0;
        for (Monster m : band(war.attackerId)) if (m.isAlive()) alive++;
        assertEquals("no new sentries", 0, alive);
    }

    @Test
    public void raidersBurnWhatStands() {
        for (int i = 0; i < 4; i++) {
            Scenery tree = new Scenery(Scenery.SceneryType.PROP, 16 + i, 18);
            tree.setPropId("dead_tree");
            maze.addScenery(tree);
        }
        Encounter raid = new Encounter(Encounter.Kind.RAID, 6, war.id, war.attackerId, war.defenderId, 0, 100, HERE, HERE);
        turn(List.of(raid));
        int trees = 0, fires = 0;
        for (Scenery s : maze.getScenery().values()) {
            if ("dead_tree".equals(s.getPropId())) trees++;
            if ("campfire".equals(s.getPropId())) fires++;
        }
        assertEquals("three of the four trees put to the torch", 1, trees);
        assertEquals(EncounterDirector.RAID_BURNS, fires);
    }

    @Test
    public void unwatchedTwoBandsStillBreakInThirtyToSixtyTurns() {
        Encounter e = skirmish(1000);
        turn(List.of(e));
        int turns = 1;
        while (!history.encounterLedger().isSpent(e) && turns < 200) {
            turn(List.of(e));
            turns++;
        }
        assertTrue("broke after " + turns + " turns", turns >= 30 && turns <= 60);
    }

    @Test
    public void aBattlefieldIsKeptInTheChunkSave() {
        Encounter e = skirmish(1000);
        turn(List.of(e));
        for (Monster m : band(war.defenderId)) kill(m);
        turn(List.of(e));
        com.bpm.minotaur.gamedata.ChunkData saved = new com.bpm.minotaur.gamedata.ChunkData(maze);
        com.bpm.minotaur.gamedata.history.war.EncounterLedger.Dressing field = history.encounterLedger().dressings.get(0);
        for (int i = 0; i < field.props.size(); i++) {
            boolean kept = false;
            for (com.bpm.minotaur.gamedata.ChunkData.SceneryData d : saved.scenery) {
                if (d.x == field.tiles.get(2 * i) && d.y == field.tiles.get(2 * i + 1) && field.props.get(i).equals(d.propId)) kept = true;
            }
            assertTrue("the " + field.props.get(i) + " is in the chunk save", kept);
        }
    }

    @Test
    public void raidersInAGashComeFromAnEdgeAndLightNoFires() {
        Encounter raid = new Encounter(Encounter.Kind.RAID, 7, war.id, war.attackerId, war.defenderId, 0, 100, HERE, HERE);
        EncounterDirector.Turn t = director.onTurn(history, HERE, 2, false, maze, PLAYER, List.of(raid), List.of(),
                recruiter, props, null, null);
        assertTrue(band(war.attackerId).size() >= 4);
        for (Scenery s : maze.getScenery().values()) assertNotEquals("campfire", s.getPropId());
        assertTrue(t.messages.get(0).contains("down into the gash"));
    }

    @Test
    public void holdingATownsGateWinsItsStanding() {
        String key = "town@4,4,3";
        com.bpm.minotaur.gamedata.history.town.Town town = history.town(key);
        int before = history.standing().of(town);
        Encounter raid = new Encounter(Encounter.Kind.RAID, 8, -1, war.attackerId, -1, 0, 100, HERE, HERE);
        director.onTurn(history, HERE, 3, false, maze, PLAYER, List.of(raid), List.of(), recruiter, props, null, key);
        for (Monster m : band(war.attackerId)) {
            m.markSeekerDrewBlood();
            kill(m);
        }
        director.onTurn(history, HERE, 3, false, maze, PLAYER, List.of(raid), List.of(), recruiter, props, null, key);
        assertTrue(history.encounterLedger().isSpent(raid));
        // The town, and half again through its allegiance (Standing.change).
        assertEquals(before + EncounterDirector.GATE_DEFENDED + EncounterDirector.GATE_DEFENDED / 2, history.standing().of(town));
    }

    @Test
    public void theShelterIsLeftInPeace() {
        director.onTurn(history, HERE, 1, true, maze, PLAYER, List.of(skirmish(1000)), Collections.emptyList(), recruiter, props, null);
        assertTrue(maze.getMonsters().isEmpty());
    }
}
