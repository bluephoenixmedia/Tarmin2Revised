package com.bpm.minotaur.managers;

import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.history.DoctrineCatalog;
import com.bpm.minotaur.gamedata.history.DoctrineCatalogTest;
import com.bpm.minotaur.gamedata.history.PlayerDeed;
import com.bpm.minotaur.gamedata.history.war.EncounterLedger;
import com.bpm.minotaur.gamedata.history.war.army.Army;
import com.bpm.minotaur.gamedata.history.war.army.ArmyBattle;
import com.bpm.minotaur.gamedata.monster.FactionMatrix;
import com.bpm.minotaur.gamedata.monster.Monster;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/** Living War A20-A24, A26: one battle state, watched or not; going away and coming back resets nothing. */
public class OneBattleStateTest {

    private static DoctrineCatalog catalog;
    private static final GridPoint2 CHUNK = new GridPoint2(BattleFixture.X, BattleFixture.Y);
    private static final GridPoint2 TILE = new GridPoint2(18, 18);

    private BattleFixture f;
    private Maze maze;
    private long clock;
    private int fullSpoils;
    private int pickedOver;
    private final List<PlayerDeed> deeds = new ArrayList<>();

    private final BattleDirector.Spoils spoils = new BattleDirector.Spoils() {
        @Override
        public void drop(Maze m, GridPoint2 near, int loser, boolean lordFell) {
            fullSpoils++;
        }

        @Override
        public void pickedOver(Maze m, GridPoint2 near) {
            pickedOver++;
        }
    };

    @BeforeClass
    public static void load() throws IOException {
        catalog = DoctrineCatalogTest.loadCatalog();
    }

    @Before
    public void setUp() {
        f = new BattleFixture(77L, catalog, 40);
        maze = new Maze(1, new int[36][36]);
    }

    private BattleDirector walkIn() {
        return new BattleDirector(f.battle, f.layer, CHUNK, 0, f.history.world(), catalog, 9L, true);
    }

    private BattleDirector.Report tick(BattleDirector d) {
        return d.tick(maze, TILE, BattleFixture::soldier, spoils, ++clock);
    }

    private static com.bpm.minotaur.gamedata.Scenery prop(String id, int x, int y) {
        com.bpm.minotaur.gamedata.Scenery s = new com.bpm.minotaur.gamedata.Scenery(com.bpm.minotaur.gamedata.Scenery.SceneryType.PROP, x, y);
        s.setPropId(id);
        return s;
    }

    private void slay(List<Monster> line, int n) {
        for (Monster m : new ArrayList<>(line).subList(0, n)) {
            m.setCurrentHP(0);
            maze.removeMonster(m);
        }
    }

    // ---------- A21: only the lines appear ----------

    @Test
    public void onlyTheLinesAppearAndTheReserveMarchesInThreeATurn() {
        BattleDirector d = walkIn();
        tick(d);
        assertEquals(BattleDirector.LINE, d.side(true).size());
        assertEquals(BattleDirector.LINE, d.side(false).size());
        slay(d.side(true), 8);
        tick(d);
        assertEquals("the dead are replaced at three a turn", BattleDirector.LINE - 8 + BattleDirector.MARCH_PER_TURN, d.side(true).size());
    }

    @Test
    public void aSmallArmyFieldsNoMoreThanItHas() {
        f.armyA().soldiers = 5;
        BattleDirector d = walkIn();
        tick(d);
        assertEquals(5, d.side(true).size());
    }

    @Test
    public void eachSoldierIsOfItsOwnHouseInProportion() {
        f.armyA().soldiers = 8;
        Army other = new Army();
        other.id = f.history.roster().nextArmyId++;
        int ally = -1;
        for (com.bpm.minotaur.gamedata.history.House h : f.history.world().livingHouses()) {
            if (h.id != f.battle.houseA && h.id != f.battle.houseB) ally = h.id;
        }
        other.houseId = ally;
        other.soldiers = 8;
        other.battle = true;
        f.history.roster().armies.add(other);
        f.battle.armiesA = new int[] {f.battle.armiesA[0], other.id};
        BattleDirector d = walkIn();
        tick(d);
        int a = 0, b = 0;
        for (Monster m : d.side(true)) {
            if (m.getHouseId() == ally) b++;
            else a++;
        }
        assertEquals(8, a);
        assertEquals(8, b);
        Monster stranger = d.side(true).get(0), foe = d.side(false).get(0);
        assertTrue("sides fight whatever their houses' stance", new FactionMatrix().isHostile(stranger, foe));
        assertFalse(new FactionMatrix().isHostile(d.side(true).get(0), d.side(true).get(1)));
    }

