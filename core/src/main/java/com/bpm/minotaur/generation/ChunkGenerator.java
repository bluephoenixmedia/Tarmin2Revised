package com.bpm.minotaur.generation;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.gamedata.*;
import com.bpm.minotaur.managers.SpawnManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Generates and populates a single "chunk" (a Maze object) for the world.
 * This class encapsulates all logic moved from GameScreen, including
 * tile-stitching, entity spawning, and transition gate placement.
 */
public class ChunkGenerator {

    private final Random random = new Random();
    private String[] finalLayout;
    private GridPoint2 playerSpawnPoint = new GridPoint2(1, 1);

    // --- Maze Content Tile Definitions (Moved from GameScreen) ---
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

    // --- Corridor Tile Templates (Moved from GameScreen) ---
    String[] corridorUL = new String[]{ "############", "#...........", "#.xxxxxxxxxx", "#.xxxxxxxxxx", "#.xxxxxxxxxx", "#.xxxxxxxxxx", "#.xxxxxxxxxx", "#.xxxxxxxxxx", "#.xxxxxxxxxx", "#.xxxxxxxxxx", "#.xxxxxxxxxx", "#.xxxxxxxxxx" };
    String[] corridorT = new String[]{ "############", "............", "xxxxxxxxxxxx", "xxxxxxxxxxxx", "xxxxxxxxxxxx", "xxxxxxxxxxxx", "xxxxxxxxxxxx", "xxxxxxxxxxxx", "xxxxxxxxxxxx", "xxxxxxxxxxxx", "xxxxxxxxxxxx", "xxxxxxxxxxxx" };
    String[] corridorUR = new String[]{ "############", "...........#", "xxxxxxxxxx.#", "xxxxxxxxxx.#", "xxxxxxxxxx.#", "xxxxxxxxxx.#", "xxxxxxxxxx.#", "xxxxxxxxxx.#", "xxxxxxxxxx.#", "xxxxxxxxxx.#", "xxxxxxxxxx.#", "xxxxxxxxxx.#" };
    String[] corridorL = new String[]{ "#.xxxxxxxxxx", "#.xxxxxxxxxx", "#.xxxxxxxxxx", "#.xxxxxxxxxx", "#.xxxxxxxxxx", "#.xxxxxxxxxx", "#.xxxxxxxxxx", "#.xxxxxxxxxx", "#.xxxxxxxxxx", "#.xxxxxxxxxx", "#.xxxxxxxxxx", "#.xxxxxxxxxx" };
    String[] corridorR = new String[]{ "xxxxxxxxxx.#", "xxxxxxxxxx.#", "xxxxxxxxxx.#", "xxxxxxxxxx.#", "xxxxxxxxxx.#", "xxxxxxxxxx.#", "xxxxxxxxxx.#", "xxxxxxxxxx.#", "xxxxxxxxxx.#", "xxxxxxxxxx.#", "xxxxxxxxxx.#", "xxxxxxxxxx.#" };
    String[] corridorLL = new String[]{ "#.xxxxxxxxxx", "#.xxxxxxxxxx", "#.xxxxxxxxxx", "#.xxxxxxxxxx", "#.xxxxxxxxxx", "#.xxxxxxxxxx", "#.xxxxxxxxxx", "#.xxxxxxxxxx", "#.xxxxxxxxxx", "#.xxxxxxxxxx", "#...........", "############" };
    String[] corridorB = new String[]{ "xxxxxxxxxxxx", "xxxxxxxxxxxx", "xxxxxxxxxxxx", "xxxxxxxxxxxx", "xxxxxxxxxxxx", "xxxxxxxxxxxx", "xxxxxxxxxxxx", "xxxxxxxxxxxx", "xxxxxxxxxxxx", "xxxxxxxxxxxx", "............", "############" };
    String[] corridorLR = new String[]{ "xxxxxxxxxx.#", "xxxxxxxxxx.#", "xxxxxxxxxx.#", "xxxxxxxxxx.#", "xxxxxxxxxx.#", "xxxxxxxxxx.#", "xxxxxxxxxx.#", "xxxxxxxxxx.#", "xxxxxxxxxx.#", "xxxxxxxxxx.#", "...........#", "############" };

