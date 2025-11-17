package com.bpm.minotaur.generation;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.gamedata.*;
import com.bpm.minotaur.gamedata.item.ItemDataManager;
import com.bpm.minotaur.gamedata.monster.MonsterDataManager;
import com.bpm.minotaur.gamedata.spawntables.SpawnTableData; // <-- ADD THIS IMPORT
import com.bpm.minotaur.managers.SpawnManager;
import com.bpm.minotaur.rendering.RetroTheme;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

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

    String[] forestTile1 = new String[]{
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
    String[] forestTile2 = new String[]{
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

    String[] forestTile3 = new String[]{
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

    String[] forestTile4 = new String[]{
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
    String[] corridorUL = new String[]{ "############", "#...........", "#.xxxxxxxxxx", "#.xxxxxxxxxx", "#.xxxxxxxxxx", "#.xxxxxxxxxx", "#.xxxxxxxxxx", "#.xxxxxxxxxx", "#.xxxxxxxxxx", "#.xxxxxxxxxx", "#.xxxxxxxxxx", "#.xxxxxxxxxx" };
    String[] corridorT = new String[]{ "############", "............", "xxxxxxxxxxxx", "xxxxxxxxxxxx", "xxxxxxxxxxxx", "xxxxxxxxxxxx", "xxxxxxxxxxxx", "xxxxxxxxxxxx", "xxxxxxxxxxxx", "xxxxxxxxxxxx", "xxxxxxxxxxxx", "xxxxxxxxxxxx" };
    String[] corridorUR = new String[]{ "############", "...........#", "xxxxxxxxxx.#", "xxxxxxxxxx.#", "xxxxxxxxxx.#", "xxxxxxxxxx.#", "xxxxxxxxxx.#", "xxxxxxxxxx.#", "xxxxxxxxxx.#", "xxxxxxxxxx.#", "xxxxxxxxxx.#", "xxxxxxxxxx.#" };
    String[] corridorL = new String[]{ "#.xxxxxxxxxx", "#.xxxxxxxxxx", "#.xxxxxxxxxx", "#.xxxxxxxxxx", "#.xxxxxxxxxx", "#.xxxxxxxxxx", "#.xxxxxxxxxx", "#.xxxxxxxxxx", "#.xxxxxxxxxx", "#.xxxxxxxxxx", "#.xxxxxxxxxx", "#.xxxxxxxxxx" };
    String[] corridorR = new String[]{ "xxxxxxxxxx.#", "xxxxxxxxxx.#", "xxxxxxxxxx.#", "xxxxxxxxxx.#", "xxxxxxxxxx.#", "xxxxxxxxxx.#", "xxxxxxxxxx.#", "xxxxxxxxxx.#", "xxxxxxxxxx.#", "xxxxxxxxxx.#", "xxxxxxxxxx.#", "xxxxxxxxxx.#" };
    String[] corridorLL = new String[]{ "#.xxxxxxxxxx", "#.xxxxxxxxxx", "#.xxxxxxxxxx", "#.xxxxxxxxxx", "#.xxxxxxxxxx", "#.xxxxxxxxxx", "#.xxxxxxxxxx", "#.xxxxxxxxxx", "#.xxxxxxxxxx", "#.xxxxxxxxxx", "#...........", "############" };
    String[] corridorB = new String[]{ "xxxxxxxxxxxx", "xxxxxxxxxxxx", "xxxxxxxxxxxx", "xxxxxxxxxxxx", "xxxxxxxxxxxx", "xxxxxxxxxxxx", "xxxxxxxxxxxx", "xxxxxxxxxxxx", "xxxxxxxxxxxx", "xxxxxxxxxxxx", "............", "############" };
    String[] corridorLR = new String[]{ "xxxxxxxxxx.#", "xxxxxxxxxx.#", "xxxxxxxxxx.#", "xxxxxxxxxx.#", "xxxxxxxxxx.#", "xxxxxxxxxx.#", "xxxxxxxxxx.#", "xxxxxxxxxx.#", "xxxxxxxxxx.#", "xxxxxxxxxx.#", "...........#", "############" };

    // A simple 2x2 grid of tile info
    private static class TileInfo { int id; int rotation; TileInfo(int id, int rotation) { this.id = id; this.rotation = rotation; } }


    /**
     * [MODIFIED] Signature updated to match IChunkGenerator
     */
    @Override
    public Maze generateChunk(GridPoint2 chunkId, int level, Difficulty difficulty, GameMode gameMode, RetroTheme.Theme theme,
                              MonsterDataManager dataManager,
                              ItemDataManager itemDataManager,
                              AssetManager assetManager,
                              SpawnTableData spawnTableData) { // <-- ADDED THIS PARAM

        // Forest chunks are always 3x2, regardless of game mode
        int mapRows = 3;
        int mapCols = 2;
        createForestFromArrayTiles(mapRows, mapCols);

        // --- 2. Create Maze Object ---
        Maze maze = createMazeFromText(level, this.finalLayout, itemDataManager, assetManager);
        maze.setTheme(theme); // Set the theme (e.g., FOREST_THEME)

        // --- 3. Populate Maze ---
        spawnEntities(maze, difficulty, level, this.finalLayout, dataManager, itemDataManager, assetManager, spawnTableData); // <-- PASS PARAM

        spawnLadder(maze, this.finalLayout);

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

    /**
     * [MODIFIED] Signature updated
     */
    private void spawnEntities(Maze maze, Difficulty difficulty, int level, String[] layout,
                               MonsterDataManager dataManager, ItemDataManager itemDataManager, AssetManager assetManager,
                               SpawnTableData spawnTableData) { // <-- ADDED THIS PARAM

        // --- [MODIFIED] ---
        // This is the compile fix
        SpawnManager spawnManager = new SpawnManager(dataManager, itemDataManager, assetManager,
            maze, difficulty, level, layout, spawnTableData); // <-- PASSED PARAM
        // --- [END MODIFIED] ---

        spawnManager.spawnEntities();
    }


    private void spawnLadder(Maze maze, String[] layout) {
        int x, y;
        do {
            x = random.nextInt(maze.getWidth());
            y = random.nextInt(maze.getHeight());
            // Only spawn on floor, not bushes
        } while (layout[maze.getHeight() - 1 - y].charAt(x) != '.' ||
            maze.getItems().containsKey(new GridPoint2(x, y)) ||
            maze.getMonsters().containsKey(new GridPoint2(x, y)));
        maze.addLadder(new Ladder(x, y));
        Gdx.app.log("ForestChunkGenerator", "Ladder spawned at (" + x + ", " + y + ")");
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

        if (isTop && isLeft) return corridorUL;
        if (isTop && isRight) return corridorUR;
        if (isBottom && isLeft) return corridorLL;
        if (isBottom && isRight) return corridorLR;
        if (isTop) return corridorT;
        if (isBottom) return corridorB;
        if (isLeft) return corridorL;
        if (isRight) return corridorR;

        // Center tile (no walls)
        return new String[]{"xxxxxxxxxxxx","xxxxxxxxxxxx","xxxxxxxxxxxx","xxxxxxxxxxxx","xxxxxxxxxxxx","xxxxxxxxxxxx","xxxxxxxxxxxx","xxxxxxxxxxxx","xxxxxxxxxxxx","xxxxxxxxxxxx","xxxxxxxxxxxx","xxxxxxxxxxxx"};
    }


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
                    case 'T':
                        maze.addScenery(new Scenery(Scenery.SceneryType.TREE, x, y));
                        break;
                    case 'R':
                        maze.addScenery(new Scenery(Scenery.SceneryType.ROCK, x, y));
                        break;
                    case 'B':
                        maze.addScenery(new Scenery(Scenery.SceneryType.BUSH, x, y));
                        break;
                    // Note: '#' is handled in the wall bitmasking loop below
                }
            }
        }

        // --- Bitmasking loop (same as MazeChunkGenerator) ---
        for (int y = 0; y < height; y++) {
            int layoutY = height - 1 - y;
            for (int x = 0; x < width; x++) {
                if (layout[layoutY].charAt(x) != '#') { // If NOT a wall
                    int mask = 0;
                    // Check neighbors. Only '#' counts as a wall.
                    if (y + 1 < height && layout[layoutY - 1].charAt(x) == '#') mask |= 0b01000000;
                    if (x + 1 < width && layout[layoutY].charAt(x + 1) == '#') mask |= 0b00000100;
                    if (y > 0 && layout[layoutY + 1].charAt(x) == '#') mask |= 0b00010000;
                    if (x > 0 && layout[layoutY].charAt(x - 1) == '#') mask |= 0b00000001;
                    bitmaskedData[y][x] = mask;
                }
            }
        }
        return maze;
    }
}
