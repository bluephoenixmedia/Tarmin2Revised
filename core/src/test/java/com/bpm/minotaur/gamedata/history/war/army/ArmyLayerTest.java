package com.bpm.minotaur.gamedata.history.war.army;

import com.badlogic.gdx.utils.Json;
import com.bpm.minotaur.gamedata.history.DoctrineCatalog;
import com.bpm.minotaur.gamedata.history.DoctrineCatalogTest;
import com.bpm.minotaur.gamedata.history.Figure;
import com.bpm.minotaur.gamedata.history.HistorySimulator;
import com.bpm.minotaur.gamedata.history.HistoryWorld;
import com.bpm.minotaur.gamedata.history.House;
import com.bpm.minotaur.gamedata.history.war.SeatMap;
import com.bpm.minotaur.managers.BiomeManager;
import com.bpm.minotaur.managers.HistoryManager;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/** Living War A1-A7: armies muster and march by themselves, and a reload walks the same future. */
public class ArmyLayerTest {

    private static DoctrineCatalog catalog;

    @BeforeClass
    public static void load() throws IOException {
        catalog = DoctrineCatalogTest.loadCatalog();
    }

    private static final class World {
        final long seed;
        final HistoryManager history;
        final BiomeManager biomes;
        final Terrain terrain;
        ArmyLayer layer;

        World(long seed) {
            this.seed = seed;
            history = HistoryManager.create(seed, catalog);
            biomes = new BiomeManager(seed);
            terrain = Terrain.of(biomes);
            layer = layer(new WarRoster());
        }

        ArmyLayer layer(WarRoster roster) {
            return new ArmyLayer(history.world(), terrain, this::seats, roster);
        }

        SeatMap seats() {
            return SeatMap.of(history.world(), biomes.getRoads(), biomes.getCastleSite(), seed, biomes::isOpenLand);
        }

        void run(int turns) {
            for (int i = 0; i < turns; i++) {
                history.tickWarClock();
                layer.tick(history.warClock());
            }
        }
    }

    private static List<String> positions(WarRoster r) {
        List<String> out = new ArrayList<>();
        for (Army a : r.armies) out.add(a.id + "@" + a.x + "," + a.y + ":" + a.objective + ":" + a.soldiers + ":" + a.progress);
        return out;
    }

    private static WarRoster throughSave(WarRoster r) {
        Json json = new Json();
        return json.fromJson(WarRoster.class, json.toJson(r));
    }

    @Test
    public void housesAtWarMusterHostsAtTheirSeats() {
        World w = new World(1000L);
        w.run(1);
        assertFalse("a new game opens on a war", w.layer.roster().armies.isEmpty());
        SeatMap seats = w.seats();
        for (Army a : w.layer.roster().armies) {
            House h = w.history.world().house(a.houseId);
            assertTrue(h.name + " musters, so it is at war or sworn to one who is",
                    w.history.world().activeWarCount(h.id) > 0 || a.levyOf >= 0);
            assertTrue("no more than one march's step from its seat on the first turn",
                    Math.max(Math.abs(a.x - seats.seat(h.id).x), Math.abs(a.y - seats.seat(h.id).y)) <= 1);
        }
    }

    @Test
    public void aHouseKeepsItsGarrison() {
        World w = new World(8919L);
        w.run(2000);
        for (House h : w.history.world().houses()) {
            if (h.isExtinct()) continue;
            int fielded = w.layer.roster().fielded(h.id);
            assertTrue(h.name + " fields " + fielded + " of " + h.strength, fielded <= h.strength * 0.75f + 1f);
        }
    }

    @Test
    public void armiesMarchAndStayOffClosedGround() {
        World w = new World(24757L);
        List<String> start = null;
        boolean moved = false;
        SeatMap seats = w.seats();
        for (int t = 0; t < 1500; t++) {
            w.run(1);
            for (Army a : w.layer.roster().armies) {
                boolean atASeat = false;
                for (House h : w.history.world().houses()) {
                    if (seats.seat(h.id) != null && seats.seat(h.id).x == a.x && seats.seat(h.id).y == a.y) atASeat = true;
                }
                assertTrue(a.id + " stands on closed ground at " + a.x + "," + a.y, atASeat || w.terrain.passable(a.x, a.y));
            }
            if (t == 5) start = positions(w.layer.roster());
            if (start != null && !start.equals(positions(w.layer.roster()))) moved = true;
        }
        assertTrue(moved);
    }

