package com.bpm.minotaur.generation;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.gamedata.*;
import com.bpm.minotaur.gamedata.item.ItemDataManager;
import com.bpm.minotaur.gamedata.monster.MonsterDataManager;
import com.bpm.minotaur.gamedata.spawntables.SpawnTableData; // <-- ADD THIS IMPORT
import com.bpm.minotaur.managers.SpawnManager;
import com.bpm.minotaur.rendering.RetroTheme;

import com.bpm.minotaur.gamedata.Direction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Random;
import java.util.Set;

/**
 * Generates and populates a "forest" chunk.
 * This biome is an open layout with trees, rocks, and bushes
 * that create a natural "maze".
 */
public class ForestChunkGenerator implements IChunkGenerator {

    private final Random random = new Random();
    private String[] finalLayout;
    // playerSpawnPoint is not used here, but required by the generator logic
    private GridPoint2 playerSpawnPoint = new GridPoint2(1, 1);

    // --- Forest Tile Definitions ---
    // # = Impassable Chunk Boundary Wall (only for edges)
    // . = Passable Floor (Grass)
    // T = Impassable Tree
    // R = Impassable Rock
    // B = Passable Bush

    String[] forestTile1 = new String[] {
            "TTTTTTT....T",
            ".T...T...R.T",
            "T...B..TTT.T",
            ".T....B.....",
            "T....TT.TTTT",
            "T..R..T....T",
            "TTT..B...R.T",
            "T..T...T.T..",
            "....R..T...T",
            "T.T...B...BT",
            "T...TT...R.T",
            "TTT..TTTTTTT"
    };
    String[] forestTile2 = new String[] {
            "T..R.TT....T",
            "T.T...T..R.T",
            "TB.B..TTT.TT",
            ".T.T..B.....",
            "T..B.TT.TTTT",
            "T..R..T....T",
            "TT...B...R.T",
            "T..T...T.T..",
            "..T.R..T...T",
            "T.T...B...BT",
            "T...TT...R.T",
            "TTT..TTTTTTT"
    };

    String[] forestTile3 = new String[] {
            "TTTTRTT....T",
            "T.T...T..R.T",
            "TB.B...TT.TT",
            ".T.T..B.....",
            "T..B.TT.TTTT",
            "T..R...R...T",
            "TT...B...R.T",
            "T..T...T.T..",
            "..T.R..T...T",
            "T.T...B...BT",
            "T...TT...R.T",
            "TTT..TTTTTTT"
    };

    String[] forestTile4 = new String[] {
            "TTTTRTT....T",
            "T.T...T..R.T",
            "TB.B...TT.TT",
            ".T.T..B.....",
            "T..B.TT.TTRT",
            "T..R...R.B.T",
            "TT...B...R.T",
            "T..T...T.T..",
            "..T.R..T...T",
            "T.T...B...BT",
            "TR..TT...R.T",
            "TTT..TTTTTTT"
    };

