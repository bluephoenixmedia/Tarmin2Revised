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
        ".T....B..T.T",
        "..B...R...BT",
        "T...T....T..",
        ".TTTT..BTT.T",
        ".R...T.TT..T",
        ".T...B...T.T",
        "T...T.TTT...",
        "TB.TTTTT.R..",
        "TTTT.TTTTTTT"
    };
    String[] forestTile2 = new String[]{
        ".TTTT.TTTTTT",
        ".B...T...T..",
        ".TTTT..RT..T",
        ".T.R....TB.T",
        "..TT..T.T..T",
        ".T......TT..",
        "....B..RT..T",
        ".R...T..T..T",
        ".T.......T..",
        "...TTTT..B..",
        "TB...R...T.T",
        "...T.TTT...T"
    };
    String[] forestTile3 = new String[]{
        "TT.TT..R.TT.",
        "....T....B.T",
        "TB...T.....T",
        "..TTT..T.R.T",
        "TR.T.......T",
        "...TTTTT...T",
        ".T...B...T.T",
        "T.TTT....R.T",
        "....T......T",
        ".B.......T.T",
        ".....R...TTT",
        "TT.TTTTTT..B."
    };
    String[] forestTile4 = new String[]{
        "..TTTTTTT...",
        "T...T..T....",
        "TR.......B.T",
        "T...B.T.....",
        "TT..TTT..R..",
        "T.....TT....",
        "B..R...TTT..",
        "T....T...T..",
        "TT.B...TTT..",
        "T..TTT...R..",
        "TR....T...B.",
        "T..........."
    };
    // Add 12 more unique forest tiles
    String[] forestTile5 = new String[]{
        "T...B.TTTR.T",
        "....T......T",
        ".R.....T..TT",
        "T.....B..T..",
        "TT..R......T",
        "T....T...B..",
        "T.B.....TTTT",
        "T.TTTT..R..T",
        "....B...T.TT",
        ".R...T....TT",
        "T.......B..T",
        "T...TTTTTTTT"
    };
    String[] forestTile6 = new String[]{
        "TT..R....T..",
        ".T......B...",
        "...TTT...T..",
        "B...T..R....",
        "...TTT......",
        ".R...T...B..",
        "T..TTT......",
        "...B...T.R..",
        "TT..T..TTTTT",
        ".T..R.......",
        "B........TTT",
        "TT..R..TTTTT"
    };
    String[] forestTile7 = new String[]{
        "............",
        ".T...R...B..",
        "TTTTTTTTTT..",
        "T...T....T..",
        "B..TTTTTRT..",
        ".R...TTTTTT.",
        "TTT.B....T..",
        "TTT...TTTT..",
        ".B...R...T..",
        "TTT...T.....",
        ".T..TTTTTB..",
        "....R...TTTT"
    };
    String[] forestTile8 = new String[]{
        "TR......T..T",
        "T...T....B.T",
        "TT..TTTTR..T",
        "TTT......T.T",
        "B...RTTT....",
        "TTTT......TT",
        "TT...BTTTTTT",
        "TTT..R.....T",
        "TTT......B.T",
        "........R..T",
        "TB...TTT...T",
        "TTT.TTT..TTT"
    };
    String[] forestTile9 = new String[]{
        ".......TTTTT",
        "T...T...R...",
        ".B......TTTT",
        "....T...B...",
        ".R....T....T",
        "T.T..TTT..TT",
        "T.R....T..TT",
        "T..B........",
        ".T..T.R.T.T.",
        "T...T..B....",
        "B.T.T..T.T.T",
        "T.T.R.T.TT.T"
    };
    String[] forestTile10 = new String[]{
        ".T...R.....T",
        ".......T...B",
        "B...T.....TT",
        "...R...T....",
        "T....TTTTT..",
        "....B..T..TT",
        ".R..........",
        "T...T....R..",
        "B...........",
        "....T...B..T",
        ".R....T.....",
        "TTTTT...T..T",
    };
    String[] forestTile11 = new String[]{
        "R........T..",
        ".T...B......",
        "....T....R..",
        "B......T....",
        ".T..R.......",
        "............",
        "T...B..T....",
        ".........R..",
        "...T........",
        ".B.....T....",
        "R....T......",
        "....T....B.."
    };
    String[] forestTile12 = new String[]{
        "............",
        ".B...T...R..",
        ".......T....",
        "T...R....B..",
        "....T.......",
        "R...........",
        "...B.T......",
        "T.......R...",
        ".B...T......",
        ".....T...B..",
        ".R.......T..",
        "............"
    };
    String[] forestTile13 = new String[]{
        "T....R...B..",
        ".B...T......",
        "............",
        "R...T....T..",
        ".........B..",
        "....T..R....",
        "T...........",
        ".R...B...T..",
        ".......R....",
        "B...T.......",
        "....T....B..",
        "R..........."
    };
    String[] forestTile14 = new String[]{
        "....T....R..",
        "B.......T...",
        ".T..R.......",
        "............",
        "R...T...B...",
        "....T.......",
        ".B......T.R.",
        "T...........",
        "....R...T...",
        ".T......B...",
        "B....T......",
        "....R....T.."
    };
    String[] forestTile15 = new String[]{
        "............",
        ".T...B...R..",
        "R....T......",
        "....T....B..",
        ".B...R...T..",
        "T...........",
        "....T.R.....",
        "B........T..",
        ".T...R......",
        ".....B...T..",
        "R...........",
        ".T...B......"
    };
    String[] forestTile16 = new String[]{
        "R....T...B..",
        ".T........R.",
        "....B..T....",
        "B....T......",
        ".R.......T..",
        "T...........",
        ".B...R...T..",
        "....T.......",
        "R........B..",
        ".T...B......",
        ".....T...R..",
        "............"
    };
    private static class TileInfo {
        int id;
        int rotation;
        TileInfo(int id, int rotation) {
            this.id = id;
            this.rotation = rotation;
        }
    }

    @Override
    public Maze generateChunk(GridPoint2 chunkId, int level, Difficulty difficulty, GameMode gameMode) {
        Gdx.app.log("ForestChunkGenerator", "Generating new FOREST chunk at " + chunkId);

        // --- 1. Create Layout (3x2 grid) ---
        createMazeFromArrayTiles(3, 2);

        // --- 2. Create Maze Object (populates walls and scenery) ---
        Maze maze = createMazeFromText(level, this.finalLayout);

        // --- 3. Populate Maze ---
        // Force Tier 2+ spawning by ensuring level is at least 4
        int effectiveSpawnLevel = Math.max(level, 4);
        Gdx.app.log("ForestChunkGenerator", "Real level: " + level + ", Effective Spawn Level: " + effectiveSpawnLevel);

        SpawnManager spawnManager = new SpawnManager(maze, difficulty, effectiveSpawnLevel, this.finalLayout);
        spawnManager.spawnEntities(); // This will use the effectiveSpawnLevel for tiering

        // --- 4. Place Gates ---
        // (Spawns gates only in ADVANCED mode, which is implied by this biome)
       // spawnTransitionGates(maze, this.finalLayout, chunkId);

        return maze;
    }

    @Override
    public GridPoint2 getInitialPlayerStartPos() {
        // Not used for starting chunk, return a safe default
        return new GridPoint2(Maze.MAZE_WIDTH / 2, Maze.MAZE_HEIGHT / 2);
    }

    // --- Generation logic (copied from MazeChunkGenerator) ---

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

        Gdx.app.log("ForestChunkGenerator", "Spawned 4 transition gates for chunk " + chunkId);
    }

    private void createMazeFromArrayTiles(int mapRows, int mapCols) {
        final int CONNECTOR_INDEX = 5;

        List<String[]> allTiles = List.of(
            forestTile1, forestTile2, forestTile3, forestTile4, forestTile5, forestTile6,
            forestTile7, forestTile8, forestTile9, forestTile10, forestTile11, forestTile12,
            forestTile13, forestTile14, forestTile15, forestTile16
        );
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

                            mapLayout[mapY][mapX] = new TileInfo(tileId, rotation);
                            tilePlaced = true;
                            break;

                    }
                    if (tilePlaced) break;
                }
                if (!tilePlaced) {
                    mapLayout[mapY][mapX] = new TileInfo(0, 0); // Fallback
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
                    // NO CORRIDORS OR MERGING FOR FOREST
                    rowBuilder.append(contentTile[tileY]);
                }
                this.finalLayout[mapY * tileHeight + tileY] = rowBuilder.toString();
            }
        }
    }

    private boolean isWall(char c) {
        return c == '#';
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
            for (int x = 0; x < width; x++) {
                rotated[x] = new String(temp[x]);
            }
            currentTile = rotated;
        }
        return currentTile;
    }

    /**
     * Creates the Maze object from the text layout.
     * This version populates wallData AND scenery.
     */
    private Maze createMazeFromText(int level, String[] layout) {
        int height = layout.length;
        int width = layout[0].length();
        int[][] bitmaskedData = new int[height][width];
        Maze maze = new Maze(level, bitmaskedData);

        for (int y = 0; y < height; y++) {
            int layoutY = height - 1 - y; // Read layout from top-down
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
                } else {
                    // This is a full wall block
                    bitmaskedData[y][x] = -1; // Or some other non-zero value
                }
            }
        }
        return maze;
    }
}
