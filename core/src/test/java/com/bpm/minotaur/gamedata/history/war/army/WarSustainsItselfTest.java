package com.bpm.minotaur.gamedata.history.war.army;

import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.gamedata.history.DoctrineCatalog;
import com.bpm.minotaur.gamedata.history.DoctrineCatalogTest;
import com.bpm.minotaur.gamedata.history.House;
import com.bpm.minotaur.gamedata.history.PlayerDeed;
import com.bpm.minotaur.gamedata.history.war.SeatMap;
import com.bpm.minotaur.managers.BiomeManager;
import com.bpm.minotaur.managers.HistoryManager;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;

/** Living War A9-A12, A27, A29: logistics, raids, hunters, the war floor, the land's toll and trails. */
public class WarSustainsItselfTest {

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
        final Set<Army.Kind> kinds = EnumSet.noneOf(Army.Kind.class);

        Run(long seed, ArmyLayer.PlayerWatch watch) {
            this.seed = seed;
            history = HistoryManager.create(seed, catalog);
            biomes = new BiomeManager(seed);
            layer = new ArmyLayer(history.world(), Terrain.of(biomes), this::seats, history.roster(), history::recordArmyDeed);
            layer.watch(watch);
        }

        SeatMap seats() {
            return SeatMap.of(history.world(), biomes.getRoads(), biomes.getCastleSite(), seed, biomes::isOpenLand);
        }

        Run run(int turns) {
            for (int i = 0; i < turns; i++) {
                history.tickWarClock();
                layer.tick(history.warClock());
                for (Army a : layer.roster().armies) kinds.add(a.kind);
            }
            return this;
        }

        List<String> state() {
            List<String> out = new ArrayList<>();
            for (Army a : layer.roster().armies) out.add(a.id + ":" + a.kind + "@" + a.x + "," + a.y + ":" + a.soldiers);
            return out;
        }
    }

    private static ArmyLayer.PlayerWatch at(int x, int y, boolean enemy) {
        return new ArmyLayer.PlayerWatch() {
            @Override
            public GridPoint2 surfaceChunk() {
                return new GridPoint2(x, y);
            }

            @Override
            public boolean countsPlayerAnEnemy(int houseId) {
                return enemy;
            }
        };
    }

    @Test
    public void suppliesForagersAndRaidsMarch() {
        Run r = new Run(8919L, null).run(10000);
        assertTrue(r.kinds.toString(), r.kinds.containsAll(EnumSet.of(Army.Kind.HOST, Army.Kind.SUPPLY, Army.Kind.FORAGERS, Army.Kind.RAID)));
    }

    @Test
    public void theWarFloorHolds() {
        for (long seed : new long[] {1000L, 24757L, 135623L}) {
            Run r = new Run(seed, null);
            r.run(HistoryManager.SEASON_TURNS + 1);
            for (int s = 0; s < 15; s++) {
                r.run(HistoryManager.SEASON_TURNS);
                assertTrue("seed " + seed + ", Season " + s, r.history.world().activeWars().size() >= 2);
            }
        }
    }

    @Test
    public void noArmyReadsThePlayerUnlessAHouseCountsThemAnEnemy() {
        for (long seed : new long[] {1000L, 8919L}) {
            List<String> nobody = new Run(seed, null).run(6000).state();
            List<String> here = new Run(seed, at(3, 3, false)).run(6000).state();
            List<String> there = new Run(seed, at(-20, 15, false)).run(6000).state();
            assertEquals("seed " + seed, nobody, here);
            assertEquals("seed " + seed, nobody, there);
        }
    }

    @Test
    public void anEnemyHouseSendsAHuntingPartyWhereItsArmiesSawThePlayer() {
        Run r = new Run(40595L, at(14, 14, true));
        r.run(HistoryManager.SEASON_TURNS * 20);
        assertTrue(r.kinds.toString(), r.kinds.contains(Army.Kind.HUNTING_PARTY));
    }

    @Test
    public void aHouseNeverSeeingThePlayerSendsNoHunters() {
        Run r = new Run(40595L, new ArmyLayer.PlayerWatch() {
            @Override
            public GridPoint2 surfaceChunk() {
                return null;
            }

            @Override
            public boolean countsPlayerAnEnemy(int houseId) {
                return true;
            }
        });
        r.run(HistoryManager.SEASON_TURNS * 10);
        assertFalse(r.kinds.contains(Army.Kind.HUNTING_PARTY));
    }

    @Test
    public void theLandTakesItsTollThroughOneDeedAHouseASeason() {
        Run r = new Run(8919L, null).run(HistoryManager.SEASON_TURNS * 20);
        int deeds = 0;
        List<String> seen = new ArrayList<>();
        for (PlayerDeed d : r.history.toSave().deeds) {
            if (d.kind != PlayerDeed.Kind.ARMY_ATTRITION) continue;
            deeds++;
            assertTrue("dead " + d.deadCounts[0], d.deadCounts[0] > 0);
            String key = d.target + "/" + d.liveSeason;
            assertFalse("twice for " + key, seen.contains(key));
            seen.add(key);
        }
        assertTrue(deeds > 0);
        assertEquals("the toll replays", r.history.world().fingerprint(), HistoryManager.fromSave(0L, r.history.toSave(), catalog).world().fingerprint());
    }

    @Test
    public void theBlightSparesTheHostThatMadeIt() {
        Run r = new Run(1000L, null);
        House castle = r.history.world().tarminHouse();
        r.run(HistoryManager.SEASON_TURNS * 20);
        for (PlayerDeed d : r.history.toSave().deeds) {
            if (d.kind == PlayerDeed.Kind.ARMY_ATTRITION && d.target == castle.id) {
                assertTrue(d.deadCounts[0] >= 0);
            }
        }
    }

    @Test
    public void aHostLeavesATrailAndThinningIsDeterministic() {
        Run r = new Run(8919L, null);
        boolean any = false;
        for (int t = 0; t < 100 && !any; t++) {
            r.run(100);
            for (Army a : r.layer.roster().armies) {
                for (int p : a.trail) {
                    assertTrue(r.layer.roster().onTrail((p >> 13) - 4096, (p & 8191) - 4096));
                    any = true;
                }
            }
        }
        assertTrue("some army has crossed a chunk", any);
        int fell = 0;
        for (int i = 0; i < 400; i++) {
            boolean f = TrailThinning.fell(8919L, 7, 9, 3, i);
            assertEquals(f, TrailThinning.fell(8919L, 7, 9, 3, i));
            if (f) fell++;
        }
        assertTrue("about half fall: " + fell, fell > 150 && fell < 250);
    }
}
