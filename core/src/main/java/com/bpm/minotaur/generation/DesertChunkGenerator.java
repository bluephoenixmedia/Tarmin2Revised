package com.bpm.minotaur.generation;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.gamedata.*;
import com.bpm.minotaur.gamedata.encounters.Encounter;
import com.bpm.minotaur.gamedata.encounters.EncounterManager;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.ItemColor;
import com.bpm.minotaur.gamedata.item.ItemDataManager;
import com.bpm.minotaur.gamedata.liquid.LiquidType;
import com.bpm.minotaur.gamedata.monster.Faction;
import com.bpm.minotaur.gamedata.monster.Monster;
import com.bpm.minotaur.gamedata.monster.Monster.MonsterType;
import com.bpm.minotaur.gamedata.monster.MonsterColor;
import com.bpm.minotaur.gamedata.monster.MonsterDataManager;
import com.bpm.minotaur.gamedata.spawntables.SpawnTableData;
import com.bpm.minotaur.managers.SpawnManager;
import com.bpm.minotaur.rendering.RetroTheme;

import java.util.*;

/**
 * Procedural generator for the Desert Biome.
 * Generates an expansive 36x36 arid wilderness featuring broad open dune corridors,
 * organic sandstone mesa bluffs, sheltered oasis pools, and desert fauna encounters.
 */
public class DesertChunkGenerator implements IChunkGenerator {

    public static final int CHUNK_SIZE = 36;

    private final Random random = new Random();
    private String[] finalLayout;
    private final GridPoint2 playerSpawnPoint = new GridPoint2(18, 18);
    private GridPoint2 forcedUpLadderPos = null;

    private static final String CACTUS_TEXTURE = "images/cactus.png";
    private static final String ROCK_TEXTURE = "images/sandstone_rock.png";
    private static final String DEAD_TREE_TEXTURE = "images/forest/tree_dead_01.png";
    private static final String BONE_PILE_TEXTURE = "images/props/bone_pile.png";
    private static final String SKULL_PILE_TEXTURE = "images/props/skull_pile.png";

    private final Map<String, Texture> textureCache = new HashMap<>();

    public void setForcedUpLadderPos(GridPoint2 pos) {
        this.forcedUpLadderPos = pos;
    }

    @Override
    public Maze generateChunk(GridPoint2 chunkId, int layoutLevel, int spawnDifficulty, Difficulty difficulty,
                              GameMode gameMode,
                              RetroTheme.Theme theme, RetroTheme.Theme mazeTheme,
                              MonsterDataManager dataManager,
                              ItemDataManager itemDataManager,
                              AssetManager assetManager,
                              EncounterManager encounterManager,
                              SpawnTableData spawnTableData,
                              long chunkSeed,
                              int playerLuck) {

        random.setSeed(chunkSeed);

        // 1. Generate 36x36 procedural organic desert layout
        createProceduralDesertLayout(CHUNK_SIZE, CHUNK_SIZE);

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

        // 3. Create Maze and instantiate scenery billboards
        Maze maze = createMazeFromText(layoutLevel, this.finalLayout, itemDataManager, assetManager);
        maze.setBiome(Biome.DESERT);
        maze.setTheme(theme != null ? theme : RetroTheme.DESERT_THEME);
        maze.setSecondaryTheme(mazeTheme);

        // 4. Distribute oasis pools in sheltered depressions
        placeOasisPools(maze, chunkSeed);

        // 5. Compute traversable reachable tiles
        Set<GridPoint2> reachable = computeReachableTiles(maze);

        // 6. Spawn Central Dunes Landmarks & POIs
        spawnLandmarks(maze, assetManager, itemDataManager, chunkSeed);

        // 7. Spawn Monsters, Items, Encounters, Ladders
        spawnEntities(maze, difficulty, spawnDifficulty, this.finalLayout, dataManager, itemDataManager, assetManager,
                spawnTableData, chunkSeed, playerLuck, reachable);

        spawnDesertFauna(maze, reachable, dataManager, assetManager, chunkSeed);

        spawnEncounters(maze, encounterManager, reachable, assetManager);

        spawnLadder(maze, this.finalLayout, reachable);

        // 8. Spawn Chunk Transition Gates at 4 Cardinal Gates
        spawnTransitionGates(maze, this.finalLayout, chunkId);

        // 9. Find valid player start position
        findPlayerStart(this.finalLayout);

        return maze;
    }

