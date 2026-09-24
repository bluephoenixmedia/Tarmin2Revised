package com.bpm.minotaur.generation;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.gamedata.*;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.ItemColor;
import com.bpm.minotaur.gamedata.item.ItemDataManager;
import com.bpm.minotaur.gamedata.monster.MonsterDataManager;
import com.bpm.minotaur.gamedata.spawntables.SpawnTableData;
import com.bpm.minotaur.managers.SpawnManager;
import com.bpm.minotaur.rendering.RetroTheme;

import java.util.*;

/**
 * Procedural generator for the Forest Biome.
 * Generates an authentic 36x36 wilderness featuring procedural organic trails,
 * central glade landmarks, secret loop groves, Synty-baked foliage billboards,
 * and alpine rock cliff boundaries.
 */
public class ForestChunkGenerator implements IChunkGenerator {

    public static final int CHUNK_SIZE = 36;

    private final Random random = new Random();
    private String[] finalLayout;
    private final GridPoint2 playerSpawnPoint = new GridPoint2(18, 18);
    private GridPoint2 forcedUpLadderPos = null;

    private static final String[] PINE_TEXTURES = {
            "images/forest/tree_pine_01.png",
            "images/forest/tree_pine_02.png",
            "images/forest/tree_pine_03.png"
    };

    private static final String[] BIRCH_TEXTURES = {
            "images/forest/tree_birch_01.png",
            "images/forest/tree_birch_02.png"
    };

    private static final String[] DEAD_TREE_TEXTURES = {
            "images/forest/tree_dead_01.png"
    };

    private static final String[] BOULDER_TEXTURES = {
            "images/forest/rock_boulder_01.png",
            "images/forest/rock_boulder_02.png",
            "images/forest/rock_boulder_03.png"
    };