    // ---------- A22: the map is the truth while watched ----------

    @Test
    public void whileWatchedOnlyTheMapKills() {
        BattleDirector d = walkIn();
        tick(d);
        int a = f.armyA().soldiers, b = f.armyB().soldiers;
        for (int i = 0; i < 50; i++) {
            f.history.tickWarClock();
            f.layer.tick(f.history.warClock());
            d.tick(maze, null, BattleFixture::soldier, spoils, ++clock); // no player, so no volleys near them
        }
        assertEquals("no blow struck on the map, no one dead", a, f.armyA().soldiers);
        assertEquals(b, f.armyB().soldiers);
    }

    @Test
    public void unwatchedTheNumbersDoTheKilling() {
        for (int i = 0; i < 400 && f.history.roster().battles.contains(f.battle); i++) {
            f.history.tickWarClock();
            f.layer.tick(f.history.warClock());
        }
        assertTrue(f.battle.turn > 5);
        assertTrue(f.armyA() == null || f.armyB() == null || f.armyA().soldiers + f.armyB().soldiers < 80);
    }

    @Test
    public void everyDeathOnTheMapComesOffItsArmyWhoeverDidIt() {
        BattleDirector d = walkIn();
        tick(d);
        slay(d.side(true), 3);
        slay(d.side(false), 2);
        tick(d);
        assertEquals(37, f.armyA().soldiers);
        assertEquals(38, f.armyB().soldiers);
        assertTrue("and it costs morale", f.battle.moraleA < 100);
    }

    // ---------- A23: collapsing ----------

    @Test
    public void leaveMidBattleAndReturnSurvivorsMoraleAndCaptainsCarry() {
        f.armyA().captainId = f.history.world().livingHouses().get(0).lordId;
        BattleDirector d = walkIn();
        tick(d);
        Monster captain = null;
        for (Monster m : d.captainsOnField().keySet()) if (d.side(true).contains(m)) captain = m;
        assertNotNull("a captain for the army", captain);
        captain.setCurrentHP(0);
        maze.removeMonster(captain);
        slay(d.side(true).subList(1, d.side(true).size()), 4);
        slay(d.side(false), 6);
        // A wound that does not kill is forgotten.
        d.side(false).get(0).setCurrentHP(1);
        tick(d);
        int soldiersA = f.armyA().soldiers, soldiersB = f.armyB().soldiers;
        int moraleA = f.battle.moraleA, moraleB = f.battle.moraleB, turn = f.battle.turn;
        assertEquals(35, soldiersA);
        assertEquals(34, soldiersB);

        d.collapse(++clock);
        assertTrue("no soldier crosses a chunk edge", maze.getMonsters().isEmpty());
        assertNull(f.layer.watchedBattle());
        assertEquals(soldiersA, f.armyA().soldiers);

        BattleDirector back = walkIn();
        tick(back);
        assertEquals("the same battle", f.battle, back.battle);
        assertEquals(soldiersA, f.layer.aliveOn(f.battle, true));
        assertEquals(soldiersB, f.layer.aliveOn(f.battle, false));
        assertEquals(moraleA, f.battle.moraleA);
        assertEquals(moraleB, f.battle.moraleB);
        assertTrue("and it did not start again", f.battle.turn >= turn);
        for (Monster m : back.captainsOnField().keySet()) {
            assertFalse("a fallen captain stays fallen", back.side(true).contains(m));
        }
        for (Monster m : back.side(false)) assertEquals("wounds are forgotten", 20, m.getCurrentHP());
    }

