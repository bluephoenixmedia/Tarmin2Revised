package com.bpm.minotaur.generation;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.gamedata.*;
import com.bpm.minotaur.gamedata.encounters.Encounter;
import com.bpm.minotaur.gamedata.encounters.EncounterManager;
import com.bpm.minotaur.gamedata.item.ItemDataManager;
import com.bpm.minotaur.gamedata.liquid.LiquidType;
import com.bpm.minotaur.gamedata.monster.Faction;
import com.bpm.minotaur.gamedata.monster.Monster;
import com.bpm.minotaur.gamedata.monster.Monster.MonsterType;
import com.bpm.minotaur.gamedata.monster.MonsterColor;
import com.bpm.minotaur.gamedata.monster.MonsterDataManager;
import com.bpm.minotaur.gamedata.monster.MonsterVariant;
import com.bpm.minotaur.gamedata.spawntables.SpawnTableData;
import com.bpm.minotaur.gamedata.spawntables.SpawnTableEntry;
import com.bpm.minotaur.managers.SpawnManager;
import com.bpm.minotaur.rendering.RetroTheme;
import com.bpm.minotaur.weather.WeatherType;

import java.util.*;

/**
 * Procedural generator for the Lakelands Biome.
 * Generates an expansive 36x36 wetland wilderness featuring cypress/mangrove thicket
 * boundaries, open marsh pools and side basins, wadeable murky shallows, toxic
 * muck depressions, and raised ancient stone causeways.
 */
public class LakelandsChunkGenerator implements IChunkGenerator {

    public static final int CHUNK_SIZE = 36;

    private final Random random = new Random();
    private final List<GridPoint2> sideBasins = new ArrayList<>();
    private String[] finalLayout;
    private final GridPoint2 playerSpawnPoint = new GridPoint2(18, 18);
    private GridPoint2 forcedUpLadderPos = null;

    /** A baked Lakelands sprite and the billboard size its canvas was rendered at. */
    private record Sprite(String path, float width, float height) {
        void applyTo(Scenery s, float jitter) {
            s.scale.set(width * jitter, height * jitter);
            s.setPixelOffsetY(0f);
        }
    }

    public static final Sprite[] SWAMP_TREES = {
            new Sprite("images/lakelands/tree_cypress_01.png", 2.0f, 3.8f),
            new Sprite("images/lakelands/tree_willow_01.png", 2.4f, 3.6f),
            new Sprite("images/lakelands/tree_dead_01.png", 1.9f, 3.2f),
            new Sprite("images/lakelands/tree_dead_02.png", 1.9f, 3.2f),
            new Sprite("images/lakelands/tree_dead_03.png", 2.2f, 3.4f),
            new Sprite("images/lakelands/tree_snag_01.png", 1.9f, 3.2f),
    };
    private static final Sprite[] DEAD_TREES = SWAMP_TREES;
    private static final Sprite[] REEDS = {
            new Sprite("images/lakelands/reeds_01.png", 0.7f, 1.0f),
            new Sprite("images/lakelands/reeds_02.png", 0.7f, 1.0f),
    };
    private static final Sprite[] SWAMP_GRASS = {
            new Sprite("images/lakelands/swamp_grass_01.png", 0.6f, 0.45f),
            new Sprite("images/lakelands/swamp_grass_02.png", 0.6f, 0.45f),
    };
    private static final Sprite[] LILYPADS = {
            new Sprite("images/lakelands/lilypads_01.png", 0.8f, 0.3f),
            new Sprite("images/lakelands/lilypads_02.png", 0.8f, 0.3f),
    };
    private static final Sprite[] DOCKS = {
            new Sprite("images/lakelands/dock_post.png", 0.8f, 1.5f),
            new Sprite("images/lakelands/dock_ramp.png", 1.4f, 0.9f),
    };
    private static final Sprite BOAT_WRECK = new Sprite("images/lakelands/boat_wreck.png", 2.2f, 1.0f);
    private static final Sprite SHRINE = new Sprite("images/lakelands/shrine_01.png", 1.2f, 1.8f);
    private static final Sprite STATUE = new Sprite("images/lakelands/statue_01.png", 1.0f, 2.0f);
    private static final Sprite MOUND = new Sprite("images/lakelands/mound_01.png", 1.3f, 0.7f);
    private static final Sprite BONES_RIB = new Sprite("images/lakelands/bones_rib.png", 1.0f, 0.7f);
    private static final Sprite BEAST_SKULL = new Sprite("images/lakelands/beast_skull.png", 1.6f, 1.2f);
    private static final Sprite ROOT = new Sprite("images/lakelands/root_01.png", 0.9f, 0.4f);
    private static final Sprite ROCK_MOSS = new Sprite("images/lakelands/rock_moss.png", 1.1f, 0.9f);
    private static final Sprite GLOWPLANT = new Sprite("images/lakelands/glowplant.png", 0.7f, 0.6f);
    private static final Sprite UNDERWATER_PLANT = new Sprite("images/lakelands/underwater_plant.png", 0.6f, 0.5f);

    /** Ground scatter on water tiles: lilypads, reeds, underwater flora. */
    private static final Sprite[] WATER_SCATTER = {
            LILYPADS[0], LILYPADS[1], REEDS[0], UNDERWATER_PLANT
    };

    /** Ground scatter on land tiles: swamp grass, roots, mossy rock, glowplants. */
    private static final Sprite[] LAND_SCATTER = {
            SWAMP_GRASS[0], SWAMP_GRASS[1], ROOT, GLOWPLANT, ROCK_MOSS
    };

    private static final MonsterType[] LAKELANDS_FAUNA = {
            MonsterType.GIANT_SNAKE,
            MonsterType.TROGLODYTE,
            MonsterType.ALLIGATOR,
            MonsterType.HARPY,
            MonsterType.GELATINOUS_CUBE,
            MonsterType.PURPLE_WORM
    };

