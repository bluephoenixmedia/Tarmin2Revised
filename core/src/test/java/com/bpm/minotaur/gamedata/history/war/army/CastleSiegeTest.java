package com.bpm.minotaur.gamedata.history.war.army;

import com.bpm.minotaur.gamedata.history.DoctrineCatalog;
import com.bpm.minotaur.gamedata.history.DoctrineCatalogTest;
import com.bpm.minotaur.gamedata.history.EventType;
import com.bpm.minotaur.gamedata.history.HistoryEvent;
import com.bpm.minotaur.gamedata.history.HistorySimulator;
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

/** Living War A35: a real host holds the breach open, and the castle never falls to a house. */
public class CastleSiegeTest {

    private static DoctrineCatalog catalog;

    @BeforeClass
    public static void load() throws IOException {
        catalog = DoctrineCatalogTest.loadCatalog();
    }

    private final long seed = 1000L;
    private final HistoryManager history = HistoryManager.create(seed, catalog);
    private final BiomeManager biomes = new BiomeManager(seed);
    private final ArmyLayer layer = new ArmyLayer(history.world(), Terrain.of(biomes), this::seats, history.roster(), history::recordArmyDeed);

    private SeatMap seats() {
        return SeatMap.of(history.world(), biomes.getRoads(), biomes.getCastleSite(), seed, biomes::isOpenLand);
    }

    private void run(int turns) {
        for (int i = 0; i < turns; i++) {
            history.tickWarClock();
            layer.tick(history.warClock());
        }
    }

    /** A house at war with the castle's, declared here if the world has none. */
    private House enemyOfTheCastle() {
        run(1);
        House tarmin = history.world().tarminHouse();
        for (War w : history.world().activeWars()) {
            if (w.involves(tarmin.id) && layerHasSeat(w.enemyOf(tarmin.id))) return history.world().house(w.enemyOf(tarmin.id));
        }
        for (House h : history.world().livingHouses()) {
            if (h.holdsCastle || !layerHasSeat(h.id)) continue;
            PlayerDeed d = new PlayerDeed(PlayerDeed.Kind.OPENING_WAR, h.id, history.world().liveSeasons());
            d.other = tarmin.id;
            history.recordArmyDeed(d);
            return h;
        }
        throw new AssertionError("no house to go to war");
    }

    private boolean layerHasSeat(int house) {
        return seats().seat(house) != null;
    }

    private long count(EventType type) {
        return history.world().events().stream().filter((HistoryEvent e) -> e.type == type).count();
    }

    private Army besiege() {
        House besieger = enemyOfTheCastle();
        Army host = layer.besiegeTheCastleNow(besieger, history.warClock());
        assertNotNull(host);
        return host;
    }

    @Test
    public void aHostInTheRingLaysTheSiegeAndTheBreachOpens() {
        long before = count(EventType.SIEGE_LAID);
        assertNull(history.world().activeSiege());
        Army host = besiege();
        assertEquals(before + 1, count(EventType.SIEGE_LAID));
        War w = history.world().activeSiege();
        assertNotNull("the breach opens", w);
        assertEquals(host.id, w.siegeHost);
        int term = w.siegeUntil - w.siegeSince;
        assertTrue("3-6 Seasons: " + term, term >= HistorySimulator.SIEGE_MIN_SEASONS && term <= HistorySimulator.SIEGE_MAX_SEASONS);
    }

    @Test
    public void aRoutedHostBreaksTheSiegeAndClosesTheBreach() {
        Army host = besiege();
        long before = count(EventType.SIEGE_BROKEN);
        host.objective = Army.Objective.RECOVER;
        run(1);
        assertEquals(before + 1, count(EventType.SIEGE_BROKEN));
        assertNull("the breach follows the host", history.world().activeSiege());
    }

    @Test
    public void aHostGoneFromTheRollBreaksTheSiege() {
        Army host = besiege();
        history.roster().armies.remove(host);
        run(1);
        assertNull(history.world().activeSiege());
    }

    @Test
    public void itIsLiftedWhenItsTermIsUp() {
        Army host = besiege();
        long before = count(EventType.SIEGE_LIFTED);
        War w = history.world().activeSiege();
        w.siegeUntil = history.world().season();
        run(1);
        assertEquals(before + 1, count(EventType.SIEGE_LIFTED));
        assertNull(history.world().activeSiege());
        assertFalse("free to go", host.beforeTheCastle);
    }

    /** A host at the end of its term does not simply lay the siege again. */
    @Test
    public void aSiegeLiftedIsNotLaidAgainAtOnce() {
        Army host = besiege();
        history.world().activeSiege().siegeUntil = history.world().season();
        long laid = count(EventType.SIEGE_LAID);
        run(ArmyTuning.IDLE_EVERY * 3);
        assertEquals("not again so soon", laid, count(EventType.SIEGE_LAID));
        assertFalse(host.beforeTheCastle);
    }

    @Test
    public void aNewWorldRollsNoSiegeAndKeepsThatInItsSave() {
        assertFalse(history.world().castleSiegesRoll());
        assertFalse(HistoryManager.fromSave(0L, history.toSave(), catalog).world().castleSiegesRoll());
    }

    @Test
    public void itIsLiftedWhenItsWarEnds() {
        besiege();
        long before = count(EventType.SIEGE_LIFTED);
        War w = history.world().activeSiege();
        w.endSeason = history.world().season();
        run(1);
        assertEquals(before + 1, count(EventType.SIEGE_LIFTED));
        assertEquals("nothing left over", -1, w.siegeHost);
    }

