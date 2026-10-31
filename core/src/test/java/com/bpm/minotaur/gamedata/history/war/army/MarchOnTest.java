package com.bpm.minotaur.gamedata.history.war.army;

import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.gamedata.history.DoctrineCatalog;
import com.bpm.minotaur.gamedata.history.DoctrineCatalogTest;
import com.bpm.minotaur.gamedata.history.War;
import com.bpm.minotaur.gamedata.history.war.SeatMap;
import com.bpm.minotaur.managers.BiomeManager;
import com.bpm.minotaur.managers.HistoryManager;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/** Living War A36: following a host changes nothing about it, and marching on stops when it meets anything. */
public class MarchOnTest {

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

    private static Army marchingHost(HistoryManager h) {
        for (Army a : h.roster().armies) if (a.isHost() && !a.battle && !a.halted() && a.levyOf < 0) return a;
        return null;
    }

    /** A36: a followed host goes where the same seed sends it unfollowed, up to the first fight the player is in. */
    @Test
    public void aFollowedHostGoesWhereItWouldHaveGone() {
        long seed = 8919L;
        HistoryManager alone = HistoryManager.create(seed, catalog), followed = HistoryManager.create(seed, catalog);
        ArmyLayer la = layer(alone, seed), lf = layer(followed, seed);
        run(alone, la, 400);
        run(followed, lf, 400);
        Army host = marchingHost(followed);
        for (int t = 0; host == null && t < 200; t++) {
            run(alone, la, 25);
            run(followed, lf, 25);
            host = marchingHost(followed);
        }
        assertNotNull("a host on the march", host);
        Army twin = alone.roster().army(host.id);
        assertNotNull(twin);
        // The player stands with it throughout, and its side counts them a friend.
        Army followedHost = host;
        lf.watch(new ArmyLayer.PlayerWatch() {
            @Override
            public GridPoint2 surfaceChunk() {
                return new GridPoint2(followedHost.x, followedHost.y);
            }

            @Override
            public boolean countsPlayerAnEnemy(int houseId) {
                return false;
            }
        });
        MarchOn march = new MarchOn(host, followed.roster());
        int turns = 0;
        for (; turns < 1500 && !host.battle && followed.roster().armies.contains(host); turns++) {
            followed.marchUntil(1, clock -> {
                lf.tick(clock);
                return march.after(followed.roster()) != MarchOn.Stop.NONE;
            });
            run(alone, la, 1);
            assertEquals("x, turn " + turns, twin.x, host.x);
            assertEquals("y, turn " + turns, twin.y, host.y);
            assertEquals("objective, turn " + turns, twin.objective, host.objective);
            assertEquals("target, turn " + turns, twin.targetX, host.targetX);
        }
        assertTrue(turns > 20);
    }

    // ---- what stops it ----

    private final WarRoster roster = new WarRoster();

    private Army army(int id, int house, int x, int y) {
        Army a = new Army();
        a.id = id;
        a.houseId = house;
        a.x = x;
        a.y = y;
        a.soldiers = 15;
        a.path = new int[] {1, 2, 3};
        roster.armies.add(a);
        return a;
    }

    @Test
    public void quietMarchingGoesOn() {
        Army host = army(1, 1, 5, 5);
        MarchOn m = new MarchOn(host, roster);
        assertEquals(MarchOn.Stop.NONE, m.after(roster));
        assertEquals(MarchOn.Stop.NONE, m.after(roster));
    }

    @Test
    public void aNewChunkIsTheEndOfASliceAndNoStop() {
        Army host = army(1, 1, 5, 5);
        MarchOn m = new MarchOn(host, roster);
        host.x = 6;
        assertEquals(MarchOn.Stop.MOVED, m.after(roster));
        assertEquals(MarchOn.Stop.NONE, m.after(roster));
    }

    @Test
    public void itStopsForABattleAndABeastIsABattle() {
        Army host = army(1, 1, 5, 5);
        MarchOn m = new MarchOn(host, roster);
        host.battle = true;
        assertEquals(MarchOn.Stop.BATTLE, m.after(roster));
    }

    @Test
    public void itStopsWhenTheHostIsGone() {
        Army host = army(1, 1, 5, 5);
        MarchOn m = new MarchOn(host, roster);
        roster.armies.remove(host);
        assertEquals(MarchOn.Stop.GONE, m.after(roster));
    }

    @Test
    public void itStopsWhenTheHostSitsDownToASiege() {
        Army host = army(1, 1, 5, 5);
        MarchOn m = new MarchOn(host, roster);
        host.sieging = 3;
        assertEquals(MarchOn.Stop.SIEGE, m.after(roster));
    }

    @Test
    public void itStopsWhenTheHostHalts() {
        Army host = army(1, 1, 5, 5);
        MarchOn m = new MarchOn(host, roster);
        host.path = null;
        assertEquals(MarchOn.Stop.HALTED, m.after(roster));
    }

    @Test
    public void itStopsForAnyArmyOfAnotherSideOnce() {
        Army host = army(1, 1, 5, 5);
        MarchOn m = new MarchOn(host, roster);
        army(2, 2, 5, 5);
        assertEquals(MarchOn.Stop.ARMY, m.after(roster));
        assertEquals("marching on again passes it by", MarchOn.Stop.NONE, m.after(roster));
    }

    @Test
    public void anArmyAlreadyThereIsNoMeetingNorIsItsOwnSide() {
        army(2, 2, 5, 5);
        Army host = army(1, 1, 5, 5);
        MarchOn m = new MarchOn(host, roster);
        army(3, 1, 5, 5);
        Army levy = army(4, 9, 5, 5);
        levy.levyOf = 1;
        assertEquals(MarchOn.Stop.NONE, m.after(roster));
    }

    @Test
    public void thePlayerHasLeftOnceAChunkBehindOrBelow() {
        Army host = army(1, 1, 5, 5);
        assertTrue(MarchOn.stillWith(host, new GridPoint2(6, 4), 1));
        assertFalse(MarchOn.stillWith(host, new GridPoint2(7, 5), 1));
        assertFalse(MarchOn.stillWith(host, new GridPoint2(5, 5), 2));
    }

    @Test
    public void aFollowedHostTurnsOnThePlayerWhoBreaksTheOath() {
        long seed = 8919L;
        HistoryManager h = HistoryManager.create(seed, catalog);
        ArmyLayer l = layer(h, seed);
        run(h, l, 1);
        War w = h.world().activeWars().get(0);
        Army host = new Army();
        host.id = 7001;
        host.houseId = w.attackerId;
        host.soldiers = 30;
        host.x = 5;
        host.y = 5;
        host.marchFromX = 5;
        host.marchFromY = 5;
        h.roster().armies.add(host);
        List<int[]> asked = new ArrayList<>();
        h.onOathBroken((house, followed) -> asked.add(new int[] {house, followed}));
        h.favour().set(w.attackerId, 70);
        h.swear(w.attackerId);
        h.oath().following = host.id;
        h.breakOath();
        assertEquals(1, asked.size());
        assertEquals(w.attackerId, asked.get(0)[0]);
        assertEquals(host.id, asked.get(0)[1]);
        assertEquals("the oath is gone, and the following with it", -1, h.oath().following);
        l.watch(new ArmyLayer.PlayerWatch() {
            @Override
            public GridPoint2 surfaceChunk() {
                return new GridPoint2(6, 5);
            }

            @Override
            public boolean countsPlayerAnEnemy(int houseId) {
                return true;
            }
        });
        l.hostTurnsOnThePlayer(host.id);
        assertEquals(6, host.targetX);
        assertEquals(5, host.targetY);
    }
}