    @Test
    public void aReloadWalksTheSameFuture() {
        for (int saveAt : new int[] {7, 133, 512, 1009}) {
            World straight = new World(40595L);
            straight.run(1500);

            World reloaded = new World(40595L);
            reloaded.run(saveAt);
            WarRoster saved = throughSave(reloaded.layer.roster());
            reloaded.layer = reloaded.layer(saved);
            reloaded.run(1500 - saveAt);

            assertFalse(straight.layer.roster().armies.isEmpty());
            assertEquals("saved at turn " + saveAt, positions(straight.layer.roster()), positions(reloaded.layer.roster()));
        }
    }

    @Test
    public void theSaveCarriesTheRoster() {
        World w = new World(40595L);
        w.layer = w.layer(w.history.roster());
        w.run(300);
        assertFalse(w.history.roster().armies.isEmpty());
        HistoryManager back = HistoryManager.fromSave(0L, w.history.toSave(), catalog);
        assertEquals(positions(w.history.roster()), positions(back.roster()));
    }

    @Test
    public void aSleepPassesTurnsWithoutAWakingSeason() {
        World w = new World(1000L);
        int season = w.history.world().liveSeasons();
        long clock = w.history.warClock();
        w.history.sleepThroughWar(c -> w.layer.tick(c));
        assertEquals(clock + HistoryManager.SLEEP_TURNS, w.history.warClock());
        assertEquals(season, w.history.world().liveSeasons());
        assertFalse(w.layer.roster().armies.isEmpty());
    }

    @Test
    public void theHistoryWinsOnLoad() {
        HistoryWorld world = HistorySimulator.prehistory(135623L, catalog);
        House h = null, other = null;
        for (House x : world.livingHouses()) {
            if (x.holdsCastle) continue;
            if (h == null) h = x;
            else if (other == null) other = x;
        }
        h.strength = 40f;
        other.strength = 40f;
        WarRoster r = new WarRoster();
        Army stale = new Army();
        stale.id = r.nextArmyId++;
        stale.houseId = h.id;
        stale.warId = 99999;
        stale.soldiers = 15;
        stale.captainId = -1;
        r.armies.add(stale);

        Army tooMany = new Army();
        tooMany.id = r.nextArmyId++;
        tooMany.houseId = other.id;
        tooMany.soldiers = 200;
        tooMany.objective = Army.Objective.GUARD;
        r.armies.add(tooMany);

        Figure captain = world.livingMembers(other).isEmpty() ? null : world.livingMembers(other).get(0);
        if (captain != null) {
            tooMany.captainId = captain.id;
            captain.deathSeason = 0;
        }

        r.reconcile(world);

        assertEquals("a war gone sends its army home", Army.Objective.DISBAND, stale.objective);
        assertTrue("armies shrink to fit strength", tooMany.soldiers <= 30);
        if (captain != null) assertNotEquals("a dead captain is replaced", captain.id, tooMany.captainId);

        h.extinctSeason = 0;
        r.reconcile(world);
        assertFalse("an extinct house's army goes", r.armies.contains(stale));
    }

    @Test
    public void aSaveFromBeforeArmiesMustersFresh() {
        World w = new World(151461L);
        w.layer = w.layer(new WarRoster());
        w.run(1);
        assertEquals(!w.history.world().activeWars().isEmpty(), !w.layer.roster().armies.isEmpty());
    }

    @Test
    public void aTurnStaysInsideItsBudgets() {
        com.sun.management.ThreadMXBean mx = (com.sun.management.ThreadMXBean) java.lang.management.ManagementFactory.getThreadMXBean();
        long me = Thread.currentThread().getId();
        World w = new World(8919L);
        w.run(2000);
        long calls = w.layer.pathCalls();
        long before = mx.getThreadAllocatedBytes(me);
        int turns = 3000;
        for (int i = 0; i < turns; i++) w.layer.tick(w.history.warClock() + i + 1);
        long allocated = mx.getThreadAllocatedBytes(me) - before;
        long searched = w.layer.pathCalls() - calls;
        int units = Math.max(1, w.layer.roster().armies.size());
        assertTrue("allocated " + allocated / turns + " bytes a turn", allocated / turns < 1024);
        assertTrue(searched + " searches over " + turns + " turns for " + units + " units", searched <= turns * units / 5);
    }
}
