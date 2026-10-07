package com.bpm.minotaur.generation;

import com.badlogic.gdx.math.GridPoint2;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.function.Predicate;

/**
 * Where the shelters stand: four roads out of the maze, and a scattering off them.
 *
 * <p>Road 0 runs to Castle Tarmin. Roads 1-3 follow clockwise about 90 degrees
 * apart and end at seal sites, at the distances in
 * {@link WorldConstants#SEAL_SITE_DISTANCES} in an order the seed picks. Shelters
 * stand along each road close enough that the next beacon is always in sight.
 *
 * <p>Everything here derives from the world seed and the castle site alone, never
 * from which chunks have been generated, so a chunk can ask "am I a shelter?" at
 * any time and get the same answer. See docs/DEsign/Requirements_ Shelter Roads.md.
 */
public final class ShelterRoads {

    public enum Kind { CASTLE, SEAL }

    /** The castle road's index. Seal roads are 1-3. */
    public static final int CASTLE_ROAD = 0;
    public static final int ROAD_COUNT = 4;
    /** {@link Site#getRoad()} of a shelter that stands on no road. */
    public static final int OFF_ROAD = -1;

    public static final class Road {
        private final int index;
        private final Kind kind;
        private final double bearingDegrees;
        private final GridPoint2 end;
        private final List<GridPoint2> shelters;

        Road(int index, Kind kind, double bearingDegrees, GridPoint2 end, List<GridPoint2> shelters) {
            this.index = index;
            this.kind = kind;
            this.bearingDegrees = bearingDegrees;
            this.end = end;
            this.shelters = Collections.unmodifiableList(shelters);
        }

        public int getIndex() { return index; }
        public Kind getKind() { return kind; }
        /** Counter-clockwise from east, in [0, 360). */
        public double getBearingDegrees() { return bearingDegrees; }
        /** The castle chunk, or this road's seal site. */
        public GridPoint2 getEnd() { return new GridPoint2(end); }
        /** Shelter chunks in order, walking out from the maze. */
        public List<GridPoint2> getShelters() { return shelters; }
    }

    /** One shelter: its chunk, its road (or {@link #OFF_ROAD}) and its place on that road. */
    public static final class Site {
        private final GridPoint2 chunk;
        private final int road;
        private final int index;

        Site(GridPoint2 chunk, int road, int index) {
            this.chunk = new GridPoint2(chunk);
            this.road = road;
            this.index = index;
        }