    private static class TileInfo { int id; int rotation; TileInfo(int id, int rotation) { this.id = id; this.rotation = rotation; } }

    /**
     * The primary public method to generate a new chunk.
     * @param chunkId The (X,Y) coordinates of the chunk in the world grid.
     * @param level The dungeon level.
     * @param difficulty The game difficulty.
     * @return A fully populated Maze object.
     */
    public Maze generateChunk(GridPoint2 chunkId, int level, Difficulty difficulty, GameMode gameMode) {
        // --- 1. Create Layout ---
        // For ADVANCED mode, we use a 3x2 grid (double the size)
        // For CLASSIC mode, we stick with the original 2x2
        int mapRows = (gameMode == GameMode.ADVANCED) ? 3 : 2;
        int mapCols = (gameMode == GameMode.ADVANCED) ? 2 : 2;
        createMazeFromArrayTiles(mapRows, mapCols);

        // --- 2. Create Maze Object ---
        Maze maze = createMazeFromText(level, this.finalLayout);

        // --- 3. Populate Maze ---
        spawnEntities(maze, difficulty, level, this.finalLayout);
        spawnLadder(maze, this.finalLayout);

        // --- 4. Place Gates ---
        if (gameMode == GameMode.CLASSIC) {
            spawnClassicGate(maze, this.finalLayout); // Original stat-jumbling gate
        } else {
            spawnTransitionGates(maze, this.finalLayout, chunkId); // New chunk-transition gates
        }

        // --- 5. Find Player Start ---
        findPlayerStart(this.finalLayout);

        return maze;
    }

    public GridPoint2 getInitialPlayerStartPos() {
        return playerSpawnPoint;
    }

    // --- All methods below were moved from GameScreen.java ---

    private void findPlayerStart(String[] layout) {
        for (int y = 0; y < layout.length; y++) {
            for (int x = 0; x < layout[0].length(); x++) {
                if (layout[layout.length - 1 - y].charAt(x) == 'P') {
                    playerSpawnPoint.set(x, y);
                    return;
                }
            }
        }
    }

    private void spawnEntities(Maze maze, Difficulty difficulty, int level, String[] layout) {
        SpawnManager spawnManager = new SpawnManager(maze, difficulty, level, layout);
        spawnManager.spawnEntities();
    }

    private void spawnLadder(Maze maze, String[] layout) {
        int x, y;
        do {
            x = random.nextInt(maze.getWidth());
            y = random.nextInt(maze.getHeight());
        } while (
            layout[maze.getHeight() - 1 - y].charAt(x) != '.' ||
                maze.getItems().containsKey(new GridPoint2(x, y)) ||
                maze.getMonsters().containsKey(new GridPoint2(x, y))
        );
        maze.addLadder(new Ladder(x, y));
        Gdx.app.log("ChunkGenerator", "Ladder spawned at (" + x + ", " + y + ")");
    }

    /**
     * Spawns the original stat-jumbling gate for CLASSIC mode.
     */
    private void spawnClassicGate(Maze maze, String[] layout) {
        if (layout == null || layout.length <= 2) return;

        List<GridPoint2> validGateLocations = new ArrayList<>();
        int height = layout.length;
        int width = layout[0].length();

        // Check top and bottom walls
        for (int x = 1; x < width - 1; x++) {
            if (layout[0].charAt(x) == '#') validGateLocations.add(new GridPoint2(x, height - 1));
            if (layout[height - 1].charAt(x) == '#') validGateLocations.add(new GridPoint2(x, 0));
        }
        // Check left and right walls
        for (int y = 1; y < height - 1; y++) {
            if (layout[y].charAt(0) == '#') validGateLocations.add(new GridPoint2(0, height - 1 - y));
            if (layout[y].charAt(width - 1) == '#') validGateLocations.add(new GridPoint2(width - 1, height - 1 - y));
        }

        if (validGateLocations.isEmpty()) return;
        GridPoint2 gatePos = validGateLocations.get(random.nextInt(validGateLocations.size()));

        // Add a non-transitioning Gate
        maze.addGate(new Gate(gatePos.x, gatePos.y));
        Gdx.app.log("ChunkGenerator", "Classic Gate spawned at (" + gatePos.x + ", " + gatePos.y + ")");
    }