    @Test
    public void aSideBrokenByTheCountOnLeavingBrokeWithNoOneThere() {
        BattleDirector d = walkIn();
        tick(d);
        f.battle.moraleA = 1;
        slay(d.side(true), 3);
        d.collapse(++clock);
        assertFalse(f.history.roster().battles.contains(f.battle));
        assertFalse("no witness", f.battle.witnessed);
        assertFalse(f.history.encounterLedger().fields.get(0).witnessed);
    }

    @Test
    public void aHouseKnowsItsSideAndABlowThatKilledStillCounts() {
        BattleDirector d = walkIn();
        tick(d);
        assertEquals(1, d.sideOf(f.battle.houseA));
        assertEquals(2, d.sideOf(f.battle.houseB));
        assertFalse(d.playerStruckSideOf(f.battle.houseA));
        Monster m = d.side(true).get(0);
        m.markSeekerDrewBlood();
        m.setCurrentHP(0);
        maze.removeMonster(m);
        tick(d);
        assertTrue(d.playerStruckSideOf(f.battle.houseA));
        assertFalse(d.playerStruckSideOf(f.battle.houseB));
    }

    // ---------- what the player meets of a real army (A22) ----------

    private List<com.bpm.minotaur.gamedata.history.war.Encounter> one(com.bpm.minotaur.gamedata.history.war.Encounter e) {
        return java.util.Collections.singletonList(e);
    }

    private com.bpm.minotaur.gamedata.history.war.Encounter meet(com.bpm.minotaur.gamedata.history.war.Encounter.Kind kind, Army a, GridPoint2 to) {
        com.bpm.minotaur.gamedata.history.war.Encounter e = new com.bpm.minotaur.gamedata.history.war.Encounter(
                kind, a.id, a.warId, a.houseId, -1, 0, Long.MAX_VALUE, CHUNK, to);
        e.soldiers = a.soldiers;
        return e;
    }

    private EncounterDirector.Turn stage(EncounterDirector s, com.bpm.minotaur.gamedata.history.war.Encounter e) {
        return s.onTurn(f.history, CHUNK, 1, false, maze, TILE, one(e), new ArrayList<>(), BattleFixture::soldier, OneBattleStateTest::prop, spoils);
    }

    private Army freeArmy() {
        Army a = f.armyA();
        a.battle = false;
        a.x = CHUNK.x;
        a.y = CHUNK.y;
        f.history.roster().battles.clear();
        return a;
    }

    @Test
    public void aPartyWithNowhereToGoStandsAndIsNotAnnouncedAgain() {
        Army a = freeArmy();
        a.soldiers = 6;
        EncounterDirector s = new EncounterDirector(catalog);
        com.bpm.minotaur.gamedata.history.war.Encounter e = meet(com.bpm.minotaur.gamedata.history.war.Encounter.Kind.COLUMN, a, CHUNK);
        assertEquals(1, stage(s, e).messages.size());
        int soldiers = maze.getMonsters().size();
        assertEquals(6, soldiers);
        for (int i = 0; i < 60; i++) assertTrue(stage(s, e).messages.isEmpty());
        assertEquals("nobody marched off the field", soldiers, maze.getMonsters().size());
        a.x = CHUNK.x + 1;
        stage(s, e);
        assertTrue("when it moves on, they go", maze.getMonsters().isEmpty());
    }

    @Test
    public void aColumnThatHasMarchedThroughDoesNotMarchThroughAgain() {
        Army a = freeArmy();
        EncounterDirector s = new EncounterDirector(catalog);
        com.bpm.minotaur.gamedata.history.war.Encounter e = meet(com.bpm.minotaur.gamedata.history.war.Encounter.Kind.COLUMN, a, new GridPoint2(CHUNK.x + 1, CHUNK.y));
        stage(s, e);
        assertFalse(maze.getMonsters().isEmpty());
        for (Monster m : new ArrayList<>(maze.getMonsters().values())) maze.removeMonster(m);
        stage(s, e);
        assertTrue(stage(s, e).messages.isEmpty());
        assertTrue("not raised a second time", maze.getMonsters().isEmpty());
    }

