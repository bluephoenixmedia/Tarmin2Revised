package com.bpm.minotaur.gamedata.history.war.army;

import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.gamedata.history.DoctrineCatalog;
import com.bpm.minotaur.gamedata.history.War;
import com.bpm.minotaur.gamedata.history.war.SeatMap;
import com.bpm.minotaur.generation.ShelterRoads;
import com.bpm.minotaur.managers.BiomeManager;
import com.bpm.minotaur.managers.HistoryManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The headless war (Living War A59): runs real worlds with a player walking the roads, whom no
 * army reads, and reports the budget in A58 and the pacing in A8. Time is reported here and never
 * asserted in the test suite, since timings on the development machine aren't steady enough.
 */
public final class WarSimulation {

    public static final long[] DEFAULT_SEEDS = {1000L, 8919L, 24757L, 40595L, 135623L, 151461L};

    private static final double BUDGET_TURN_MEAN_MS = 0.25, BUDGET_TURN_P99_MS = 2, BUDGET_JUMP_MS = 150, BUDGET_ALLOC_KB = 1;
    /** War turns the walker takes to cross a chunk, and the overland turns of an expedition before it sleeps. */
    private static final int WALK_TURNS = 45, EXPEDITION_TURNS = 1500;
    /** Soldiers in all, from which a fight is a pitched battle. */
    private static final int PITCHED_FROM = 30;

    private WarSimulation() {
    }

    /** A player who walks the roads out and back, sleeps, and takes the next road. */
    private static final class Walker {
        final ShelterRoads roads;
        int road, step, x, y, toX, toY, prog, sinceSleep;
        boolean homeward;
        long overland, encounters, pitched, expeditions, earshot;
        final Set<Integer> inView = new HashSet<>();
        final Set<String> battlesSeen = new HashSet<>();

        Walker(ShelterRoads roads) {
            this.roads = roads;
            pick();
        }

        private void pick() {
            GridPoint2 end = roads.getRoad(road++ % ShelterRoads.ROAD_COUNT).getEnd();
            toX = end.x;
            toY = end.y;
            homeward = false;
        }

        void walk() {
            overland++;
            sinceSleep++;
            if (++prog < WALK_TURNS) return;
            prog = 0;
            x += Integer.signum(toX - x);
            y += Integer.signum(toY - y);
            if (x == toX && y == toY) {
                if (homeward) {
                    pick();
                } else {
                    toX = 0;
                    toY = 0;
                    homeward = true;
                }
            }
        }

        void observe(WarRoster r) {
            Set<Integer> now = new HashSet<>();
            for (Army a : r.armies) {
                if (Math.max(Math.abs(a.x - x), Math.abs(a.y - y)) > 1) continue;
                now.add(a.id);
                if (!inView.contains(a.id)) encounters++;
            }
            inView.clear();
            inView.addAll(now);
            boolean heard = false;
            for (ArmyBattle b : r.battles) {
                int d = Math.max(Math.abs(b.x - x), Math.abs(b.y - y));
                if (d <= ArmyTuning.SIGHT) heard = true;
                if (d <= 1 && b.startA + b.startB >= PITCHED_FROM && battlesSeen.add(b.x + "," + b.y + "," + b.houseA + "," + b.houseB)) pitched++;
            }
            if (heard) earshot++;
        }
    }

    /** How near the Shelter is near, as the Table counts it: three days' march. */
    private static final int NEAR_THE_SHELTER = com.bpm.minotaur.gamedata.history.war.Dispatches.NEAR_DAYS * com.bpm.minotaur.gamedata.history.war.Dispatches.CHUNKS_PER_DAY;
    /** Chunks from a crossroads that count as being at it: a battle in earshot of someone standing there. */
    private static final int AT_A_CROSSROADS = 3;

    /** @return the report, as lines */
    public static List<String> run(DoctrineCatalog catalog, long[] seeds, int seasons) {
        return run(catalog, seeds, seasons, ArmyTuning.CROSSROADS_WEIGHT);
    }

