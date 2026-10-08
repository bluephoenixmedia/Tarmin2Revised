package com.bpm.minotaur.gamedata.map;

import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.gamedata.shelter.ShelterNetwork;
import com.bpm.minotaur.generation.ShelterRoads;
import com.bpm.minotaur.managers.BiomeManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntFunction;

/**
 * What the expedition map shows, worked out from what the player knows.
 *
 * <p>Free of rendering, so the map's rules -- what is known, which roads are drawn,
 * where the next step lies -- can be tested without a screen. The map screen and the
 * HUD minimap both read this. See docs/DEsign/Requirements_ Expedition Map.md.
 */
public final class MapModel {

    /** How much the player knows about a chunk. */
    public enum Knowledge { UNKNOWN, GLIMPSED, VISITED }

    /** A shelter as the map draws it. */
    public enum ShelterMark { HOME, LIT, COLD, RUMOURED }

    /** One stretch of road between two points the player knows. */
    public static final class RoadSegment {
        private final int road;
        private final GridPoint2 from;
        private final GridPoint2 to;
        private final boolean spent;

        RoadSegment(int road, GridPoint2 from, GridPoint2 to, boolean spent) {
            this.road = road;
            this.from = new GridPoint2(from);
            this.to = new GridPoint2(to);
            this.spent = spent;
        }

        public int getRoad() { return road; }
        public GridPoint2 getFrom() { return new GridPoint2(from); }
        public GridPoint2 getTo() { return new GridPoint2(to); }
        /** The road's seal is won: it is drawn faded. */
        public boolean isSpent() { return spent; }
    }

    /** A shelter on the map: where, how it is drawn, and its road ({@link ShelterRoads#OFF_ROAD} if none). */
    public static final class KnownShelter {
        private final GridPoint2 chunk;
        private final ShelterMark mark;
        private final int road;

        KnownShelter(GridPoint2 chunk, ShelterMark mark, int road) {
            this.chunk = new GridPoint2(chunk);
            this.mark = mark;
            this.road = road;
        }

        public GridPoint2 getChunk() { return new GridPoint2(chunk); }
        public ShelterMark getMark() { return mark; }
        public int getRoad() { return road; }
    }

    private static final GridPoint2 HOME = new GridPoint2(0, 0);

    private final BiomeManager biomes;
    private final ShelterNetwork network;
    private final MapKnowledge knowledge;
    private final IntFunction<Set<GridPoint2>> visitedOnFloor;
    private final int maxFloor;
    private final Map<Integer, Set<GridPoint2>> visitedCache = new HashMap<>();

    /**
     * @param visitedOnFloor the chunks with a save file on each floor; read once per floor
     * @param maxFloor       the deepest floor the player has reached
     */
    public MapModel(BiomeManager biomes, ShelterNetwork network, MapKnowledge knowledge,
                    IntFunction<Set<GridPoint2>> visitedOnFloor, int maxFloor) {
        this.biomes = biomes;
        this.network = network;
        this.knowledge = knowledge;
        this.visitedOnFloor = visitedOnFloor;
        this.maxFloor = maxFloor;
    }

    /** The chunks entered on {@code floor}. */
    public Set<GridPoint2> visited(int floor) {
        return visitedCache.computeIfAbsent(floor, f -> {
            Set<GridPoint2> s = visitedOnFloor.apply(f);
            return s == null ? Collections.emptySet() : s;
        });
    }

    public Knowledge knowledgeOf(int floor, GridPoint2 chunk) {
        if (chunk == null) return Knowledge.UNKNOWN;
        if (visited(floor).contains(chunk)) return Knowledge.VISITED;
        if (floor == 1 && knowledge.isGlimpsed(chunk)) return Knowledge.GLIMPSED;
        return Knowledge.UNKNOWN;
    }

    /** The surface shelter in this chunk as the player knows it, or null if none is known there. */
    public ShelterMark shelterAt(GridPoint2 chunk) {
        if (chunk == null) return null;
        if (HOME.equals(chunk)) return ShelterMark.HOME;
        if (biomes == null || biomes.getShelterSite(chunk) == null) return null;
        if (network.isClaimed(chunk)) return ShelterMark.LIT;
        if (visited(1).contains(chunk)) return ShelterMark.COLD;
        if (knowledge.isSighted(chunk)) return ShelterMark.RUMOURED;
        return null;
    }

    /** Every shelter the map shows: home, and each one entered, sighted or lit. */
    public List<KnownShelter> knownShelters() {
        java.util.Set<GridPoint2> candidates = new java.util.LinkedHashSet<>();
        candidates.add(HOME);
        candidates.addAll(network.getClaimed());
        candidates.addAll(knowledge.getSighted());
        candidates.addAll(visited(1));
        List<KnownShelter> out = new ArrayList<>();
        for (GridPoint2 c : candidates) {
            ShelterMark mark = shelterAt(c);
            if (mark == null) continue;
            ShelterRoads.Site site = biomes == null ? null : biomes.getShelterSite(c);
            out.add(new KnownShelter(c, mark, site == null ? ShelterRoads.OFF_ROAD : site.getRoad()));
        }
        return out;
    }

