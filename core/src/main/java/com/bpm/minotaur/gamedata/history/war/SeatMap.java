package com.bpm.minotaur.gamedata.history.war;

import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.gamedata.boss.SealLord;
import com.bpm.minotaur.gamedata.history.HistoryWorld;
import com.bpm.minotaur.gamedata.history.House;
import com.bpm.minotaur.generation.ShelterRoads;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.function.Predicate;

/**
 * Where each house sits on the overland map (plan T2.1). A great house sits at its gash, which
 * is the seal site at the end of that gash's road; Tarmin-Zul sits in his castle; a lesser house
 * has a holdfast out in the wilds, placed from the world seed on the nearest open land. Death re-rolls the world, so the
 * houses move with it; the history does not care where.
 */
public final class SeatMap {

    /** Holdfasts stand beyond the central maze and short of the far marches. */
    public static final int HOLDFAST_MIN = 14;
    public static final int HOLDFAST_MAX = 32;
    /** How far a holdfast rolled onto sea or mountains looks for open land. */
    private static final int OPEN_LAND_REACH = 60;

    private final Map<Integer, GridPoint2> seats = new HashMap<>();

    private SeatMap() {
    }

    /** @param openLand whether a holdfast can stand on a chunk: soldiers must be able to march to it */
    public static SeatMap of(HistoryWorld world, ShelterRoads roads, GridPoint2 castleSite, long worldSeed,
            Predicate<GridPoint2> openLand) {
        SeatMap map = new SeatMap();
        for (House h : world.houses()) {
            if (h.isExtinct()) continue;
            GridPoint2 seat;
            if (h.holdsCastle) {
                seat = new GridPoint2(castleSite);
            } else if (h.isGreat()) {
                seat = roads.getRoad(roadOf(h.gashIndex)).getEnd();
            } else {
                Random rng = new Random(worldSeed ^ (h.id * 0x632BE59BD9B4E019L) ^ 0x4F1DL);
                double angle = rng.nextDouble() * Math.PI * 2;
                int dist = HOLDFAST_MIN + rng.nextInt(HOLDFAST_MAX - HOLDFAST_MIN + 1);
                seat = nearestOpen(new GridPoint2((int) Math.round(Math.cos(angle) * dist), (int) Math.round(Math.sin(angle) * dist)), openLand);
            }
            map.seats.put(h.id, seat);
        }
        return map;
    }

    /**
     * The open chunk nearest {@code from}, walking outward ring by ring in one fixed order, so the
     * seat follows from the seed alone. {@code from} itself if there is none in reach.
     */
    public static GridPoint2 nearestOpen(GridPoint2 from, Predicate<GridPoint2> openLand) {
        if (openLand.test(from)) return from;
        GridPoint2 at = new GridPoint2();
        for (int ring = 1; ring <= OPEN_LAND_REACH; ring++) {
            for (int dy = -ring; dy <= ring; dy++) {
                for (int dx = -ring; dx <= ring; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != ring) continue;
                    at.set(from.x + dx, from.y + dy);
                    if (openLand.test(at)) return new GridPoint2(at);
                }
            }
        }
        return from;
    }

    /** The chunk house {@code houseId} sits in, or null for an extinct or unknown house. */
    public GridPoint2 seat(int houseId) {
        GridPoint2 s = seats.get(houseId);
        return s == null ? null : new GridPoint2(s);
    }

    /** The road whose end holds gash {@code gashIndex}. */
    static int roadOf(int gashIndex) {
        for (int road = 0; road < ShelterRoads.ROAD_COUNT; road++) {
            if (SealLord.gashIndexForRoad(road) == gashIndex) return road;
        }
        throw new IllegalArgumentException("No road leads to gash " + gashIndex);
    }
}
