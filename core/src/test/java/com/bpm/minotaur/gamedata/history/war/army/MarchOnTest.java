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

    // ---- waiting in the siege (A42) ----

    private Army besieger(SeatSiege s) {
        Army host = army(1, 1, 5, 5);
        host.path = null;
        host.sieging = s.id;
        return host;
    }

    private SeatSiege siege() {
        SeatSiege s = new SeatSiege();
        s.id = 40;
        s.x = 5;
        s.y = 5;
        s.holder = 2;
        s.besieger = 1;
        roster.sieges.add(s);
        return s;
    }

    private ArmyBattle battleOf(Army host) {
        ArmyBattle b = new ArmyBattle();
        b.armiesA = new int[] {host.id};
        roster.battles.add(b);
        host.battle = true;
        return b;
    }

    @Test
    public void aWaitGoesOnWhileNothingComes() {
        MarchOn m = MarchOn.waitingInTheSiege(besieger(siege()), roster);
        assertEquals(MarchOn.Stop.NONE, m.after(roster));
        assertEquals(MarchOn.Stop.NONE, m.after(roster));
    }

    @Test
    public void itWakesForTheAssault() {
        Army host = besieger(siege());
        MarchOn m = MarchOn.waitingInTheSiege(host, roster);
        battleOf(host).assault = true;
        assertEquals(MarchOn.Stop.ASSAULT, m.after(roster));
    }

    @Test
    public void itWakesForASally() {
        Army host = besieger(siege());
        MarchOn m = MarchOn.waitingInTheSiege(host, roster);
        battleOf(host).sally = true;
        assertEquals(MarchOn.Stop.SALLY, m.after(roster));
    }

    @Test
    public void itWakesForAReliefComingToTheWallsAndForItsBattle() {
        Army host = besieger(siege());
        MarchOn m = MarchOn.waitingInTheSiege(host, roster);
        army(2, 2, 5, 5);
        assertEquals(MarchOn.Stop.RELIEF, m.after(roster));
        assertEquals("once", MarchOn.Stop.NONE, m.after(roster));
        battleOf(host);
        assertEquals(MarchOn.Stop.RELIEF, m.after(roster));
    }

    @Test
    public void aSupplyColumnOnTheRoadWakesNoOne() {
        MarchOn m = MarchOn.waitingInTheSiege(besieger(siege()), roster);
        army(2, 2, 5, 5).kind = Army.Kind.SUPPLY;
        assertEquals(MarchOn.Stop.NONE, m.after(roster));
    }

    @Test
    public void itWakesWhenTheSiegeIsOverOrTheHostIsGone() {
        Army host = besieger(siege());
        MarchOn m = MarchOn.waitingInTheSiege(host, roster);
        host.sieging = -1;
        assertEquals(MarchOn.Stop.RAISED, m.after(roster));
        roster.armies.remove(host);
        assertEquals(MarchOn.Stop.GONE, m.after(roster));
    }

    @Test
    public void itWakesOnceWhenTheLaddersAreUp() {
        SeatSiege s = siege();
        MarchOn m = MarchOn.waitingInTheSiege(besieger(s), roster);
        s.works = SeatSiege.LADDER_TURNS;
        assertEquals(MarchOn.Stop.LADDERS, m.after(roster));
        assertEquals(MarchOn.Stop.NONE, m.after(roster));
        assertEquals("ladders already up are no news", MarchOn.Stop.NONE, MarchOn.waitingInTheSiege(roster.army(1), roster).after(roster));
    }

    @Test
    public void ladderAndAssaultOnOneTurnWakeThemForTheAssault() {
        SeatSiege s = siege();
        Army host = besieger(s);
        MarchOn m = MarchOn.waitingInTheSiege(host, roster);
        s.works = SeatSiege.LADDER_TURNS;
        battleOf(host).assault = true;
        assertEquals(MarchOn.Stop.ASSAULT, m.after(roster));
    }

    /** A42: only the captain's temper and the numbers say when; waiting moves it by no turn. */
    @Test
    public void theAssaultComesOnTheSameTurnWhetherThePlayerWaitsOrNot() {
        long seed = 1000L;
        HistoryManager alone = HistoryManager.create(seed, catalog), waiting = HistoryManager.create(seed, catalog);
        ArmyLayer la = layer(alone, seed), lw = layer(waiting, seed);
        run(alone, la, 1);
        run(waiting, lw, 1);
        SeatSiege sa = null, sw = null;
        for (War w : waiting.world().activeWars()) {
            com.bpm.minotaur.gamedata.history.House a = waiting.world().house(w.attackerId), b = waiting.world().house(w.defenderId);
            if (a == null || b == null || a.holdsCastle || b.holdsCastle) continue;
            sw = lw.investNow(a, b, waiting.warClock());
            if (sw == null) continue;
            sa = la.investNow(alone.world().house(a.id), alone.world().house(b.id), alone.warClock());
            break;
        }
        assertNotNull("a siege to wait in", sw);
        assertNotNull(sa);
        // Ladders finished on a turn the captain thinks would go in on that turn, with no turn between to ask.
        int toLadders = (waiting.warClock() + 40 + sw.id) % SeatSiege.THINK_EVERY == 0 ? 41 : 40;
        sa.works = sw.works = SeatSiege.LADDER_TURNS - toLadders;
        Army host = waiting.roster().army(sw.armies[0]), twin = alone.roster().army(sa.armies[0]);
        host.soldiers = twin.soldiers = 5000;
        MarchOn wait = MarchOn.waitingInTheSiege(host, waiting.roster());
        List<MarchOn.Stop> woke = new ArrayList<>();
        long wokeAt = -1;
        for (int slice = 0; slice < 40 && wokeAt < 0; slice++) {
            // Turn by turn beside the one left alone: the wait wakes as often as it likes and changes nothing.
            for (int t = 0; t < MarchOn.SLICE && wokeAt < 0; t++) {
                waiting.marchUntil(1, clock -> {
                    lw.tick(clock);
                    return wait.after(waiting.roster()) != MarchOn.Stop.NONE;
                });
                run(alone, la, 1);
                assertEquals("works, clock " + waiting.warClock(), sa.works, sw.works);
                assertEquals("a battle or none, clock " + waiting.warClock(), twin.battle, host.battle);
                if (!wait.last().goesOn()) woke.add(wait.last());
                if (wait.last() == MarchOn.Stop.ASSAULT) wokeAt = waiting.warClock();
            }
        }
        assertEquals("the ladders woke them, then the assault", List.of(MarchOn.Stop.LADDERS, MarchOn.Stop.ASSAULT), woke);
        assertTrue(twin.battle);
        assertEquals(alone.warClock(), wokeAt);
    }

    /** A36: a Soldier near enough stops the march on coming into a chunk; far off, or Fodder, or a house's, it does not. */
    @Test
    public void itStopsForANativeSoldierNearby() {
        com.bpm.minotaur.gamedata.monster.Monster soldier =
                new com.bpm.minotaur.gamedata.monster.Monster(com.bpm.minotaur.gamedata.monster.Monster.MonsterType.values()[0], 20, 12);
        soldier.getPosition().set(10, 10);
        List<com.bpm.minotaur.gamedata.monster.Monster> here = List.of(soldier);
        assertTrue(MarchOn.nativesBar(here, m -> -1, 12, 10));
        assertFalse("too far", MarchOn.nativesBar(here, m -> -1, 30, 10));
        assertFalse("a house's soldier is the armies' business", MarchOn.nativesBar(here, m -> 3, 12, 10));
    }

    @Test
    public void onlyNativeFodderLeavesTheColumnAlone() {
        assertTrue(MarchOn.leavesTheColumnAlone(com.bpm.minotaur.gamedata.monster.MonsterRole.FODDER, false, -1));
        assertFalse(MarchOn.leavesTheColumnAlone(com.bpm.minotaur.gamedata.monster.MonsterRole.SOLDIER, false, -1));
        assertFalse(MarchOn.leavesTheColumnAlone(com.bpm.minotaur.gamedata.monster.MonsterRole.HEAVY, false, -1));
        assertFalse("a house's", MarchOn.leavesTheColumnAlone(com.bpm.minotaur.gamedata.monster.MonsterRole.FODDER, false, 2));
        assertFalse("in a war band", MarchOn.leavesTheColumnAlone(com.bpm.minotaur.gamedata.monster.MonsterRole.FODDER, true, -1));
    }

    @Test
    public void aSleeperInAHostsCampHasAlarmAndShieldForNothing() {
        com.bpm.minotaur.gamedata.shelter.NightsWards w = com.bpm.minotaur.gamedata.shelter.NightsWards.ofAHostsCamp();
        assertTrue(w.has(com.bpm.minotaur.gamedata.shelter.Ward.ALARM));
        assertTrue(w.has(com.bpm.minotaur.gamedata.shelter.Ward.SHIELD));
        assertTrue(w.kept());
        assertTrue(w.keptOffBySentries(com.bpm.minotaur.gamedata.monster.MonsterRole.FODDER));
        assertFalse(w.keptOffBySentries(com.bpm.minotaur.gamedata.monster.MonsterRole.HEAVY));
        assertFalse("not one's own wards", new com.bpm.minotaur.gamedata.shelter.NightsWards().keptOffBySentries(com.bpm.minotaur.gamedata.monster.MonsterRole.FODDER));
    }

    /** A42: with the relief is marching with a host at that seat, or bound for it. */
    @Test
    public void aFollowedHostIsTheReliefIfAtTheSeatOrBoundForIt() {
        Army host = army(1, 1, 5, 5);
        assertTrue("at it", MarchOn.withTheRelief(host, 5, 5));
        assertFalse("elsewhere, bound elsewhere", MarchOn.withTheRelief(host, 9, 9));
        host.targetX = 9;
        host.targetY = 9;
        assertTrue("bound for it", MarchOn.withTheRelief(host, 9, 9));
        assertFalse("following no one", MarchOn.withTheRelief(null, 5, 5));
        host.objective = Army.Objective.RECOVER;
        assertFalse("beaten and limping there is no relief", MarchOn.withTheRelief(host, 9, 9));
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
        ArmyBattle b = new ArmyBattle();
        b.armiesB = new int[] {host.id};
        b.armiesA = new int[0];
        b.playerSide = 2;
        h.roster().battles.add(b);
        host.battle = true;
        l.hostTurnsOnThePlayer(host.id);
        assertEquals("in its line, they are no more its ally", 0, b.playerSide);
        assertEquals(2, b.turnedOnPlayer);
    }
}
