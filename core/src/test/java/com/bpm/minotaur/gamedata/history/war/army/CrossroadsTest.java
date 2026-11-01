package com.bpm.minotaur.gamedata.history.war.army;

import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.gamedata.history.DoctrineCatalog;
import com.bpm.minotaur.gamedata.history.DoctrineCatalogTest;
import com.bpm.minotaur.gamedata.history.war.SeatMap;
import com.bpm.minotaur.managers.BiomeManager;
import com.bpm.minotaur.managers.HistoryManager;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.Assert.*;

/** Living War A52: crossroads near the Shelter are strategic ground in every world, and the weight never reads the player. */
public class CrossroadsTest {

    private static DoctrineCatalog catalog;
    private static final long SEED = 8919L;

    @BeforeClass
    public static void load() throws IOException {
        catalog = DoctrineCatalogTest.loadCatalog();
    }

    private static final class World {
        final HistoryManager history = HistoryManager.create(SEED, catalog);
        final BiomeManager biomes = new BiomeManager(SEED);
        final Terrain terrain = Terrain.of(biomes);
        final ArmyLayer layer = new ArmyLayer(history.world(), terrain,
                () -> SeatMap.of(history.world(), biomes.getRoads(), biomes.getCastleSite(), SEED, biomes::isOpenLand),
                history.roster(), history::recordArmyDeed);

        void run(int turns) {
            for (int i = 0; i < turns; i++) {
                history.tickWarClock();
                layer.tick(history.warClock());
            }
        }
    }

    private static ArmyLayer.PlayerWatch standingAt(int x, int y) {
        return new ArmyLayer.PlayerWatch() {
            @Override
            public GridPoint2 surfaceChunk() {
                return new GridPoint2(x, y);
            }

            @Override
            public boolean countsPlayerAnEnemy(int houseId) {
                return false;
            }
        };
    }

    @Test
    public void theCrossroadsLieOnEachRoadJustPastItsFirstShelterAndNeverOnOne() {
        for (long seed : WarSimulation.DEFAULT_SEEDS) {
            BiomeManager biomes = new BiomeManager(seed);
            Terrain terrain = Terrain.of(biomes);
            List<GridPoint2> crossroads = terrain.crossroads();
            assertEquals(com.bpm.minotaur.generation.ShelterRoads.ROAD_COUNT, crossroads.size());
            for (int r = 0; r < crossroads.size(); r++) {
                GridPoint2 c = crossroads.get(r), first = biomes.getRoads().getRoad(r).getShelters().get(0);
                assertNull("no battle can be fought on a shelter's ground", biomes.getRoads().shelterAt(c, biomes::isOpenLand));
                assertTrue(biomes.isOpenLand(c));
                assertTrue("near its shelter", Math.max(Math.abs(c.x - first.x), Math.abs(c.y - first.y)) <= 8);
            }
        }
    }

    @Test
    public void lordsMarchToHoldThemWithTheWeightOnAndNeverWithItOff() {
        World on = new World(), off = new World();
        off.layer.crossroadsWeight(0f);
        boolean heldOn = false;
        for (int t = 0; t < 400; t++) {
            on.run(50);
            off.run(50);
            for (Army a : on.history.roster().armies) heldOn |= a.objective == Army.Objective.CROSSING;
            for (Army a : off.history.roster().armies) assertNotEquals(Army.Objective.CROSSING, a.objective);
        }
        assertTrue("some lord goes for a crossing", heldOn);
    }

    /** A52: the army layer never reads the player; where they stand changes nothing about the crossings. */
    @Test
    public void theWeightReadsNoPlayerState() {
        World far = new World(), near = new World();
        GridPoint2 crossing = near.terrain.crossroads().get(0);
        far.layer.watch(standingAt(55, 55));
        near.layer.watch(standingAt(crossing.x, crossing.y));
        int held = 0;
        for (int t = 0; t < 10000; t++) {
            far.run(1);
            near.run(1);
            List<Army> a = far.history.roster().armies, b = near.history.roster().armies;
            assertEquals("armies, turn " + t, a.size(), b.size());
            for (int i = 0; i < a.size(); i++) {
                assertEquals(a.get(i).objective, b.get(i).objective);
                assertEquals(a.get(i).targetX, b.get(i).targetX);
                assertEquals(a.get(i).targetY, b.get(i).targetY);
                assertEquals(a.get(i).x, b.get(i).x);
                assertEquals(a.get(i).y, b.get(i).y);
                if (a.get(i).objective == Army.Objective.CROSSING) held++;
            }
        }
        assertTrue("and the weight was in play", held > 0);
    }

    @Test
    public void aHostStandingOnACrossingLooksToAnother() {
        GridPoint2 crossing = new GridPoint2(10, 10), other = new GridPoint2(40, 10);
        assertNull("underfoot, it is no place to go", ArmyLayer.nearestCrossing(List.of(crossing), 10, 10));
        assertEquals(other, ArmyLayer.nearestCrossing(List.of(crossing, other), 10, 10));
        assertEquals(crossing, ArmyLayer.nearestCrossing(List.of(other, crossing), 11, 10));
    }
}