    @Test
    public void anArmyDrawnIntoABattleLeavesItsColumnToTheLine() {
        Army a = freeArmy();
        EncounterDirector s = new EncounterDirector(catalog);
        com.bpm.minotaur.gamedata.history.war.Encounter e = meet(com.bpm.minotaur.gamedata.history.war.Encounter.Kind.COLUMN, a, new GridPoint2(CHUNK.x + 1, CHUNK.y));
        stage(s, e);
        int before = a.soldiers;
        Monster slain = maze.getMonsters().values().iterator().next();
        slain.setCurrentHP(0);
        maze.removeMonster(slain);
        a.battle = true;
        stage(s, e);
        assertEquals("the last kill still counts", before - 1, a.soldiers);
        assertTrue("and the column is gone", maze.getMonsters().isEmpty());
    }

    @Test
    public void aHeraldsEscortFallingLeavesTheHeraldOnTheRoster() {
        Army a = freeArmy();
        a.soldiers = 1;
        EncounterDirector s = new EncounterDirector(catalog);
        com.bpm.minotaur.gamedata.history.war.Encounter e = meet(com.bpm.minotaur.gamedata.history.war.Encounter.Kind.HERALD, a, new GridPoint2(CHUNK.x + 1, CHUNK.y));
        stage(s, e);
        for (Monster m : new ArrayList<>(maze.getMonsters().values())) {
            if (!m.isPeaceful()) {
                m.setCurrentHP(0);
                maze.removeMonster(m);
            }
        }
        stage(s, e);
        assertNotNull(f.history.roster().army(a.id));
    }

    @Test
    public void beatingRaidersEarnsTheThanksOfTheHouseRaided() {
        Army a = freeArmy();
        EncounterDirector s = new EncounterDirector(catalog);
        com.bpm.minotaur.gamedata.history.war.Encounter e = new com.bpm.minotaur.gamedata.history.war.Encounter(
                com.bpm.minotaur.gamedata.history.war.Encounter.Kind.RAID, a.id, a.warId, a.houseId, f.battle.houseB, 0, Long.MAX_VALUE, CHUNK, CHUNK);
        stage(s, e);
        for (Monster m : new ArrayList<>(maze.getMonsters().values())) {
            m.markSeekerDrewBlood();
            m.setCurrentHP(0);
            maze.removeMonster(m);
        }
        assertEquals(f.battle.houseB, stage(s, e).raidBeaten);
    }

    @Test
    public void aBrokenCampIsOfOnePlace() {
        com.bpm.minotaur.gamedata.history.war.Encounter here = new com.bpm.minotaur.gamedata.history.war.Encounter(
                com.bpm.minotaur.gamedata.history.war.Encounter.Kind.CAMP, 5, 1, 2, -1, 0, Long.MAX_VALUE, CHUNK, CHUNK);
        com.bpm.minotaur.gamedata.history.war.Encounter there = new com.bpm.minotaur.gamedata.history.war.Encounter(
                com.bpm.minotaur.gamedata.history.war.Encounter.Kind.CAMP, 5, 1, 2, -1, 0, Long.MAX_VALUE, new GridPoint2(9, 9), new GridPoint2(9, 9));
        EncounterLedger ledger = f.history.encounterLedger();
        ledger.spend(here);
        assertTrue(ledger.isSpent(here));
        assertFalse("the host pitches again wherever it next halts", ledger.isSpent(there));
    }

    @Test
    public void whileAwayTheBattleGoesOnInItsNumbers() {
        BattleDirector d = walkIn();
        tick(d);
        d.collapse(++clock);
        int turn = f.battle.turn;
        for (int i = 0; i < 30 && f.history.roster().battles.contains(f.battle); i++) {
            f.history.tickWarClock();
            f.layer.tick(f.history.warClock());
        }
        assertTrue(f.battle.turn > turn);
    }

    // ---------- A24: spoils, and a field from every battle ----------

    private void breakIt(BattleDirector d) {
        for (int i = 0; i < 400 && d.phase() == BattleDirector.Phase.BATTLE; i++) {
            tick(d);
            if (d.phase() == BattleDirector.Phase.BATTLE) slay(d.side(true), Math.min(4, d.side(true).size()));
        }
    }

