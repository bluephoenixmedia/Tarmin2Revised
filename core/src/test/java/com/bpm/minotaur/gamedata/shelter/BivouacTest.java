package com.bpm.minotaur.gamedata.shelter;

import com.bpm.minotaur.gamedata.history.DoctrineCatalog;
import com.bpm.minotaur.gamedata.history.DoctrineCatalogTest;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.managers.HistoryManager;
import org.junit.Test;

import java.io.IOException;
import java.util.Random;

import static org.junit.Assert.*;

/** Living War A60-A63: a night in the field, by the numbers. */
public class BivouacTest {

    private static final float EPS = 0.0001f;

    @Test
    public void ambushOddsFollowTheFormula() {
        assertEquals("15% + 5 EDL + 2 stages", 0.26f, Bivouac.ambushChance(5, 2, false, false), EPS);
        assertEquals("underground", 0.39f, Bivouac.ambushChance(5, 2, true, false), EPS);
        assertEquals("a lit fire", 0.39f, Bivouac.ambushChance(5, 2, false, true), EPS);
        assertEquals("both", 0.585f, Bivouac.ambushChance(5, 2, true, true), EPS);
    }

    @Test
    public void ambushOddsAreCapped() {
        assertEquals(Bivouac.AMBUSH_CAP, Bivouac.ambushChance(60, 4, true, true), EPS);
        assertEquals(0.75f, Bivouac.AMBUSH_CAP, EPS);
    }

    @Test
    public void anAmbushIsOneToThreeAtSomeTurnOfTheSleep() {
        Random rng = new Random(7);
        boolean[] seen = new boolean[4];
        for (int i = 0; i < 500; i++) {
            Bivouac.Night n = Bivouac.roll(rng, 1f, 250);
            assertFalse(n.quiet());
            assertTrue(n.ambushTurn >= 1 && n.ambushTurn <= 250);
            assertTrue(n.ambushers >= 1 && n.ambushers <= 3);
            seen[n.ambushers] = true;
        }
        assertTrue(seen[1] && seen[2] && seen[3]);
        assertTrue(Bivouac.roll(rng, 0f, 250).quiet());
    }

    @Test
    public void aWholeNightGivesHalfAndACutShortOneLess() {
        assertEquals(50, Bivouac.rest(100, 250, 250));
        assertEquals(25, Bivouac.rest(100, 125, 250));
        assertEquals(0, Bivouac.rest(100, 0, 250));
        assertEquals("never more than a night's worth", 50, Bivouac.rest(100, 900, 250));
    }

    @Test
    public void aNightCountsAQuarterTowardLingering() {
        assertEquals(62, Bivouac.linger(HistoryManager.SLEEP_TURNS));
        assertEquals(25, Bivouac.linger(100));
    }

    @Test
    public void aFireDrawsASecondFreeRound() {
        assertEquals(1, Bivouac.freeRounds(false));
        assertEquals(2, Bivouac.freeRounds(true));
    }

    @Test
    public void aKitLastsTenNightsAndMendsByThreeUpToNew() {
        Item kit = new Item(Item.ItemType.BIVOUAC_KIT, 0, 0, com.bpm.minotaur.gamedata.item.ItemColor.TAN, null, null);
        assertEquals(10, Bivouac.nightsLeft(kit));
        assertFalse("a new kit wants no mending", Bivouac.canMend(kit));
        for (int i = 0; i < 9; i++) assertFalse(Bivouac.wear(kit));
        assertEquals(1, Bivouac.nightsLeft(kit));
        Bivouac.mend(kit);
        assertEquals(4, Bivouac.nightsLeft(kit));
        kit.setCurrentCharges(9);
        Bivouac.mend(kit);
        assertEquals(10, Bivouac.nightsLeft(kit));
        kit.setCurrentCharges(1);
        assertTrue("the last night wears it through", Bivouac.wear(kit));
    }

    /** A61: the same war-clock jump and Season tick as a Shelter sleep. */
    @Test
    public void aBivouacSleepAdvancesTheWorldExactlyAsAShelterSleepDoes() throws IOException {
        DoctrineCatalog catalog = DoctrineCatalogTest.loadCatalog();
        HistoryManager shelter = HistoryManager.create(4242L, catalog);
        HistoryManager field = HistoryManager.create(4242L, catalog);
        for (int night = 0; night < 3; night++) {
            shelter.onSleep();
            shelter.sleepThroughWar(c -> { });
            field.onSleep();
            assertEquals(HistoryManager.SLEEP_TURNS, field.sleepUntil(HistoryManager.SLEEP_TURNS, c -> false));
        }
        assertEquals(shelter.warClock(), field.warClock());
        assertEquals(shelter.world().fingerprint(), field.world().fingerprint());
        assertEquals(shelter.world().liveSeasons(), field.world().liveSeasons());
    }

    @Test
    public void aThreatStopsTheJumpAtItsTurn() throws IOException {
        HistoryManager h = HistoryManager.create(4243L, DoctrineCatalogTest.loadCatalog());
        long from = h.warClock();
        assertEquals(40, h.sleepUntil(HistoryManager.SLEEP_TURNS, c -> c == from + 40));
        assertEquals(from + 40, h.warClock());
    }
}
