package com.bpm.minotaur.generation;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.Color;
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
import com.bpm.minotaur.gamedata.monster.MonsterVariant;
import com.bpm.minotaur.gamedata.spawntables.SpawnTableData;
import com.bpm.minotaur.gamedata.spawntables.SpawnTableEntry;
import com.bpm.minotaur.lighting.LightSource;
import com.bpm.minotaur.lighting.LightingManager;
import com.bpm.minotaur.managers.SpawnManager;
import com.bpm.minotaur.rendering.RetroTheme;
import com.bpm.minotaur.weather.WeatherType;

import java.util.*;

/**
 * Procedural generator for the Siberian Tundra Biome.
 * Generates an expansive 36x36 sub-zero wilderness featuring glacial ice crags,
 * a central frozen ice lake basin (PACKED_ICE), shoreline freezing slush bogs (FREEZING_SLUSH),
 * sheltered trapper campsite coves with warming campfires, and dark boreal pine taiga.
 */
public class TundraChunkGenerator implements IChunkGenerator {

    public static final int CHUNK_SIZE = 36;

    private final Random random = new Random();
    private final List<GridPoint2> campsiteCoves = new ArrayList<>();
    private String[] finalLayout;
    private final GridPoint2 playerSpawnPoint = new GridPoint2(18, 18);
    private GridPoint2 forcedUpLadderPos = null;

    /** A baked Tundra sprite and the billboard size its canvas was rendered at. */
    public record Sprite(String path, float width, float height) {
        public void applyTo(Scenery s, float jitter) {
            s.scale.set(width * jitter, height * jitter);
            s.setPixelOffsetY(0f);
        }
    }

    public static final Sprite PINE_01 = new Sprite("images/tundra/tree_pine_01.png", 2.2f, 4.0f);
    public static final Sprite PINE_02 = new Sprite("images/tundra/tree_pine_02.png", 1.8f, 3.2f);
    public static final Sprite PINE_SNAG = new Sprite("images/tundra/tree_snag_01.png", 1.6f, 3.0f);
    public static final Sprite SNOW_MOUND = new Sprite("images/tundra/snow_mound_01.png", 1.4f, 0.6f);
    public static final Sprite PINE_STUMP = new Sprite("images/tundra/pine_stump_01.png", 0.8f, 0.6f);
    public static final Sprite LOG_SNOW = new Sprite("images/tundra/log_snow_01.png", 1.5f, 0.5f);
    public static final Sprite STALACTITE = new Sprite("images/tundra/stalactite_01.png", 0.8f, 1.6f);
    public static final Sprite CAIRN = new Sprite("images/tundra/cairn_01.png", 1.0f, 1.4f);
    public static final Sprite CAMPFIRE = new Sprite("images/tundra/campfire_01.png", 1.0f, 0.8f);
    public static final Sprite WOOD_PILE = new Sprite("images/tundra/wood_pile_01.png", 1.2f, 0.7f);
    public static final Sprite ICE_HUT = new Sprite("images/tundra/ice_hut_01.png", 2.2f, 2.2f);

    public static final Sprite[] TUNDRA_TREES = { PINE_01, PINE_02, PINE_SNAG };
    public static final Sprite[] SCATTER_PROPS = { SNOW_MOUND, PINE_STUMP, LOG_SNOW, STALACTITE, CAIRN };

    private static final MonsterType[] TUNDRA_BEASTS = {
            MonsterType.WEREWOLF,
            MonsterType.TROLL,
            MonsterType.GIANT,
            MonsterType.OWLBEAR
    };

    private static final MonsterType[] TUNDRA_UNDEAD = {
            MonsterType.CLOAKED_SKELETON,
            MonsterType.WRAITH,
            MonsterType.GHOUL
    };

