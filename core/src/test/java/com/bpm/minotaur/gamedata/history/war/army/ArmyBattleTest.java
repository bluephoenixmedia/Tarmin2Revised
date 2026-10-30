package com.bpm.minotaur.gamedata.history.war.army;

import com.bpm.minotaur.gamedata.history.DoctrineCatalog;
import com.bpm.minotaur.gamedata.history.DoctrineCatalogTest;
import com.bpm.minotaur.gamedata.history.HistorySaveData;
import com.bpm.minotaur.gamedata.history.House;
import com.bpm.minotaur.gamedata.history.PlayerDeed;
import com.bpm.minotaur.gamedata.history.War;
import com.bpm.minotaur.gamedata.history.war.SeatMap;
import com.bpm.minotaur.managers.BiomeManager;
import com.bpm.minotaur.managers.HistoryManager;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.*;

/** Living War A14, A16-A17, A20, A25: armies fight in numbers, and the history hears it only through deeds. */
public class ArmyBattleTest {

    private static DoctrineCatalog catalog;

    @BeforeClass
    public static void load() throws IOException {
        catalog = DoctrineCatalogTest.loadCatalog();
    }

    private static final class Run {
        final long seed;
        final HistoryManager history;
        final BiomeManager biomes;
        final ArmyLayer layer;

        Run(long seed, int seasons) {
            this.seed = seed;
            history = HistoryManager.create(seed, catalog);
            biomes = new BiomeManager(seed);
            layer = new ArmyLayer(history.world(), Terrain.of(biomes), this::seats, history.roster(), history::recordArmyDeed);
            for (int i = 0; i < seasons * HistoryManager.SEASON_TURNS; i++) {
                history.tickWarClock();
                layer.tick(history.warClock());
            }
        }

        SeatMap seats() {
            return SeatMap.of(history.world(), biomes.getRoads(), biomes.getCastleSite(), seed, biomes::isOpenLand);
        }

        int count(PlayerDeed.Kind kind) {
            int n = 0;
            for (PlayerDeed d : history.toSave().deeds) if (d.kind == kind) n++;
            return n;
        }
    }

    @Test
    public void armiesFightAndWriteDeeds() {
        Run r = new Run(8919L, 30);
        assertTrue("armies should fight in 30 Seasons", r.count(PlayerDeed.Kind.ARMY_BATTLE) > 0);
    }

    @Test
    public void theHistoryReplaysFromItsDeedsAlone() {
        for (long seed : new long[] {1000L, 8919L, 135623L}) {
            Run r = new Run(seed, 25);
            HistorySaveData save = r.history.toSave();
            String live = r.history.world().fingerprint();
            assertEquals("seed " + seed + " replays", live, HistoryManager.fromSave(0L, save, catalog).world().fingerprint());
            save.roster = null;
            assertEquals("seed " + seed + " replays with no roster at all", live, HistoryManager.fromSave(0L, save, catalog).world().fingerprint());
        }
    }

    @Test
    public void aSaveFromBeforeArmiesKeepsThePastItHad() {
        HistoryManager old = HistoryManager.create(40595L, catalog);
        old.world().armiesFromLiveSeason(Integer.MAX_VALUE);
        for (int i = 0; i < 5 * HistoryManager.SEASON_TURNS; i++) old.tickWarClock();
        HistorySaveData save = old.toSave();
        save.armiesFromLiveSeason = null;
        save.roster = null;
        HistoryManager back = HistoryManager.fromSave(0L, save, catalog);
        assertEquals(old.world().fingerprint(), back.world().fingerprint());
        assertEquals("armies take over from its next Season", 5, back.world().armiesFromLiveSeason());
    }

    @Test
    public void theDeadComeOffTheirHousesAndNeverBelowTheFloor() {
        Run r = new Run(24757L, 30);
        for (House h : r.history.world().houses()) {
            if (!h.isExtinct()) assertTrue(h.name + " at " + h.strength, h.strength >= 5f);
        }
        for (PlayerDeed d : r.history.toSave().deeds) {
            if (d.kind != PlayerDeed.Kind.ARMY_BATTLE) continue;
            assertNotNull(d.deadHouses);
            assertEquals(d.deadHouses.length, d.deadCounts.length);
        }
    }

    @Test
    public void aBeatenHouseSuesForPeace() {
        HistoryManager history = HistoryManager.create(1000L, catalog);
        BiomeManager biomes = new BiomeManager(1000L);
        ArmyLayer layer = new ArmyLayer(history.world(), Terrain.of(biomes),
                () -> SeatMap.of(history.world(), biomes.getRoads(), biomes.getCastleSite(), 1000L, biomes::isOpenLand),
                history.roster(), history::recordArmyDeed);
        War w = history.world().activeWars().get(0);
        House beaten = history.world().house(w.defenderId).holdsCastle ? history.world().house(w.attackerId) : history.world().house(w.defenderId);
        beaten.strength = 60f;
        layer.tick(1);
        history.roster().armies.removeIf(a -> a.houseId == beaten.id);
        beaten.strength = 5f;
        for (int i = 0; i < HistoryManager.SEASON_TURNS && w.isActive(); i++) {
            history.tickWarClock();
            layer.tick(history.warClock());
        }
        assertFalse("the war ends", w.isActive());
        boolean sued = false;
        for (PlayerDeed d : history.toSave().deeds) if (d.kind == PlayerDeed.Kind.SUED_FOR_PEACE && d.target == beaten.id) sued = true;
        assertTrue(sued);
    }

    @Test
    public void aShiftEndsBattlesAndKeepsEachArmysFractionOfItsWay() {
        Run r = new Run(135623L, 6);
        r.layer.resolveBattles(r.history.warClock());
        assertTrue(r.history.roster().battles.isEmpty());
        BiomeManager reborn = new BiomeManager(135624L);
        SeatMap oldSeats = r.layer.seats();
        ArmyLayer next = new ArmyLayer(r.history.world(), Terrain.of(reborn),
                () -> SeatMap.of(r.history.world(), reborn.getRoads(), reborn.getCastleSite(), 135624L, reborn::isOpenLand),
                r.history.roster(), r.history::recordArmyDeed);
        int before = r.history.roster().armies.size();
        next.shiftFrom(oldSeats);
        assertEquals(before, r.history.roster().armies.size());
        Terrain t = Terrain.of(reborn);
        for (Army a : r.history.roster().armies) {
            assertTrue("army " + a.id + " on closed ground", t.passable(a.x, a.y));
        }
        for (int i = 0; i < 300; i++) {
            r.history.tickWarClock();
            next.tick(r.history.warClock());
        }
    }
}