    /** @param crossroadsWeight A52's weight, to compare it on and off */
    public static List<String> run(DoctrineCatalog catalog, long[] seeds, int seasons, float crossroadsWeight) {
        long battlesAll = 0, battlesNear = 0, hostTurns = 0, hostTurnsNear = 0, seasonsAll = 0, seasonsNear = 0;
        long battlesAtCrossroads = 0, hostTurnsAtCrossroads = 0, seasonsAtCrossroads = 0, busiestChunk = 0;
        com.sun.management.ThreadMXBean mx = (com.sun.management.ThreadMXBean) java.lang.management.ManagementFactory.getThreadMXBean();
        long me = Thread.currentThread().getId();
        int warm = HistoryManager.SEASON_TURNS;
        int turnsEach = seasons * HistoryManager.SEASON_TURNS;
        long[] turn = new long[turnsEach * seeds.length];
        int n = 0;
        List<Double> jumps = new ArrayList<>();
        double coldJump = -1, units = 0, wars = 0, battles = 0, allocated = 0;
        int unitsMax = 0, battlesMax = 0;
        long measured = 0, paths = 0, battlesFought = 0, overland = 0, encounters = 0, pitched = 0, expeditions = 0, earshot = 0, seasonsPassed = 0;
        int warsEnded = 0, warsBoth = 0, warSeasons = 0;
        long siegesLaid = 0, siegesRaised = 0, seatsFell = 0, siegeTurns = 0;
        long[] castleSieges = new long[3];
        for (long seed : seeds) {
            HistoryManager history = HistoryManager.create(seed, catalog);
            BiomeManager biomes = new BiomeManager(seed);
            ArmyLayer layer = new ArmyLayer(history.world(), Terrain.of(biomes),
                    () -> SeatMap.of(history.world(), biomes.getRoads(), biomes.getCastleSite(), seed, biomes::isOpenLand), history.roster(), history::recordArmyDeed);
            layer.crossroadsWeight(crossroadsWeight);
            Set<Integer> battlesCounted = new HashSet<>();
            Set<Integer> seasonsWithOneNear = new HashSet<>(), seasonsWithOneAtCrossroads = new HashSet<>();
            java.util.Map<String, Integer> battlesByChunk = new java.util.HashMap<>();
            List<GridPoint2> crossroads = Terrain.of(biomes).crossroads();
            int startSeason = history.world().season();
            if (coldJump < 0) {
                long start = System.nanoTime();
                history.sleepThroughWar(layer::tick);
                coldJump = (System.nanoTime() - start) / 1e6;
            }
            for (int i = 0; i < warm; i++) {
                history.tickWarClock();
                layer.tick(history.warClock());
            }
            Walker walker = new Walker(biomes.getRoads());
            long window = 0;
            long[] ring = new long[HistoryManager.SLEEP_TURNS];
            long pathStart = layer.pathCalls(), battlesStart = layer.battlesEnded();
            int asleep = 0;
            for (int i = 0; i < turnsEach; i++) {
                // A sleep passes its own Season and then its turns, as the game's does.
                if (asleep == 0 && walker.sinceSleep >= EXPEDITION_TURNS) {
                    history.onSleep();
                    walker.sinceSleep = 0;
                    walker.expeditions++;
                    asleep = HistoryManager.SLEEP_TURNS;
                }
                if (asleep > 0) {
                    asleep--;
                    history.sleepTurn();
                } else {
                    history.tickWarClock();
                    walker.walk();
                }
                long a0 = mx.getThreadAllocatedBytes(me), t0 = System.nanoTime();
                layer.tick(history.warClock());
                long d = System.nanoTime() - t0;
                allocated += mx.getThreadAllocatedBytes(me) - a0;
                measured++;
                turn[n++] = d;
                window += d - ring[i % ring.length];
                ring[i % ring.length] = d;
                if (i >= ring.length - 1 && i % 25 == 0) jumps.add(window / 1e6);
                units += layer.roster().armies.size();
                unitsMax = Math.max(unitsMax, layer.roster().armies.size());
                battles += layer.roster().battles.size();
                battlesMax = Math.max(battlesMax, layer.roster().battles.size());
                if (asleep == 0) walker.observe(layer.roster());
                for (int b = 0; b < layer.roster().battles.size(); b++) {
                    ArmyBattle fight = layer.roster().battles.get(b);
                    boolean near = Math.max(Math.abs(fight.x), Math.abs(fight.y)) <= NEAR_THE_SHELTER;
                    boolean atCrossroads = atACrossroads(crossroads, fight.x, fight.y);
                    if (battlesCounted.add(fight.id)) {
                        battlesAll++;
                        if (near) battlesNear++;
                        if (atCrossroads) battlesAtCrossroads++;
                        battlesByChunk.merge(fight.x + "," + fight.y, 1, Integer::sum);
                    }
                    if (near) seasonsWithOneNear.add(history.world().season());
                    if (atCrossroads) seasonsWithOneAtCrossroads.add(history.world().season());
                }
                for (int h = 0; h < layer.roster().armies.size(); h++) {
                    Army host = layer.roster().armies.get(h);
                    if (!host.isHost()) continue;
                    hostTurns++;
                    if (Math.max(Math.abs(host.x), Math.abs(host.y)) <= NEAR_THE_SHELTER) hostTurnsNear++;
                    if (atACrossroads(crossroads, host.x, host.y)) hostTurnsAtCrossroads++;
                }
                if (i % 25 == 0) wars += history.world().activeWars().size() * 25;
            }
            paths += layer.pathCalls() - pathStart;
            battlesFought += layer.battlesEnded() - battlesStart;
            overland += walker.overland;
            encounters += walker.encounters;
            pitched += walker.pitched;
            expeditions += walker.expeditions;
            earshot += walker.earshot;
            seasonsPassed += history.world().season() - startSeason;
            seasonsAll += history.world().season() - startSeason;
            seasonsNear += seasonsWithOneNear.size();
            seasonsAtCrossroads += seasonsWithOneAtCrossroads.size();
            int busiest = 0;
            for (int count : battlesByChunk.values()) busiest = Math.max(busiest, count);
            busiestChunk += busiest;
            long[] castle = layer.castleSiegeCounts();
            for (int c = 0; c < 3; c++) castleSieges[c] += castle[c];
            long[] sieged = layer.siegeCounts();
            siegesLaid += sieged[0];
            siegesRaised += sieged[1];
            seatsFell += sieged[2];
            siegeTurns += sieged[3];
            for (War w : history.world().wars()) {
                if (w.isActive() || w.endSeason < startSeason) continue;
                warsEnded++;
                warSeasons += w.endSeason - Math.max(w.startSeason, startSeason);
                if (!history.world().house(w.attackerId).isExtinct() && !history.world().house(w.defenderId).isExtinct()) warsBoth++;
            }
        }
        Arrays.sort(turn);
        Collections.sort(jumps);
        double mean = 0;
        for (long t : turn) mean += t;
        mean /= turn.length * 1e6;
        double p99 = turn[(int) (turn.length * 0.99)] / 1e6;
        double jumpWorst = Math.max(jumps.get(jumps.size() - 1), coldJump);
        double allocKb = allocated / 1024.0 / measured;

        List<String> out = new ArrayList<>();
        out.add(line("Worlds %d, %d turns each, %d Seasons in all, %d expeditions", seeds.length, turnsEach, seasonsPassed, expeditions));
        out.add(line("Units mean %.1f, max %d; battles at once mean %.2f, max %d; wars mean %.2f; searches per turn %.2f",
                units / turn.length, unitsMax, battles / turn.length, battlesMax, wars / turn.length, (double) paths / turn.length));
        out.add(line("Encounters: one per %.0f overland turns (target 400-700)", encounters == 0 ? 0.0 : (double) overland / encounters));
        out.add(line("Battles: %.2f a Season (target about 1); pitched at the walker's side, one per %.0f expeditions (target about 20); earshot %.1f%% of overland turns",
                (double) battlesFought / Math.max(1, seasonsPassed), pitched == 0 ? 0.0 : (double) expeditions / pitched, 100.0 * earshot / Math.max(1, overland)));
        out.add(line("Near the Shelter (%d chunks, weight %.2f): %.1f%% of battles, %.1f%% of host turns, a battle there in %.1f%% of Seasons",
                NEAR_THE_SHELTER, crossroadsWeight, 100.0 * battlesNear / Math.max(1, battlesAll), 100.0 * hostTurnsNear / Math.max(1, hostTurns),
                100.0 * seasonsNear / Math.max(1, seasonsAll)));
        out.add(line("At a crossroads (%d chunks): %.1f%% of battles, %.1f%% of host turns, a battle there in %.1f%% of Seasons; the busiest chunk of a world holds %.1f%% of its battles",
                AT_A_CROSSROADS, 100.0 * battlesAtCrossroads / Math.max(1, battlesAll), 100.0 * hostTurnsAtCrossroads / Math.max(1, hostTurns),
                100.0 * seasonsAtCrossroads / Math.max(1, seasonsAll), 100.0 * busiestChunk / Math.max(1, battlesAll)));
        out.add(line("Wars ended %d, mean %.1f Seasons (target 4-16), both houses standing %d", warsEnded, warsEnded == 0 ? 0.0 : (double) warSeasons / warsEnded, warsBoth));
        out.add(line("Sieges of seats: laid %d, raised %d, seats fell %d, mean %.1f Seasons to their end", siegesLaid, siegesRaised, seatsFell,
                siegesRaised + seatsFell == 0 ? 0.0 : (double) siegeTurns / (siegesRaised + seatsFell) / HistoryManager.SEASON_TURNS));
        out.add(line("Sieges of the castle: laid %d, broken %d, lifted %d", castleSieges[0], castleSieges[1], castleSieges[2]));
        out.add(line("War turn: mean %.3f ms (budget %.2f) %s", mean, BUDGET_TURN_MEAN_MS, verdict(mean, BUDGET_TURN_MEAN_MS)));
        out.add(line("War turn: p50 %.3f ms, p99 %.3f ms (budget %.0f) %s, max %.2f ms", turn[turn.length / 2] / 1e6, p99, BUDGET_TURN_P99_MS, verdict(p99, BUDGET_TURN_P99_MS), turn[turn.length - 1] / 1e6));
        out.add(line("Sleep jump: p50 %.1f ms, worst %.1f ms, first in a fresh JVM %.1f ms (budget %.0f) %s", jumps.get(jumps.size() / 2), jumps.get(jumps.size() - 1), coldJump, BUDGET_JUMP_MS, verdict(jumpWorst, BUDGET_JUMP_MS)));
        out.add(line("Allocated: %.2f KB a turn (budget %.0f) %s", allocKb, BUDGET_ALLOC_KB, verdict(allocKb, BUDGET_ALLOC_KB)));
        return out;
    }

    private static boolean atACrossroads(List<GridPoint2> crossroads, int x, int y) {
        for (int i = 0; i < crossroads.size(); i++) {
            GridPoint2 c = crossroads.get(i);
            if (Math.max(Math.abs(c.x - x), Math.abs(c.y - y)) <= AT_A_CROSSROADS) return true;
        }
        return false;
    }

    /** The default seeds, and as many more as asked for, for a comparison the noise can't decide. */
    public static long[] seeds(int count) {
        long[] out = java.util.Arrays.copyOf(DEFAULT_SEEDS, count);
        for (int i = DEFAULT_SEEDS.length; i < count; i++) out[i] = 1000L + i * 15838L;
        return out;
    }

    private static String verdict(double value, double budget) {
        return value <= budget ? "OK" : "OVER BUDGET";
    }

    private static String line(String format, Object... args) {
        return String.format(Locale.ROOT, format, args);
    }
}