    /** Preloads all Tundra textures. */
    public static List<String> textures() {
        Set<String> paths = new LinkedHashSet<>();
        paths.addAll(Arrays.asList(
                PINE_01.path(), PINE_02.path(), PINE_SNAG.path(),
                SNOW_MOUND.path(), PINE_STUMP.path(), LOG_SNOW.path(),
                STALACTITE.path(), CAIRN.path(), CAMPFIRE.path(),
                WOOD_PILE.path(), ICE_HUT.path()
        ));
        return new ArrayList<>(paths);
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

        // 1. Generate 36x36 procedural Tundra moraine and basin layout
        createProceduralTundraLayout(CHUNK_SIZE, CHUNK_SIZE);

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
        maze.setBiome(Biome.TUNDRA);
        maze.setTheme(theme != null ? theme : RetroTheme.TUNDRA_THEME);
        maze.setSecondaryTheme(mazeTheme);

        // 4. Distribute permafrost ground hazards: glassy packed ice & freezing slush
        distributeTundraHazards(maze, chunkSeed);

        // 5. Compute reachable traversable tiles
        Set<GridPoint2> reachable = computeReachableTiles(maze);

        // 6. Spawn Campsite Shelters (Hearth campfire, woodpile, trapper shack, firewood)
        spawnCampsites(maze, reachable, itemDataManager, assetManager, chunkSeed);

        // 7. Spawn Boreal Pine Taiga & Dead Winter Timber
        spawnTundraTrees(maze, reachable, assetManager, chunkSeed);

        // 8. Scatter ground cover props (snow mounds, stumps, logs, stalactites, cairns)
        scatterGroundCover(maze, reachable, assetManager, chunkSeed);

        // 9. Spawn Monsters, Items, Encounters, Ladders
        spawnEntities(maze, difficulty, spawnDifficulty, this.finalLayout, dataManager, itemDataManager, assetManager,
                spawnTableData, chunkSeed, playerLuck, reachable);

        spawnTundraFauna(maze, reachable, dataManager, assetManager, spawnTableData, layoutLevel, chunkSeed);

        spawnEncounters(maze, encounterManager, reachable, assetManager);

        spawnLadder(maze, this.finalLayout, reachable);

        // 10. Spawn Chunk Transition Gates at 4 Cardinal Gates
        spawnTransitionGates(maze, this.finalLayout, chunkId);

        // 11. Find valid player start position
        findPlayerStart(this.finalLayout);

        return maze;
    }

    @Override
    public GridPoint2 getInitialPlayerStartPos() {
        return playerSpawnPoint;
    }