    /** Reconciling a finished war turns the host for home before the siege looks: still a lifting, not a breaking. */
    @Test
    public void aHostTurnedForHomeByItsWarsEndLiftsTheSiege() {
        Army host = besiege();
        long broken = count(EventType.SIEGE_BROKEN), lifted = count(EventType.SIEGE_LIFTED);
        War w = history.world().activeSiege();
        w.endSeason = history.world().season();
        host.objective = Army.Objective.DISBAND;
        run(1);
        assertEquals(broken, count(EventType.SIEGE_BROKEN));
        assertEquals(lifted + 1, count(EventType.SIEGE_LIFTED));
    }

    @Test
    public void aSallyIsARealBattleThatCanBreakTheSiegeAndNeverTakeTheCastle() {
        Army host = besiege();
        ArmyBattle b = layer.legionComesOutNow(host);
        assertTrue(host.battle);
        assertTrue(history.roster().battles.contains(b));
        // The host loses: the siege is broken.
        b.moraleA = -100;
        for (int i = 0; i < 5 && history.roster().battles.contains(b); i++) run(1);
        assertFalse(history.roster().battles.contains(b));
        run(1);
        assertNull(history.world().activeSiege());
        assertTrue(history.world().tarminHouse().holdsCastle);
    }

    @Test
    public void aSallyBeatenBackLeavesTheSiegeStanding() {
        Army host = besiege();
        ArmyBattle b = layer.legionComesOutNow(host);
        b.moraleB = -100;
        for (int i = 0; i < 5 && history.roster().battles.contains(b); i++) run(1);
        assertFalse(history.roster().battles.contains(b));
        run(1);
        assertNotNull("it holds", history.world().activeSiege());
        assertTrue(host.beforeTheCastle);
        assertTrue(history.world().tarminHouse().holdsCastle);
    }

    /** No path lets a house take the castle: not as a seat, not by a deed, not by a century of war. */
    @Test
    public void noPathLetsAHouseTakeTheCastle() {
        House tarmin = history.world().tarminHouse();
        House besieger = enemyOfTheCastle();
        assertNull("its seat cannot be invested", layer.investNow(besieger, tarmin, history.warClock()));
        // A deed claiming the fall of its seat takes nothing.
        PlayerDeed fell = new PlayerDeed(PlayerDeed.Kind.ARMY_BATTLE, besieger.id, history.world().liveSeasons());
        fell.other = tarmin.id;
        fell.atSeat = true;
        fell.seatFell = true;
        HistorySimulator.applyDeed(history.world(), fell);
        assertTrue(tarmin.holdsCastle);
        assertFalse(besieger.holdsCastle);
        for (int season = 0; season < 40; season++) {
            besieger.strength = Math.max(besieger.strength, 90f);
            history.onSleep();
            run(40);
            assertTrue(tarmin.holdsCastle);
            for (Army a : history.roster().armies) {
                if (a.houseId != tarmin.id && a.objective == Army.Objective.SEAT) {
                    assertFalse("nobody marches on the castle as a seat", seats().seat(tarmin.id) != null
                            && a.targetX == seats().seat(tarmin.id).x && a.targetY == seats().seat(tarmin.id).y);
                }
            }
            for (House h : history.world().houses()) {
                if (h.id != tarmin.id) assertFalse(h.name + " holds the castle", h.holdsCastle);
            }
        }
    }

    /** A world with armies lays no siege by the Season's roll. */
    @Test
    public void theSeasonRollsNoSiegeForAWorldWithArmies() {
        House besieger = enemyOfTheCastle();
        long before = count(EventType.SIEGE_LAID);
        for (int i = 0; i < 30; i++) {
            besieger.strength = 90f;
            history.onSleep();
        }
        assertEquals(before, count(EventType.SIEGE_LAID));
    }

    @Test
    public void aRealSiegeReplaysFromTheSave() {
        Army host = besiege();
        HistoryManager loaded = HistoryManager.fromSave(0L, history.toSave(), catalog);
        assertEquals(history.world().fingerprint(), loaded.world().fingerprint());
        assertNotNull(loaded.world().activeSiege());
        assertEquals(host.id, loaded.world().activeSiege().siegeHost);
    }

    /** Old SIEGE_* events from the Season's roll, in a world from before the armies, still play back the same. */
    @Test
    public void anOldSiegeFromTheRollStillReplays() {
        for (long s = 1; s <= 300; s++) {
            HistoryManager old = HistoryManager.create(s, catalog);
            // A save from develop before hosts laid them: armies on, the Season rolling, no mark in the file.
            old.world().castleSiegesRoll(true);
            for (int i = 0; i < 40 && old.world().activeSiege() == null; i++) old.onSleep();
            if (old.world().activeSiege() == null) continue;
            com.bpm.minotaur.gamedata.history.HistorySaveData save = old.toSave();
            save.castleSiegesByHosts = null;
            HistoryManager loaded = HistoryManager.fromSave(0L, save, catalog);
            assertEquals(old.world().fingerprint(), loaded.world().fingerprint());
            assertEquals(old.world().activeSiege().id, loaded.world().activeSiege().id);
            assertEquals("no host behind it", -1, loaded.world().activeSiege().siegeHost);
            return;
        }
        fail("no siege rolled in 300 worlds");
    }
}
