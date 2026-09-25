package com.bpm.minotaur.generation;

import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.gamedata.*;
import com.bpm.minotaur.gamedata.encounters.EncounterManager;
import com.bpm.minotaur.gamedata.item.ItemDataManager;
import com.bpm.minotaur.gamedata.liquid.LiquidType;
import com.bpm.minotaur.gamedata.monster.MonsterDataManager;
import com.bpm.minotaur.gamedata.spawntables.SpawnTableData;
import com.bpm.minotaur.rendering.RetroTheme;

import java.util.Random;

/**
 * Procedural generator for the Lakelands Biome.
 * Generates an organic 36x36 wetland wilderness with marsh islets, winding
 * causeways, wadeable shallow water channels, and cypress thicket boundaries.
 */
public class LakelandsChunkGenerator implements IChunkGenerator {

    public static final int CHUNK_SIZE = 36;
    private final Random random = new Random();
    private final GridPoint2 playerSpawnPoint = new GridPoint2(18, 18);
    private GridPoint2 forcedUpLadderPos = null;

    public void setForcedUpLadderPos(GridPoint2 pos) {
        this.forcedUpLadderPos = pos;
    }

    @Override
    public Maze generateChunk(GridPoint2 chunkId, int layoutLevel, int spawnDifficulty, Difficulty difficulty,
                              GameMode gameMode, RetroTheme.Theme theme, RetroTheme.Theme mazeTheme,
                              MonsterDataManager dataManager,
                              ItemDataManager itemDataManager,
                              AssetManager assetManager,
                              EncounterManager encounterManager,
                              SpawnTableData spawnTableData,
                              long chunkSeed,
                              int playerLuck) {

        random.setSeed(chunkSeed);

        // Generate 36x36 layout with open perimeter and traversable causeways
        int width = CHUNK_SIZE;
        int height = CHUNK_SIZE;
        FastNoiseLite noise = new FastNoiseLite((int) chunkSeed);
        noise.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        noise.SetFrequency(0.08f);

        char[][] grid = new char[height][width];

        // 1. Initialise with walkable marsh floor ('.') and impassable boundary cliffs/thickets ('#')
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                // Keep outer border open for wilderness seamlessness
                if (x == 0 || x == width - 1 || y == 0 || y == height - 1) {
                    grid[y][x] = '.';
                    continue;
                }

                float n = noise.GetNoise(x, y);
                // Dense mangrove/cypress thickets where noise is elevated
                if (n > 0.35f) {
                    grid[y][x] = '#';
                } else {
                    grid[y][x] = '.';
                }
            }
        }

        // Forced UP ladder tile guarantee
        if (forcedUpLadderPos != null) {
            int ladderLayoutY = height - 1 - forcedUpLadderPos.y;
            if (ladderLayoutY >= 0 && ladderLayoutY < height && forcedUpLadderPos.x >= 0 && forcedUpLadderPos.x < width) {
                grid[ladderLayoutY][forcedUpLadderPos.x] = '.';
            }
        }

        int[][] bitmaskedData = new int[height][width];
        for (int y = 0; y < height; y++) {
            int layoutY = height - 1 - y;
            for (int x = 0; x < width; x++) {
                if (grid[layoutY][x] == '#') {
                    int mask = 0;
                    if (y + 1 < height && grid[layoutY - 1][x] != '#') mask |= 0b01000000;
                    if (x + 1 < width && grid[layoutY][x + 1] != '#') mask |= 0b00000100;
                    if (y - 1 >= 0 && grid[layoutY + 1][x] != '#') mask |= 0b00000001;
                    if (x - 1 >= 0 && grid[layoutY][x - 1] != '#') mask |= 0b00010000;
                    bitmaskedData[y][x] = mask;
                }
            }
        }

        Maze maze = new Maze(layoutLevel, bitmaskedData);
        maze.setBiome(Biome.LAKELANDS);
        maze.setTheme(theme != null ? theme : RetroTheme.LAKELANDS_THEME);
        maze.setSecondaryTheme(mazeTheme);

        // 2. Liquid distribution: shallow water shallows and muck pockets on walkable tiles
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (grid[y][x] == '.') {
                    float n = noise.GetNoise(x + 100, y + 100);
                    if (n > 0.15f) {
                        // Shallow wadeable water
                        maze.getLiquidManager().setLiquidAt(x, y, LiquidType.WATER);
                    } else if (n < -0.40f) {
                        // Deep stagnant muck pocket
                        maze.getLiquidManager().setLiquidAt(x, y, LiquidType.BLACK_MUCK);
                    }
                }
            }
        }

        return maze;
    }

    @Override
    public GridPoint2 getInitialPlayerStartPos() {
        return new GridPoint2(playerSpawnPoint);
    }
}