    @Override
    public GridPoint2 getInitialPlayerStartPos() {
        return playerSpawnPoint;
    }

    private void createProceduralDesertLayout(int width, int height) {
        char[][] grid = new char[height][width];

        // 1. Initialise with open traversable sand floor ('.')
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (x == 0 || x == width - 1 || y == 0 || y == height - 1) {
                    grid[y][x] = '#'; // Boundary sandstone mesa cliffs
                } else {
                    grid[y][x] = '.'; // Open sand dunes
                }
            }
        }

        // 2. Generate organic sandstone mesa bluffs using simplex noise
        FastNoiseLite noise = new FastNoiseLite(random.nextInt());
        noise.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        noise.SetFrequency(0.08f);

        int midX = width / 2;  // 18
        int midY = height / 2; // 18

        for (int y = 2; y < height - 2; y++) {
            for (int x = 2; x < width - 2; x++) {
                float n = noise.GetNoise(x, y);
                // Mesa bluffs form when noise is high
                if (n > 0.38f) {
                    grid[y][x] = '#';
                }
            }
        }

        // 3. Carve Central Dune Bowl (large circular expanse around 18, 18)
        float bowlRadius = 6.0f;
        for (int y = midY - 8; y <= midY + 8; y++) {
            for (int x = midX - 8; x <= midX + 8; x++) {
                if (x <= 1 || x >= width - 2 || y <= 1 || y >= height - 2) continue;
                float dx = x - midX;
                float dy = y - midY;
                float dist = (float) Math.sqrt(dx * dx + dy * dy);
                if (dist <= bowlRadius + 0.6f * (float) Math.sin(dx * 1.5f + dy * 1.2f)) {
                    grid[y][x] = '.';
                }
            }
        }

        // 4. Clear 4 Cardinal Gate Openings
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

        // 5. Carve Broad Dune Corridors (3-5 tiles wide) connecting gates to central bowl
        carveBroadCorridor(grid, midX, 3, midX, midY - 4, 2);
        carveBroadCorridor(grid, midX, height - 4, midX, midY + 4, 2);
        carveBroadCorridor(grid, 3, midY, midX - 4, midY, 2);
        carveBroadCorridor(grid, width - 4, midY, midX + 4, midY, 2);

        // 6. Perimeter ring linking all four gate approaches to prevent single-tile choke points
        int ringLow = 6;
        int ringHighX = width - 7;
        int ringHighY = height - 7;

        carveBroadCorridor(grid, midX, 3, ringLow, ringLow, 1);
        carveBroadCorridor(grid, ringLow, ringLow, ringLow, midY, 1);
        carveBroadCorridor(grid, ringLow, midY, ringLow, ringHighY, 1);
        carveBroadCorridor(grid, ringLow, ringHighY, midX, height - 4, 1);
        carveBroadCorridor(grid, midX, height - 4, ringHighX, ringHighY, 1);
        carveBroadCorridor(grid, ringHighX, ringHighY, ringHighX, midY, 1);
        carveBroadCorridor(grid, ringHighX, midY, ringHighX, ringLow, 1);
        carveBroadCorridor(grid, ringHighX, ringLow, midX, 3, 1);

        // 7. Scatter organic desert props (Cactus 'T', Sandstone Rock 'R', Dead Tree 'D', Bones 'B')
        for (int y = 2; y < height - 2; y++) {
            for (int x = 2; x < width - 2; x++) {
                if (grid[y][x] == '.') {
                    // Keep gate approaches and center clear of impassable props
                    if (isGateApproachOrCenter(x, y, midX, midY, width, height)) continue;

                    float r = random.nextFloat();
                    if (r < 0.035f) {
                        grid[y][x] = 'T'; // Cactus (impassable)
                    } else if (r < 0.065f) {
                        grid[y][x] = 'R'; // Sandstone Rock (impassable)
                    } else if (r < 0.080f) {
                        grid[y][x] = 'D'; // Dead Tree (impassable)
                    } else if (r < 0.095f) {
                        grid[y][x] = 'B'; // Bone pile (passable)
                    } else if (r < 0.105f) {
                        grid[y][x] = 'S'; // Skull pile (passable)
                    }
                }
            }
        }

        // 8. Rescue Corridor Guarantee: ensure all four gate approaches reach the central bowl
        ensureGatesReachGlade(grid, midX, midY);

        // Convert grid (y: 0..35 South to North) to finalLayout (index 0 is North, index 35 is South)
        this.finalLayout = new String[height];
        for (int y = 0; y < height; y++) {
            int layoutY = height - 1 - y;
            this.finalLayout[layoutY] = new String(grid[y]);
        }
    }

    private boolean isGateApproachOrCenter(int x, int y, int midX, int midY, int width, int height) {
        if (Math.abs(x - midX) <= 2 && Math.abs(y - midY) <= 2) return true;
        if (Math.abs(x - midX) <= 2 && (y <= 4 || y >= height - 5)) return true;
        if (Math.abs(y - midY) <= 2 && (x <= 4 || x >= width - 5)) return true;
        return false;
    }

    private void carveBroadCorridor(char[][] grid, int x0, int y0, int x1, int y1, int radius) {
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

            carveBroadCorridor(grid, approach[0], approach[1], midX, midY, 1);
            reachable = floodFillOpen(grid, midX, midY);
        }
    }

    private Set<Integer> floodFillOpen(char[][] grid, int startX, int startY) {
        int height = grid.length;
        int width = grid[0].length;

        Set<Integer> seen = new HashSet<>();
        ArrayDeque<int[]> queue = new ArrayDeque<>();

        if (grid[startY][startX] != '.' && grid[startY][startX] != 'B' && grid[startY][startX] != 'S') return seen;
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
                char c = grid[ny][nx];
                if (c != '.' && c != 'B' && c != 'S') continue;
                int key = ny * width + nx;
                if (!seen.add(key)) continue;
                queue.add(new int[]{nx, ny});
            }
        }
        return seen;
    }

    private void placeOasisPools(Maze maze, long seed) {
        // Place a small sheltered oasis pool
        int oasisX = 22;
        int oasisY = 20;
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                int x = oasisX + dx;
                int y = oasisY + dy;
                if (x >= 2 && x < CHUNK_SIZE - 2 && y >= 2 && y < CHUNK_SIZE - 2) {
                    if (!maze.isWall(x, y)) {
                        maze.getLiquidManager().setLiquidAt(x, y, LiquidType.WATER);
                    }
                }
            }
        }
    }

    private void spawnLandmarks(Maze maze, AssetManager assetManager, ItemDataManager itemDataManager, long seed) {
        int archetype = (int) (Math.abs(seed) % 3);
        int cx = 18;
        int cy = 18;

        if (archetype == 0) {
            // Archetype 0: Desert Oasis Outpost
            placeThemedProp(maze, "campfire", cx, cy, assetManager);
            placeThemedProp(maze, "camp_tent", cx + 2, cy + 2, assetManager);
        } else if (archetype == 1) {
            // Archetype 1: Ancient Sandstone Crypt Ruin
            placeThemedProp(maze, "cairn", cx, cy, assetManager);
            placeThemedProp(maze, "skull_pile", cx + 2, cy, assetManager);
            placeThemedProp(maze, "ruined_pillar", cx - 2, cy + 1, assetManager);
        } else {
            // Archetype 2: Titan Bone Graveyard
            placeThemedProp(maze, "bone_pile", cx, cy, assetManager);
            placeThemedProp(maze, "skull_pile", cx - 2, cy, assetManager);
            placeThemedProp(maze, "bone_pile", cx + 2, cy - 1, assetManager);
        }
    }

    private void placeThemedProp(Maze maze, String propId, int x, int y, AssetManager assetManager) {
        if (x < 1 || x >= maze.getWidth() - 1 || y < 1 || y >= maze.getHeight() - 1) return;
        if (maze.isWall(x, y)) return;
        GridPoint2 pos = new GridPoint2(x, y);
        if (maze.getScenery().containsKey(pos)) return;

        Scenery s = Scenery.fromProp(propId, x, y);
        if (s != null) {
            if (s.getTexturePath() != null) {
                loadTextureSafely(s, s.getTexturePath(), assetManager);
            }
            maze.addScenery(s);
        }
    }

    private void spawnDesertFauna(Maze maze, Set<GridPoint2> reachable, MonsterDataManager dataManager,
                                  AssetManager assetManager, long seed) {
        MonsterType[] desertMonsters = {
                MonsterType.GIANT_SCORPION,
                MonsterType.GIANT_SNAKE,
                MonsterType.BASILISK,
                MonsterType.MUMMY,
                MonsterType.TROGLODYTE
        };

        Random rng = new Random(seed ^ 0xCAFEBABE12345678L);
        List<GridPoint2> spots = new ArrayList<>(reachable);
        Collections.shuffle(spots, rng);

        int faunaCount = 3 + rng.nextInt(3);
        int placed = 0;
        for (GridPoint2 pt : spots) {
            if (placed >= faunaCount) break;
            if (Math.abs(pt.x - 18) <= 4 && Math.abs(pt.y - 18) <= 4) continue;
            if (maze.getMonsters().containsKey(pt) || maze.getScenery().containsKey(pt)) continue;

            MonsterType type = desertMonsters[rng.nextInt(desertMonsters.length)];
            Monster m = new Monster(type, 30 + rng.nextInt(20), 10 + rng.nextInt(5), pt.x, pt.y);
            m.setFaction(Faction.BEASTS_AND_VERMIN);
            maze.addMonster(m);
            placed++;
        }
    }

    private void spawnEntities(Maze maze, Difficulty difficulty, int level, String[] layout,
                               MonsterDataManager dataManager, ItemDataManager itemDataManager, AssetManager assetManager,
                               SpawnTableData spawnTableData, long chunkSeed, int playerLuck, Set<GridPoint2> reachable) {
        if (spawnTableData == null) return;
        long spawnSeed = chunkSeed ^ 0xDEADBEEF98765432L;
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
            Ladder ladder = new Ladder(ladderPos.x, ladderPos.y, Ladder.LadderType.DOWN, Ladder.EntranceStyle.CAVE_MOUTH);
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

        for (int y = 0; y < height; y++) {
            int layoutY = height - 1 - y;
            for (int x = 0; x < width; x++) {
                char c = layout[layoutY].charAt(x);
                switch (c) {
                    case 'T': {
                        Scenery s = new Scenery(Scenery.SceneryType.CACTUS, x, y);
                        s.setFlippedX(random.nextBoolean());
                        float jitter = 0.90f + random.nextFloat() * 0.25f;
                        s.scale.set(1.5f * jitter, 2.5f * jitter);
                        loadTextureSafely(s, CACTUS_TEXTURE, assetManager);
                        maze.addScenery(s);
                        break;
                    }
                    case 'R': {
                        Scenery s = new Scenery(Scenery.SceneryType.SANDSTONE_ROCK, x, y);
                        s.setFlippedX(random.nextBoolean());
                        float jitter = 0.85f + random.nextFloat() * 0.35f;
                        s.scale.set(1.2f * jitter, 1.0f * jitter);
                        loadTextureSafely(s, ROCK_TEXTURE, assetManager);
                        maze.addScenery(s);
                        break;
                    }
                    case 'D': {
                        Scenery s = new Scenery(Scenery.SceneryType.TREE, x, y);
                        s.setFlippedX(random.nextBoolean());
                        float jitter = 0.90f + random.nextFloat() * 0.20f;
                        s.scale.set(1.8f * jitter, 3.2f * jitter);
                        loadTextureSafely(s, DEAD_TREE_TEXTURE, assetManager);
                        maze.addScenery(s);
                        break;
                    }
                    case 'B': {
                        Scenery s = Scenery.fromProp("bone_pile", x, y);
                        if (s != null) {
                            loadTextureSafely(s, BONE_PILE_TEXTURE, assetManager);
                            maze.addScenery(s);
                        }
                        break;
                    }
                    case 'S': {
                        Scenery s = Scenery.fromProp("skull_pile", x, y);
                        if (s != null) {
                            loadTextureSafely(s, SKULL_PILE_TEXTURE, assetManager);
                            maze.addScenery(s);
                        }
                        break;
                    }
                }
            }
        }

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
        return Math.max(Math.abs(x), Math.abs(y)) <= WorldConstants.CENTRAL_MAZE_RADIUS;
    }

    private boolean isWall(char c) {
        return c == '#' || c == 'M';
    }

    private boolean isTraversable(char c) {
        return c == '.' || c == 'B' || c == 'S';
    }
}