    /** The seal site at the end of {@code road} is on the map. */
    public boolean isSealSiteKnown(int road) {
        ShelterRoads roads = roads();
        if (roads == null || road <= ShelterRoads.CASTLE_ROAD || road >= ShelterRoads.ROAD_COUNT) return false;
        ShelterRoads.Road r = roads.getRoad(road);
        if (visited(1).contains(r.getEnd())) return true;
        List<GridPoint2> stops = r.getShelters();
        return !stops.isEmpty() && network.isClaimed(stops.get(stops.size() - 1));
    }

    /** Ground the player knows, and so may set a waypoint or a pin on. */
    public boolean isMarkable(int floor, GridPoint2 chunk) {
        if (chunk == null) return false;
        if (knowledgeOf(floor, chunk) != Knowledge.UNKNOWN) return true;
        if (floor != 1) return false;
        if (shelterAt(chunk) != null) return true;
        int sealRoad = biomes == null ? -1 : biomes.getSealRoad(chunk);
        return sealRoad > 0 && isSealSiteKnown(sealRoad);
    }

    /** The player has entered the castle's chunk. Its direction is always known. */
    public boolean isCastleKnown() {
        GridPoint2 site = biomes == null ? null : biomes.getCastleSite();
        return site != null && visited(1).contains(site);
    }

    /** Every stretch of road whose two ends are both known, walking out from home. */
    public List<RoadSegment> knownRoadSegments() {
        List<RoadSegment> out = new ArrayList<>();
        ShelterRoads roads = roads();
        if (roads == null) return out;
        for (ShelterRoads.Road road : roads.getRoads()) {
            boolean spent = road.getKind() == ShelterRoads.Kind.SEAL && network.hasSeal(road.getIndex());
            List<GridPoint2> points = new ArrayList<>();
            points.add(HOME);
            points.addAll(road.getShelters());
            points.add(road.getEnd());
            for (int i = 0; i + 1 < points.size(); i++) {
                if (isKnownPoint(road, points.get(i)) && isKnownPoint(road, points.get(i + 1))) {
                    out.add(new RoadSegment(road.getIndex(), points.get(i), points.get(i + 1), spent));
                }
            }
        }
        return out;
    }

    private boolean isKnownPoint(ShelterRoads.Road road, GridPoint2 p) {
        if (HOME.equals(p)) return true;
        if (p.equals(road.getEnd())) {
            return road.getKind() == ShelterRoads.Kind.CASTLE ? isCastleKnown() : isSealSiteKnown(road.getIndex());
        }
        return shelterAt(p) != null;
    }

    /**
     * Where the player should head next: the next cold shelter on the road to the nearest
     * unwon seal site, then that site; with every seal won, the same along the castle road.
     * Null in a world without roads.
     */
    public GridPoint2 suggestion() {
        ShelterRoads roads = roads();
        if (roads == null) return null;
        ShelterRoads.Road target = null;
        for (ShelterRoads.Road road : roads.getRoads()) {
            if (road.getKind() != ShelterRoads.Kind.SEAL || network.hasSeal(road.getIndex())) continue;
            if (target == null || road.getEnd().dst2(HOME) < target.getEnd().dst2(HOME)) target = road;
        }
        if (target == null) target = roads.getRoad(ShelterRoads.CASTLE_ROAD);
        for (GridPoint2 stop : target.getShelters()) {
            if (!network.isClaimed(stop)) return new GridPoint2(stop);
        }
        return target.getEnd();
    }

    /** The deepest stratum explored beneath this surface chunk: 1 for floor 2, 0 for none. */
    public int deepestStratumBelow(GridPoint2 chunk) {
        for (int floor = maxFloor; floor >= 2; floor--) {
            if (visited(floor).contains(chunk)) return floor - 1;
        }
        return 0;
    }

    /** The lit shelter fewest chunk crossings from {@code from}; home when none is nearer. */
    public GridPoint2 nearestLitShelter(GridPoint2 from) {
        GridPoint2 best = new GridPoint2(HOME);
        if (from == null) return best;
        for (GridPoint2 lit : network.getClaimed()) {
            if (distance(from, lit) < distance(from, best)) best = lit;
        }
        return best;
    }

    /** Chunk crossings between two chunks: the player walks between chunks orthogonally. */
    public static int distance(GridPoint2 a, GridPoint2 b) {
        return Math.abs(a.x - b.x) + Math.abs(a.y - b.y);
    }

    public BiomeManager getBiomes() {
        return biomes;
    }

    public int getMaxFloor() {
        return maxFloor;
    }

    private ShelterRoads roads() {
        return biomes == null ? null : biomes.getRoads();
    }
}
