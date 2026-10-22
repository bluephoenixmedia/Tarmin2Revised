package com.bpm.minotaur.gamedata.history;

import com.bpm.minotaur.gamedata.history.town.Settlement;
import com.bpm.minotaur.ui.UiGlyphs;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import static org.junit.Assert.*;

/** "World Log: the soul's name and each life's prisoner" (#279): who the Champion wakes in. */
public class LifeForgeTest {

    private static HistoryWorld world;

    @BeforeClass
    public static void load() throws IOException {
        world = HistorySimulator.prehistory(21L, DoctrineCatalogTest.loadCatalog());
    }

    @Test
    public void theSameSeedWakesTheSamePrisoner() {
        assertEquals(LifeForge.forge(world, 99L).summary(), LifeForge.forge(world, 99L).summary());
        Set<String> seen = new HashSet<>();
        for (long seed = 0; seed < 20; seed++) seen.add(LifeForge.forge(world, seed).summary());
        assertTrue("twenty seeds should not all wake the same prisoner", seen.size() > 10);
    }

    @Test
    public void aPrisonerComesFromARealSettlementOfTheHistory() {
        for (long seed = 0; seed < 50; seed++) {
            Life life = LifeForge.forge(world, seed);
            Settlement home = world.settlement(life.settlementId);
            assertNotNull(home);
            assertEquals(home.name, life.settlement);
            assertEquals(home.allegiance, life.allegiance);
        }
    }

    @Test
    public void theRaidedWereTakenByTheAntleredHostAndTheTithedSentForACrime() {
        boolean raided = false;
        boolean tithed = false;
        for (long seed = 0; seed < 200; seed++) {
            Life life = LifeForge.forge(world, seed);
            if (life.origin == Life.Origin.RAIDED) {
                raided = true;
                assertTrue(life.raider, life.raider.contains("the Antlered Host"));
                assertNull(life.crime);
            } else {
                tithed = true;
                assertNotNull(life.crime);
                assertFalse(life.crime.isEmpty());
                assertEquals(life.allegiance.displayName, life.kingdom);
                assertNull(life.raider);
            }
        }
        assertTrue(raided);
        assertTrue(tithed);
    }

    @Test
    public void everyPrisonerCanBeWrittenInTheGameFont() {
        for (long seed = 0; seed < 100; seed++) {
            String s = LifeForge.forge(world, seed).summary();
            assertEquals(s, UiGlyphs.sanitize(s));
        }
    }

    @Test
    public void theSoulsNameGeneratorGivesVariedNamesTheFontCanWrite() {
        Random rng = new Random(5L);
        Set<String> names = new HashSet<>();
        for (int i = 0; i < 50; i++) {
            String name = LifeForge.soulName(rng);
            assertFalse(name.trim().isEmpty());
            assertEquals(name, UiGlyphs.sanitize(name));
            names.add(name);
        }
        assertTrue(names.size() >= 25);
    }
}