    // --- Corridor Tile Templates (same as MazeChunkGenerator) ---
    String[] corridorUL = new String[] { "############", "#...........", "#.xxxxxxxxxx", "#.xxxxxxxxxx", "#.xxxxxxxxxx",
            "#.xxxxxxxxxx", "#.xxxxxxxxxx", "#.xxxxxxxxxx", "#.xxxxxxxxxx", "#.xxxxxxxxxx", "#.xxxxxxxxxx",
            "#.xxxxxxxxxx" };
    String[] corridorT = new String[] { "############", "............", "xxxxxxxxxxxx", "xxxxxxxxxxxx", "xxxxxxxxxxxx",
            "xxxxxxxxxxxx", "xxxxxxxxxxxx", "xxxxxxxxxxxx", "xxxxxxxxxxxx", "xxxxxxxxxxxx", "xxxxxxxxxxxx",
            "xxxxxxxxxxxx" };
    String[] corridorUR = new String[] { "############", "...........#", "xxxxxxxxxx.#", "xxxxxxxxxx.#", "xxxxxxxxxx.#",
            "xxxxxxxxxx.#", "xxxxxxxxxx.#", "xxxxxxxxxx.#", "xxxxxxxxxx.#", "xxxxxxxxxx.#", "xxxxxxxxxx.#",
            "xxxxxxxxxx.#" };
    String[] corridorL = new String[] { "#.xxxxxxxxxx", "#.xxxxxxxxxx", "#.xxxxxxxxxx", "#.xxxxxxxxxx", "#.xxxxxxxxxx",
            "#.xxxxxxxxxx", "#.xxxxxxxxxx", "#.xxxxxxxxxx", "#.xxxxxxxxxx", "#.xxxxxxxxxx", "#.xxxxxxxxxx",
            "#.xxxxxxxxxx" };
    String[] corridorR = new String[] { "xxxxxxxxxx.#", "xxxxxxxxxx.#", "xxxxxxxxxx.#", "xxxxxxxxxx.#", "xxxxxxxxxx.#",
            "xxxxxxxxxx.#", "xxxxxxxxxx.#", "xxxxxxxxxx.#", "xxxxxxxxxx.#", "xxxxxxxxxx.#", "xxxxxxxxxx.#",
            "xxxxxxxxxx.#" };
    String[] corridorLL = new String[] { "#.xxxxxxxxxx", "#.xxxxxxxxxx", "#.xxxxxxxxxx", "#.xxxxxxxxxx", "#.xxxxxxxxxx",
            "#.xxxxxxxxxx", "#.xxxxxxxxxx", "#.xxxxxxxxxx", "#.xxxxxxxxxx", "#.xxxxxxxxxx", "#...........",
            "############" };
    String[] corridorB = new String[] { "xxxxxxxxxxxx", "xxxxxxxxxxxx", "xxxxxxxxxxxx", "xxxxxxxxxxxx", "xxxxxxxxxxxx",
            "xxxxxxxxxxxx", "xxxxxxxxxxxx", "xxxxxxxxxxxx", "xxxxxxxxxxxx", "xxxxxxxxxxxx", "............",
            "############" };
    String[] corridorLR = new String[] { "xxxxxxxxxx.#", "xxxxxxxxxx.#", "xxxxxxxxxx.#", "xxxxxxxxxx.#", "xxxxxxxxxx.#",
            "xxxxxxxxxx.#", "xxxxxxxxxx.#", "xxxxxxxxxx.#", "xxxxxxxxxx.#", "xxxxxxxxxx.#", "...........#",
            "############" };

    // A simple 2x2 grid of tile info
    private static class TileInfo {
        int id;
        int rotation;

        TileInfo(int id, int rotation) {
            this.id = id;
            this.rotation = rotation;
        }
    }

