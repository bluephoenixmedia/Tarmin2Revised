package com.bpm.minotaur.generation;

import com.bpm.minotaur.gamedata.history.Doctrine;
import com.bpm.minotaur.gamedata.history.DoctrineCatalog;
import com.bpm.minotaur.gamedata.history.DoctrineCatalogTest;
import com.bpm.minotaur.gamedata.history.HistorySimulator;
import com.bpm.minotaur.gamedata.history.HistoryWorld;
import com.bpm.minotaur.gamedata.history.House;
import com.bpm.minotaur.managers.SealCourt;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.*;

/** Plan T1.13, D43: the strata under a seal site take the holding house's doctrine. */
public class GashInteriorTest {

    private static DoctrineCatalog catalog;

    @BeforeClass
    public static void load() throws IOException {
        catalog = DoctrineCatalogTest.loadCatalog();
    }

    /** The first road that leads to a gash. */
    private static int sealRoad() {
        for (int road = 0; road < ShelterRoads.ROAD_COUNT; road++) {
            if (com.bpm.minotaur.gamedata.boss.SealLord.gashIndexForRoad(road) >= 0) return road;
        }
        throw new AssertionError();
    }

    @Test
    public void everyDoctrineHasAnInteriorOfItsOwn() {
        java.util.Set<Stratum> seen = new java.util.HashSet<>();
        for (Doctrine d : catalog.all()) {
            Stratum s = Stratum.interior(d.interiorTheme);
            assertNotNull(d.id + " has an interior for " + d.interiorTheme, s);
            assertTrue("one doctrine to an interior", seen.add(s));
            assertFalse("no town stands in a gash", s.allowsTowns);
            assertNotNull(s.fogColor);
            assertNotNull(s.glow);
            assertTrue(s.props.length > 0);
        }
    }

    @Test
    public void theStrataUnderASealSiteDownToTheCourtAreTheHoldersInterior() {
        HistoryWorld w = HistorySimulator.prehistory(41L, catalog);
        int road = sealRoad();
        House holder = w.gashHolder(com.bpm.minotaur.gamedata.boss.SealLord.gashIndexForRoad(road));
        Stratum expected = Stratum.interior(catalog.get(holder.doctrineId).interiorTheme);
        for (int level = 2; level <= SealCourt.COURT_LEVEL; level++) {
            assertEquals(expected, GashInterior.of(w, catalog, road, level));
        }
        assertNull("the surface is not the gash", GashInterior.of(w, catalog, road, 1));
        assertNull("nor the strata below the court", GashInterior.of(w, catalog, road, SealCourt.COURT_LEVEL + 1));
        assertNull("nor a road that leads to no gash", GashInterior.of(w, catalog, ShelterRoads.CASTLE_ROAD, 2));
    }

    @Test
    public void aNewHolderReThemesTheGash() {
        HistoryWorld w = HistorySimulator.prehistory(42L, catalog);
        int road = sealRoad();
        int gash = com.bpm.minotaur.gamedata.boss.SealLord.gashIndexForRoad(road);
        House before = w.gashHolder(gash);
        House after = null;
        for (House h : w.livingHouses()) {
            if (!h.isGreat() && !h.holdsCastle && !h.doctrineId.equals(before.doctrineId)) after = h;
        }
        assertNotNull(after);
        Stratum old = GashInterior.of(w, catalog, road, 2);
        before.gashIndex = -1;
        after.gashIndex = gash;
        Stratum fresh = GashInterior.of(w, catalog, road, 2);
        assertNotEquals("the next entry wears the new holder's theme", old, fresh);
        assertEquals(Stratum.interior(catalog.get(after.doctrineId).interiorTheme), fresh);
    }
}