    private static final String[] BUSH_TEXTURES = {
            "images/forest/bush_01.png",
            "images/forest/bush_02.png"
    };

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
                              com.bpm.minotaur.gamedata.encounters.EncounterManager encounterManager,
                              SpawnTableData spawnTableData,
                              long chunkSeed,
                              int playerLuck) {

        random.setSeed(chunkSeed);

        // 1. Generate 36x36 procedural organic wilderness layout
        createProceduralForestLayout(CHUNK_SIZE, CHUNK_SIZE);

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

        // 3. Create Maze and instantiate foliage / scenery billboards
        Maze maze = createMazeFromText(layoutLevel, this.finalLayout, itemDataManager, assetManager);
        maze.setBiome(Biome.FOREST);
        maze.setTheme(theme != null ? theme : RetroTheme.FOREST_THEME);
        maze.setSecondaryTheme(mazeTheme);

        // 4. Compute traversable reachable tiles
        Set<GridPoint2> reachable = computeReachableTiles(maze);

        // 5. Spawn Central Glade Landmarks & Secret Grove POIs
        spawnLandmarks(maze, assetManager, itemDataManager, chunkSeed);

        // 6. Spawn Monsters, Items, Encounters, Ladders
        spawnEntities(maze, difficulty, spawnDifficulty, this.finalLayout, dataManager, itemDataManager, assetManager,
                spawnTableData, chunkSeed, playerLuck, reachable);

        spawnEncounters(maze, encounterManager, reachable, assetManager);

        spawnLadder(maze, this.finalLayout, reachable);

        // 7. Spawn Chunk Transition Gates at 4 Cardinal Gates
        spawnTransitionGates(maze, this.finalLayout, chunkId);

        // 8. Find valid player start position
        findPlayerStart(this.finalLayout);

        return maze;
    }

    @Override
    public GridPoint2 getInitialPlayerStartPos() {
        return playerSpawnPoint;
    }

    private void createProceduralForestLayout(int width, int height) {
        char[][] grid = new char[height][width];

        // Fill entire chunk with dense impassable wilderness
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (x == 0 || x == width - 1 || y == 0 || y == height - 1) {
                    grid[y][x] = '#'; // Sheer cliff boundary
                } else if (x == 1 || x == width - 2 || y == 1 || y == height - 2) {
                    grid[y][x] = 'T'; // Dense pine line backing cliffs
                } else {
                    float r = random.nextFloat();
                    if (r < 0.76f) {
                        grid[y][x] = 'T'; // Tree
                    } else if (r < 0.90f) {
                        grid[y][x] = 'R'; // Rock / Boulder
                    } else {
                        grid[y][x] = 'B'; // Bush
                    }
                }
            }
        }

        int midX = width / 2;  // 18
        int midY = height / 2; // 18

        // 1. Carve Central Glade: organic circular clearing around (18, 18)
        float gladeRadius = 4.2f;
        for (int y = midY - 6; y <= midY + 6; y++) {
            for (int x = midX - 6; x <= midX + 6; x++) {
                if (x <= 1 || x >= width - 2 || y <= 1 || y >= height - 2) continue;
                float dx = x - midX;
                float dy = y - midY;
                float dist = (float) Math.sqrt(dx * dx + dy * dy);
                float angle = (float) Math.atan2(dy, dx);
                float perturbedRadius = gladeRadius + 0.65f * (float) Math.sin(angle * 3.0f + 1.2f)
                        + 0.45f * (float) Math.cos(angle * 5.0f);
                if (dist <= perturbedRadius) {
                    grid[y][x] = '.';
                }
            }
        }

        // 2. Carve Secondary Secret Glades
        // NW Glade around (9, 27)
        carveSecondaryGlade(grid, 9, 27, 2.9f);
        // SE Glade around (27, 9)
        carveSecondaryGlade(grid, 27, 9, 2.9f);

        // 3. Clear 4 Cardinal Gate Approached Openings
        // North Gate at (18, 35)
        for (int y = height - 1; y >= height - 4; y--) grid[y][midX] = '.';
        // South Gate at (18, 0)
        for (int y = 0; y <= 3; y++) grid[y][midX] = '.';
        // East Gate at (35, 18)
        for (int x = width - 1; x >= width - 4; x--) grid[midY][x] = '.';
        // West Gate at (0, 18)
        for (int x = 0; x <= 3; x++) grid[midY][x] = '.';

        // 4. Carve Organic Meandering Trails connecting gates to central glade
        carveMeanderingTrail(grid, midX, 3, midX, midY - 3);         // South to Central
        carveMeanderingTrail(grid, midX, height - 4, midX, midY + 3); // North to Central
        carveMeanderingTrail(grid, 3, midY, midX - 3, midY);         // West to Central
        carveMeanderingTrail(grid, width - 4, midY, midX + 3, midY); // East to Central

        // 5. Carve Secret Trails to Groves and Loop Paths
        carveMeanderingTrail(grid, midX - 2, midY + 3, 9, 27);       // Central to NW Grove
        carveMeanderingTrail(grid, 9, 27, midX, height - 5);         // NW Grove to North Loop
        carveMeanderingTrail(grid, midX + 2, midY - 3, 27, 9);       // Central to SE Grove
        carveMeanderingTrail(grid, 27, 9, width - 5, midY);          // SE Grove to East Loop

        // 6. Natural Undergrowth Edge Softening (sprinkle bushes 'B' along trail borders)
        for (int y = 2; y < height - 2; y++) {
            for (int x = 2; x < width - 2; x++) {
                if (grid[y][x] == 'T') {
                    boolean adjToTrail = (grid[y + 1][x] == '.' || grid[y - 1][x] == '.' ||
                            grid[y][x + 1] == '.' || grid[y][x - 1] == '.');
                    if (adjToTrail && random.nextFloat() < 0.22f) {
                        grid[y][x] = 'B';
                    }
                }
            }
        }

        // Convert grid (y: 0..35 South to North) to finalLayout (index 0 is North, index 35 is South)
        this.finalLayout = new String[height];
        for (int y = 0; y < height; y++) {
            int layoutY = height - 1 - y;
            this.finalLayout[layoutY] = new String(grid[y]);
        }
    }

    private void carveSecondaryGlade(char[][] grid, int cx, int cy, float radius) {
        int rInt = (int) Math.ceil(radius + 1.5f);
        for (int y = cy - rInt; y <= cy + rInt; y++) {
            for (int x = cx - rInt; x <= cx + rInt; x++) {
                if (x <= 1 || x >= grid[0].length - 2 || y <= 1 || y >= grid.length - 2) continue;
                float dx = x - cx;
                float dy = y - cy;
                float dist = (float) Math.sqrt(dx * dx + dy * dy);
                if (dist <= radius + 0.4f * (float) Math.sin(dx * 2.1f + dy * 1.7f)) {
                    grid[y][x] = '.';
                }
            }
        }
    }

    private void carveMeanderingTrail(char[][] grid, int x0, int y0, int x1, int y1) {
        int curX = x0;
        int curY = y0;
        grid[curY][curX] = '.';

        int maxSteps = 200;
        int steps = 0;

        while ((curX != x1 || curY != y1) && steps++ < maxSteps) {
            int dx = x1 - curX;
            int dy = y1 - curY;

            boolean moveX;
            if (dx == 0) {
                moveX = false;
            } else if (dy == 0) {
                moveX = true;
            } else {
                moveX = (random.nextFloat() < (Math.abs(dx) / (float) (Math.abs(dx) + Math.abs(dy))));
            }

            // 18% meandering noise step
            if (random.nextFloat() < 0.18f) {
                moveX = !moveX;
            }

            if (moveX) {
                curX += (dx != 0) ? Integer.signum(dx) : (random.nextBoolean() ? 1 : -1);
            } else {
                curY += (dy != 0) ? Integer.signum(dy) : (random.nextBoolean() ? 1 : -1);
            }

            curX = Math.max(2, Math.min(grid[0].length - 3, curX));
            curY = Math.max(2, Math.min(grid.length - 3, curY));

            grid[curY][curX] = '.';

            // Occasionally broaden path to 2 tiles at curves
            if (random.nextFloat() < 0.35f) {
                if (moveX && curY + 1 < grid.length - 2) {
                    grid[curY + 1][curX] = '.';
                } else if (!moveX && curX + 1 < grid[0].length - 2) {
                    grid[curY][curX + 1] = '.';
                }
            }
        }
    }

    private void spawnLandmarks(Maze maze, AssetManager assetManager, ItemDataManager itemDataManager, long seed) {
        int archetype = (int) (Math.abs(seed) % 3);

        int centerX = 18;
        int centerY = 18;

        if (archetype == 0) {
            // Archetype 0: Ranger Campsite
            placeThemedProp(maze, "campfire", centerX, centerY, assetManager);
            placeThemedProp(maze, "camp_tent", centerX + 2, centerY + 2, assetManager);
            placeThemedProp(maze, "stump_pine", centerX - 2, centerY, assetManager);
            placeThemedProp(maze, "log_pile", centerX, centerY - 2, assetManager);
            if (itemDataManager != null) {
                Item arrows = itemDataManager.createItem(Item.ItemType.QUIVER, centerX + 1, centerY - 1, ItemColor.TAN, assetManager);
                if (arrows != null) {
                    maze.addItem(arrows);
                }
            }
            Gdx.app.log("ForestChunkGenerator", "Spawned Ranger Campsite Landmark in central glade");
        } else if (archetype == 1) {
            // Archetype 1: Ancient Runestone Circle
            placeThemedProp(maze, "runestone", centerX, centerY, assetManager);
            placeThemedProp(maze, "cairn", centerX, centerY + 3, assetManager);
            placeThemedProp(maze, "cairn", centerX, centerY - 3, assetManager);
            placeThemedProp(maze, "cairn", centerX - 3, centerY, assetManager);
            placeThemedProp(maze, "cairn", centerX + 3, centerY, assetManager);
            Gdx.app.log("ForestChunkGenerator", "Spawned Ancient Runestone Circle Landmark in central glade");
        } else {
            // Archetype 2: Overgrown Ruins
            placeThemedProp(maze, "ruined_pillar", centerX - 2, centerY + 2, assetManager);
            placeThemedProp(maze, "ruined_pillar", centerX + 2, centerY - 2, assetManager);
            placeThemedProp(maze, "rubble_pile", centerX, centerY, assetManager);
            placeThemedProp(maze, "bramble", centerX - 1, centerY - 1, assetManager);
            placeThemedProp(maze, "bramble", centerX + 1, centerY + 1, assetManager);
            Gdx.app.log("ForestChunkGenerator", "Spawned Overgrown Ruins Landmark in central glade");
        }

        // Secret Grove Caches
        // NW Grove (9, 27)
        placeThemedProp(maze, "log_fallen", 9, 27, assetManager);
        if (itemDataManager != null) {
            Item potion = itemDataManager.createItem(Item.ItemType.POTION_BLUE, 9, 28, ItemColor.BLUE, assetManager);
            if (potion != null) {
                maze.addItem(potion);
            }
        }

        // SE Grove (27, 9)
        placeThemedProp(maze, "grave_mound", 27, 9, assetManager);
        placeThemedProp(maze, "cairn", 28, 9, assetManager);
    }

    private void placeThemedProp(Maze maze, String propId, int x, int y, AssetManager assetManager) {
        if (x < 1 || x >= CHUNK_SIZE - 1 || y < 1 || y >= CHUNK_SIZE - 1) return;
        GridPoint2 pos = new GridPoint2(x, y);
        if (maze.getScenery().containsKey(pos) || maze.getItems().containsKey(pos)) return;

        Scenery s = Scenery.fromProp(propId, x, y);
        if (s != null) {
            s.setFlippedX(random.nextBoolean());
            if (s.getTexturePath() != null) {
                loadTextureSafely(s, s.getTexturePath(), assetManager);
            }
            maze.addScenery(s);
        }
    }

    private void loadTextureSafely(Scenery s, String path, AssetManager assetManager) {
        if (path == null) return;
        s.setTexturePath(path);
        try {
            if (assetManager != null && Gdx.files != null && Gdx.files.internal(path) != null && Gdx.files.internal(path).exists()) {
                if (!assetManager.isLoaded(path)) {
                    assetManager.load(path, Texture.class);
                    assetManager.finishLoading();
                }
                s.setTexture(assetManager.get(path, Texture.class));
            }
        } catch (Exception ignored) {
            // Safe fallback during tests without LibGDX graphics context
        }
    }

    private void findPlayerStart(String[] layout) {
        int height = layout.length;
        int width = layout[0].length();

        // 1. Reset to central glade if open
        int midX = width / 2;
        int midY = height / 2;
        int layoutMidY = height - 1 - midY;
        if (layout[layoutMidY].charAt(midX) == '.') {
            playerSpawnPoint.set(midX, midY);
            return;
        }

        // 2. Scan for first passable floor tile
        for (int y = 0; y < height; y++) {
            int layoutY = height - 1 - y;
            for (int x = 0; x < width; x++) {
                char c = layout[layoutY].charAt(x);
                if (c == '.' || c == 'B') {
                    playerSpawnPoint.set(x, y);
                    return;
                }
            }
        }
    }

    private void spawnEntities(Maze maze, Difficulty difficulty, int level, String[] layout,
                               MonsterDataManager dataManager, ItemDataManager itemDataManager, AssetManager assetManager,
                               SpawnTableData spawnTableData, long chunkSeed, int playerLuck, Set<GridPoint2> reachable) {

        if (spawnTableData == null) return;

        long spawnSeed = chunkSeed ^ 0xDEADBEEF12345678L;
        SpawnManager spawnManager = new SpawnManager(dataManager, itemDataManager, assetManager,
                maze, difficulty, level, level, playerLuck, layout, spawnTableData, spawnSeed, reachable);
        spawnManager.spawnEntities();
    }

    private void spawnEncounters(Maze maze, com.bpm.minotaur.gamedata.encounters.EncounterManager encounterManager,
                                 Set<GridPoint2> reachable, AssetManager assetManager) {
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
                com.bpm.minotaur.gamedata.encounters.Encounter enc = encounterManager.getEncounter(encounterId);
                String img = (enc != null) ? enc.imagePath : null;
                Scenery statue = new Scenery(Scenery.SceneryType.STATUE, pos.x, pos.y, img);
                if (img != null) {
                    loadTextureSafely(statue, img, assetManager);
                }
                maze.addScenery(statue);
                placed++;
            }
        }
    }

    void spawnLadder(Maze maze, String[] layout, Set<GridPoint2> reachable) {
        GridPoint2 upLadderPos = null;
        if (forcedUpLadderPos != null) {
            upLadderPos = new GridPoint2(forcedUpLadderPos.x, forcedUpLadderPos.y);
            maze.addLadder(new Ladder(forcedUpLadderPos.x, forcedUpLadderPos.y, Ladder.LadderType.UP, Ladder.EntranceStyle.ROPE));
            forcedUpLadderPos = null;
        }

        List<GridPoint2> candidates = new ArrayList<>();
        int height = maze.getHeight();
        for (int y = 0; y < height; y++) {
            int layoutY = height - 1 - y;
            for (int x = 0; x < maze.getWidth(); x++) {
                if (layout[layoutY].charAt(x) != '.') continue;
                GridPoint2 pos = new GridPoint2(x, y);
                if (!reachable.contains(pos)) continue;
                if (maze.getItems().containsKey(pos)) continue;
                if (maze.getMonsters().containsKey(pos)) continue;
                if (upLadderPos != null && x == upLadderPos.x && y == upLadderPos.y) continue;
                candidates.add(pos);
            }
        }

        Ladder.EntranceStyle style = (random.nextBoolean()) ? Ladder.EntranceStyle.SINKHOLE : Ladder.EntranceStyle.CAVE_MOUTH;

        int targetLadders = 2 + random.nextInt(2); // 2 or 3
        List<GridPoint2> placed = new ArrayList<>();
        if (upLadderPos != null) {
            placed.add(upLadderPos);
        }

        int downLaddersPlaced = 0;
        for (int i = 0; i < targetLadders && !candidates.isEmpty(); i++) {
            GridPoint2 best = null;
            float maxMinDist = -1;
            int samples = Math.min(15, candidates.size());
            for (int s = 0; s < samples; s++) {
                GridPoint2 cand = candidates.get(random.nextInt(candidates.size()));
                float minDist = Float.MAX_VALUE;
                for (GridPoint2 p : placed) {
                    float dist = Math.abs(cand.x - p.x) + Math.abs(cand.y - p.y);
                    if (dist < minDist) minDist = dist;
                }
                if (minDist >= 8) {
                    best = cand;
                    break;
                }
                if (minDist > maxMinDist) {
                    maxMinDist = minDist;
                    best = cand;
                }
            }
            if (best == null) {
                best = candidates.get(random.nextInt(candidates.size()));
            }
            candidates.remove(best);
            placed.add(best);
            maze.addLadder(new Ladder(best.x, best.y, Ladder.LadderType.DOWN, style));
            downLaddersPlaced++;
        }

        if (downLaddersPlaced == 0) {
            List<GridPoint2> fallbackCandidates = new ArrayList<>();
            for (GridPoint2 pos : reachable) {
                int layoutY = height - 1 - pos.y;
                if (layoutY >= 0 && layoutY < layout.length && pos.x >= 0 && pos.x < maze.getWidth()) {
                    if (layout[layoutY].charAt(pos.x) == '.' &&
                            (upLadderPos == null || pos.x != upLadderPos.x || pos.y != upLadderPos.y)) {
                        fallbackCandidates.add(pos);
                    }
                }
            }
            if (!fallbackCandidates.isEmpty()) {
                GridPoint2 chosen = fallbackCandidates.get(random.nextInt(fallbackCandidates.size()));
                maze.addLadder(new Ladder(chosen.x, chosen.y, Ladder.LadderType.DOWN, style));
            }
        }
    }

    private Set<GridPoint2> computeReachableTiles(Maze maze) {
        int height = maze.getHeight();
        int width = maze.getWidth();

        if (forcedUpLadderPos != null) {
            int layoutY = height - 1 - forcedUpLadderPos.y;
            if (forcedUpLadderPos.x >= 0 && forcedUpLadderPos.x < width
                    && forcedUpLadderPos.y >= 0 && forcedUpLadderPos.y < height
                    && isTraversable(finalLayout[layoutY].charAt(forcedUpLadderPos.x))) {
                Set<GridPoint2> fromEntrance = floodFillComponent(maze, forcedUpLadderPos);
                if (!fromEntrance.isEmpty()) {
                    return fromEntrance;
                }
            }
        }

        Set<GridPoint2> visited = new HashSet<>();
        Set<GridPoint2> largestComponent = new HashSet<>();

        // Check from central glade first
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

        // Instantiate Foliage and Scenery Objects
        for (int y = 0; y < height; y++) {
            int layoutY = height - 1 - y;
            for (int x = 0; x < width; x++) {
                char c = layout[layoutY].charAt(x);
                switch (c) {
                    case 'T': {
                        Scenery s = new Scenery(Scenery.SceneryType.TREE, x, y);
                        s.setFlippedX(random.nextBoolean());

                        // Tree type selection: 65% pine, 25% birch, 10% dead tree
                        float r = random.nextFloat();
                        String path;
                        if (r < 0.65f) {
                            path = PINE_TEXTURES[random.nextInt(PINE_TEXTURES.length)];
                        } else if (r < 0.90f) {
                            path = BIRCH_TEXTURES[random.nextInt(BIRCH_TEXTURES.length)];
                        } else {
                            path = DEAD_TREE_TEXTURES[random.nextInt(DEAD_TREE_TEXTURES.length)];
                        }

                        // Organic scale jitter
                        float jitter = 0.92f + random.nextFloat() * 0.22f;
                        s.scale.set(2.0f * jitter, 4.0f * jitter);

                        loadTextureSafely(s, path, assetManager);
                        maze.addScenery(s);
                        break;
                    }
                    case 'R': {
                        Scenery s = new Scenery(Scenery.SceneryType.ROCK, x, y);
                        s.setFlippedX(random.nextBoolean());
                        String path = BOULDER_TEXTURES[random.nextInt(BOULDER_TEXTURES.length)];

                        float jitter = 0.85f + random.nextFloat() * 0.35f;
                        s.scale.set(1.0f * jitter, 0.8f * jitter);

                        loadTextureSafely(s, path, assetManager);
                        maze.addScenery(s);
                        break;
                    }
                    case 'B': {
                        Scenery s = new Scenery(Scenery.SceneryType.BUSH, x, y);
                        s.setFlippedX(random.nextBoolean());
                        String path = BUSH_TEXTURES[random.nextInt(BUSH_TEXTURES.length)];

                        float jitter = 0.9f + random.nextFloat() * 0.25f;
                        s.scale.set(1.0f * jitter, 0.75f * jitter);

                        loadTextureSafely(s, path, assetManager);
                        maze.addScenery(s);
                        break;
                    }
                }
            }
        }

        // Bitmasking loop for cliff perimeter walls '#' and maze walls 'M'
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

    private boolean isMaze(int x, int y) {
        return Math.max(Math.abs(x), Math.abs(y)) <= WorldConstants.CENTRAL_MAZE_RADIUS;
    }

    private boolean isWall(char c) {
        return c == '#' || c == 'M';
    }

    private boolean isTraversable(char c) {
        return c == '.' || c == 'B';
    }
}