    @Test
    public void aPlayerPresentAtTheRoutGetsTheFullSpoils() {
        BattleDirector d = walkIn();
        tick(d);
        breakIt(d);
        assertEquals(BattleDirector.Phase.AFTERMATH, d.phase());
        assertEquals(1, fullSpoils);
        assertEquals(f.battle.houseA, f.battle.loser);
        assertTrue(f.battle.witnessed);
        EncounterLedger.Field field = f.history.encounterLedger().fields.get(0);
        assertTrue(field.witnessed);
    }

    @Test
    public void everyBattleLeavesAFieldAndALateArrivalFindsItPickedOver() {
        for (int i = 0; i < 2000 && f.history.roster().battles.contains(f.battle); i++) {
            f.history.tickWarClock();
            f.layer.tick(f.history.warClock());
        }
        assertFalse("it ended unwatched", f.history.roster().battles.contains(f.battle));
        boolean found = false;
        for (EncounterLedger.Field x : f.history.encounterLedger().fields) {
            if (x.chunkX == BattleFixture.X && x.chunkY == BattleFixture.Y) {
                found = true;
                assertFalse(x.witnessed);
            }
        }
        assertTrue(found);
        EncounterDirector stages = new EncounterDirector(catalog);
        stages.onTurn(f.history, CHUNK, 1, false, maze, TILE, new ArrayList<>(), new ArrayList<>(), BattleFixture::soldier,
                OneBattleStateTest::prop, spoils);
        assertEquals("no signets for those who come after", 0, fullSpoils);
        assertEquals(1, pickedOver);
        assertFalse("the dead lie where they fell", f.history.encounterLedger().dressings.isEmpty());
    }

    @Test
    public void aFieldNobodyVisitsGoesAfterThreeSleeps() {
        EncounterLedger ledger = f.history.encounterLedger();
        ledger.battleFought(2, 2, 1, false, EncounterDirector.AFTERMATH_SLEEPS);
        for (int i = 0; i < EncounterDirector.AFTERMATH_SLEEPS; i++) ledger.newExpedition(true);
        assertTrue(ledger.fields.isEmpty());
    }

    // ---------- A26: choosing a side ----------

    @Test
    public void theChosenSideIsAnAllyUntilTheFightEnds() {
        BattleDirector d = walkIn();
        tick(d);
        d.chooseSide(1);
        Monster friend = d.side(true).get(0), foe = d.side(false).get(0);
        assertTrue(d.alliedWith(friend));
        assertFalse(d.alliedWith(foe));
        assertTrue("standing with a side is joining the fight", d.playerJoined());
    }

    @Test
    public void strikingYourOwnSideEndsIt() {
        BattleDirector d = walkIn();
        tick(d);
        d.chooseSide(1);
        d.side(true).get(0).markSeekerDrewBlood();
        tick(d);
        assertEquals(0, f.battle.playerSide);
    }

    @Test
    public void whomThePlayerFoughtForIsWhoseEnemiesAloneTheyStruckAndItCarries() {
        BattleDirector d = walkIn();
        tick(d);
        d.side(false).get(0).markSeekerDrewBlood();
        tick(d);
        assertEquals(f.battle.houseA, d.playerFoughtFor());
        d.collapse(++clock);
        assertEquals(f.battle.houseA, walkIn().playerFoughtFor());
        ArmyBattle b = f.battle;
        b.playerStruck = 3;
        assertEquals("both sides is neither", -1, b.foughtFor());
    }

    @Test
    public void theDeedSaysWhomThePlayerFoughtForAndThatTheyStoodThere() {
        BattleDirector d = walkIn();
        tick(d);
        d.side(false).get(0).markSeekerDrewBlood();
        breakIt(d);
        PlayerDeed deed = null;
        for (PlayerDeed x : f.history.toSave().deeds) if (x.kind == PlayerDeed.Kind.ARMY_BATTLE) deed = x;
        assertNotNull(deed);
        assertTrue(deed.witnessed);
        assertEquals(f.battle.houseA, deed.foughtFor);
    }
}