    private void createProceduralTundraLayout(int width, int height) {
        char[][] grid = new char[height][width];
        campsiteCoves.clear();

        // 1. Initialise with perimeter glacial wall crags ('#') and open permafrost ('.')
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (x == 0 || x == width - 1 || y == 0 || y == height - 1) {
                    grid[y][x] = '#';
                } else {
                    grid[y][x] = '.';
                }
            }
        }

        // 2. Procedural glacial moraine ridges using FastNoiseLite
        FastNoiseLite noise = new FastNoiseLite(random.nextInt());
        noise.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        noise.SetFrequency(0.08f);

        int midX = width / 2;  // 18
        int midY = height / 2; // 18

        for (int y = 2; y < height - 2; y++) {
            for (int x = 2; x < width - 2; x++) {
                float n = noise.GetNoise(x, y);
                if (n > 0.30f) {
                    grid[y][x] = '#'; // Glacial ice wall bluff
                }
            }
        }

        // 3. Carve Central Glacial Tarn Basin (wide open frozen lake around 18, 18)
        float basinRadius = 6.2f;
        for (int y = midY - 8; y <= midY + 8; y++) {
            for (int x = midX - 8; x <= midX + 8; x++) {
                if (x <= 1 || x >= width - 2 || y <= 1 || y >= height - 2) continue;
                float dx = x - midX;
                float dy = y - midY;
                float dist = (float) Math.sqrt(dx * dx + dy * dy);
                if (dist <= basinRadius + 0.7f * (float) Math.sin(dx * 1.4f + dy * 1.1f)) {
                    grid[y][x] = '.';
                }
            }
        }

        // 4. Carve 2 Sheltered Campsite Coves in opposite diagonal quadrants
        int[][] quadCandidates = {
                {10, 26}, {26, 10}, {10, 10}, {26, 26}
        };
        List<int[]> quadList = new ArrayList<>(Arrays.asList(quadCandidates));
        Collections.shuffle(quadList, random);

        for (int i = 0; i < 2; i++) {
            int[] qc = quadList.get(i);
            int cx = qc[0] + random.nextInt(3) - 1;
            int cy = qc[1] + random.nextInt(3) - 1;
            campsiteCoves.add(new GridPoint2(cx, cy));

            float coveRadius = 3.6f;
            for (int y = cy - 5; y <= cy + 5; y++) {
                for (int x = cx - 5; x <= cx + 5; x++) {
                    if (x <= 1 || x >= width - 2 || y <= 1 || y >= height - 2) continue;
                    float dx = x - cx;
                    float dy = y - cy;
                    if (dx * dx + dy * dy <= coveRadius * coveRadius) {
                        grid[y][x] = '.';
                    }
                }
            }
            // Link cove to central basin with width-2 trail
            carveCauseway(grid, cx, cy, midX, midY, 1);
        }

        // 5. Clear 4 Cardinal Gate Openings (width 3)
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

        // 6. Carve Corridors connecting all gates to central basin (width 2)
        carveCauseway(grid, midX, 3, midX, midY - 3, 2);
        carveCauseway(grid, midX, height - 4, midX, midY + 3, 2);
        carveCauseway(grid, 3, midY, midX - 3, midY, 2);
        carveCauseway(grid, width - 4, midY, midX + 3, midY, 2);

        // 7. Connectivity verification: Flood-fill to ensure all gates are connected
        ensureGateConnectivity(grid, width, height, midX, midY);

        // Convert char grid into layout strings
        this.finalLayout = new String[height];
        for (int y = 0; y < height; y++) {
            this.finalLayout[y] = new String(grid[y]);
        }
    }

    private void carveCauseway(char[][] grid, int x1, int y1, int x2, int y2, int halfWidth) {
        int height = grid.length;
        int width = grid[0].length;

        int curX = x1;
        int curY = y1;

        while (curX != x2 || curY != y2) {
            for (int dy = -halfWidth; dy <= halfWidth; dy++) {
                for (int dx = -halfWidth; dx <= halfWidth; dx++) {
                    int nx = curX + dx;
                    int ny = curY + dy;
                    if (nx > 0 && nx < width - 1 && ny > 0 && ny < height - 1) {
                        grid[ny][nx] = '.';
                    }
                }
            }

            if (curX != x2 && (curY == y2 || random.nextBoolean())) {
                curX += (x2 > curX) ? 1 : -1;
            } else if (curY != y2) {
                curY += (y2 > curY) ? 1 : -1;
            }
        }
    }

    private void ensureGateConnectivity(char[][] grid, int width, int height, int midX, int midY) {
        int[][] gates = {
                {midX, 1},
                {midX, height - 2},
                {1, midY},
                {width - 2, midY}
        };

        Set<Integer> visited = floodFill(grid, width, height, midX, midY);
        for (int[] gate : gates) {
            int key = gate[1] * width + gate[0];
            if (!visited.contains(key)) {
                carveCauseway(grid, gate[0], gate[1], midX, midY, 1);
                visited = floodFill(grid, width, height, midX, midY);
            }
        }
    }

    private Set<Integer> floodFill(char[][] grid, int width, int height, int startX, int startY) {
        Set<Integer> seen = new HashSet<>();
        Queue<int[]> queue = new ArrayDeque<>();

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

    private void distributeTundraHazards(Maze maze, long seed) {
        FastNoiseLite slushNoise = new FastNoiseLite((int) (seed ^ 0x7E4D815CL));
        slushNoise.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        slushNoise.SetFrequency(0.09f);

        int midX = 18;
        int midY = 18;

        for (int y = 1; y < CHUNK_SIZE - 1; y++) {
            for (int x = 1; x < CHUNK_SIZE - 1; x++) {
                if (maze.isWall(x, y)) continue;

                // Keep player spawn dry
                if (x == midX && y == midY) continue;
                // Keep immediate gate doorsteps dry
                if ((x == midX && (y <= 2 || y >= CHUNK_SIZE - 3)) || (y == midY && (x <= 2 || x >= CHUNK_SIZE - 3))) continue;

                // Keep campsite centers dry
                boolean nearCampsite = false;
                for (GridPoint2 cove : campsiteCoves) {
                    if (Math.abs(x - cove.x) <= 2 && Math.abs(y - cove.y) <= 2) {
                        nearCampsite = true;
                        break;
                    }
                }
                if (nearCampsite) continue;

                float dx = x - midX;
                float dy = y - midY;
                float distFromCenter = (float) Math.sqrt(dx * dx + dy * dy);

                if (distFromCenter <= 5.2f) {
                    // Central glassy frozen lake: low-friction glacial ice sheet
                    maze.getLiquidManager().setLiquidAt(x, y, LiquidType.PACKED_ICE);
                } else if (distFromCenter <= 7.0f) {
                    // Shoreline ice/slush margin: hampering freezing slush
                    maze.getLiquidManager().setLiquidAt(x, y, LiquidType.FREEZING_SLUSH);
                } else {
                    // Scattered hollows across the permafrost
                    float n = slushNoise.GetNoise(x, y);
                    if (n > 0.35f) {
                        maze.getLiquidManager().setLiquidAt(x, y, LiquidType.FREEZING_SLUSH);
                    }
                }
            }
        }
    }

    private void spawnCampsites(Maze maze, Set<GridPoint2> reachable, ItemDataManager itemDataManager,
                                AssetManager assetManager, long seed) {
        for (int i = 0; i < campsiteCoves.size(); i++) {
            GridPoint2 cove = campsiteCoves.get(i);
            if (!reachable.contains(cove)) continue;

            int cx = cove.x;
            int cy = cove.y;

            // 1. Campfire Hearth
            placeScenerySprite(maze, CAMPFIRE, cx, cy, 1f, assetManager);
            // Light source: radius 4.5, amber glow, warming aura in TurnManager
            maze.addLight(new LightSource("tundra_campfire_" + i, cx + 0.5f, cy + 0.5f,
                    LightingManager.COLOR_CAMPFIRE, 4.5f, 1.2f, LightSource.FlickerProfile.CAMPFIRE_FLICKER));

            // 2. Stacked Wood Pile
            if (isStandable(maze, cx + 1, cy)) {
                placeScenerySprite(maze, WOOD_PILE, cx + 1, cy, 1f, assetManager);
            }

            // 3. Trapper Ice Hut / Shack
            if (isStandable(maze, cx - 1, cy + 1)) {
                placeSolidSprite(maze, ICE_HUT, cx - 1, cy + 1, 1f, assetManager);
            }

            // 4. Firewood Bundles dropped near the wood pile
            if (itemDataManager != null) {
                int dropX = cx;
                int dropY = cy - 1;
                if (isStandable(maze, dropX, dropY)) {
                    Item wood = itemDataManager.createItem(Item.ItemType.FIREWOOD, dropX, dropY, ItemColor.TAN, assetManager);
                    if (wood != null) {
                        maze.addItem(wood);
                    }
                }
            }
        }
    }

    private void spawnTundraTrees(Maze maze, Set<GridPoint2> reachable, AssetManager assetManager, long seed) {
        Random rng = new Random(seed ^ 0x31B91E5AL);

        // 1. Solid Impassable Pines
        List<GridPoint2> candidates = new ArrayList<>();
        for (GridPoint2 pt : reachable) {
            // Keep central spawn clearing clear
            if (Math.abs(pt.x - 18) <= 2 && Math.abs(pt.y - 18) <= 2) continue;
            // Keep main cardinal walking avenues clear
            if (Math.abs(pt.x - 18) <= 1 || Math.abs(pt.y - 18) <= 1) continue;
            // Keep gate approaches clear
            if (pt.x <= 3 || pt.x >= CHUNK_SIZE - 4 || pt.y <= 3 || pt.y >= CHUNK_SIZE - 4) continue;
            // Keep campsite centers clear
            boolean inCampsite = false;
            for (GridPoint2 c : campsiteCoves) {
                if (Math.abs(pt.x - c.x) <= 2 && Math.abs(pt.y - c.y) <= 2) {
                    inCampsite = true;
                    break;
                }
            }
            if (inCampsite) continue;
            if (maze.getScenery().containsKey(pt) || maze.getGateAt(pt.x, pt.y) != null) continue;

            candidates.add(pt);
        }
        Collections.shuffle(candidates, rng);

        int currentReachable = countReachable(maze);
        int targetSolid = 30 + rng.nextInt(12); // ~30-42 solid trees
        int solidPlaced = 0;

        for (GridPoint2 pt : candidates) {
            if (solidPlaced >= targetSolid) break;

            Scenery tree = new Scenery(Scenery.SceneryType.TREE, pt.x, pt.y);
            tree.setImpassable(true);
            tree.setFlippedX(rng.nextBoolean());
            Sprite sp = TUNDRA_TREES[rng.nextInt(TUNDRA_TREES.length)];
            float jitter = 0.90f + rng.nextFloat() * 0.25f;
            sp.applyTo(tree, jitter);
            loadTextureSafely(tree, sp.path(), assetManager);
            maze.addScenery(tree);

            int after = countReachable(maze);
            if (after < currentReachable - 1) {
                maze.getScenery().remove(pt);
                continue;
            }
            currentReachable = after;
            solidPlaced++;
        }

        // 2. Backdrop Trees along Glacial Moraine Bluff Edges
        for (int y = 2; y < CHUNK_SIZE - 2; y++) {
            for (int x = 2; x < CHUNK_SIZE - 2; x++) {
                if (!isWallAt(x, y)) continue;
                if (!facesOpenGround(x, y)) continue;
                if (rng.nextFloat() > 0.35f) continue;

                float[] offset = directionToOpenGround(x, y);
                float px = x + 0.5f + offset[0] * 0.35f;
                float py = y + 0.5f + offset[1] * 0.35f;

                Scenery bluffTree = new Scenery(Scenery.SceneryType.TREE, x, y);
                bluffTree.getPosition().set(px, py);
                bluffTree.setFlippedX(rng.nextBoolean());
                Sprite sp = TUNDRA_TREES[rng.nextInt(TUNDRA_TREES.length)];
                float jitter = 0.85f + rng.nextFloat() * 0.35f;
                sp.applyTo(bluffTree, jitter);
                loadTextureSafely(bluffTree, sp.path(), assetManager);
                maze.addBackdropScenery(bluffTree);
            }
        }
    }

    private void scatterGroundCover(Maze maze, Set<GridPoint2> reachable, AssetManager assetManager, long seed) {
        Random rng = new Random(seed ^ 0x5C477382L);

        int currentReachable = countReachable(maze);

        for (GridPoint2 pt : reachable) {
            // Keep central start tile clear
            if (pt.x == 18 && pt.y == 18) continue;
            // Keep gate doorstep clear
            if ((pt.x == 18 && (pt.y <= 1 || pt.y >= CHUNK_SIZE - 2)) || (pt.y == 18 && (pt.x <= 1 || pt.x >= CHUNK_SIZE - 2))) continue;
            if (maze.getScenery().containsKey(pt) || maze.getGateAt(pt.x, pt.y) != null) continue;

            // ~15% chance for ground scatter per reachable tile
            if (rng.nextFloat() > 0.16f) continue;

            Sprite sp = SCATTER_PROPS[rng.nextInt(SCATTER_PROPS.length)];
            float ox = (rng.nextFloat() - 0.5f) * 0.4f;
            float oy = (rng.nextFloat() - 0.5f) * 0.4f;

            if (sp == LOG_SNOW || sp == PINE_STUMP) {
                // Impassable fallen timber: can be bumped to harvest into Firewood bundles
                Scenery sc = new Scenery(Scenery.SceneryType.PROP, pt.x, pt.y, sp.path());
                sc.setImpassable(true);
                sc.setFlippedX(rng.nextBoolean());
                sp.applyTo(sc, 1.0f);
                loadTextureSafely(sc, sp.path(), assetManager);
                maze.addScenery(sc);

                if (countReachable(maze) < currentReachable - 1) {
                    // Placing this log would cut a path; degrade to non-blocking backdrop
                    maze.getScenery().remove(pt);
                    sc.setImpassable(false);
                    maze.addBackdropScenery(sc);
                } else {
                    currentReachable = countReachable(maze);
                }
            } else {
                Scenery sc = new Scenery(Scenery.SceneryType.PROP, pt.x, pt.y, sp.path());
                sc.getPosition().set(pt.x + 0.5f + ox, pt.y + 0.5f + oy);
                sc.setFlippedX(rng.nextBoolean());
                float jitter = 0.85f + rng.nextFloat() * 0.30f;
                sp.applyTo(sc, jitter);
                loadTextureSafely(sc, sp.path(), assetManager);
                maze.addBackdropScenery(sc);
            }
        }
    }

    private void spawnTundraFauna(Maze maze, Set<GridPoint2> reachable, MonsterDataManager dataManager,
                                  AssetManager assetManager, SpawnTableData spawnTableData, int layoutLevel, long seed) {
        if (dataManager == null) return;
        Random rng = new Random(seed ^ 0x901AD37BL);

        List<GridPoint2> spots = new ArrayList<>(reachable);
        Collections.shuffle(spots, rng);

        int faunaCount = 4 + rng.nextInt(3); // 4 to 6 beasts/undead
        int spawned = 0;

        for (GridPoint2 pt : spots) {
            if (spawned >= faunaCount) break;
            if (Math.abs(pt.x - 18) <= 4 && Math.abs(pt.y - 18) <= 4) continue;
            if (maze.getMonsters().containsKey(pt) || maze.getScenery().containsKey(pt)) continue;

            boolean isBeast = rng.nextBoolean();
            MonsterType mType = isBeast
                    ? TUNDRA_BEASTS[rng.nextInt(TUNDRA_BEASTS.length)]
                    : TUNDRA_UNDEAD[rng.nextInt(TUNDRA_UNDEAD.length)];

            MonsterVariant variant = dataManager.getRandomVariantForMonster(mType, layoutLevel);
            MonsterColor color = (variant != null) ? variant.color : MonsterColor.WHITE;
            Monster m = new Monster(mType, pt.x, pt.y, color, dataManager, assetManager);
            m.scaleStats(layoutLevel);
            m.setCurrentHP(m.getMaxHP());
            m.setFaction(isBeast ? Faction.BEASTS_AND_VERMIN : Faction.UNDEAD);
            maze.addMonster(m);
            spawned++;
        }
    }

    private void spawnEntities(Maze maze, Difficulty difficulty, int spawnDifficulty, String[] layout,
                               MonsterDataManager dataManager, ItemDataManager itemDataManager,
                               AssetManager assetManager, SpawnTableData spawnTableData, long chunkSeed,
                               int playerLuck, Set<GridPoint2> reachable) {
        if (spawnTableData == null) return;
        long spawnSeed = chunkSeed ^ 0xFEEDFACECAFEBABEL;
        SpawnManager spawnManager = new SpawnManager(dataManager, itemDataManager, assetManager,
                maze, difficulty, spawnDifficulty, spawnDifficulty, playerLuck, layout, spawnTableData, spawnSeed, reachable);
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

        int numEncounters = 1 + random.nextInt(2);
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

        if (forcedUpLadderPos != null) {
            maze.addLadder(new Ladder(forcedUpLadderPos.x, forcedUpLadderPos.y, Ladder.LadderType.UP, Ladder.EntranceStyle.ROPE));
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

    private boolean isStandable(Maze maze, int x, int y) {
        return x > 0 && x < CHUNK_SIZE - 1 && y > 0 && y < CHUNK_SIZE - 1
                && !maze.isWall(x, y)
                && !maze.getScenery().containsKey(new GridPoint2(x, y));
    }

    private boolean facesOpenGround(int x, int y) {
        int[] dx = {0, 0, 1, -1};
        int[] dy = {1, -1, 0, 0};
        for (int i = 0; i < 4; i++) {
            int nx = x + dx[i];
            int ny = y + dy[i];
            if (nx >= 0 && nx < CHUNK_SIZE && ny >= 0 && ny < CHUNK_SIZE && !isWallAt(nx, ny)) {
                return true;
            }
        }
        return false;
    }

    private float[] directionToOpenGround(int x, int y) {
        int[] dx = {0, 0, 1, -1};
        int[] dy = {1, -1, 0, 0};
        for (int i = 0; i < 4; i++) {
            int nx = x + dx[i];
            int ny = y + dy[i];
            if (nx >= 0 && nx < CHUNK_SIZE && ny >= 0 && ny < CHUNK_SIZE && !isWallAt(nx, ny)) {
                return new float[]{dx[i], dy[i]};
            }
        }
        return new float[]{0, 0};
    }

    private boolean isWallAt(int x, int y) {
        if (finalLayout == null) return false;
        int layoutY = CHUNK_SIZE - 1 - y;
        if (layoutY < 0 || layoutY >= CHUNK_SIZE || x < 0 || x >= CHUNK_SIZE) return true;
        char c = finalLayout[layoutY].charAt(x);
        return isWall(c);
    }

    private Maze createMazeFromText(int level, String[] layout, ItemDataManager itemDataManager, AssetManager assetManager) {
        int height = layout.length;
        int width = layout[0].length();
        int[][] bitmaskedData = new int[height][width];
        Maze maze = new Maze(level, bitmaskedData);

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
                    bitmaskedData[y][x] = 0b01010101;
                }
            }
        }
        return maze;
    }

    private int countReachable(Maze maze) {
        return computeReachableTiles(maze).size();
    }

    private Set<GridPoint2> computeReachableTiles(Maze maze) {
        Set<GridPoint2> component = new HashSet<>();
        Queue<GridPoint2> queue = new ArrayDeque<>();

        int midX = CHUNK_SIZE / 2;
        int midY = CHUNK_SIZE / 2;
        GridPoint2 seed = new GridPoint2(midX, midY);
        if (maze.isWall(midX, midY) || (maze.getScenery().containsKey(seed) && maze.getScenery().get(seed).isImpassable())) {
            // Find alternate open seed
            for (int r = 1; r < CHUNK_SIZE / 2; r++) {
                for (int dy = -r; dy <= r; dy++) {
                    for (int dx = -r; dx <= r; dx++) {
                        int x = midX + dx;
                        int y = midY + dy;
                        if (x > 0 && x < CHUNK_SIZE - 1 && y > 0 && y < CHUNK_SIZE - 1 && !maze.isWall(x, y)) {
                            GridPoint2 cand = new GridPoint2(x, y);
                            if (!maze.getScenery().containsKey(cand) || !maze.getScenery().get(cand).isImpassable()) {
                                seed = cand;
                                break;
                            }
                        }
                    }
                }
            }
        }

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
                if (maze.getScenery().containsKey(next) && maze.getScenery().get(next).isImpassable()) continue;

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