    /**
     * Spawns four transition gates for ADVANCED mode.
     */
    private void spawnTransitionGates(Maze maze, String[] layout, GridPoint2 chunkId) {
        int height = layout.length;
        int width = layout[0].length();

        // Hardcoded positions for simplicity (e.g., middle of each wall)
        // These positions are (x, y) in maze coordinates (bottom-left 0,0)
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
        GridPoint2 northTargetPlayer = new GridPoint2(width / 2, 1);           // Arrive at bottom
        GridPoint2 southTargetPlayer = new GridPoint2(width / 2, height - 2); // Arrive at top
        GridPoint2 eastTargetPlayer = new GridPoint2(1, height / 2);          // Arrive at left
        GridPoint2 westTargetPlayer = new GridPoint2(width - 2, height / 2);  // Arrive at right

        // Add the four gates
        maze.addGate(new Gate(northPos.x, northPos.y, northTargetChunk, northTargetPlayer));
        Gdx.app.log("ChunkGenerator", "North Gate coordinates x = " + northPos.x + " and y = " + northPos.y);
        maze.addGate(new Gate(southPos.x, southPos.y, southTargetChunk, southTargetPlayer));
        Gdx.app.log("ChunkGenerator", "South Gate coordinates x = " + southPos.x + " and y = " + southPos.y);

        maze.addGate(new Gate(eastPos.x, eastPos.y, eastTargetChunk, eastTargetPlayer));
        Gdx.app.log("ChunkGenerator", "East Gate coordinates x = " + eastPos.x + " and y = " + eastPos.y);

        maze.addGate(new Gate(westPos.x, westPos.y, westTargetChunk, westTargetPlayer));
        Gdx.app.log("ChunkGenerator", "West Gate coordinates x = " + westPos.x + " and y = " + westPos.y);

        Gdx.app.log("ChunkGenerator", "Spawned 4 transition gates for chunk " + chunkId);
    }

    private void createMazeFromArrayTiles(int mapRows, int mapCols) {
        final int CONNECTOR_INDEX = 5;

        List<String[]> allTiles = List.of(tile1, tile2, tile3, tile4, tile5, tile6, tile7, tile8, tile9, tile10, tile11, tile12, tile13, tile14, tile15, tile16);
        TileInfo[][] mapLayout = new TileInfo[mapRows][mapCols];

        for (int mapY = 0; mapY < mapRows; mapY++) {
            for (int mapX = 0; mapX < mapCols; mapX++) {
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
                            String[] leftNeighborTile = rotateTile(allTiles.get(leftNeighborInfo.id), leftNeighborInfo.rotation);
                            if (isWall(leftNeighborTile[CONNECTOR_INDEX].charAt(tile1[0].length() - 1)) != isWall(candidateTile[CONNECTOR_INDEX].charAt(0))) {
                                fits = false;
                            }
                        }
                        if (fits && mapY > 0) {
                            TileInfo topNeighborInfo = mapLayout[mapY - 1][mapX];
                            String[] topNeighborTile = rotateTile(allTiles.get(topNeighborInfo.id), topNeighborInfo.rotation);
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
                    String[] contentTile = rotateTile(allTiles.get(info.id), info.rotation);
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

    private Maze createMazeFromText(int level, String[] layout) {
        int height = layout.length;
        int width = layout[0].length();
        int[][] bitmaskedData = new int[height][width];
        Maze maze = new Maze(level, bitmaskedData);

        for (int y = 0; y < height; y++) {
            int layoutY = height - 1 - y;
            for (int x = 0; x < width; x++) {
                char c = layout[layoutY].charAt(x);
                if (c == 'D') { maze.addGameObject(new Door(), x, y); }
                // Gates are now added by spawnGate() methods, so 'G' is ignored here.
                else if (c == 'S') { maze.addItem(new Item(Item.ItemType.LARGE_POTION, x, y, ItemColor.BLUE)); }
                else if (c == 'H') { maze.addItem(new Item(Item.ItemType.SMALL_POTION, x, y, ItemColor.RED)); }
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
}