    /** Every baked Lakelands texture, for the asset preload. */
    public static List<String> textures() {
        List<String> paths = new ArrayList<>();
        for (Sprite[] set : new Sprite[][]{DEAD_TREES, REEDS, SWAMP_GRASS, LILYPADS, DOCKS, WATER_SCATTER, LAND_SCATTER}) {
            for (Sprite sp : set) paths.add(sp.path());
        }
        paths.addAll(Arrays.asList(
                BOAT_WRECK.path(), SHRINE.path(), STATUE.path(), MOUND.path(),
                BONES_RIB.path(), BEAST_SKULL.path(), ROOT.path(), ROCK_MOSS.path(),
                GLOWPLANT.path(), UNDERWATER_PLANT.path()
        ));
        return paths;
    }

    private final Map<String, Texture> textureCache = new HashMap<>();

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

        // 1. Procedural 36x36 wetland layout with elevation bands and side basins
        createProceduralLakelandsLayout(CHUNK_SIZE, CHUNK_SIZE);

        int height = this.finalLayout.length;
        int width = this.finalLayout[0].length();

        // 2. Border logic adjacent to central dungeon maze
        boolean northIsMaze = isMaze(chunkId.x, chunkId.y + 1);
        boolean southIsMaze = isMaze(chunkId.x, chunkId.y - 1);
        boolean eastIsMaze = isMaze(chunkId.x + 1, chunkId.y);
        boolean westIsMaze = isMaze(chunkId.x - 1, chunkId.y);

        if (northIsMaze) {
            char[] row = this.finalLayout[0].toCharArray();
            for (int x = 0; x < width; x++) {
                if (row[x] == '#') row[x] = 'M';
            }
            this.finalLayout[0] = new String(row);
        }

        if (southIsMaze) {
            char[] row = this.finalLayout[height - 1].toCharArray();
            for (int x = 0; x < width; x++) {
                if (row[x] == '#') row[x] = 'M';
            }
            this.finalLayout[height - 1] = new String(row);
        }

        for (int y = 0; y < height; y++) {
            char[] row = this.finalLayout[y].toCharArray();
            boolean changed = false;
            if (westIsMaze && row[0] == '#') {
                row[0] = 'M';
                changed = true;
            }
            if (eastIsMaze && row[width - 1] == '#') {
                row[width - 1] = 'M';
                changed = true;
            }
            if (changed) {
                this.finalLayout[y] = new String(row);
            }
        }

        // Forced UP ladder tile guarantee
        if (forcedUpLadderPos != null) {
            int ladderLayoutY = height - 1 - forcedUpLadderPos.y;
            if (ladderLayoutY >= 0 && ladderLayoutY < height && forcedUpLadderPos.x >= 0 && forcedUpLadderPos.x < width) {
                char[] r = this.finalLayout[ladderLayoutY].toCharArray();
                r[forcedUpLadderPos.x] = '.';
                this.finalLayout[ladderLayoutY] = new String(r);
            }
        }

        // 3. Create Maze and assign biome/theme
        Maze maze = createMazeFromText(layoutLevel, this.finalLayout, itemDataManager, assetManager);
        maze.setBiome(Biome.LAKELANDS);
        maze.setTheme(theme != null ? theme : RetroTheme.LAKELANDS_THEME);
        maze.setSecondaryTheme(mazeTheme);

        // 4. Distribute wetland liquids: shallow wadeable water and toxic muck
        distributeWetlandLiquids(maze, chunkSeed);

        // 5. Compute traversable reachable tiles
        Set<GridPoint2> reachable = computeReachableTiles(maze);

        // 6. Spawn Central & Side Basin Landmarks & Scenery
        spawnLandmarks(maze, reachable, assetManager, chunkSeed);

        // 7. Spawn Swamp Trees (Cypress, Willow, and Dead Snag Copses & Groves)
        spawnSwampTrees(maze, reachable, assetManager, chunkSeed);

        // 8. Scatter ground cover decoration (render-only backdrop scenery)
        scatterGroundCover(maze, assetManager, chunkSeed);

        // 8. Spawn Monsters, Items, Encounters, Ladders
        spawnEntities(maze, difficulty, spawnDifficulty, this.finalLayout, dataManager, itemDataManager, assetManager,
                spawnTableData, chunkSeed, playerLuck, reachable);

        spawnLakelandsFauna(maze, reachable, dataManager, assetManager, spawnTableData, layoutLevel, chunkSeed);

        spawnEncounters(maze, encounterManager, reachable, assetManager);

        spawnLadder(maze, this.finalLayout, reachable);

        // 9. Spawn Chunk Transition Gates at 4 Cardinal Gates
        spawnTransitionGates(maze, this.finalLayout, chunkId);

        // 10. Find valid player start position
        findPlayerStart(this.finalLayout);

