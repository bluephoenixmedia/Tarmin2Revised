package com.bpm.minotaur.gamedata.history.war.army;

import com.bpm.minotaur.gamedata.history.DoctrineCatalog;
import com.bpm.minotaur.gamedata.history.DoctrineCatalogTest;
import com.bpm.minotaur.gamedata.history.HistorySaveData;
import com.bpm.minotaur.gamedata.history.war.Encounter;
import com.bpm.minotaur.gamedata.history.war.SeatMap;
import com.bpm.minotaur.managers.BiomeManager;
import com.bpm.minotaur.managers.HistoryManager;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;

/** Living War A1, A2, A15, A18: what the player can meet comes from the roster, and only from it. */
public class ArmyEncountersTest {

    private static DoctrineCatalog catalog;

    @BeforeClass
    public static void load() throws IOException {
        catalog = DoctrineCatalogTest.loadCatalog();
    }

    private static ArmyLayer layer(HistoryManager h, long seed) {
        BiomeManager b = new BiomeManager(seed);
        return new ArmyLayer(h.world(), Terrain.of(b), () -> SeatMap.of(h.world(), b.getRoads(), b.getCastleSite(), seed, b::isOpenLand),
                h.roster(), h::recordArmyDeed);
    }

    private static void run(HistoryManager h, ArmyLayer l, int turns) {
        for (int i = 0; i < turns; i++) {
            h.tickWarClock();
            l.tick(h.warClock());
        }
    }

    @Test
    public void everyEncounterStandsWhereItsArmyDoes() {
        HistoryManager h = HistoryManager.create(8919L, catalog);
        ArmyLayer l = layer(h, 8919L);
        Set<Encounter.Kind> kinds = EnumSet.noneOf(Encounter.Kind.class);
        for (int t = 0; t < 120; t++) {
            run(h, l, 100);
            for (Encounter e : l.encounters()) {
                kinds.add(e.kind);
                if (e.kind == Encounter.Kind.SKIRMISH) {
                    boolean found = false;
                    for (ArmyBattle b : h.roster().battles) if (b.id == e.slot && b.x == e.from.x && b.y == e.from.y) found = true;
                    assertTrue("a fight has its battle", found);
                } else {
                    Army a = h.roster().army((int) e.slot);
                    assertNotNull(a);
                    assertEquals(a.x, e.from.x);
                    assertEquals(a.y, e.from.y);
                    assertEquals(a.soldiers, e.soldiers);
                }
            }
            for (Encounter c : l.camps()) {
                Army a = h.roster().army((int) c.slot);
                assertNotNull(a);
                assertFalse(a.battle);
            }
        }
        assertTrue(kinds.toString(), kinds.contains(Encounter.Kind.COLUMN));
        assertTrue(kinds.toString(), kinds.contains(Encounter.Kind.SKIRMISH));
    }

    @Test
    public void aHostMarchesOnWhetherOrNotAnyoneWasThere() {
        HistoryManager h = HistoryManager.create(24757L, catalog);
        ArmyLayer l = layer(h, 24757L);
        run(h, l, 600);
        Encounter column = null;
        for (int t = 0; t < 400 && column == null; t++) {
            run(h, l, 25);
            for (Encounter e : l.encounters()) if (e.kind == Encounter.Kind.COLUMN && e.soldiers >= 10 && !e.from.equals(e.to)) column = e;
        }
        assertNotNull("a host on the march", column);
        Army a = h.roster().army((int) column.slot);
        int x = a.x, y = a.y;
        run(h, l, Terrain.ROUGH_TURNS + 1);
        assertTrue("it has moved on, or stopped", a.x != x || a.y != y || h.roster().army(a.id) == null || a.battle
                || a.marchStep >= a.path.length);
    }

    @Test
    public void heraldsCarryNewsAndFightNoOne() {
        HistoryManager h = HistoryManager.create(1000L, catalog);
        ArmyLayer l = layer(h, 1000L);
        boolean herald = false;
        for (int t = 0; t < 40 && !herald; t++) {
            run(h, l, HistoryManager.SEASON_TURNS / 2);
            for (Army a : h.roster().armies) {
                if (a.kind == Army.Kind.HERALD) {
                    herald = true;
                    assertFalse("a herald fights no one", a.battle);
                    assertEquals(1, a.soldiers);
                }
            }
        }
        assertTrue("news sends a herald in 20 Seasons", herald);
    }

    @Test
    public void aSaveFromBeforeArmiesLoadsAndMusters() {
        HistoryManager h = HistoryManager.create(40595L, catalog);
        HistorySaveData save = h.toSave();
        save.roster = null;
        HistoryManager back = HistoryManager.fromSave(0L, save, catalog);
        assertTrue(back.roster().armies.isEmpty());
        ArmyLayer l = layer(back, 40595L);
        run(back, l, 1);
        assertFalse(back.world().activeWars().isEmpty());
        assertFalse("it musters fresh", back.roster().armies.isEmpty());
        List<Encounter> met = l.encounters();
        assertNotNull(met);
    }
}
