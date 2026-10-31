package com.bpm.minotaur.gamedata.history.war.army;

import com.bpm.minotaur.gamedata.history.DoctrineCatalog;
import com.bpm.minotaur.gamedata.history.DoctrineCatalogTest;
import com.bpm.minotaur.gamedata.history.EventType;
import com.bpm.minotaur.gamedata.history.HistoryEvent;
import com.bpm.minotaur.gamedata.history.House;
import com.bpm.minotaur.gamedata.history.PlayerDeed;
import com.bpm.minotaur.gamedata.history.War;
import com.bpm.minotaur.gamedata.history.war.SeatMap;
import com.bpm.minotaur.managers.BiomeManager;
import com.bpm.minotaur.managers.HistoryManager;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/** Living War A30-A34, through the real armies: a seat is invested, starved, relieved, stormed. */
public class SiegesOfSeatsTest {

    private static DoctrineCatalog catalog;

    @BeforeClass
    public static void load() throws IOException {
        catalog = DoctrineCatalogTest.loadCatalog();
    }

    private final long seed = 1000L;
    private final HistoryManager history = HistoryManager.create(seed, catalog);
    private final BiomeManager biomes = new BiomeManager(seed);
    private final List<PlayerDeed> deeds = new ArrayList<>();
    private final ArmyLayer layer = new ArmyLayer(history.world(), Terrain.of(biomes), this::seats, history.roster(), d -> {
        deeds.add(d);
        history.recordArmyDeed(d);
    });
    private House besieger;
    private House holder;

    private SeatMap seats() {
            return SeatMap.of(history.world(), biomes.getRoads(), biomes.getCastleSite(), seed, biomes::isOpenLand);
    }

    private void run(int turns) {
        for (int i = 0; i < turns; i++) {
            history.tickWarClock();
            layer.tick(history.warClock());
        }
    }

    private SeatSiege invest() {
        run(1);
        for (War w : history.world().activeWars()) {
            House a = history.world().house(w.attackerId), b = history.world().house(w.defenderId);
            if (a == null || b == null || a.holdsCastle || b.holdsCastle) continue;
            SeatSiege s = layer.investNow(a, b, history.warClock());
            if (s != null) {
                besieger = a;
                holder = b;
                s.works = 0;
                return s;
            }
        }
        fail("no war to lay a siege in");
        return null;
    }

    private long count(EventType type) {
        return history.world().events().stream().filter((HistoryEvent e) -> e.type == type).count();
    }

    @Test
    public void investingIsChronicledAndTheArmyStays() {
        long before = count(EventType.SEAT_INVESTED);
        SeatSiege s = invest();
        assertEquals(before + 1, count(EventType.SEAT_INVESTED));
        Army a = history.roster().army(s.armies[0]);
        assertEquals(s.id, a.sieging);
        // Nothing else to fight, and no ladders yet: it camps.
        run(10);
        assertEquals(s.x, a.x);
        assertEquals(s.y, a.y);
        assertEquals(1, history.roster().sieges.size());
    }

    @Test
    public void aGarrisonStarves() {
        SeatSiege s = invest();
        int before = history.roster().garrison(history.world(), s.holder);
        // Keep the captain from going in, and the garrison at home.
        for (int i = 0; i < SeatSiege.STARVE_TURNS * 3 + 1; i++) {
            s.works = 0;
            run(1);
            if (history.roster().siegeAt(s.x, s.y) == null) fail("the siege ended early");
        }
        assertTrue("before " + before + ", now " + history.roster().garrison(history.world(), s.holder),
                history.roster().garrison(history.world(), s.holder) <= before - 3);
    }

    @Test
    public void wallsWearWithTheSeasons() {
        SeatSiege s = invest();
        s.wear();
        s.wear();
        assertEquals(3f, s.walls, 0f);
    }

    @Test
    public void theDefendersHostsAreDrawnToRelieve() {
        SeatSiege s = invest();
        run(1);
        boolean drawn = false;
        for (Army a : history.roster().armies) {
            if (a.houseId == holder.id && a.isHost() && !a.battle && a.objective == Army.Objective.HUNT && a.targetX == s.x && a.targetY == s.y) drawn = true;
        }
        boolean hasHost = false;
        for (Army a : history.roster().armies) hasHost |= a.houseId == holder.id && a.isHost();
        assertEquals("every free host marches to it", hasHost, drawn);
    }

    @Test
    public void aSiegeThatLiftsWritesSiegeRaised() {
        SeatSiege s = invest();
        long before = count(EventType.SIEGE_RAISED);
        Army a = history.roster().army(s.armies[0]);
        a.objective = Army.Objective.RECOVER;
        run(1);
        assertEquals(before + 1, count(EventType.SIEGE_RAISED));
        assertNull(history.roster().siegeAt(s.x, s.y));
        assertEquals("free to go", -1, a.sieging);
    }

    /** The captain's temper starts it, with the ladders up. */
    @Test
    public void noAssaultBeforeTheLaddersAreUp() {
        SeatSiege s = invest();
        for (int i = 0; i < SeatSiege.LADDER_TURNS - 5; i++) {
            run(1);
            ArmyBattle b = layer.battleIn(s.x, s.y);
            assertTrue("no storming yet", b == null || !b.assault);
            if (history.roster().siegeAt(s.x, s.y) == null) return;
        }
    }

