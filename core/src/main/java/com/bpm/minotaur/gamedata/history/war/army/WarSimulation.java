package com.bpm.minotaur.gamedata.history.war.army;

import com.bpm.minotaur.gamedata.history.DoctrineCatalog;
import com.bpm.minotaur.gamedata.history.war.SeatMap;
import com.bpm.minotaur.managers.BiomeManager;
import com.bpm.minotaur.managers.HistoryManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * The headless war (Living War A59): runs real worlds with nobody watching, and reports the budget
 * in A58 and what the war did. Time is reported here and never asserted in the test suite, since
 * timings on the development machine aren't steady enough for that.
 */
public final class WarSimulation {

    public static final long[] DEFAULT_SEEDS = {1000L, 8919L, 24757L, 40595L, 135623L, 151461L};

    /** The budget at 26 units and 2 battles (A58), in the units below. */
    private static final double BUDGET_TURN_MEAN_MS = 0.25, BUDGET_TURN_P99_MS = 2, BUDGET_JUMP_MS = 150, BUDGET_ALLOC_KB = 1;

    private WarSimulation() {
    }

    /** @return the report, as lines */
    public static List<String> run(DoctrineCatalog catalog, long[] seeds, int seasons) {
        com.sun.management.ThreadMXBean mx = (com.sun.management.ThreadMXBean) java.lang.management.ManagementFactory.getThreadMXBean();
        long me = Thread.currentThread().getId();
        int warm = HistoryManager.SEASON_TURNS;
        int turnsEach = seasons * HistoryManager.SEASON_TURNS;
        long[] turn = new long[(turnsEach - warm) * seeds.length];
        int n = 0;
        List<Double> jumps = new ArrayList<>();
        double coldJump = -1, allocated = 0, units = 0, wars = 0;
        int unitsMax = 0;
        long paths = 0;
        for (long seed : seeds) {
            HistoryManager history = HistoryManager.create(seed, catalog);
            BiomeManager biomes = new BiomeManager(seed);
            ArmyLayer layer = new ArmyLayer(history.world(), Terrain.of(biomes),
                    () -> SeatMap.of(history.world(), biomes.getRoads(), biomes.getCastleSite(), seed, biomes::isOpenLand), history.roster());
            if (coldJump < 0) {
                long start = System.nanoTime();
                for (int i = 0; i < HistoryManager.SLEEP_TURNS; i++) {
                    history.tickWarClock();
                    layer.tick(history.warClock());
                }
                coldJump = (System.nanoTime() - start) / 1e6;
            }
            for (int i = 0; i < warm; i++) {
                history.tickWarClock();
                layer.tick(history.warClock());
            }
            long allocStart = mx.getThreadAllocatedBytes(me), allocInSeasons = 0, pathStart = layer.pathCalls();
            long window = 0;
            long[] ring = new long[HistoryManager.SLEEP_TURNS];
            for (int i = 0; i < turnsEach - warm; i++) {
                long a0 = mx.getThreadAllocatedBytes(me);
                history.tickWarClock();
                allocInSeasons += mx.getThreadAllocatedBytes(me) - a0;
                long t0 = System.nanoTime();
                layer.tick(history.warClock());
                long d = System.nanoTime() - t0;
                turn[n++] = d;
                window += d - ring[i % ring.length];
                ring[i % ring.length] = d;
                if (i >= ring.length - 1 && i % 25 == 0) jumps.add(window / 1e6);
                units += layer.roster().armies.size();
                unitsMax = Math.max(unitsMax, layer.roster().armies.size());
                if (i % 25 == 0) wars += history.world().activeWars().size() * 25;
            }
            allocated += mx.getThreadAllocatedBytes(me) - allocStart - allocInSeasons;
            paths += layer.pathCalls() - pathStart;
        }
        Arrays.sort(turn);
        Collections.sort(jumps);
        double mean = 0;
        for (long t : turn) mean += t;
        mean /= turn.length * 1e6;
        double p99 = turn[(int) (turn.length * 0.99)] / 1e6;
        double jumpWorst = Math.max(jumps.get(jumps.size() - 1), coldJump);
        double allocKb = allocated / 1024.0 / turn.length;

        List<String> out = new ArrayList<>();
        out.add(line("Worlds %d, %d Seasons each, %d turns timed", seeds.length, seasons, turn.length));
        out.add(line("Units mean %.1f, max %d; wars mean %.2f; searches per turn %.2f", units / turn.length, unitsMax, wars / turn.length, (double) paths / turn.length));
        out.add(line("War turn: mean %.3f ms (budget %.2f) %s", mean, BUDGET_TURN_MEAN_MS, verdict(mean, BUDGET_TURN_MEAN_MS)));
        out.add(line("War turn: p50 %.3f ms, p99 %.3f ms (budget %.0f) %s, max %.2f ms", turn[turn.length / 2] / 1e6, p99, BUDGET_TURN_P99_MS, verdict(p99, BUDGET_TURN_P99_MS), turn[turn.length - 1] / 1e6));
        out.add(line("Sleep jump: p50 %.1f ms, worst %.1f ms, first in a fresh JVM %.1f ms (budget %.0f) %s", jumps.get(jumps.size() / 2), jumps.get(jumps.size() - 1), coldJump, BUDGET_JUMP_MS, verdict(jumpWorst, BUDGET_JUMP_MS)));
        out.add(line("Allocated: %.2f KB a turn (budget %.0f) %s", allocKb, BUDGET_ALLOC_KB, verdict(allocKb, BUDGET_ALLOC_KB)));
        return out;
    }

    private static String verdict(double value, double budget) {
        return value <= budget ? "OK" : "OVER BUDGET";
    }

    private static String line(String format, Object... args) {
        return String.format(Locale.ROOT, format, args);
    }
}
