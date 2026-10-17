package com.bpm.minotaur.gamedata.monster;

import com.bpm.minotaur.gamedata.history.DoctrineCatalogTest;
import com.bpm.minotaur.gamedata.history.HistorySimulator;
import com.bpm.minotaur.gamedata.history.HistoryWorld;
import com.bpm.minotaur.gamedata.history.House;
import com.bpm.minotaur.gamedata.history.War;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.*;

/** Plan T1.5 / ADR 0004: Maze houses fight each other as the history says. */
public class HouseFactionTest {

    private static final Faction MAZE = Faction.MAZE_HOUSE;

    @Test
    public void housesAtWarInfightAndAlliedOrSwornHousesDoNot() throws IOException {
        boolean sawWar = false, sawFriends = false;
        for (long seed = 0; seed < 40 && !(sawWar && sawFriends); seed++) {
            HistoryWorld w = HistorySimulator.prehistory(seed, DoctrineCatalogTest.loadCatalog());
            FactionMatrix m = new FactionMatrix(seed);
            m.setHouseRelations(w.houseRelations());

            for (War war : w.activeWars()) {
                assertTrue(m.isHostile(MAZE, war.attackerId, MAZE, war.defenderId));
                sawWar = true;
            }
            for (House a : w.livingHouses()) {
                for (House b : w.livingHouses()) {
                    HistoryWorld.Stance s = w.stance(a.id, b.id);
                    if (a != b && (s == HistoryWorld.Stance.ALLIED || s == HistoryWorld.Stance.SWORN)) {
                        assertFalse(m.isHostile(MAZE, a.id, MAZE, b.id));
                        sawFriends = true;
                    }
                }
            }
        }
        assertTrue("found a war to test", sawWar);
        assertTrue("found an alliance or oath to test", sawFriends);
    }

    @Test
    public void theLegionIsTarminZulsHouse() throws IOException {
        for (long seed = 0; seed < 40; seed++) {
            HistoryWorld w = HistorySimulator.prehistory(seed, DoctrineCatalogTest.loadCatalog());
            FactionMatrix m = new FactionMatrix(seed);
            m.setHouseRelations(w.houseRelations());
            int tarmin = w.tarminHouse().id;
            for (House h : w.livingHouses()) {
                if (h.id == tarmin) continue;
                assertEquals(m.isHostile(MAZE, tarmin, MAZE, h.id),
                        m.isHostile(Faction.TARMIN_LEGION, -1, MAZE, h.id));
            }
        }
    }

    @Test
    public void aMazeHouseIsHostileToMortalsButNotToTheNeutral() {
        FactionMatrix m = new FactionMatrix(1L);
        m.setHouseRelations(new HouseRelations() {
            public int tarminHouseId() { return 9; }
            public FactionMatrix.Relation between(int a, int b) { return FactionMatrix.Relation.NEUTRAL; }
        });
        for (Faction f : new Faction[]{Faction.GOBLIN_CLANS, Faction.UNDEAD, Faction.BEASTS_AND_VERMIN,
                Faction.OUTCASTS_AND_HERMITS, Faction.CHAOS_BERSERK}) {
            assertTrue(f.name(), m.isHostile(MAZE, 2, f, -1));
            assertTrue(f.name(), m.isHostile(f, -1, MAZE, 2));
        }
        assertFalse(m.isHostile(MAZE, 2, Faction.NEUTRAL, -1));
        assertFalse("same house", m.isHostile(MAZE, 2, MAZE, 2));
    }

    @Test
    public void theLegionKeepsItsSeededMortalRelations() {
        FactionMatrix plain = new FactionMatrix(31L);
        FactionMatrix withHouses = new FactionMatrix(31L);
        withHouses.setHouseRelations(new HouseRelations() {
            public int tarminHouseId() { return 0; }
            public FactionMatrix.Relation between(int a, int b) { return FactionMatrix.Relation.HOSTILE; }
        });
        for (Faction f : Faction.values()) {
            if (f == MAZE) continue;
            assertEquals(f.name(), plain.getRelation(Faction.TARMIN_LEGION, f),
                    withHouses.getRelation(Faction.TARMIN_LEGION, -1, f, -1));
        }
    }

    @Test
    public void withoutAHistoryMazeHousesStillFightMortals() {
        FactionMatrix m = new FactionMatrix(4L);
        assertTrue(m.isHostile(MAZE, 1, Faction.UNDEAD, -1));
        assertFalse("two houses are wary, not at war, until a history says otherwise",
                m.isHostile(MAZE, 1, MAZE, 2));
    }
}
