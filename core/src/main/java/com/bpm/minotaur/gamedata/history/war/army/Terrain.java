package com.bpm.minotaur.gamedata.history.war.army;

import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.generation.Biome;
import com.bpm.minotaur.generation.ShelterRoads;
import com.bpm.minotaur.managers.BiomeManager;

import java.util.function.ToIntBiFunction;

/**
 * The overland as armies see it (Living War A5): how many war turns each chunk takes to cross, or
 * that it can't be crossed. Sampled once per world, since the layout never changes under a world.
 */
public final class Terrain {

    public static final int ROAD_TURNS = 15;
    public static final int OFF_ROAD_TURNS = 20;
    public static final int ROUGH_TURNS = 26;
    /** Chunks either side of a road's line that march at its pace. */
    private static final double CORRIDOR = 1.0;
    /** Every seat lies inside this; armies never march beyond it. */
    public static final int RADIUS = 60;

    public final int radius;
    public final int size;
    /** War turns to cross each chunk, by {@link #index}; 0 where no army can go. */
    private final byte[] turns;
    /** The Blight, by {@link #index}: it triples the land's toll (A27). */
    private final boolean[] blighted;
    private ShelterRoads roads;

    /** @param turnsAt war turns to cross chunk (x, y), or 0 where no army can go */
    public Terrain(int radius, ToIntBiFunction<Integer, Integer> turnsAt) {
        this.radius = radius;
        this.size = 2 * radius + 1;
        this.turns = new byte[size * size];
        this.blighted = new boolean[size * size];
        for (int y = -radius; y <= radius; y++) {
            for (int x = -radius; x <= radius; x++) {
                turns[index(x, y)] = (byte) turnsAt.applyAsInt(x, y);
            }
        }
    }

    /** A world's overland: open land only, so the central maze, the sea and the mountains are closed. */
    public static Terrain of(BiomeManager biomes) {
        ShelterRoads roads = biomes.getRoads();
        GridPoint2 at = new GridPoint2();
        Terrain t = new Terrain(RADIUS, (x, y) -> {
            at.set(x, y);
            if (!biomes.isOpenLand(at)) return 0;
            if (roads != null && onRoad(roads, x, y)) return ROAD_TURNS;
            Biome b = biomes.getBiome(at);
            return b == Biome.TUNDRA || b == Biome.BLIGHT ? ROUGH_TURNS : OFF_ROAD_TURNS;
        });
        t.roads = roads;
        if (roads != null) t.findCrossroads(biomes);
        for (int y = -RADIUS; y <= RADIUS; y++) {
            for (int x = -RADIUS; x <= RADIUS; x++) {
                at.set(x, y);
                t.blighted[t.index(x, y)] = biomes.getBiome(at) == Biome.BLIGHT;
            }
        }
        return t;
    }

    /** How far on down its road a crossroads lies from the road's first shelter, at the least. */
    private static final int CROSSROADS_PAST = 2;
    private final java.util.List<GridPoint2> crossroads = new java.util.ArrayList<>();

    /**
     * The crossroads near the Shelter (A52): each road, just past its first shelter, where the way from
     * the maze meets the country. Never a shelter's own chunk, where no battle can be fought. Fixed by
     * the seed; empty without roads.
     */
    public java.util.List<GridPoint2> crossroads() {
        return crossroads;
    }

    private void findCrossroads(BiomeManager biomes) {
        for (ShelterRoads.Road r : roads.getRoads()) {
            if (r.getShelters().isEmpty()) continue;
            GridPoint2 first = r.getShelters().get(0), end = r.getEnd();
            double dx = end.x - first.x, dy = end.y - first.y, len = Math.hypot(dx, dy);
            if (len < 1) continue;
            for (int on = CROSSROADS_PAST; on < len; on++) {
                GridPoint2 at = new GridPoint2((int) Math.round(first.x + dx / len * on), (int) Math.round(first.y + dy / len * on));
                if (biomes.isOpenLand(at) && roads.shelterAt(at, biomes::isOpenLand) == null) {
                    crossroads.add(at);
                    break;
                }
            }
        }
    }

    /** Roads are straight spokes from the maze, so a chunk is on one if it lies beside that line. */
    private static boolean onRoad(ShelterRoads roads, int x, int y) {
        for (ShelterRoads.Road r : roads.getRoads()) {
            GridPoint2 end = r.getEnd();
            double len2 = (double) end.x * end.x + (double) end.y * end.y;
            if (len2 == 0) continue;
            double t = Math.max(0, Math.min(1, (x * (double) end.x + y * (double) end.y) / len2));
            double dx = x - t * end.x, dy = y - t * end.y;
            if (dx * dx + dy * dy <= CORRIDOR * CORRIDOR) return true;
        }
        return false;
    }

    /** The shelter nearest (x, y) on the road ending at a seat, or null if no road ends there (A11). */
    public GridPoint2 shelterOnRoadTo(int seatX, int seatY, int x, int y) {
        if (roads == null) return null;
        GridPoint2 best = null;
        for (ShelterRoads.Road r : roads.getRoads()) {
            GridPoint2 end = r.getEnd();
            if (end.x != seatX || end.y != seatY) continue;
            for (GridPoint2 s : r.getShelters()) {
                if (!passable(s.x, s.y)) continue;
                if (best == null || Math.max(Math.abs(s.x - x), Math.abs(s.y - y)) < Math.max(Math.abs(best.x - x), Math.abs(best.y - y))) best = s;
            }
        }
        return best;
    }

    public boolean blightedAt(int index) {
        return blighted[index];
    }

    public boolean contains(int x, int y) {
        return Math.abs(x) <= radius && Math.abs(y) <= radius;
    }

    public int index(int x, int y) {
        return (y + radius) * size + (x + radius);
    }

    public int xOf(int index) {
        return index % size - radius;
    }

    public int yOf(int index) {
        return index / size - radius;
    }

    /** War turns to cross a chunk by index, or 0 where no army can go. */
    public int turnsAt(int index) {
        return turns[index];
    }

    public boolean passable(int x, int y) {
        return contains(x, y) && turns[index(x, y)] != 0;
    }
}