        return maze;
    }

    @Override
    public GridPoint2 getInitialPlayerStartPos() {
        return playerSpawnPoint;
    }

    private void createProceduralLakelandsLayout(int width, int height) {
        char[][] grid = new char[height][width];
        sideBasins.clear();

        // 1. Initialise with perimeter boundary thickets ('#') and open marsh floor ('.')
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (x == 0 || x == width - 1 || y == 0 || y == height - 1) {
                    grid[y][x] = '#';
                } else {
                    grid[y][x] = '.';
                }
            }
        }

        // 2. Generate elevation using FastNoiseLite Simplex
        FastNoiseLite noise = new FastNoiseLite(random.nextInt());
        noise.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        noise.SetFrequency(0.075f);

        int midX = width / 2;  // 18
        int midY = height / 2; // 18

        for (int y = 2; y < height - 2; y++) {
            for (int x = 2; x < width - 2; x++) {
                float n = noise.GetNoise(x, y);
                if (n > 0.28f) {
                    grid[y][x] = '#'; // Dense cypress/mangrove thicket wall
                }
            }
        }

        // 3. Carve Central Marsh Clearing (circular islet and pool around 18, 18)
        float isletRadius = 5.2f;
        for (int y = midY - 7; y <= midY + 7; y++) {
            for (int x = midX - 7; x <= midX + 7; x++) {
                if (x <= 1 || x >= width - 2 || y <= 1 || y >= height - 2) continue;
                float dx = x - midX;
                float dy = y - midY;
                float dist = (float) Math.sqrt(dx * dx + dy * dy);
                if (dist <= isletRadius + 0.6f * (float) Math.sin(dx * 1.5f + dy * 1.2f)) {
                    grid[y][x] = '.';
                }
            }
        }

        // 4. Carve 2 to 3 Side Basins in quadrants
        int[][] quadrantCenters = {
                {9, 9}, {27, 9}, {9, 27}, {27, 27}
        };
        List<int[]> quadList = new ArrayList<>(Arrays.asList(quadrantCenters));
        Collections.shuffle(quadList, random);

        int basinCount = 2 + random.nextInt(2); // 2 or 3 basins
        for (int i = 0; i < basinCount; i++) {
            int[] qc = quadList.get(i);
            int bx = qc[0] + random.nextInt(3) - 1;
            int by = qc[1] + random.nextInt(3) - 1;
            sideBasins.add(new GridPoint2(bx, by));

            float basinRadius = 3.8f + random.nextFloat() * 1.2f;
            for (int y = by - 5; y <= by + 5; y++) {
                for (int x = bx - 5; x <= bx + 5; x++) {
                    if (x <= 1 || x >= width - 2 || y <= 1 || y >= height - 2) continue;
                    float dx = x - bx;
                    float dy = y - by;
                    if (dx * dx + dy * dy <= basinRadius * basinRadius) {
                        grid[y][x] = '.';
                    }
                }
            }
            // Link side basin to central clearing with a 2-wide channel
            carveCauseway(grid, bx, by, midX, midY, 1);
        }

        // 5. Clear 4 Cardinal Gate Openings
        // North Gate at (18, 35)
        for (int y = height - 1; y >= height - 4; y--) {
            grid[y][midX] = '.';
            grid[y][midX - 1] = '.';
            grid[y][midX + 1] = '.';
        }
        // South Gate at (18, 0)
        for (int y = 0; y <= 3; y++) {
            grid[y][midX] = '.';
            grid[y][midX - 1] = '.';
            grid[y][midX + 1] = '.';
        }
        // East Gate at (35, 18)
        for (int x = width - 1; x >= width - 4; x--) {
            grid[midY][x] = '.';
            grid[midY - 1][x] = '.';
            grid[midY + 1][x] = '.';
        }
        // West Gate at (0, 18)
        for (int x = 0; x <= 3; x++) {
            grid[midY][x] = '.';
            grid[midY - 1][x] = '.';
            grid[midY + 1][x] = '.';
        }

        // 6. Carve Causeway Corridors connecting gates to central islet (width 2)
        carveCauseway(grid, midX, 3, midX, midY - 3, 2);
        carveCauseway(grid, midX, height - 4, midX, midY + 3, 2);
        carveCauseway(grid, 3, midY, midX - 3, midY, 2);
        carveCauseway(grid, width - 4, midY, midX + 3, midY, 2);

        // 7. Perimeter rescue causeway ring linking all four gate approaches
        int ringLow = 6;
        int ringHighX = width - 7;
        int ringHighY = height - 7;

        carveCauseway(grid, midX, 3, ringLow, ringLow, 1);
        carveCauseway(grid, ringLow, ringLow, ringLow, midY, 1);
        carveCauseway(grid, ringLow, midY, ringLow, ringHighY, 1);
        carveCauseway(grid, ringLow, ringHighY, midX, height - 4, 1);
        carveCauseway(grid, midX, height - 4, ringHighX, ringHighY, 1);
        carveCauseway(grid, ringHighX, ringHighY, ringHighX, midY, 1);
        carveCauseway(grid, ringHighX, midY, ringHighX, ringLow, 1);
        carveCauseway(grid, ringHighX, ringLow, midX, 3, 1);

        // 8. Rescue Corridor Guarantee: ensure all four gates reach the central islet
        ensureGatesReachGlade(grid, midX, midY);

        // Convert grid (y: 0..35 South to North) to finalLayout (index 0 is North, index 35 is South)
        this.finalLayout = new String[height];
        for (int y = 0; y < height; y++) {
            int layoutY = height - 1 - y;
            this.finalLayout[layoutY] = new String(grid[y]);
        }
    }

    private void carveCauseway(char[][] grid, int x0, int y0, int x1, int y1, int radius) {
        int width = grid[0].length;
        int height = grid.length;

        int curX = x0;
        int curY = y0;
        while (curX != x1 || curY != y1) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    int nx = curX + dx;
                    int ny = curY + dy;
                    if (nx >= 2 && nx < width - 2 && ny >= 2 && ny < height - 2) {
                        grid[ny][nx] = '.';
                    }
                }
            }

            if (curX != x1) {
                curX += Integer.signum(x1 - curX);
            } else if (curY != y1) {
                curY += Integer.signum(y1 - curY);
            }
        }

        for (int dy = -radius; dy <= radius; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                int nx = x1 + dx;
                int ny = y1 + dy;
                if (nx >= 2 && nx < width - 2 && ny >= 2 && ny < height - 2) {
                    grid[ny][nx] = '.';
                }
            }
        }
    }

    private void ensureGatesReachGlade(char[][] grid, int midX, int midY) {
        int height = grid.length;
        int width = grid[0].length;

        Set<Integer> reachable = floodFillOpen(grid, midX, midY);

        int[][] approaches = {
                {midX, 3},
                {midX, height - 4},
                {3, midY},
                {width - 4, midY}
        };

        for (int[] approach : approaches) {
            if (reachable.contains(approach[1] * width + approach[0])) continue;

            carveCauseway(grid, approach[0], approach[1], midX, midY, 1);
            reachable = floodFillOpen(grid, midX, midY);
        }
    }

    private Set<Integer> floodFillOpen(char[][] grid, int startX, int startY) {
        int height = grid.length;
        int width = grid[0].length;

        Set<Integer> seen = new HashSet<>();
        ArrayDeque<int[]> queue = new ArrayDeque<>();

        if (grid[startY][startX] == '#') return seen;
        queue.add(new int[]{startX, startY});
        seen.add(startY * width + startX);

        int[] dx = {1, -1, 0, 0};
        int[] dy = {0, 0, 1, -1};

        while (!queue.isEmpty()) {
            int[] cur = queue.poll();
            for (int i = 0; i < 4; i++) {
                int nx = cur[0] + dx[i];
                int ny = cur[1] + dy[i];
                if (nx < 0 || nx >= width || ny < 0 || ny >= height) continue;
                if (grid[ny][nx] == '#') continue;
                int key = ny * width + nx;
                if (!seen.add(key)) continue;
                queue.add(new int[]{nx, ny});
            }
        }
        return seen;
    }

    private void distributeWetlandLiquids(Maze maze, long seed) {
        FastNoiseLite liquidNoise = new FastNoiseLite((int) (seed ^ 0x51A7F00DL));
        liquidNoise.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        liquidNoise.SetFrequency(0.09f);

        for (int y = 1; y < CHUNK_SIZE - 1; y++) {
            for (int x = 1; x < CHUNK_SIZE - 1; x++) {
                if (maze.isWall(x, y)) continue;

                // Keep gate approaches and exact center dry for clean footing
                if ((Math.abs(x - 18) <= 1 && Math.abs(y - 18) <= 1)
                        || (x == 18 && (y <= 3 || y >= CHUNK_SIZE - 4))
                        || (y == 18 && (x <= 3 || x >= CHUNK_SIZE - 4))) {
                    continue;
                }

                float n = liquidNoise.GetNoise(x, y);
                if (n > 0.10f) {
                    // Wadeable shallow water
                    maze.getLiquidManager().setLiquidAt(x, y, LiquidType.WATER);
                } else if (n < -0.40f) {
                    // Toxic stagnant muck depression
                    maze.getLiquidManager().setLiquidAt(x, y, LiquidType.BLACK_MUCK);
                }
            }
        }
    }

    /**
     * Weather tide: during rain or storm, the water table rises across low causeways.
     */
    public void onWeatherChange(WeatherType weather, Maze maze, long seed) {
        if (maze == null || maze.getLiquidManager() == null) return;
        boolean flooding = (weather == WeatherType.RAIN || weather == WeatherType.STORM);
        if (!flooding) return;

        FastNoiseLite liquidNoise = new FastNoiseLite((int) (seed ^ 0x51A7F00DL));
        liquidNoise.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        liquidNoise.SetFrequency(0.09f);

        for (int y = 2; y < CHUNK_SIZE - 2; y++) {
            for (int x = 2; x < CHUNK_SIZE - 2; x++) {
                if (maze.isWall(x, y) || maze.getLiquidManager().hasLiquidAt(x, y)) continue;
                // Exclude central spawn and gate direct lines
                if (Math.abs(x - 18) <= 1 && Math.abs(y - 18) <= 1) continue;
                if (x == 18 || y == 18) continue;

                float n = liquidNoise.GetNoise(x, y);
                if (n > -0.05f && n <= 0.10f) {
                    // Low causeway submerged by tide surge
                    maze.getLiquidManager().setLiquidAt(x, y, LiquidType.WATER);
                }
            }
        }
    }

    private void spawnLandmarks(Maze maze, Set<GridPoint2> reachable, AssetManager assetManager, long seed) {
        int archetype = (int) (Math.abs(seed) % 3);
        int cx = 18;
        int cy = 18;

        if (archetype == 0) {
            // Archetype 0: Sunken Shrine on islet bank
            placeSolidSprite(maze, SHRINE, cx + 2, cy + 2, 1f, assetManager);
            placeScenerySprite(maze, STATUE, cx + 3, cy + 1, 1f, assetManager);
            placeScenerySprite(maze, LILYPADS[0], cx - 2, cy - 2, 1f, assetManager);
            placeScenerySprite(maze, REEDS[0], cx + 2, cy - 2, 1f, assetManager);
        } else if (archetype == 1) {
            // Archetype 1: Wrecked Skiff & Stilt Fisher Pier
            placeSolidSprite(maze, BOAT_WRECK, cx + 2, cy + 2, 1f, assetManager);
            placeScenerySprite(maze, DOCKS[1], cx - 2, cy - 2, 1f, assetManager);
            placeScenerySprite(maze, REEDS[0], cx - 3, cy - 2, 1f, assetManager);
            placeScenerySprite(maze, ROOT, cx + 3, cy + 1, 1f, assetManager);
        } else {
            // Archetype 2: Rotting Bog Barrow
            placeSolidSprite(maze, MOUND, cx + 2, cy + 2, 1f, assetManager);
            placeScenerySprite(maze, BEAST_SKULL, cx + 3, cy + 1, 1f, assetManager);
            placeScenerySprite(maze, BONES_RIB, cx - 2, cy - 2, 1f, assetManager);
            placeScenerySprite(maze, GLOWPLANT, cx + 1, cy - 3, 1f, assetManager);
        }

        // Side Basin Landmarks
        for (int i = 0; i < sideBasins.size(); i++) {
            GridPoint2 bp = sideBasins.get(i);
            if (!reachable.contains(bp)) continue;

            if (i == 0) {
                placeScenerySprite(maze, STATUE, bp.x, bp.y, 1f, assetManager);
                placeScenerySprite(maze, LILYPADS[1], bp.x + 1, bp.y, 1f, assetManager);
            } else if (i == 1) {
                placeScenerySprite(maze, BEAST_SKULL, bp.x, bp.y, 1f, assetManager);
                placeScenerySprite(maze, REEDS[1], bp.x - 1, bp.y + 1, 1f, assetManager);
            } else {
                placeScenerySprite(maze, ROCK_MOSS, bp.x, bp.y, 1f, assetManager);
                placeScenerySprite(maze, GLOWPLANT, bp.x + 1, bp.y - 1, 1f, assetManager);
            }
        }
    }

    private void spawnSwampTrees(Maze maze, Set<GridPoint2> reachable, AssetManager assetManager, long seed) {
        Random rng = new Random(seed ^ 0x72EE51A4EL);

        // 1. Solid Trees (Physical Obstacle Copses & Groves)
        List<GridPoint2> candidates = new ArrayList<>();
        for (GridPoint2 pt : reachable) {
            // Keep central spawn clearing clear
            if (Math.abs(pt.x - 18) <= 2 && Math.abs(pt.y - 18) <= 2) continue;
            // Keep main cardinal walking avenues clear
            if (Math.abs(pt.x - 18) <= 1 || Math.abs(pt.y - 18) <= 1) continue;
            // Keep gate approaches clear
            if (pt.x <= 3 || pt.x >= CHUNK_SIZE - 4 || pt.y <= 3 || pt.y >= CHUNK_SIZE - 4) continue;
            // Avoid landmarks and existing scenery
            if (maze.getScenery().containsKey(pt) || maze.getGateAt(pt.x, pt.y) != null) continue;

            candidates.add(pt);
        }
        Collections.shuffle(candidates, rng);

        int currentReachable = countReachable(maze);
        int targetSolid = 36 + rng.nextInt(12); // ~36-48 solid trees
        int solidPlaced = 0;
        List<GridPoint2> solidTreeLocations = new ArrayList<>();

        for (GridPoint2 pt : candidates) {
            if (solidPlaced >= targetSolid) break;

            Scenery tree = new Scenery(Scenery.SceneryType.TREE, pt.x, pt.y);
            tree.setImpassable(true);
            tree.setFlippedX(rng.nextBoolean());
            Sprite sp = SWAMP_TREES[rng.nextInt(SWAMP_TREES.length)];
            float jitter = 0.90f + rng.nextFloat() * 0.25f;
            sp.applyTo(tree, jitter);
            loadTextureSafely(tree, sp.path(), assetManager);
            maze.addScenery(tree);

            int after = countReachable(maze);
            // Must not disconnect reachable open ground
            if (after < currentReachable - 1) {
                maze.getScenery().remove(pt);
                continue;
            }
            currentReachable = after;
            solidTreeLocations.add(pt);
            solidPlaced++;
        }

        // 2. Clustered Stand / Companion Trees (Visual Backdrop Depth behind/beside solid trees)
        for (GridPoint2 pt : solidTreeLocations) {
            int companionCount = 1 + (rng.nextFloat() < 0.45f ? 1 : 0);
            for (int i = 0; i < companionCount; i++) {
                float ox = (rng.nextFloat() - 0.5f) * 0.7f;
                float oy = (rng.nextFloat() - 0.5f) * 0.7f;
                Scenery trunk = new Scenery(Scenery.SceneryType.TREE, pt.x, pt.y);
                trunk.getPosition().set(pt.x + 0.5f + ox, pt.y + 0.5f + oy);
                trunk.setFlippedX(rng.nextBoolean());
                Sprite sp = SWAMP_TREES[rng.nextInt(SWAMP_TREES.length)];
                float jitter = 0.75f + rng.nextFloat() * 0.35f;
                sp.applyTo(trunk, jitter);
                loadTextureSafely(trunk, sp.path(), assetManager);
                maze.addBackdropScenery(trunk);
            }
        }

        // 3. Bluff Edge Trees (Rising along the foot and ledges of the thicket/bluff walls '#')
        for (int y = 2; y < CHUNK_SIZE - 2; y++) {
            for (int x = 2; x < CHUNK_SIZE - 2; x++) {
                if (!maze.isWall(x, y)) continue;
                if (!facesOpenGround(x, y)) continue;
                if (rng.nextFloat() > 0.35f) continue;

                float[] offset = directionToOpenGround(x, y);
                float px = x + 0.5f + offset[0] * 0.35f;
                float py = y + 0.5f + offset[1] * 0.35f;

                Scenery bluffTree = new Scenery(Scenery.SceneryType.TREE, x, y);
                bluffTree.getPosition().set(px, py);
                bluffTree.setFlippedX(rng.nextBoolean());
                Sprite sp = SWAMP_TREES[rng.nextInt(SWAMP_TREES.length)];
                float jitter = 0.85f + rng.nextFloat() * 0.35f;
                sp.applyTo(bluffTree, jitter);
                loadTextureSafely(bluffTree, sp.path(), assetManager);
                maze.addBackdropScenery(bluffTree);
            }
        }

        // 4. Water Pools & Basin Trees (Bald cypress and weeping willows rising from the shallows)
        for (int y = 3; y < CHUNK_SIZE - 3; y++) {
            for (int x = 3; x < CHUNK_SIZE - 3; x++) {
                if (maze.isWall(x, y)) continue;
                if (Math.abs(x - 18) <= 1 && Math.abs(y - 18) <= 1) continue;
                if (x == 18 || y == 18) continue;

                boolean isWater = maze.getLiquidManager() != null && maze.getLiquidManager().hasLiquidAt(x, y);
                float chance = isWater ? 0.22f : 0.15f;
                if (rng.nextFloat() > chance) continue;

                GridPoint2 pt = new GridPoint2(x, y);
                if (maze.getScenery().containsKey(pt) || maze.getGateAt(x, y) != null) continue;

                float ox = (rng.nextFloat() - 0.5f) * 0.5f;
                float oy = (rng.nextFloat() - 0.5f) * 0.5f;

                Scenery waterTree = new Scenery(Scenery.SceneryType.TREE, x, y);
                waterTree.getPosition().set(x + 0.5f + ox, y + 0.5f + oy);
                waterTree.setFlippedX(rng.nextBoolean());

                Sprite sp;
                if (isWater) {
                    float roll = rng.nextFloat();
                    if (roll < 0.40f) sp = SWAMP_TREES[0]; // cypress
                    else if (roll < 0.70f) sp = SWAMP_TREES[1]; // willow
                    else sp = SWAMP_TREES[2 + rng.nextInt(4)]; // dead snags
                } else {
                    sp = SWAMP_TREES[rng.nextInt(SWAMP_TREES.length)];
                }

                float jitter = 0.85f + rng.nextFloat() * 0.35f;
                sp.applyTo(waterTree, jitter);
                loadTextureSafely(waterTree, sp.path(), assetManager);
                maze.addBackdropScenery(waterTree);
            }
        }
    }

    private boolean facesOpenGround(int x, int y) {
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] d : dirs) {
            int nx = x + d[0];
            int ny = y + d[1];
            if (nx >= 0 && nx < CHUNK_SIZE && ny >= 0 && ny < CHUNK_SIZE) {
                int ly = CHUNK_SIZE - 1 - ny;
                if (isTraversable(finalLayout[ly].charAt(nx))) return true;
            }
        }
        return false;
    }

    private float[] directionToOpenGround(int x, int y) {
        float ox = 0f;
        float oy = 0f;
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] d : dirs) {
            int nx = x + d[0];
            int ny = y + d[1];
            if (nx >= 0 && nx < CHUNK_SIZE && ny >= 0 && ny < CHUNK_SIZE) {
                int ly = CHUNK_SIZE - 1 - ny;
                if (isTraversable(finalLayout[ly].charAt(nx))) {
                    ox += d[0];
                    oy += d[1];
                }
            }
        }
        float len = (float) Math.sqrt(ox * ox + oy * oy);
        if (len > 0f) {
            ox /= len;
            oy /= len;
        }
        return new float[]{ox, oy};
    }

    private int countReachable(Maze maze) {
        int width = maze.getWidth();
        int height = maze.getHeight();
        boolean[] seen = new boolean[width * height];
        ArrayDeque<GridPoint2> queue = new ArrayDeque<>();
        GridPoint2 start = new GridPoint2(width / 2, height / 2);
        if (!maze.isPassable(start.x, start.y)) return 0;
        seen[start.y * width + start.x] = true;
        queue.add(start);
        int count = 0;
        int[][] steps = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!queue.isEmpty()) {
            GridPoint2 c = queue.poll();
            count++;
            for (int[] d : steps) {
                int nx = c.x + d[0];
                int ny = c.y + d[1];
                if (nx < 0 || ny < 0 || nx >= width || ny >= height || seen[ny * width + nx]) continue;
                int ly = height - 1 - ny;
                if (!isTraversable(finalLayout[ly].charAt(nx))) continue;
                if (!maze.isPassable(nx, ny)) continue;
                seen[ny * width + nx] = true;
                queue.add(new GridPoint2(nx, ny));
            }
        }
        return count;
    }

    private void scatterGroundCover(Maze maze, AssetManager assetManager, long seed) {
        Random rng = new Random(seed ^ 0x7EA1A4E998877L);
        for (int y = 2; y < CHUNK_SIZE - 2; y++) {
            for (int x = 2; x < CHUNK_SIZE - 2; x++) {
                if (maze.isWall(x, y)) continue;
                GridPoint2 pt = new GridPoint2(x, y);
                if (maze.getScenery().containsKey(pt) || maze.getGateAt(x, y) != null) continue;
                // Cardinal corridors and center glade must stay clear of blocking sightline debris
                if (Math.abs(x - 18) <= 2 || Math.abs(y - 18) <= 2) continue;

                // Scatter on ~20% of open tiles
                if (rng.nextFloat() > 0.20f) continue;

                boolean isWater = maze.getLiquidManager() != null && maze.getLiquidManager().hasLiquidAt(x, y);
                Sprite[] pool = isWater ? WATER_SCATTER : LAND_SCATTER;
                Sprite pick = pool[rng.nextInt(pool.length)];

                Scenery sc = new Scenery(Scenery.SceneryType.PROP, x, y, pick.path());
                pick.applyTo(sc, 0.85f + rng.nextFloat() * 0.3f);
                loadTextureSafely(sc, pick.path(), assetManager);
                maze.addBackdropScenery(sc);
            }
        }
    }

    private void placeSolidSprite(Maze maze, Sprite sp, int x, int y, float jitter, AssetManager assetManager) {
        if (x < 1 || x >= CHUNK_SIZE - 1 || y < 1 || y >= CHUNK_SIZE - 1) return;
        if (maze.isWall(x, y)) return;
        GridPoint2 pos = new GridPoint2(x, y);
        if (maze.getScenery().containsKey(pos)) return;

        Scenery sc = new Scenery(Scenery.SceneryType.PROP, x, y, sp.path());
        sc.setImpassable(true);
        sp.applyTo(sc, jitter);
        loadTextureSafely(sc, sp.path(), assetManager);
        maze.addScenery(sc);
    }

    private void placeScenerySprite(Maze maze, Sprite sp, int x, int y, float jitter, AssetManager assetManager) {
        if (x < 1 || x >= CHUNK_SIZE - 1 || y < 1 || y >= CHUNK_SIZE - 1) return;
        if (maze.isWall(x, y)) return;
        GridPoint2 pos = new GridPoint2(x, y);
        if (maze.getScenery().containsKey(pos)) return;

        Scenery sc = new Scenery(Scenery.SceneryType.PROP, x, y, sp.path());
        sp.applyTo(sc, jitter);
        loadTextureSafely(sc, sp.path(), assetManager);
        maze.addScenery(sc);
    }

    static List<MonsterType> faunaFor(SpawnTableData table, int level) {
        List<MonsterType> eligible = new ArrayList<>();
        if (table == null || table.monsterSpawnTable == null) return eligible;
        for (MonsterType type : LAKELANDS_FAUNA) {
            if (weightOf(table, type, level) > 0) eligible.add(type);
        }
        return eligible;
    }

    private static int weightOf(SpawnTableData table, MonsterType type, int level) {
        for (SpawnTableEntry e : table.monsterSpawnTable) {
            if (type.name().equals(e.type) && level >= e.minLevel && level <= e.maxLevel) return e.weight;
        }
        return 0;
    }

    private void spawnLakelandsFauna(Maze maze, Set<GridPoint2> reachable, MonsterDataManager dataManager,
                                     AssetManager assetManager, SpawnTableData spawnTableData, int level, long seed) {
        List<MonsterType> fauna = faunaFor(spawnTableData, level);
        if (dataManager == null || fauna.isEmpty()) return;

        int total = 0;
        for (MonsterType type : fauna) total += weightOf(spawnTableData, type, level);
        if (total <= 0) return;

        Random rng = new Random(seed ^ 0xBADC0FFEE1234567L);
        List<GridPoint2> spots = new ArrayList<>(reachable);
        Collections.shuffle(spots, rng);

        int faunaCount = 2 + rng.nextInt(3);
        int placed = 0;
        for (GridPoint2 pt : spots) {
            if (placed >= faunaCount) break;
            if (Math.abs(pt.x - 18) <= 5 && Math.abs(pt.y - 18) <= 5) continue;
            if (maze.getMonsters().containsKey(pt) || maze.getScenery().containsKey(pt)) continue;

            int pick = rng.nextInt(total);
            MonsterType type = fauna.get(0);
            for (MonsterType t : fauna) {
                pick -= weightOf(spawnTableData, t, level);
                if (pick < 0) {
                    type = t;
                    break;
                }
            }

            MonsterVariant variant = dataManager.getRandomVariantForMonster(type, level);
            MonsterColor color = (variant != null) ? variant.color : MonsterColor.WHITE;
            Monster m = new Monster(type, pt.x, pt.y, color, dataManager, assetManager);
            m.scaleStats(level);
            m.setCurrentHP(m.getMaxHP());
            m.setFaction(Faction.BEASTS_AND_VERMIN);
            maze.addMonster(m);
            placed++;
        }
    }

    private void spawnEntities(Maze maze, Difficulty difficulty, int level, String[] layout,
                               MonsterDataManager dataManager, ItemDataManager itemDataManager, AssetManager assetManager,
                               SpawnTableData spawnTableData, long chunkSeed, int playerLuck, Set<GridPoint2> reachable) {
        if (spawnTableData == null) return;
        long spawnSeed = chunkSeed ^ 0xFEEDFACECAFEBABEL;
        SpawnManager spawnManager = new SpawnManager(dataManager, itemDataManager, assetManager,
                maze, difficulty, level, level, playerLuck, layout, spawnTableData, spawnSeed, reachable);
        spawnManager.spawnEntities();
    }

    private void spawnEncounters(Maze maze, EncounterManager encounterManager, Set<GridPoint2> reachable, AssetManager assetManager) {
        if (encounterManager == null) return;

        List<GridPoint2> candidates = new ArrayList<>();
        int height = maze.getHeight();
        for (GridPoint2 tile : reachable) {
            int layoutY = height - 1 - tile.y;
            if (layoutY < 0 || layoutY >= finalLayout.length) continue;
            if (finalLayout[layoutY].charAt(tile.x) != '.') continue;
            if (maze.getScenery().containsKey(tile)) continue;
            if (maze.getItems().containsKey(tile)) continue;
            if (maze.getMonsters().containsKey(tile)) continue;
            if (maze.getEventAt(tile.x, tile.y) != null) continue;
            candidates.add(tile);
        }
        Collections.shuffle(candidates, random);

        int numEncounters = 1 + random.nextInt(3);
        int placed = 0;
        for (int i = 0; i < candidates.size() && placed < numEncounters; i++) {
            GridPoint2 pos = candidates.get(i);
            String encounterId = encounterManager.getRandomEncounterId();
            if (encounterId != null) {
                maze.addEvent(pos.x, pos.y, encounterId);
                Encounter enc = encounterManager.getEncounter(encounterId);
                String img = (enc != null) ? enc.imagePath : null;
                Scenery statue = new Scenery(Scenery.SceneryType.STATUE, pos.x, pos.y, img);
                if (img != null && assetManager != null) {
                    loadTextureSafely(statue, img, assetManager);
                }
                maze.addScenery(statue);
                placed++;
            }
        }
    }

    private void spawnLadder(Maze maze, String[] layout, Set<GridPoint2> reachable) {
        List<GridPoint2> candidates = new ArrayList<>();
        int height = maze.getHeight();
        int midX = maze.getWidth() / 2;
        int midY = maze.getHeight() / 2;

        for (GridPoint2 tile : reachable) {
            int layoutY = height - 1 - tile.y;
            if (layoutY < 0 || layoutY >= layout.length) continue;
            if (layout[layoutY].charAt(tile.x) != '.') continue;
            if (maze.getScenery().containsKey(tile)) continue;
            if (maze.getItems().containsKey(tile)) continue;
            if (maze.getMonsters().containsKey(tile)) continue;
            if (maze.getEventAt(tile.x, tile.y) != null) continue;

            float distFromCenter = (float) Math.hypot(tile.x - midX, tile.y - midY);
            if (distFromCenter >= 6.0f) {
                candidates.add(tile);
            }
        }

        if (candidates.isEmpty()) {
            candidates.addAll(reachable);
        }

        if (!candidates.isEmpty()) {
            Collections.shuffle(candidates, random);
            GridPoint2 ladderPos = candidates.get(0);
            Ladder ladder = new Ladder(ladderPos.x, ladderPos.y, Ladder.LadderType.DOWN, Ladder.EntranceStyle.SINKHOLE);
            maze.addLadder(ladder);
        }
    }

    private void spawnTransitionGates(Maze maze, String[] layout, GridPoint2 chunkId) {
        int height = layout.length;
        int width = layout[0].length();

        GridPoint2 northPos = new GridPoint2(width / 2, height - 1);
        GridPoint2 southPos = new GridPoint2(width / 2, 0);
        GridPoint2 eastPos = new GridPoint2(width - 1, height / 2);
        GridPoint2 westPos = new GridPoint2(0, height / 2);

        GridPoint2 northTargetChunk = new GridPoint2(chunkId.x, chunkId.y + 1);
        GridPoint2 southTargetChunk = new GridPoint2(chunkId.x, chunkId.y - 1);
        GridPoint2 eastTargetChunk = new GridPoint2(chunkId.x + 1, chunkId.y);
        GridPoint2 westTargetChunk = new GridPoint2(chunkId.x - 1, chunkId.y);

        GridPoint2 northTargetPlayer = new GridPoint2(width / 2, 1);
        GridPoint2 southTargetPlayer = new GridPoint2(width / 2, height - 2);
        GridPoint2 eastTargetPlayer = new GridPoint2(1, height / 2);
        GridPoint2 westTargetPlayer = new GridPoint2(width - 2, height / 2);

        Gate northGate = new Gate(northPos.x, northPos.y, northTargetChunk, northTargetPlayer);
        northGate.setOrientation(Door.Orientation.NORTH_SOUTH);
        maze.addGate(northGate);

        Gate southGate = new Gate(southPos.x, southPos.y, southTargetChunk, southTargetPlayer);
        southGate.setOrientation(Door.Orientation.NORTH_SOUTH);
        maze.addGate(southGate);

        Gate eastGate = new Gate(eastPos.x, eastPos.y, eastTargetChunk, eastTargetPlayer);
        eastGate.setOrientation(Door.Orientation.EAST_WEST);
        maze.addGate(eastGate);

        Gate westGate = new Gate(westPos.x, westPos.y, westTargetChunk, westTargetPlayer);
        westGate.setOrientation(Door.Orientation.EAST_WEST);
        maze.addGate(westGate);
    }

    private Maze createMazeFromText(int level, String[] layout, ItemDataManager itemDataManager, AssetManager assetManager) {
        int height = layout.length;
        int width = layout[0].length();
        int[][] bitmaskedData = new int[height][width];
        Maze maze = new Maze(level, bitmaskedData);

        // Bitmasking loop on walkable floor tiles
        for (int y = 0; y < height; y++) {
            int layoutY = height - 1 - y;
            for (int x = 0; x < width; x++) {
                if (!isWall(layout[layoutY].charAt(x))) {
                    int mask = 0;
                    if (y + 1 < height) {
                        char n = layout[layoutY - 1].charAt(x);
                        if (isWall(n)) {
                            mask |= 0b01000000; // NORTH
                            if (n == 'M') mask |= 2048;
                        }
                    }
                    if (x + 1 < width) {
                        char n = layout[layoutY].charAt(x + 1);
                        if (isWall(n)) {
                            mask |= 0b00000100; // EAST
                            if (n == 'M') mask |= 512;
                        }
                    }
                    if (y > 0) {
                        char n = layout[layoutY + 1].charAt(x);
                        if (isWall(n)) {
                            mask |= 0b00010000; // SOUTH
                            if (n == 'M') mask |= 1024;
                        }
                    }
                    if (x > 0) {
                        char n = layout[layoutY].charAt(x - 1);
                        if (isWall(n)) {
                            mask |= 0b00000001; // WEST
                            if (n == 'M') mask |= 256;
                        }
                    }
                    bitmaskedData[y][x] = mask;
                } else {
                    // Cypress/mangrove thicket wall is solid
                    bitmaskedData[y][x] = 0b01010101;
                }
            }
        }

        return maze;
    }

    private void loadTextureSafely(Scenery s, String path, AssetManager assetManager) {
        if (path == null) return;
        s.setTexturePath(path);
        Texture tex = textureCache.get(path);
        if (tex != null) {
            s.setTexture(tex);
            return;
        }
        try {
            if (assetManager != null) {
                if (assetManager.isLoaded(path)) {
                    tex = assetManager.get(path, Texture.class);
                    textureCache.put(path, tex);
                    s.setTexture(tex);
                } else if (Gdx.files != null && Gdx.files.internal(path) != null && Gdx.files.internal(path).exists()) {
                    assetManager.load(path, Texture.class);
                    assetManager.finishLoadingAsset(path);
                    if (assetManager.isLoaded(path)) {
                        tex = assetManager.get(path, Texture.class);
                        textureCache.put(path, tex);
                        s.setTexture(tex);
                    }
                }
            }
        } catch (Exception ignored) {
            // Headless unit tests without LibGDX context
        }
    }

    private Set<GridPoint2> computeReachableTiles(Maze maze) {
        int width = maze.getWidth();
        int height = maze.getHeight();

        if (forcedUpLadderPos != null) {
            int layoutY = height - 1 - forcedUpLadderPos.y;
            if (layoutY >= 0 && layoutY < height
                    && forcedUpLadderPos.x >= 0 && forcedUpLadderPos.x < width
                    && isTraversable(finalLayout[layoutY].charAt(forcedUpLadderPos.x))) {
                Set<GridPoint2> fromEntrance = floodFillComponent(maze, forcedUpLadderPos);
                if (!fromEntrance.isEmpty()) {
                    return fromEntrance;
                }
            }
        }

        Set<GridPoint2> visited = new HashSet<>();
        Set<GridPoint2> largestComponent = new HashSet<>();

        GridPoint2 centerSeed = new GridPoint2(width / 2, height / 2);
        int centerLayoutY = height - 1 - centerSeed.y;
        if (isTraversable(finalLayout[centerLayoutY].charAt(centerSeed.x))) {
            largestComponent = floodFillComponent(maze, centerSeed);
            visited.addAll(largestComponent);
        }

        for (int y = 0; y < height; y++) {
            int layoutY = height - 1 - y;
            for (int x = 0; x < width; x++) {
                if (!isTraversable(finalLayout[layoutY].charAt(x))) continue;
                GridPoint2 pt = new GridPoint2(x, y);
                if (visited.contains(pt)) continue;

                Set<GridPoint2> component = floodFillComponent(maze, pt);
                visited.addAll(component);
                if (component.size() > largestComponent.size()) {
                    largestComponent = component;
                }
            }
        }

        return largestComponent;
    }

    private Set<GridPoint2> floodFillComponent(Maze maze, GridPoint2 seed) {
        Set<GridPoint2> component = new HashSet<>();
        Queue<GridPoint2> queue = new LinkedList<>();
        component.add(seed);
        queue.add(seed);

        while (!queue.isEmpty()) {
            GridPoint2 cur = queue.poll();
            for (Direction dir : Direction.values()) {
                if ((maze.getWallDataAt(cur.x, cur.y) & dir.getWallMask()) != 0) continue;

                int nx = cur.x + (int) dir.getVector().x;
                int ny = cur.y + (int) dir.getVector().y;
                if (nx < 0 || nx >= maze.getWidth() || ny < 0 || ny >= maze.getHeight()) continue;

                GridPoint2 next = new GridPoint2(nx, ny);
                if (component.contains(next)) continue;

                int layoutY = maze.getHeight() - 1 - ny;
                if (!isTraversable(finalLayout[layoutY].charAt(nx))) continue;

                component.add(next);
                queue.add(next);
            }
        }
        return component;
    }

    private void findPlayerStart(String[] layout) {
        int height = layout.length;
        int width = layout[0].length();

        int midX = width / 2;
        int midY = height / 2;
        int layoutMidY = height - 1 - midY;
        if (isTraversable(layout[layoutMidY].charAt(midX))) {
            playerSpawnPoint.set(midX, midY);
            return;
        }

        for (int y = 0; y < height; y++) {
            int layoutY = height - 1 - y;
            for (int x = 0; x < width; x++) {
                char c = layout[layoutY].charAt(x);
                if (isTraversable(c)) {
                    playerSpawnPoint.set(x, y);
                    return;
                }
            }
        }
    }

    private boolean isMaze(int x, int y) {
        return Math.abs(x) <= 10 && Math.abs(y) <= 10;
    }

    private boolean isWall(char c) {
        return c == '#' || c == 'M';
    }

    private boolean isTraversable(char c) {
        return !isWall(c);
    }
}
