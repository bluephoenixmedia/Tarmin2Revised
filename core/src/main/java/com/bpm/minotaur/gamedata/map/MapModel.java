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
     * Null in a world without roads, or while that next step is not yet known to the player.
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
        GridPoint2 next = target.getEnd();
        for (GridPoint2 stop : target.getShelters()) {
            if (!network.isClaimed(stop)) {
                next = stop;
                break;
            }
        }
        // The map never gives away ground the player has not learned. The castle is the
        // exception: its bearing is known from the first day.
        boolean castle = biomes.isCastleChunk(next);
        return castle || isMarkable(1, next) ? new GridPoint2(next) : null;
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

    /** A major landmark or tactical point of interest cycled by TAB on the macro map. */
    public static final class MacroMarker {
        public enum Type { HOME, SHELTER, GATE, SEAL, CASTLE, THREAT, PIN }
        public final Type type;
        public final GridPoint2 chunk;
        public final int floor;
        public final String name;
        public final String details;

        public MacroMarker(Type type, GridPoint2 chunk, int floor, String name, String details) {
            this.type = type;
            this.chunk = new GridPoint2(chunk);
            this.floor = floor;
            this.name = name;
            this.details = details;
        }

        public GridPoint2 getChunk() { return new GridPoint2(chunk); }
    }

    /** A roaming Megabeast or imminent Doom horror detected within sensor range. */
    public static final class ThreatMark {
        public final GridPoint2 chunk;
        public final int floor;
        public final String name;
        public final String status;
        public final int dangerPips;

        public ThreatMark(GridPoint2 chunk, int floor, String name, String status, int dangerPips) {
            this.chunk = new GridPoint2(chunk);
            this.floor = floor;
            this.name = name;
            this.status = status;
            this.dangerPips = dangerPips;
        }
    }

    /** The destination chunk for a biome portal, or null if unlocated in this world. */
    public GridPoint2 gateChunk(com.bpm.minotaur.gamedata.progression.BiomePortal portal) {
        if (portal == null || biomes == null) return null;
        ShelterRoads roads = roads();
        if (roads != null) {
            return roads.portalArrival(portal.getDestination(), biomes::getBiome);
        }
        return null;
    }

    /** Cardinal or diagonal compass bearing from Home (0, 0) to a destination chunk. */
    public static String bearingFromHome(GridPoint2 target) {
        return bearing(HOME, target);
    }

    /** Cardinal or diagonal compass bearing from source to target chunk. */
    public static String bearing(GridPoint2 from, GridPoint2 to) {
        if (from == null || to == null) return "";
        int dx = to.x - from.x;
        int dy = to.y - from.y;
        if (dx == 0 && dy == 0) return "HERE";
        double angle = Math.atan2(dy, dx) * 180.0 / Math.PI;
        if (angle < 0) angle += 360.0;
        if (angle >= 337.5 || angle < 22.5) return "> E";
        if (angle >= 22.5 && angle < 67.5) return "NE";
        if (angle >= 67.5 && angle < 112.5) return "^ N";
        if (angle >= 112.5 && angle < 157.5) return "NW";
        if (angle >= 157.5 && angle < 202.5) return "< W";
        if (angle >= 202.5 && angle < 247.5) return "SW";
        if (angle >= 247.5 && angle < 292.5) return "v S";
        return "SE";
    }

    /** All macro markers on {@code floor} for Tab cycling. */
    public List<MacroMarker> macroMarkers(int floor) {
        List<MacroMarker> list = new ArrayList<>();
        if (floor == 1) {
            list.add(new MacroMarker(MacroMarker.Type.HOME, HOME, 1, "Home Shelter", "Safe hearth"));
            for (KnownShelter s : knownShelters()) {
                if (HOME.equals(s.getChunk())) continue;
                String name = "Shelter " + s.getChunk().x + ", " + s.getChunk().y;
                list.add(new MacroMarker(MacroMarker.Type.SHELTER, s.getChunk(), 1, name, s.getMark().name()));
            }
            for (com.bpm.minotaur.gamedata.progression.BiomePortal p : com.bpm.minotaur.gamedata.progression.BiomePortal.values()) {
                GridPoint2 target = gateChunk(p);
                if (target != null) {
                    list.add(new MacroMarker(MacroMarker.Type.GATE, target, 1, p.getDisplayName(), "Ancient Gate"));
                }
            }
            ShelterRoads roads = roads();
            if (roads != null) {
                for (ShelterRoads.Road r : roads.getRoads()) {
                    if (r.getKind() == ShelterRoads.Kind.SEAL && isSealSiteKnown(r.getIndex())) {
                        list.add(new MacroMarker(MacroMarker.Type.SEAL, r.getEnd(), 1, "Seal Site " + r.getIndex(), "Ancient Seal"));
                    }
                }
            }
            if (biomes != null && biomes.getCastleSite() != null && isCastleKnown()) {
                list.add(new MacroMarker(MacroMarker.Type.CASTLE, biomes.getCastleSite(), 1, "Castle Tarmin", "Citadel"));
            }
        }
        for (Map.Entry<GridPoint2, MapKnowledge.Pin> p : knowledge.getPins(floor).entrySet()) {
            list.add(new MacroMarker(MacroMarker.Type.PIN, p.getKey(), floor, "Pin: " + p.getValue().name(), "Marked point"));
        }
        return list;
    }

    /** Returns the chunk sequence along known roads from {@code from} to the suggestion target. */
    public List<GridPoint2> routeToSuggestion(GridPoint2 from) {
        GridPoint2 sugg = suggestion();
        if (sugg == null || from == null) return Collections.emptyList();
        ShelterRoads roads = roads();
        if (roads == null) return Collections.singletonList(new GridPoint2(sugg));

        for (ShelterRoads.Road road : roads.getRoads()) {
            List<GridPoint2> stops = new ArrayList<>();
            stops.add(HOME);
            stops.addAll(road.getShelters());
            stops.add(road.getEnd());
            int idx = stops.indexOf(sugg);
            if (idx >= 0) {
                List<GridPoint2> path = new ArrayList<>();
                for (int i = 0; i <= idx; i++) {
                    path.add(new GridPoint2(stops.get(i)));
                }
                return path;
            }
        }
        return Collections.singletonList(new GridPoint2(sugg));
    }
}

