package com.bpm.minotaur.generation;

import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.liquid.LiquidManager;
import com.bpm.minotaur.gamedata.liquid.LiquidType;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Where drinkable water is (#193, decided in #180): common on the surface, scarce in the
 * strata, where Blood, Black Muck and Freezing Slush take its place. Runs once over a freshly
 * generated chunk, after its biome and theme have laid their own liquids.
 */
public final class WaterSupply {

    /** Below the surface, the share of water tiles that stay water. */
    static final float STRATA_WATER_KEPT = 0.25f;
    /** Of the strata water that turns, the shares that become blood and muck; the rest is slush. */
    static final float STRATA_TO_BLOOD = 0.45f;
    static final float STRATA_TO_MUCK = 0.35f;

    /** A surface chunk with fewer water tiles than this gets puddles. */
    static final int SURFACE_MIN_WATER = 4;
    static final int PUDDLES_MIN = 2;
    static final int PUDDLES_MAX = 4;

    /** How far, in chunks, a Shelter's promise of water reaches. */
    static final int HOME_REACH = 2;
    /** The deepest level near home that is promised clean water: the first stratum. */
    static final int HOME_WATER_DEPTH = 2;
    /** The chunk of the home Shelter, where every new game begins. */
    public static final GridPoint2 HOME_CHUNK = new GridPoint2(0, 0);

    private WaterSupply() {
    }

    /** Applies the rule for this chunk's depth. */
    public static void apply(Maze maze, Biome biome, int level, long chunkSeed) {
        apply(maze, biome, level, chunkSeed, false);
    }

    /**
     * As above; {@code nearHome} chunks on the surface and the first stratum always hold some
     * clean water, whatever their biome, so a life that sets out from the Shelter can find a
     * drink before thirst finishes it (playtest 2026-10-22: two lives died parched).
     */
    public static void apply(Maze maze, Biome biome, int level, long chunkSeed, boolean nearHome) {
        if (maze == null || maze.getLiquidManager() == null) return;
        Random rng = new Random(chunkSeed ^ 0x3A7E55L);
        if (level > 1) {
            foulStrataWater(maze.getLiquidManager(), maze.getWidth(), maze.getHeight(), rng);
        } else if (biome != Biome.DESERT && biome != Biome.TUNDRA) {
            // The desert's oases and the tundra's slush are their own design; everywhere else
            // on the surface a traveller finds a puddle or two.
            addPuddles(maze, rng);
        }
        // After the fouling, so the promised spring stays clean.
        if (nearHome && level <= HOME_WATER_DEPTH
                && countWater(maze.getLiquidManager(), maze.getWidth(), maze.getHeight()) == 0) {
            addSpring(maze, rng);
        }
    }

    /** Whether {@code chunk} is within reach of the home Shelter or the one the soul now wakes in. */
    public static boolean nearShelter(GridPoint2 chunk, GridPoint2 home, GridPoint2 respawn) {
        return within(chunk, home) || within(chunk, respawn);
    }

    private static boolean within(GridPoint2 chunk, GridPoint2 shelter) {
        return shelter != null && Math.abs(chunk.x - shelter.x) <= HOME_REACH
                && Math.abs(chunk.y - shelter.y) <= HOME_REACH;
    }

    /** One small spring of clean water on open floor. */
    private static void addSpring(Maze maze, Random rng) {
        List<GridPoint2> open = openTiles(maze);
        if (open.isEmpty()) return;
        GridPoint2 c = open.get(rng.nextInt(open.size()));
        maze.getLiquidManager().setLiquidAt(c.x, c.y, LiquidType.WATER);
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] d : dirs) {
            if (isOpen(maze, c.x + d[0], c.y + d[1])) {
                maze.getLiquidManager().setLiquidAt(c.x + d[0], c.y + d[1], LiquidType.WATER);
                break;
            }
        }
    }

    /** What a strata water tile becomes, from a roll in [0, 1). */
    static LiquidType strataWater(float roll) {
        if (roll < STRATA_WATER_KEPT) return LiquidType.WATER;
        float r = (roll - STRATA_WATER_KEPT) / (1f - STRATA_WATER_KEPT);
        if (r < STRATA_TO_BLOOD) return LiquidType.BLOOD;
        if (r < STRATA_TO_BLOOD + STRATA_TO_MUCK) return LiquidType.BLACK_MUCK;
        return LiquidType.FREEZING_SLUSH;
    }

    static void foulStrataWater(LiquidManager liquids, int width, int height, Random rng) {
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (liquids.getLiquidAt(x, y) == LiquidType.WATER) {
                    liquids.setLiquidAt(x, y, strataWater(rng.nextFloat()));
                }
            }
        }
    }

    static int countWater(LiquidManager liquids, int width, int height) {
        int n = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (liquids.getLiquidAt(x, y) == LiquidType.WATER) n++;
            }
        }
        return n;
    }

    private static void addPuddles(Maze maze, Random rng) {
        LiquidManager liquids = maze.getLiquidManager();
        if (countWater(liquids, maze.getWidth(), maze.getHeight()) >= SURFACE_MIN_WATER) return;
        List<GridPoint2> open = openTiles(maze);
        if (open.isEmpty()) return;
        int puddles = PUDDLES_MIN + rng.nextInt(PUDDLES_MAX - PUDDLES_MIN + 1);
        for (int i = 0; i < puddles; i++) {
            GridPoint2 c = open.get(rng.nextInt(open.size()));
            liquids.setLiquidAt(c.x, c.y, LiquidType.WATER);
            // A puddle spreads to a neighbour or two.
            int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
            for (int[] d : dirs) {
                if (rng.nextFloat() < 0.4f && isOpen(maze, c.x + d[0], c.y + d[1])) {
                    liquids.setLiquidAt(c.x + d[0], c.y + d[1], LiquidType.WATER);
                }
            }
        }
    }

    /** Open floor away from the chunk's edge, where water may be laid. */
    private static List<GridPoint2> openTiles(Maze maze) {
        List<GridPoint2> open = new ArrayList<>();
        for (int y = 2; y < maze.getHeight() - 2; y++) {
            for (int x = 2; x < maze.getWidth() - 2; x++) {
                if (isOpen(maze, x, y)) open.add(new GridPoint2(x, y));
            }
        }
        return open;
    }

    private static boolean isOpen(Maze maze, int x, int y) {
        if (!maze.isPassable(x, y)) return false;
        if (maze.getLiquidAt(x, y) != LiquidType.NONE) return false;
        GridPoint2 pt = new GridPoint2(x, y);
        return !maze.getLadders().containsKey(pt) && !maze.getGates().containsKey(pt)
                && !maze.getItems().containsKey(pt) && !maze.getScenery().containsKey(pt)
                && !maze.isHomeTile(x, y);
    }
}
