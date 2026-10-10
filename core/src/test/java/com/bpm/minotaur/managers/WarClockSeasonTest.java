package com.bpm.minotaur.managers;

import com.bpm.minotaur.gamedata.history.DoctrineCatalog;
import com.bpm.minotaur.gamedata.history.DoctrineCatalogTest;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.*;

/** Plan K1: the Maze's history moves between sleeps, so a house can break mid-expedition. */
public class WarClockSeasonTest {

    private static DoctrineCatalog catalog;

    @BeforeClass
    public static void load() throws IOException {
        catalog = DoctrineCatalogTest.loadCatalog();
    }

    @Test
    public void aSeasonPassesEverySoManyTurnsOfTheWarClock() {
        HistoryManager h = HistoryManager.create(61L, catalog);
        int seasons = h.world().liveSeasons();
        for (int i = 0; i < HistoryManager.SEASON_TURNS - 1; i++) h.tickWarClock();
        assertEquals("not yet", seasons, h.world().liveSeasons());
        h.tickWarClock();
        assertEquals(seasons + 1, h.world().liveSeasons());
    }

    @Test
    public void sleepStillPassesASeasonAndTheSaveReplaysThemAll() {
        HistoryManager h = HistoryManager.create(62L, catalog);
        for (int i = 0; i < HistoryManager.SEASON_TURNS * 3 + 7; i++) h.tickWarClock();
        h.onSleep();
        assertEquals(4, h.world().liveSeasons());
        HistoryManager loaded = HistoryManager.fromSave(0L, h.toSave(), catalog);
        assertEquals(h.world().fingerprint(), loaded.world().fingerprint());
        assertEquals(h.warClock(), loaded.warClock());
    }
}