    private ArmyBattle storm() {
        SeatSiege s = invest();
        s.works = SeatSiege.RAM_TURNS;
        return layer.assaultNow(s);
    }

    /** A33: broken on the walls, the garrison falls back; the seat is not taken until the yard is cleared. */
    @Test
    public void theSeatFallsOnlyWhenTheYardIsCleared() {
        ArmyBattle b = storm();
        assertTrue(b.assault);
        assertTrue("the walls count for it", b.wallEdge >= SeatSiege.WALLS_FLOOR);
        int startedDeeds = deeds.size();
        b.moraleB = 0;
        run(1);
        assertTrue("the garrison broke, and fell back", b.yard);
        assertTrue("battle goes on", history.roster().battles.contains(b));
        assertEquals("with the walls' edge gone", SeatSiege.YARD_EDGE, b.wallEdge, 0f);
        for (int i = startedDeeds; i < deeds.size(); i++) assertFalse(deeds.get(i).seatFell);

        // No morale can end it now: only the last of them falling.
        b.moraleA = 100;
        b.moraleB = -50;
        run(1);
        assertTrue("morale counts for nothing in the yard", history.roster().battles.contains(b));
        for (PlayerDeed d : deeds) assertFalse(d.seatFell);
    }

    @Test
    public void aStormedSeatThatFallsEndsItsSiegeWithoutRaisingIt() {
        ArmyBattle b = storm();
        int x = b.x, y = b.y;
        long raised = count(EventType.SIEGE_RAISED);
        // Nothing left to stand: every soldier of the garrison is gone.
        for (int i = 0; i < 2000 && history.roster().battles.contains(b); i++) {
            b.moraleA = 100;
            layer.soldierFell(b, false, holder.id);
            run(1);
        }
        boolean fell = false;
        for (PlayerDeed d : deeds) fell |= d.kind == PlayerDeed.Kind.ARMY_BATTLE && d.seatFell;
        assertTrue("the seat fell", fell);
        assertNull(history.roster().siegeAt(x, y));
        assertEquals("a fall is no raising", raised, count(EventType.SIEGE_RAISED));
    }

    @Test
    public void anAssaultOutOfTimeHasFailed() {
        ArmyBattle b = storm();
        b.turn = ArmyTuning.TURN_CAP;
        b.moraleA = 100;
        b.moraleB = 100;
        run(1);
        for (PlayerDeed d : deeds) assertFalse(d.seatFell);
    }

    /** A33: morale or none, the walls are given up when too few are left to man them. */
    @Test
    public void aGarrisonWornDownAbandonsTheWallsWhateverItsMorale() {
        ArmyBattle b = storm();
        assertFalse(b.yard);
        b.startB = 300;
        run(1);
        assertTrue(b.yard);
    }

    /** A besieged house takes in no one: its Season's recovery is lost with the starving. */
    @Test
    public void aBesiegedHouseCannotMakeGoodItsDead() {
        SeatSiege s = invest();
        history.roster().attritionHouses = new int[0];
        history.roster().attritionDead = new int[0];
        int before = history.roster().garrison(history.world(), s.holder);
        s.works = 0;
        for (int i = 0; i < com.bpm.minotaur.managers.HistoryManager.SEASON_TURNS * 2 && history.roster().siegeAt(s.x, s.y) != null; i++) {
            s.works = 0;
            history.roster().battles.removeIf(o -> o.x == s.x && o.y == s.y);
            run(1);
        }
        assertTrue("a garrison left the poorer: " + before + " to " + history.roster().garrison(history.world(), s.holder),
                history.roster().garrison(history.world(), s.holder) < before);
    }

    @Test
    public void anArmyThatComesBackToItsSiegeIsCountedOnce() {
        SeatSiege s = invest();
        Army a = history.roster().army(s.armies[0]);
        int n = s.armies.length;
        a.objective = Army.Objective.SEAT;
        layer.reinvest(a, history.warClock());
        assertEquals(n, s.armies.length);
    }

    /** A34: a garrison beaten in its sally has lost soldiers, not its seat. */
    @Test
    public void aSallyBeatenBackLosesNoSeat() {
        SeatSiege s = invest();
        ArmyBattle b = layer.sallyNow(s);
        assertFalse(b.assault);
        assertEquals("no wall to stand behind", 1f, b.wallEdge, 0f);
        b.moraleB = 0;
        run(1);
        assertFalse(history.roster().battles.contains(b));
        for (PlayerDeed d : deeds) assertFalse(d.seatFell);
        assertNotNull("the siege goes on", history.roster().siegeAt(s.x, s.y));
    }

    /** A siege survives a save: it is plain numbers in the roster. */
    @Test
    public void aSiegeIsSavedWithTheRoster() {
        SeatSiege s = invest();
        s.works = 123;
        s.walls = 2.5f;
        com.badlogic.gdx.utils.Json json = new com.badlogic.gdx.utils.Json();
        WarRoster back = json.fromJson(WarRoster.class, json.toJson(history.roster()));
        SeatSiege b = back.siegeAt(s.x, s.y);
        assertNotNull(b);
        assertEquals(123, b.works);
        assertEquals(2.5f, b.walls, 0f);
        assertEquals(s.holder, b.holder);
        assertEquals(s.id, back.army(s.armies[0]).sieging);
    }
}