    /**
     * [MODIFIED] Signature updated to match IChunkGenerator
     */
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
            int playerLuck) { // <-- ADDED THIS PARAM

        random.setSeed(chunkSeed);

        // Forest chunks are always 3x2, regardless of game mode
        int mapRows = 3;
        int mapCols = 2;
        createForestFromArrayTiles(mapRows, mapCols);

        // --- BORDER LOGIC ---
        // Replace '#' with 'M' on borders adjacent to Key/Maze Chunks
        boolean northIsMaze = isMaze(chunkId.x, chunkId.y + 1);
        boolean southIsMaze = isMaze(chunkId.x, chunkId.y - 1);
        boolean eastIsMaze = isMaze(chunkId.x + 1, chunkId.y);
        boolean westIsMaze = isMaze(chunkId.x - 1, chunkId.y);

        int height = this.finalLayout.length;
        int width = this.finalLayout[0].length();

        // North Border (Array Index 0)
        if (northIsMaze) {
            char[] row = this.finalLayout[0].toCharArray();
            for (int x = 0; x < width; x++) {
                if (row[x] == '#')
                    row[x] = 'M';
            }
            this.finalLayout[0] = new String(row);
        } else {
            // Seamless neighbor: Clear obstacles
            char[] row = this.finalLayout[0].toCharArray();
            for (int x = 0; x < width; x++) {
                if (row[x] == 'T' || row[x] == 'R' || row[x] == '#')
                    row[x] = '.';
            }
            this.finalLayout[0] = new String(row);
        }

        // South Border (Array Index height-1)
        if (southIsMaze) {
            char[] row = this.finalLayout[height - 1].toCharArray();
            for (int x = 0; x < width; x++) {
                if (row[x] == '#')
                    row[x] = 'M';
            }
            this.finalLayout[height - 1] = new String(row);
        } else {
            // Seamless neighbor
            char[] row = this.finalLayout[height - 1].toCharArray();
            for (int x = 0; x < width; x++) {
                if (row[x] == 'T' || row[x] == 'R' || row[x] == '#')
                    row[x] = '.';
            }
            this.finalLayout[height - 1] = new String(row);
        }

        // East/West Borders
        for (int y = 0; y < height; y++) {
            char[] row = this.finalLayout[y].toCharArray();
            boolean changed = false;

            // West Border (Index 0)
            if (westIsMaze && row[0] == '#') {
                row[0] = 'M';
                changed = true;
            } else if (!westIsMaze) {
                if (row[0] == 'T' || row[0] == 'R' || row[0] == '#') {
                    row[0] = '.';
                    changed = true;
                }
            }

            // East Border (Index width-1)
            if (eastIsMaze && row[width - 1] == '#') {
                row[width - 1] = 'M';
                changed = true;
            } else if (!eastIsMaze) {
                if (row[width - 1] == 'T' || row[width - 1] == 'R' || row[width - 1] == '#') {
                    row[width - 1] = '.';
                    changed = true;
                }
            }

            if (changed) {
                this.finalLayout[y] = new String(row);
            }
        }

        // --- 2. Create Maze Object ---
        Maze maze = createMazeFromText(layoutLevel, this.finalLayout, itemDataManager, assetManager);
        maze.setTheme(theme); // Set the theme (e.g., FOREST_THEME)
        maze.setSecondaryTheme(mazeTheme);

        // --- 3. Populate Maze ---
        Set<GridPoint2> reachable = computeReachableTiles(maze);

        spawnEntities(maze, difficulty, spawnDifficulty, this.finalLayout, dataManager, itemDataManager, assetManager,
                spawnTableData, chunkSeed, playerLuck, reachable);

        spawnEncounters(maze, encounterManager, reachable);

        spawnLadder(maze, this.finalLayout, reachable);

        // --- 4. Place Gates ---
        // Forests *only* use transition gates, even in classic mode
        // (or else the player would be stuck)
        spawnTransitionGates(maze, this.finalLayout, chunkId); // New chunk-transition gates

        // --- 5. Find Player Start ---
        // This is necessary to set *a* valid spawn point, even if the
        // player will never start a new game here.
        findPlayerStart(this.finalLayout);

        return maze;
    }

    @Override
    public GridPoint2 getInitialPlayerStartPos() {
        // This generator is never used for the initial spawn,
        // so we can return a simple default.
        return playerSpawnPoint;
    }

    /**
     * Finds a safe, passable spawn point.
     * 
     * @param layout The newly generated text layout.
     */
    private void findPlayerStart(String[] layout) {
        int height = layout.length;
        int width = layout[0].length();

        // 1. Reset to default
        playerSpawnPoint.set(1, 1);

        // 2. Check if (1, 1) is passable
        char defaultTileChar = layout[height - 2].charAt(1);
        if (defaultTileChar == '.' || defaultTileChar == 'B') {
            Gdx.app.log("ForestChunkGenerator", "Set player start to default (1, 1).");
            return;
        }

        // 3. Scan for first passable tile
        Gdx.app.log("ForestChunkGenerator", "Default spawn (1, 1) is blocked. Scanning...");
        for (int y = 0; y < height; y++) {
            int layoutY = height - 1 - y;
            for (int x = 0; x < width; x++) {
                char c = layout[layoutY].charAt(x);
                if (c == '.' || c == 'B') { // Find first floor or bush
                    playerSpawnPoint.set(x, y);
                    Gdx.app.log("ForestChunkGenerator", "Found fallback safe spawn at (" + x + ", " + y + ")");
                    return;
                }
            }
        }
    }

    private void spawnEntities(Maze maze, Difficulty difficulty, int level, String[] layout,
            MonsterDataManager dataManager, ItemDataManager itemDataManager, AssetManager assetManager,
            SpawnTableData spawnTableData, long chunkSeed, int playerLuck, Set<GridPoint2> reachable) {

        long spawnSeed = chunkSeed ^ 0xDEADBEEF12345678L;

        SpawnManager spawnManager = new SpawnManager(dataManager, itemDataManager, assetManager,
                maze, difficulty, level, level, playerLuck, layout, spawnTableData, spawnSeed, reachable);
        spawnManager.spawnEntities();
    }

    private void spawnEncounters(Maze maze, com.bpm.minotaur.gamedata.encounters.EncounterManager encounterManager,
            Set<GridPoint2> reachable) {
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
                Gdx.app.log("ForestChunkGenerator", "Added event " + encounterId + " at " + pos.x + "," + pos.y);
                placed++;
            }
        }
    }

    private void spawnLadder(Maze maze, String[] layout, Set<GridPoint2> reachable) {
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
                candidates.add(pos);
            }
        }

        if (!candidates.isEmpty()) {
            GridPoint2 chosen = candidates.get(random.nextInt(candidates.size()));
            maze.addLadder(new Ladder(chosen.x, chosen.y));
            Gdx.app.log("ForestChunkGenerator", "Ladder spawned at (" + chosen.x + ", " + chosen.y + ")");
        } else {
            // Fallback
            int x, y;
            do {
                x = random.nextInt(maze.getWidth());
                y = random.nextInt(maze.getHeight());
            } while (layout[maze.getHeight() - 1 - y].charAt(x) != '.' ||
                    maze.getItems().containsKey(new GridPoint2(x, y)) ||
                    maze.getMonsters().containsKey(new GridPoint2(x, y)));
            Gdx.app.log("ForestChunkGenerator", "WARN: No reachable ladder candidate, fallback at (" + x + "," + y + ")");
            maze.addLadder(new Ladder(x, y));
        }
    }

    /** BFS flood-fill from first '.' tile; doors treated as always open. */
    private Set<GridPoint2> computeReachableTiles(Maze maze) {
        GridPoint2 seed = findFirstFloorTile(maze);
        if (seed == null) return Collections.emptySet();

        Set<GridPoint2> reachable = new HashSet<>();
        Queue<GridPoint2> queue = new LinkedList<>();
        reachable.add(seed);
        queue.add(seed);

        while (!queue.isEmpty()) {
            GridPoint2 cur = queue.poll();
            for (Direction dir : Direction.values()) {
                if ((maze.getWallDataAt(cur.x, cur.y) & dir.getWallMask()) != 0) continue;

                int nx = cur.x + (int) dir.getVector().x;
                int ny = cur.y + (int) dir.getVector().y;
                if (nx < 0 || nx >= maze.getWidth() || ny < 0 || ny >= maze.getHeight()) continue;

                GridPoint2 next = new GridPoint2(nx, ny);
                if (reachable.contains(next)) continue;

                int layoutY = maze.getHeight() - 1 - ny;
                if (isWall(finalLayout[layoutY].charAt(nx))) continue;

                reachable.add(next);
                queue.add(next);
            }
        }

        Gdx.app.log("ForestChunkGenerator", "Reachability: " + reachable.size() + " tiles reachable from " + seed);
        return reachable;
    }

    private GridPoint2 findFirstFloorTile(Maze maze) {
        int height = maze.getHeight();
        int width = maze.getWidth();
        for (int y = 0; y < height; y++) {
            int layoutY = height - 1 - y;
            for (int x = 0; x < width; x++) {
                if (finalLayout[layoutY].charAt(x) == '.') {
                    return new GridPoint2(x, y);
                }
            }
        }
        return null;
    }

    /**
     * Spawns four transition gates for ADVANCED mode.
     */
    private void spawnTransitionGates(Maze maze, String[] layout, GridPoint2 chunkId) {
        int height = layout.length;
        int width = layout[0].length();

        GridPoint2 northPos = new GridPoint2(width / 2, height - 1);
        GridPoint2 southPos = new GridPoint2(width / 2, 0);
        GridPoint2 eastPos = new GridPoint2(width - 1, height / 2);
        GridPoint2 westPos = new GridPoint2(0, height / 2);

        // Define where gates lead
        GridPoint2 northTargetChunk = new GridPoint2(chunkId.x, chunkId.y + 1);
        GridPoint2 southTargetChunk = new GridPoint2(chunkId.x, chunkId.y - 1);
        GridPoint2 eastTargetChunk = new GridPoint2(chunkId.x + 1, chunkId.y);
        GridPoint2 westTargetChunk = new GridPoint2(chunkId.x - 1, chunkId.y);

        // Define where player appears in the new chunk
        GridPoint2 northTargetPlayer = new GridPoint2(width / 2, 1);
        GridPoint2 southTargetPlayer = new GridPoint2(width / 2, height - 2);
        GridPoint2 eastTargetPlayer = new GridPoint2(1, height / 2);
        GridPoint2 westTargetPlayer = new GridPoint2(width - 2, height / 2);

        maze.addGate(new Gate(northPos.x, northPos.y, northTargetChunk, northTargetPlayer));
        maze.addGate(new Gate(southPos.x, southPos.y, southTargetChunk, southTargetPlayer));
        maze.addGate(new Gate(eastPos.x, eastPos.y, eastTargetChunk, eastTargetPlayer));
        maze.addGate(new Gate(westPos.x, westPos.y, westTargetChunk, westTargetPlayer));

        Gdx.app.log("ForestChunkGenerator", "Spawned 4 transition gates for chunk " + chunkId);
    }

    private void createForestFromArrayTiles(int mapRows, int mapCols) {
        // We only have 4 forest tiles, so we just list them
        List<String[]> allTiles = List.of(forestTile1, forestTile2, forestTile3, forestTile4);
        TileInfo[][] mapLayout = new TileInfo[mapRows][mapCols];

        // This generator is much simpler. It just places random tiles
        // and doesn't bother with stitching, since forests are
        // naturally chaotic.
        for (int mapY = 0; mapY < mapRows; mapY++) {
            for (int mapX = 0; mapX < mapCols; mapX++) {
                int tileId = random.nextInt(allTiles.size());
                int rotation = random.nextInt(4);
                mapLayout[mapY][mapX] = new TileInfo(tileId, rotation);
            }
        }

        // --- Stitching (largely the same as MazeChunkGenerator) ---
        int tileHeight = allTiles.get(0).length;
        int finalHeight = mapRows * tileHeight;
        this.finalLayout = new String[finalHeight];

        for (int mapY = 0; mapY < mapRows; mapY++) {
            for (int tileY = 0; tileY < tileHeight; tileY++) {
                StringBuilder rowBuilder = new StringBuilder();
                for (int mapX = 0; mapX < mapCols; mapX++) {
                    TileInfo info = mapLayout[mapY][mapX];
                    String[] contentTile = rotateTile(allTiles.get(info.id), info.rotation);
                    String[] corridorTile = getCorridorTemplate(mapX, mapY, mapCols, mapRows);

                    String mergedRow = mergeTileRow(corridorTile[tileY], contentTile[tileY]);
                    rowBuilder.append(mergedRow);
                }
                this.finalLayout[mapY * tileHeight + tileY] = rowBuilder.toString();
            }
        }
    }

    private String mergeTileRow(String corridorRow, String contentRow) {
        StringBuilder merged = new StringBuilder(corridorRow);
        for (int i = 0; i < corridorRow.length(); i++) {
            if (corridorRow.charAt(i) == 'x') {
                merged.setCharAt(i, contentRow.charAt(i));
            }
        }
        return merged.toString();
    }

    private String[] getCorridorTemplate(int x, int y, int cols, int rows) {
        boolean isTop = (y == 0);
        boolean isBottom = (y == rows - 1);
        boolean isLeft = (x == 0);
        boolean isRight = (x == cols - 1);

        if (isTop && isLeft)
            return corridorUL;
        if (isTop && isRight)
            return corridorUR;
        if (isBottom && isLeft)
            return corridorLL;
        if (isBottom && isRight)
            return corridorLR;
        if (isTop)
            return corridorT;
        if (isBottom)
            return corridorB;
        if (isLeft)
            return corridorL;
        if (isRight)
            return corridorR;

        // Center tile (no walls)
        return new String[] { "xxxxxxxxxxxx", "xxxxxxxxxxxx", "xxxxxxxxxxxx", "xxxxxxxxxxxx", "xxxxxxxxxxxx",
                "xxxxxxxxxxxx", "xxxxxxxxxxxx", "xxxxxxxxxxxx", "xxxxxxxxxxxx", "xxxxxxxxxxxx", "xxxxxxxxxxxx",
                "xxxxxxxxxxxx" };
    }

    private String[] rotateTile(String[] tile, int rotation) {
        if (rotation == 0)
            return tile;
        String[] currentTile = tile;
        for (int i = 0; i < rotation; i++) {
            int height = currentTile.length;
            int width = currentTile[0].length();
            char[][] temp = new char[width][height];
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    temp[x][height - 1 - y] = currentTile[y].charAt(x);
                }
            }
            String[] rotated = new String[width];
            for (int x = 0; x < width; x++) {
                rotated[x] = new String(temp[x]);
            }
            currentTile = rotated;
        }
        return currentTile;
    }

    private Maze createMazeFromText(int level, String[] layout, ItemDataManager itemDataManager,
            AssetManager assetManager) {
        int height = layout.length;
        int width = layout[0].length();
        int[][] bitmaskedData = new int[height][width];
        Maze maze = new Maze(level, bitmaskedData);

        // --- Scenery loop ---
        // We create scenery objects from the layout
        for (int y = 0; y < height; y++) {
            int layoutY = height - 1 - y;
            for (int x = 0; x < width; x++) {
                char c = layout[layoutY].charAt(x);
                switch (c) {
                    case 'T': {
                        Scenery s = new Scenery(Scenery.SceneryType.TREE, x, y);
                        String path = "images/tree_pine.png";
                        if (Gdx.files.internal(path).exists()) {
                            if (!assetManager.isLoaded(path)) {
                                Gdx.app.log("ForestChunkGenerator", "Loading texture on demand: " + path);
                                assetManager.load(path, Texture.class);
                                assetManager.finishLoading();
                            }
                            s.setTexture(assetManager.get(path, Texture.class));
                            Gdx.app.log("ForestChunkGenerator", "Assigned TREE texture: " + path);
                        } else {
                            Gdx.app.error("ForestChunkGenerator", "Texture NOT FOUND: " + path);
                        }
                        maze.addScenery(s);
                    }
                        break;
                    case 'R': {
                        Scenery s = new Scenery(Scenery.SceneryType.ROCK, x, y);
                        String path = "images/mossy_rock.png";
                        if (Gdx.files.internal(path).exists()) {
                            if (!assetManager.isLoaded(path)) {
                                Gdx.app.log("ForestChunkGenerator", "Loading texture on demand: " + path);
                                assetManager.load(path, Texture.class);
                                assetManager.finishLoading();
                            }
                            s.setTexture(assetManager.get(path, Texture.class));
                            Gdx.app.log("ForestChunkGenerator", "Assigned ROCK texture: " + path);
                        } else {
                            Gdx.app.error("ForestChunkGenerator", "Texture NOT FOUND: " + path);
                        }
                        maze.addScenery(s);
                    }
                        break;
                    case 'B': {
                        Scenery s = new Scenery(Scenery.SceneryType.BUSH, x, y);
                        String path = "images/bush.png";
                        if (Gdx.files.internal(path).exists()) {
                            if (!assetManager.isLoaded(path)) {
                                Gdx.app.log("ForestChunkGenerator", "Loading texture on demand: " + path);
                                assetManager.load(path, Texture.class);
                                assetManager.finishLoading();
                            }
                            s.setTexture(assetManager.get(path, Texture.class));
                            Gdx.app.log("ForestChunkGenerator", "Assigned BUSH texture: " + path);
                        } else {
                            Gdx.app.error("ForestChunkGenerator", "Texture NOT FOUND: " + path);
                        }
                        maze.addScenery(s);
                    }
                        break;
                    // Note: '#' is handled in the wall bitmasking loop below
                }
            }
        }

        // --- Bitmasking loop (same as MazeChunkGenerator) ---
        for (int y = 0; y < height; y++) {
            int layoutY = height - 1 - y;
            for (int x = 0; x < width; x++) {
                if (!isWall(layout[layoutY].charAt(x))) { // If NOT a wall
                    int mask = 0;
                    // Check neighbors. '#' and 'M' count as walls.
                    if (y + 1 < height) {
                        char n = layout[layoutY - 1].charAt(x);
                        if (isWall(n)) {
                            mask |= 0b01000000; // NORTH
                            if (n == 'M')
                                mask |= 2048; // MAZE_WALL_NORTH
                        }
                    }
                    if (x + 1 < width) {
                        char n = layout[layoutY].charAt(x + 1);
                        if (isWall(n)) {
                            mask |= 0b00000100; // EAST
                            if (n == 'M')
                                mask |= 512; // MAZE_WALL_EAST
                        }
                    }
                    if (y > 0) {
                        char n = layout[layoutY + 1].charAt(x);
                        if (isWall(n)) {
                            mask |= 0b00010000; // SOUTH
                            if (n == 'M')
                                mask |= 1024; // MAZE_WALL_SOUTH
                        }
                    }
                    if (x > 0) {
                        char n = layout[layoutY].charAt(x - 1);
                        if (isWall(n)) {
                            mask |= 0b00000001; // WEST
                            if (n == 'M')
                                mask |= 256; // MAZE_WALL_WEST
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
}