        public GridPoint2 getChunk() { return new GridPoint2(chunk); }
        public int getRoad() { return road; }
        /** 0 for the shelter nearest the maze. Meaningless off-road. */
        public int getIndex() { return index; }
        public boolean isOffRoad() { return road == OFF_ROAD; }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Site)) return false;
            Site s = (Site) o;
            return road == s.road && index == s.index && chunk.equals(s.chunk);
        }

        @Override
        public int hashCode() {
            return Objects.hash(chunk, road, index);
        }

        @Override
        public String toString() {
            return (isOffRoad() ? "off-road" : "road " + road + " #" + index) + " at " + chunk;
        }
    }

    private final long seed;
    private final int mazeRadius;
    private final List<Road> roads = new ArrayList<>();
    private final Map<GridPoint2, Site> roadSites = new HashMap<>();
    private final Map<GridPoint2, Integer> sealSites = new HashMap<>();
    /** Every road shelter, road end and the castle: the points off-road shelters keep clear of. */
    private final List<GridPoint2> anchors = new ArrayList<>();

    public ShelterRoads(long seed, GridPoint2 castleSite) {
        this.seed = seed;
        this.mazeRadius = WorldConstants.CENTRAL_MAZE_RADIUS;

        // Its own stream, so retuning anything else never moves a road.
        Random rng = new Random(seed ^ 0x5E17E2A0ADL);
        double castleBearing = Math.toDegrees(Math.atan2(castleSite.y, castleSite.x));

        List<Integer> distances = new ArrayList<>();
        for (int d : WorldConstants.SEAL_SITE_DISTANCES) distances.add(d);
        Collections.shuffle(distances, rng);

        for (int r = 0; r < ROAD_COUNT; r++) {
            Kind kind = r == CASTLE_ROAD ? Kind.CASTLE : Kind.SEAL;
            double bearing;
            GridPoint2 end;
            if (kind == Kind.CASTLE) {
                bearing = castleBearing;
                end = new GridPoint2(castleSite);
            } else {
                double jitter = (rng.nextDouble() * 2 - 1) * WorldConstants.ROAD_BEARING_JITTER_DEGREES;
                bearing = castleBearing - 90.0 * r + jitter; // clockwise from the castle road
                double rad = Math.toRadians(bearing);
                int dist = distances.get((r - 1) % distances.size());
                end = new GridPoint2((int) Math.round(Math.cos(rad) * dist), (int) Math.round(Math.sin(rad) * dist));
            }
            bearing = ((bearing % 360) + 360) % 360;
            List<GridPoint2> stops = placeStops(end, rng);
            Road road = new Road(r, kind, bearing, end, stops);
            roads.add(road);
            for (int i = 0; i < stops.size(); i++) {
                roadSites.put(stops.get(i), new Site(stops.get(i), r, i));
                anchors.add(stops.get(i));
            }
            if (kind == Kind.SEAL) sealSites.put(end, r);
            anchors.add(end);
        }
    }

    /** Shelter chunks along the straight line to {@code end}, maze edge outward. */
    private List<GridPoint2> placeStops(GridPoint2 end, Random rng) {
        List<GridPoint2> stops = new ArrayList<>();
        double len = Math.hypot(end.x, end.y);
        if (len < 1e-6) return stops;
        double ux = end.x / len;
        double uy = end.y / len;
        // Where the ray leaves the square maze.
        double edge = (mazeRadius + 0.5) / Math.max(Math.abs(ux), Math.abs(uy));
        double t = edge + WorldConstants.ROAD_FIRST_SHELTER_OFFSET;
        while (t <= len - WorldConstants.ROAD_END_CLEARANCE) {
            GridPoint2 c = new GridPoint2((int) Math.round(ux * t), (int) Math.round(uy * t));
            if (Math.max(Math.abs(c.x), Math.abs(c.y)) > mazeRadius && !c.equals(end)
                    && (stops.isEmpty() || !stops.get(stops.size() - 1).equals(c))) {
                stops.add(c);
            }
            int span = WorldConstants.ROAD_SHELTER_SPACING_MAX - WorldConstants.ROAD_SHELTER_SPACING_MIN;
            t += WorldConstants.ROAD_SHELTER_SPACING_MIN + (span > 0 ? rng.nextInt(span + 1) : 0);
        }
        if (stops.isEmpty()) {
            // A short road, on a diagonal, can leave no room past the maze edge. Every road
            // still gets one shelter, halfway from the edge to its end.
            double mid = edge + Math.max(0.5, (len - edge) / 2);
            GridPoint2 c = new GridPoint2((int) Math.round(ux * mid), (int) Math.round(uy * mid));
            if (Math.max(Math.abs(c.x), Math.abs(c.y)) > mazeRadius && !c.equals(end)) stops.add(c);
        }
        return stops;
    }

    public List<Road> getRoads() {
        return Collections.unmodifiableList(roads);
    }

    public Road getRoad(int index) {
        return roads.get(index);
    }

    /** The seal road ending in this chunk, or -1. */
    public int sealRoadAt(GridPoint2 chunk) {
        Integer r = chunk == null ? null : sealSites.get(chunk);
        return r == null ? -1 : r;
    }

    /** Within the walkable band of any road: no ocean or mountain may stand here. */
    public boolean inCorridor(int x, int y) {
        for (Road road : roads) {
            if (distanceToSegment(x, y, road.end) <= WorldConstants.CASTLE_CORRIDOR_HALF_WIDTH) return true;
        }
        return false;
    }

    /**
     * The shelter standing in this chunk, or null.
     *
     * @param openLand whether a chunk is land a shelter may stand on (not maze, sea or mountain)
     */
    public Site shelterAt(GridPoint2 chunk, Predicate<GridPoint2> openLand) {
        if (chunk == null) return null;
        Site road = roadSites.get(chunk);
        if (road != null) return road;
        int cx = Math.floorDiv(chunk.x, WorldConstants.OFF_ROAD_CELL);
        int cy = Math.floorDiv(chunk.y, WorldConstants.OFF_ROAD_CELL);
        GridPoint2 candidate = offRoadCandidate(cx, cy, openLand);
        return candidate != null && candidate.equals(chunk) ? new Site(chunk, OFF_ROAD, 0) : null;
    }

    /** Every shelter within {@code radius} chunks (Euclidean) of {@code center}. */
    public List<Site> sheltersWithin(GridPoint2 center, float radius, Predicate<GridPoint2> openLand) {
        List<Site> out = new ArrayList<>();
        double r2 = (double) radius * radius;
        for (Site s : roadSites.values()) {
            if (dist2(s.chunk, center) <= r2) out.add(s);
        }
        int cell = WorldConstants.OFF_ROAD_CELL;
        int minX = Math.floorDiv((int) Math.floor(center.x - radius), cell);
        int maxX = Math.floorDiv((int) Math.ceil(center.x + radius), cell);
        int minY = Math.floorDiv((int) Math.floor(center.y - radius), cell);
        int maxY = Math.floorDiv((int) Math.ceil(center.y + radius), cell);
        for (int cx = minX; cx <= maxX; cx++) {
            for (int cy = minY; cy <= maxY; cy++) {
                GridPoint2 c = offRoadCandidate(cx, cy, openLand);
                if (c != null && dist2(c, center) <= r2) out.add(new Site(c, OFF_ROAD, 0));
            }
        }
        return out;
    }

    /**
     * The one chunk in this cell that may hold an off-road shelter, or null.
     *
     * <p>The candidate keeps a margin from its cell's edges, so candidates in
     * neighbouring cells are always at least {@code SHELTER_MIN_SPACING} apart
     * without either needing to look at the other.
     */
    private GridPoint2 offRoadCandidate(int cx, int cy, Predicate<GridPoint2> openLand) {
        int cell = WorldConstants.OFF_ROAD_CELL;
        // Neighbouring candidates differ by at least 2 * margin + 1 on one axis.
        int margin = WorldConstants.SHELTER_MIN_SPACING / 2;
        int span = Math.max(1, cell - 2 * margin);
        Random rng = new Random(seed ^ (cx * 0x9E3779B97F4A7C15L) ^ (cy * 0xC2B2AE3D27D4EB4FL) ^ 0x0FF0AD5L);
        GridPoint2 c = new GridPoint2(cx * cell + margin + rng.nextInt(span), cy * cell + margin + rng.nextInt(span));

        if (Math.max(Math.abs(c.x), Math.abs(c.y)) <= mazeRadius + 2) return null;
        for (GridPoint2 a : anchors) {
            if (dist2(c, a) < (double) WorldConstants.SHELTER_MIN_SPACING * WorldConstants.SHELTER_MIN_SPACING) {
                return null;
            }
        }
        for (Road road : roads) {
            if (distanceToSegment(c.x, c.y, road.end) < WorldConstants.OFF_ROAD_CLEARANCE) return null;
        }
        if (openLand != null && !openLand.test(c)) return null;
        return c;
    }

    /** Distance from (x, y) to the segment from the origin to {@code end}. */
    private static double distanceToSegment(int x, int y, GridPoint2 end) {
        double ex = end.x;
        double ey = end.y;
        double lenSq = ex * ex + ey * ey;
        double t = lenSq == 0 ? 0 : Math.max(0, Math.min(1, (x * ex + y * ey) / lenSq));
        return Math.hypot(ex * t - x, ey * t - y);
    }

    private static double dist2(GridPoint2 a, GridPoint2 b) {
        double dx = a.x - b.x;
        double dy = a.y - b.y;
        return dx * dx + dy * dy;
    }
}
