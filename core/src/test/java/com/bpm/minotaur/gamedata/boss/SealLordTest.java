package com.bpm.minotaur.gamedata.boss;

import com.bpm.minotaur.gamedata.history.Doctrine;
import com.bpm.minotaur.gamedata.history.DoctrineCatalog;
import com.bpm.minotaur.gamedata.history.DoctrineCatalogTest;
import com.bpm.minotaur.gamedata.history.Figure;
import com.bpm.minotaur.gamedata.history.HistorySimulator;
import com.bpm.minotaur.gamedata.history.HistoryWorld;
import com.bpm.minotaur.gamedata.history.House;
import com.bpm.minotaur.gamedata.history.Trait;
import com.bpm.minotaur.generation.ShelterRoads;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.*;

/** Plan T1.12, D7, D30: the seal bosses are the lords who hold the gashes. */
public class SealLordTest {

    private static DoctrineCatalog catalog;

    @BeforeClass
    public static void load() throws IOException {
        catalog = DoctrineCatalogTest.loadCatalog();
    }

    @Test
    public void eachSealRoadLeadsToOneGash() {
        assertEquals(-1, SealLord.gashIndexForRoad(ShelterRoads.CASTLE_ROAD));
        Set<Integer> gashes = new HashSet<>();
        for (int road = 0; road < ShelterRoads.ROAD_COUNT; road++) {
            if (road != ShelterRoads.CASTLE_ROAD) gashes.add(SealLord.gashIndexForRoad(road));
        }
        assertEquals(new HashSet<>(java.util.Arrays.asList(0, 1, 2)), gashes);
    }

    @Test
    public void theBossIsTheLordWhoHoldsTheGash() {
        HistoryWorld w = HistorySimulator.prehistory(12L, catalog);
        for (int g = 0; g < HistoryWorld.GASH_COUNT; g++) {
            House holder = w.gashHolder(g);
            Figure lord = w.lordOf(holder);
            Doctrine d = catalog.get(holder.doctrineId);
            SealLord.Spec spec = SealLord.compose(w, g, catalog);
            assertEquals(lord.id, spec.figureId);
            assertEquals(holder.id, spec.houseId);
            assertEquals(d.bossBase, spec.monsterType);
            assertTrue(spec.name, spec.name.startsWith(lord.name));
            assertTrue(spec.name, spec.name.endsWith(holder.name));
            assertTrue("a deep boss, but a winnable one: " + spec.maxHp(), spec.maxHp() >= 120 && spec.maxHp() <= 320);
        }
    }

    @Test
    public void theRetinueIsTheHousesNamedSwornSwords() {
        HistoryWorld w = HistorySimulator.prehistory(13L, catalog);
        for (int g = 0; g < HistoryWorld.GASH_COUNT; g++) {
            House holder = w.gashHolder(g);
            Doctrine d = catalog.get(holder.doctrineId);
            SealLord.Spec spec = SealLord.compose(w, g, catalog);
            assertTrue(spec.retinue.size() <= SealLord.MAX_RETINUE);
            for (SealLord.Retainer r : spec.retinue) {
                Figure f = w.figure(r.figureId);
                assertEquals(Figure.Role.SWORN_SWORD, f.role);
                assertEquals(holder.id, f.houseId);
                assertTrue(f.isAlive());
                assertTrue(r.name.startsWith(f.name));
                assertTrue(d.roster.contains(r.monsterType));
            }
        }
    }

