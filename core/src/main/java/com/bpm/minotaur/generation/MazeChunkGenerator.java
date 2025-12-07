package com.bpm.minotaur.generation;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.gamedata.*;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.ItemColor;
import com.bpm.minotaur.gamedata.item.ItemDataManager;
import com.bpm.minotaur.gamedata.monster.MonsterDataManager;
import com.bpm.minotaur.gamedata.spawntables.SpawnTableData;
import com.bpm.minotaur.managers.SpawnManager;
import com.bpm.minotaur.rendering.RetroTheme;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class MazeChunkGenerator implements IChunkGenerator {

    private final Random random = new Random();
    private String[] finalLayout;
    private GridPoint2 playerSpawnPoint = new GridPoint2(1, 1);

    // --- Forced Ladder Position ---
    private GridPoint2 forcedUpLadderPos = null;

    private List<GridPoint2> currentChunkHomeTiles = new ArrayList<>();

    // --- Maze Content Tile Definitions ---
    String[] tile1 = new String[]{ "#####.#.####", "#...#.#D##.#", "#.#D#....#.#", "#.D...P..D.#", "###..###.###", ".....#.D....", "###..#.#####", "#.#..#.#####", "#D#..#.D....", "#.#..###....", "#.....##....", "#####.######" };
    String[] tile2 = new String[]{ "#####.######", "#...D.D....#", "#####.######", "#...D.D....#", "#####.######", "....D.D.....", "#####.######", "#...D.D....#", "#####.######", "#...D.D....#", "#####.######", "#####.######" };
    String[] tile3 = new String[]{ "#####.######", "#..........#", "###D###D####", "............", "#.#D#..###.#", "..#.#..#.#..", "#.#.#..#.#.#", "#.#.#..#.D.#", "#.#.#..#.#.#", "#.#.#..#.#.#", "#.#.####.#.#", "#.#......#.#" };
    String[] tile4 = new String[]{ "#####..#####", "#...#..#...#", "#...#..#...#", "#...D..#...#", "#...#..#...#", ".####D###D#.", "....D...D...", "###D#D######", "#...#..D...#", "#...#..#...#", "#...#..#...#", "#####..#####" };
    String[] tile5 = new String[]{ "#####.###...", "#.##....#.##", "#D##D##D#.D.", "#.#...#.#.##", "#D#...#D#...", "........D...", "#D#...#D#...", "#.#...#.#...", "#D#...#D#.##", "#.##D##.#.D.", "####...##.##", "#####..##...." };
    String[] tile6 = new String[]{ "#####..#####", "#....#.#...#", "#....#.#...#", "#....#.#...#", "#....D.D...#", ".#####.####.", "............", "######.#####", "#....D.D...#", "#....#.#...#", "#....#.#...#", "#####..#####" };
    String[] tile7 = new String[]{ "#####.######", "#.#........#", "#.D.######.#", "###.#....#.#", "....#....#.#", ".#D##D####..", "#...#......#", "#...#......#", "#...#..###D#", "#...#..#...#", "#...#..#...#", "#####.######" };
    String[] tile8 = new String[]{ "###.#..#.###", "#.#D#..#D#.#", "#.#.#..#.#.#", "....#..#...#", "###D#..#D###", "............", "######D#####", "#.#....#...#", "#.D....D...#", "#.#....#...#", "#.#....#...#", "###....#####" };
    String[] tile9 = new String[]{ ".####.#.....", ".#..D.#..###", ".####.#..#.#", "......#..#.#", "##D####..#D#", "....D.D.....", "....####D###", "#D#..#......", "#.#..#......", "#.#..#.####.", "###..#.D..#.", ".....#.####." };
    String[] tile10 = new String[]{ "####........", "#..#.#D#####", "#..#.#..#..#", "#..#.#..#..#", "##D#######D#", "...D.D......", "##D#####D###", "#....#......", "#....#......", "#....#.####.", "#....#.D..#.", "######.####." };
    String[] tile11 = new String[]{ "#####.######", "#.#.D.D.#..#", "#.###.###..#", "#.#.D.D.#..#", "#D###.###D##", "..D.D.#.#..#", "#D###.#D#..#", "#.#.D.D.##D#", "#.###.###..#", "#.#.D.D.D..#", "#.#.#.#.#..#", "#####.######" };
    String[] tile12 = new String[]{ "############", "#...D.D....#", "#D###.####D#", "....#.#.....", "#D#.#.#..###", "#.#.#.#..#.#", "###.#.#..#D#", "....#.#.....", "#D###.####D#", "#...D.D....#", "#####.######", "............" };
    String[] tile13 = new String[]{ "....#.######", "....D.D....#", "#D###.#....#", "#.#...#....#", "###.########", "....D.D.....", "#######.....", "....#....###", "....#....#.#", "....#.####D#", "....D.D....#", "....#.######" };
    String[] tile14 = new String[]{ "############", "#...D.D..#.#", "#.#D#.##.D.#", "#D#.....##D#", "#...####...#", "....D..#....", "#...###....#", "#D#.....#D##", "#.D.....#..#", "#.###.#D#..#", "#...D.D....#", "############" };
    String[] tile15 = new String[]{ "#####.######", "#...D.D....#", "#...#.#....#", "#...#.#....#", "##D##.##D###", "....D.......", "#####D######", "#...#.#....#", "#...D.D....#", "#...#.#....#", "#...#.#....#", "#####.######" };
    String[] tile16 = new String[]{ "..##########", "..D........#", "..#####..###", "......D..#.#", "####D##..#.#", "......#..D.#", "......#..#.#", "..###.#..#.#", "..D.#.#..#.#", "..###.#..#.#", "......#..#.#", "......#..#.#" };

    // --- Home Tile ---
    String[] homeTile = new String[]{
        "............",
        "............",
        "............",
        "...##D###...",
        "...#....#...",
        "...#....#...",
        "...#....#...",
        "...######...",
        "............",
        "............",
        "............",
        "............",
    };
    private static final int HOME_TILE_ID = -1;

    // --- Corridor Tile Templates ---
    String[] corridorUL = new String[]{ "############", "#...........", "#.xxxxxxxxxx", "#.xxxxxxxxxx", "#.xxxxxxxxxx", "#.xxxxxxxxxx", "#.xxxxxxxxxx", "#.xxxxxxxxxx", "#.xxxxxxxxxx", "#.xxxxxxxxxx", "#.xxxxxxxxxx", "#.xxxxxxxxxx" };
    String[] corridorT = new String[]{ "############", "............", "xxxxxxxxxxxx", "xxxxxxxxxxxx", "xxxxxxxxxxxx", "xxxxxxxxxxxx", "xxxxxxxxxxxx", "xxxxxxxxxxxx", "xxxxxxxxxxxx", "xxxxxxxxxxxx", "xxxxxxxxxxxx", "xxxxxxxxxxxx" };
    String[] corridorUR = new String[]{ "############", "...........#", "xxxxxxxxxx.#", "xxxxxxxxxx.#", "xxxxxxxxxx.#", "xxxxxxxxxx.#", "xxxxxxxxxx.#", "xxxxxxxxxx.#", "xxxxxxxxxx.#", "xxxxxxxxxx.#", "xxxxxxxxxx.#", "xxxxxxxxxx.#" };
    String[] corridorL = new String[]{ "#.xxxxxxxxxx", "#.xxxxxxxxxx", "#.xxxxxxxxxx", "#.xxxxxxxxxx", "#.xxxxxxxxxx", "#.xxxxxxxxxx", "#.xxxxxxxxxx", "#.xxxxxxxxxx", "#.xxxxxxxxxx", "#.xxxxxxxxxx", "#.xxxxxxxxxx", "#.xxxxxxxxxx" };
    String[] corridorR = new String[]{ "xxxxxxxxxx.#", "xxxxxxxxxx.#", "xxxxxxxxxx.#", "xxxxxxxxxx.#", "xxxxxxxxxx.#", "xxxxxxxxxx.#", "xxxxxxxxxx.#", "xxxxxxxxxx.#", "xxxxxxxxxx.#", "xxxxxxxxxx.#", "xxxxxxxxxx.#", "xxxxxxxxxx.#" };
    String[] corridorLL = new String[]{ "#.xxxxxxxxxx", "#.xxxxxxxxxx", "#.xxxxxxxxxx", "#.xxxxxxxxxx", "#.xxxxxxxxxx", "#.xxxxxxxxxx", "#.xxxxxxxxxx", "#.xxxxxxxxxx", "#.xxxxxxxxxx", "#.xxxxxxxxxx", "#...........", "############" };
    String[] corridorB = new String[]{ "xxxxxxxxxxxx", "xxxxxxxxxxxx", "xxxxxxxxxxxx", "xxxxxxxxxxxx", "xxxxxxxxxxxx", "xxxxxxxxxxxx", "xxxxxxxxxxxx", "xxxxxxxxxxxx", "xxxxxxxxxxxx", "xxxxxxxxxxxx", "............", "############" };
    String[] corridorLR = new String[]{ "xxxxxxxxxx.#", "xxxxxxxxxx.#", "xxxxxxxxxx.#", "xxxxxxxxxx.#", "xxxxxxxxxx.#", "xxxxxxxxxx.#", "xxxxxxxxxx.#", "xxxxxxxxxx.#", "xxxxxxxxxx.#", "xxxxxxxxxx.#", "...........#", "############" };

    private static class TileInfo { int id; int rotation; TileInfo(int id, int rotation) { this.id = id; this.rotation = rotation; } }

    public void setForcedUpLadderPos(GridPoint2 pos) {
        this.forcedUpLadderPos = pos;
    }

    public Maze generateChunk(GridPoint2 chunkId, int level, Difficulty difficulty, GameMode gameMode, RetroTheme.Theme theme,
                              MonsterDataManager dataManager, ItemDataManager itemDataManager, AssetManager assetManager,
                              SpawnTableData spawnTableData) {

        int mapRows = (gameMode == GameMode.ADVANCED) ? 3 : 2;
        int mapCols = (gameMode == GameMode.ADVANCED) ? 3 : 2;

        // --- RE-GENERATION LOGIC START ---
        int attempts = 0;
        int maxAttempts = 50; // Try 50 times to find a valid natural layout

        do {
            createMazeFromArrayTiles(mapRows, mapCols, chunkId, level);
            attempts++;
        } while (forcedUpLadderPos != null && !isValidSpawnPosition(forcedUpLadderPos) && attempts < maxAttempts);

        // Fallback: If we still have an invalid position after max attempts, force clear the tile
        if (forcedUpLadderPos != null && !isValidSpawnPosition(forcedUpLadderPos)) {
            Gdx.app.log("MazeChunkGenerator", "Forcing clear tile at " + forcedUpLadderPos + " after " + attempts + " failed generation attempts.");
            forceClearTile(forcedUpLadderPos);
        }
        // --- RE-GENERATION LOGIC END ---

        Maze maze = createMazeFromText(level, this.finalLayout, itemDataManager, assetManager);
        maze.setTheme(theme);

        if (!currentChunkHomeTiles.isEmpty()) {
            maze.setHomeTiles(new ArrayList<>(currentChunkHomeTiles));
        }

        spawnEntities(maze, difficulty, level, this.finalLayout, dataManager, itemDataManager, assetManager, spawnTableData);
        spawnLadder(maze, this.finalLayout);

        if (gameMode == GameMode.CLASSIC) {
            spawnClassicGate(maze, this.finalLayout);
        } else {
            spawnTransitionGates(maze, this.finalLayout, chunkId);
        }

        findPlayerStart(this.finalLayout);

        // Hint player start if forced ladder exists
        if (forcedUpLadderPos != null) {
            playerSpawnPoint.set(forcedUpLadderPos.x, forcedUpLadderPos.y);
            forcedUpLadderPos = null;
        }

        return maze;
    }

    @Override
    public GridPoint2 getInitialPlayerStartPos() {
        return playerSpawnPoint;
    }

    private void findPlayerStart(String[] layout) {
        if (!currentChunkHomeTiles.isEmpty()) {
            int midIndex = currentChunkHomeTiles.size() / 2;
            GridPoint2 homeSpawn = currentChunkHomeTiles.get(midIndex);
            playerSpawnPoint.set(homeSpawn.x, homeSpawn.y);
            Gdx.app.log("ChunkGenerator", "findPlayerStart: Set spawn to Home Tile center: " + homeSpawn);
            return;
        }

        int height = layout.length;
        int width = layout[0].length();

        playerSpawnPoint.set(1, 1);

        char defaultTileChar = layout[height - 2].charAt(1);
        if (defaultTileChar != '#') {
            return;
        }

        Gdx.app.log("ChunkGenerator", "Default spawn blocked. Scanning for safe spot...");
        for (int y = 0; y < height; y++) {
            int layoutY = height - 1 - y;
            for (int x = 0; x < width; x++) {
                char c = layout[layoutY].charAt(x);
                if (c != '#') {
                    playerSpawnPoint.set(x, y);
                    Gdx.app.log("ChunkGenerator", "Found fallback safe spawn at (" + x + ", " + y + ")");
                    return;
                }
            }
        }
    }

    private void spawnEntities(Maze maze, Difficulty difficulty, int level, String[] layout,
                               MonsterDataManager dataManager, ItemDataManager itemDataManager, AssetManager assetManager,
                               SpawnTableData spawnTableData) {

        SpawnManager spawnManager = new SpawnManager(dataManager,
            itemDataManager,
            assetManager,
            maze, difficulty, level, layout,
            spawnTableData);

        spawnManager.spawnEntities();
    }

    private void spawnLadder(Maze maze, String[] layout) {
        // A. Spawn the standard DOWN ladder randomly
        int x, y;
        do {
            x = random.nextInt(maze.getWidth());
            y = random.nextInt(maze.getHeight());
        } while (
            layout[maze.getHeight() - 1 - y].charAt(x) != '.' ||
                maze.getItems().containsKey(new GridPoint2(x, y)) ||
                maze.getMonsters().containsKey(new GridPoint2(x, y)) ||
                (currentChunkHomeTiles.contains(new GridPoint2(x, y))) ||
                (forcedUpLadderPos != null && x == forcedUpLadderPos.x && y == forcedUpLadderPos.y)
        );
        maze.addLadder(new Ladder(x, y, Ladder.LadderType.DOWN));

        // B. Spawn the forced UP ladder if requested
        if (forcedUpLadderPos != null) {
            maze.addLadder(new Ladder(forcedUpLadderPos.x, forcedUpLadderPos.y, Ladder.LadderType.UP));
        }
    }

    private void spawnClassicGate(Maze maze, String[] layout) {
        if (layout == null || layout.length <= 2) return;

        List<GridPoint2> validGateLocations = new ArrayList<>();
        int height = layout.length;
        int width = layout[0].length();

        for (int x = 1; x < width - 1; x++) {
            if (layout[0].charAt(x) == '#') validGateLocations.add(new GridPoint2(x, height - 1));
            if (layout[height - 1].charAt(x) == '#') validGateLocations.add(new GridPoint2(x, 0));
        }
        for (int y = 1; y < height - 1; y++) {
            if (layout[y].charAt(0) == '#') validGateLocations.add(new GridPoint2(0, height - 1 - y));
            if (layout[y].charAt(width - 1) == '#') validGateLocations.add(new GridPoint2(width - 1, height - 1 - y));
        }

        if (validGateLocations.isEmpty()) return;
        GridPoint2 gatePos = validGateLocations.get(random.nextInt(validGateLocations.size()));

        maze.addGate(new Gate(gatePos.x, gatePos.y));
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

        maze.addGate(new Gate(northPos.x, northPos.y, northTargetChunk, northTargetPlayer));
        maze.addGate(new Gate(southPos.x, southPos.y, southTargetChunk, southTargetPlayer));
        maze.addGate(new Gate(eastPos.x, eastPos.y, eastTargetChunk, eastTargetPlayer));
        maze.addGate(new Gate(westPos.x, westPos.y, westTargetChunk, westTargetPlayer));
    }

    private String[] getTileContent(List<String[]> allTiles, int id, int rotation) {
        if (id == HOME_TILE_ID) return homeTile;
        return rotateTile(allTiles.get(id), rotation);
    }

    private void createMazeFromArrayTiles(int mapRows, int mapCols, GridPoint2 chunkId, int level) {
        final int CONNECTOR_INDEX = 5;
        this.currentChunkHomeTiles.clear();

        List<String[]> allTiles = List.of(tile1, tile2, tile3, tile4, tile5, tile6, tile7, tile8, tile9, tile10, tile11, tile12, tile13, tile14, tile15, tile16);
        TileInfo[][] mapLayout = new TileInfo[mapRows][mapCols];

        int homeX = -1;
        int homeY = -1;
        boolean isStartChunk = (chunkId.x == 0 && chunkId.y == 0 && level ==1);

        if (isStartChunk) {
            // Center the Home Tile if grid is 3x3
            if (mapRows >= 3 && mapCols >= 3) {
                homeX = 1;
                homeY = 1;
            } else {
                // Fallback for Classic 2x2: Bottom-Left
                homeX = 0;
                homeY = mapRows - 1;
            }
            Gdx.app.log("ChunkGenerator", "Injecting Home Tile at map coordinates [" + homeX + "," + homeY + "]");
        }

        for (int mapY = 0; mapY < mapRows; mapY++) {
            for (int mapX = 0; mapX < mapCols; mapX++) {

                if (isStartChunk && mapY == homeY && mapX == homeX) {
                    mapLayout[mapY][mapX] = new TileInfo(HOME_TILE_ID, 0);
                    continue;
                }

                List<Integer> tileIds = new ArrayList<>();
                for (int i = 0; i < allTiles.size(); i++) tileIds.add(i);
                Collections.shuffle(tileIds, random);
                List<Integer> rotations = new ArrayList<>(List.of(0, 1, 2, 3));
                Collections.shuffle(rotations, random);

                boolean tilePlaced = false;
                for (int tileId : tileIds) {
                    for (int rotation : rotations) {
                        String[] candidateTile = rotateTile(allTiles.get(tileId), rotation);
                        boolean fits = true;

                        if (mapX > 0) {
                            TileInfo leftNeighborInfo = mapLayout[mapY][mapX - 1];
                            String[] leftNeighborTile = getTileContent(allTiles, leftNeighborInfo.id, leftNeighborInfo.rotation);
                            if (isWall(leftNeighborTile[CONNECTOR_INDEX].charAt(tile1[0].length() - 1)) != isWall(candidateTile[CONNECTOR_INDEX].charAt(0))) {
                                fits = false;
                            }
                        }

                        if (fits && mapY > 0) {
                            TileInfo topNeighborInfo = mapLayout[mapY - 1][mapX];
                            String[] topNeighborTile = getTileContent(allTiles, topNeighborInfo.id, topNeighborInfo.rotation);
                            if (isWall(topNeighborTile[tile1.length - 1].charAt(CONNECTOR_INDEX)) != isWall(candidateTile[0].charAt(CONNECTOR_INDEX))) {
                                fits = false;
                            }
                        }

                        if (fits) {
                            mapLayout[mapY][mapX] = new TileInfo(tileId, rotation);
                            tilePlaced = true;
                            break;
                        }
                    }
                    if (tilePlaced) break;
                }
                if (!tilePlaced) {
                    mapLayout[mapY][mapX] = new TileInfo(0, 0);
                }
            }
        }

        int tileHeight = allTiles.get(0).length;
        int finalHeight = mapRows * tileHeight;
        this.finalLayout = new String[finalHeight];

        for (int mapY = 0; mapY < mapRows; mapY++) {
            for (int tileY = 0; tileY < tileHeight; tileY++) {
                StringBuilder rowBuilder = new StringBuilder();
                for (int mapX = 0; mapX < mapCols; mapX++) {
                    TileInfo info = mapLayout[mapY][mapX];
                    String[] contentTile = getTileContent(allTiles, info.id, info.rotation);

                    if (info.id == HOME_TILE_ID) {
                        for (int tx = 0; tx < 12; tx++) {
                            boolean isHomeWalkable = (tileY >= 4 && tileY <= 6 && tx >= 4 && tx <= 7);
                            if (isHomeWalkable) {
                                int gameX = mapX * 12 + tx;
                                int gameY = finalHeight - 1 - (mapY * tileHeight + tileY);
                                currentChunkHomeTiles.add(new GridPoint2(gameX, gameY));
                            }
                        }
                    }

                    String[] corridorTile = getCorridorTemplate(mapX, mapY, mapCols, mapRows);
                    String mergedRow = mergeTileRow(corridorTile[tileY], contentTile[tileY]);
                    rowBuilder.append(mergedRow);
                }
                this.finalLayout[mapY * tileHeight + tileY] = rowBuilder.toString();
            }
        }

        cleanUpOrphanedDoors();
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

        if (isTop && isLeft) return corridorUL;
        if (isTop && isRight) return corridorUR;
        if (isBottom && isLeft) return corridorLL;
        if (isBottom && isRight) return corridorLR;
        if (isTop) return corridorT;
        if (isBottom) return corridorB;
        if (isLeft) return corridorL;
        if (isRight) return corridorR;

        return new String[]{"xxxxxxxxxxxx","xxxxxxxxxxxx","xxxxxxxxxxxx","xxxxxxxxxxxx","xxxxxxxxxxxx","xxxxxxxxxxxx","xxxxxxxxxxxx","xxxxxxxxxxxx","xxxxxxxxxxxx","xxxxxxxxxxxx","xxxxxxxxxxxx","xxxxxxxxxxxx"};
    }

    private boolean isWall(char c) { return c == '#'; }

    private String[] rotateTile(String[] tile, int rotation) {
        if (rotation == 0) return tile;
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
            for(int x = 0; x < width; x++){
                rotated[x] = new String(temp[x]);
            }
            currentTile = rotated;
        }
        return currentTile;
    }

    private Maze createMazeFromText(int level, String[] layout, ItemDataManager itemDataManager, AssetManager assetManager) {
        Gdx.app.log("MazeChunkGenerator [DEBUG]", "createMazeFromText STARTING.");

        int height = layout.length;
        int width = layout[0].length();
        int[][] bitmaskedData = new int[height][width];
        Maze maze = new Maze(level, bitmaskedData);

        for (int y = 0; y < height; y++) {
            int layoutY = height - 1 - y;
            for (int x = 0; x < width; x++) {
                char c = layout[layoutY].charAt(x);
                if (c == 'D') {
                    maze.addGameObject(new Door(), x, y);
                }
                else if (c == 'S') {
                    maze.addItem(itemDataManager.createItem(Item.ItemType.POTION_BLUE, x, y, ItemColor.BLUE, assetManager));
                } else if (c == 'H') {
                    maze.addItem(itemDataManager.createItem(Item.ItemType.POTION_PINK, x, y, ItemColor.RED, assetManager));
                }
            }
        }

        for (int y = 0; y < height; y++) {
            int layoutY = height - 1 - y;
            for (int x = 0; x < width; x++) {
                if (layout[layoutY].charAt(x) != '#') {
                    int mask = 0;
                    if (y + 1 < height && layout[layoutY - 1].charAt(x) == '#') mask |= 0b01000000;
                    else if (y + 1 < height && layout[layoutY - 1].charAt(x) == 'D') mask |= 0b10000000;
                    if (x + 1 < width && layout[layoutY].charAt(x + 1) == '#') mask |= 0b00000100;
                    else if (x + 1 < width && layout[layoutY].charAt(x + 1) == 'D') mask |= 0b00001000;
                    if (y > 0 && layout[layoutY + 1].charAt(x) == '#') mask |= 0b00010000;
                    else if (y > 0 && layout[layoutY + 1].charAt(x) == 'D') mask |= 0b00100000;
                    if (x > 0 && layout[layoutY].charAt(x - 1) == '#') mask |= 0b00000001;
                    else if (x > 0 && layout[layoutY].charAt(x - 1) == 'D') mask |= 0b00000010;
                    bitmaskedData[y][x] = mask;
                }
            }
        }
        Gdx.app.log("MazeChunkGenerator [DEBUG]", "createMazeFromText FINISHED.");
        return maze;
    }

    private void cleanUpOrphanedDoors() {
        if (finalLayout == null) return;

        int height = finalLayout.length;
        if (height == 0) return;
        int width = finalLayout[0].length();

        char[][] layoutChars = new char[height][width];
        for (int y = 0; y < height; y++) {
            layoutChars[y] = finalLayout[y].toCharArray();
        }

        char[][] originalChars = new char[height][width];
        for (int y = 0; y < height; y++) {
            originalChars[y] = finalLayout[y].toCharArray();
        }

        for (int y = 1; y < height - 1; y++) {
            for (int x = 1; x < width - 1; x++) {
                if (originalChars[y][x] == 'D') {
                    boolean hasWallTop = (originalChars[y - 1][x] == '#');
                    boolean hasWallBottom = (originalChars[y + 1][x] == '#');
                    boolean hasWallLeft = (originalChars[y][x - 1] == '#');
                    boolean hasWallRight = (originalChars[y][x + 1] == '#');
                    boolean isVerticallyEnclosed = hasWallTop && hasWallBottom;
                    boolean isHorizontallyEnclosed = hasWallLeft && hasWallRight;

                    if (!isVerticallyEnclosed && !isHorizontallyEnclosed) {
                        Gdx.app.log("ChunkGenerator", "Removing orphaned door at (" + x + ", " + (height - 1 - y) + ")");
                        layoutChars[y][x] = '.';
                    }
                }
            }
        }

        for (int y = 0; y < height; y++) {
            finalLayout[y] = new String(layoutChars[y]);
        }
    }

    // --- Validation Helper Methods ---

    /**
     * Checks if the character at the given position is a safe spawn (floor).
     * Returns false if it is a Wall ('#') or Door ('D').
     */
    private boolean isValidSpawnPosition(GridPoint2 pos) {
        if (finalLayout == null || finalLayout.length == 0) return false;
        int height = finalLayout.length;
        int width = finalLayout[0].length();

        if (pos.x < 0 || pos.x >= width || pos.y < 0 || pos.y >= height) return false;

        // Map Game Y to Layout Y
        int layoutY = height - 1 - pos.y;
        char c = finalLayout[layoutY].charAt(pos.x);

        return c != '#' && c != 'D';
    }

    /**
     * Forces the tile at the given position to be a floor ('.').
     */
    private void forceClearTile(GridPoint2 pos) {
        if (finalLayout == null) return;
        int height = finalLayout.length;
        int layoutY = height - 1 - pos.y;

        if (layoutY >= 0 && layoutY < height) {
            StringBuilder sb = new StringBuilder(finalLayout[layoutY]);
            if (pos.x >= 0 && pos.x < sb.length()) {
                sb.setCharAt(pos.x, '.');
                finalLayout[layoutY] = sb.toString();
            }
        }
    }
}