    @Test
    public void lordsWithDifferentTraitsFightDifferently() {
        SealLord.Spec wrathful = null, craven = null;
        for (long seed = 0; seed < 200 && (wrathful == null || craven == null); seed++) {
            HistoryWorld w = HistorySimulator.prehistory(seed, catalog);
            for (int g = 0; g < HistoryWorld.GASH_COUNT; g++) {
                Figure lord = w.lordOf(w.gashHolder(g));
                if (wrathful == null && lord.has(Trait.WRATHFUL) && !lord.has(Trait.CRAVEN)) wrathful = SealLord.compose(w, g, catalog);
                if (craven == null && lord.has(Trait.CRAVEN) && !lord.has(Trait.WRATHFUL)) craven = SealLord.compose(w, g, catalog);
            }
        }
        assertNotNull(wrathful);
        assertNotNull(craven);
        assertTrue(wrathful.behaviours.contains(SealLord.Behaviour.BERSERK_AT_HALF));
        assertFalse(wrathful.behaviours.contains(SealLord.Behaviour.CALLS_RETINUE));
        assertTrue(craven.behaviours.contains(SealLord.Behaviour.CALLS_RETINUE));
        assertFalse(craven.behaviours.contains(SealLord.Behaviour.BERSERK_AT_HALF));
        assertTrue("a wrathful lord hits harder", wrathful.damageBonus > craven.damageBonus);
    }

    @Test
    public void sameWorldSameLord() {
        HistoryWorld a = HistorySimulator.prehistory(14L, catalog);
        HistoryWorld b = HistorySimulator.prehistory(14L, catalog);
        SealLord.Spec sa = SealLord.compose(a, 1, catalog);
        SealLord.Spec sb = SealLord.compose(b, 1, catalog);
        assertEquals(sa.name, sb.name);
        assertEquals(sa.hpMult, sb.hpMult, 0f);
        assertEquals(sa.behaviours, sb.behaviours);
        assertEquals(sa.retinue.size(), sb.retinue.size());
    }

    @Test
    public void damageBonusRaisesTheDiceModifier() {
        assertEquals("2d10+9", SealLord.withDamageBonus("2d10+6", 3));
        assertEquals("1d8+2", SealLord.withDamageBonus("1d8", 2));
        assertEquals("3d6", SealLord.withDamageBonus("3d6", 0));
        assertEquals("1d4", SealLord.withDamageBonus(null, 0));
    }

    @Test
    public void whenAGashChangesHandsSoDoesItsSealBoss() {
        for (long seed = 0; seed < 60; seed++) {
            HistoryWorld w = HistorySimulator.prehistory(seed, catalog);
            SealLord.Spec[] before = new SealLord.Spec[HistoryWorld.GASH_COUNT];
            for (int g = 0; g < before.length; g++) before[g] = SealLord.compose(w, g, catalog);
            int from = w.events().size();
            for (int season = 0; season < 80; season++) {
                HistorySimulator.tickSeason(w, catalog);
                for (com.bpm.minotaur.gamedata.history.HistoryEvent e : w.events().subList(from, w.events().size())) {
                    if (e.type != com.bpm.minotaur.gamedata.history.EventType.SEAT_SEIZED || e.gashIndex < 0) continue;
                    SealLord.Spec after = SealLord.compose(w, e.gashIndex, catalog);
                    assertEquals(e.houseA, after.houseId);
                    assertNotEquals(before[e.gashIndex].houseId, after.houseId);
                    assertEquals("the castle still wants three seals", 3, com.bpm.minotaur.gamedata.blight.CastleGate.SEALS_REQUIRED);
                    return;
                }
                from = w.events().size();
                for (int g = 0; g < before.length; g++) before[g] = SealLord.compose(w, g, catalog);
            }
        }
        fail("no gash changed hands in 60 worlds of 20 years");
    }

    @Test
    public void aLordGrowsWithItsCourtAtHalfTheWildlifesRate() {
        SealLord.Spec s = new SealLord.Spec();
        assertEquals(SealLord.BASE_HP, s.maxHp(1));
        // Half the curve Monster.scaleStats gives the wildlife: +7.5% hit points a level.
        assertEquals(Math.round(SealLord.BASE_HP * (1f + 17 * 0.075f)), s.maxHp(18));
        assertTrue(s.armor(18) > s.armor(1));
        assertNotEquals(s.damageDice(1), s.damageDice(18));
    }
}
