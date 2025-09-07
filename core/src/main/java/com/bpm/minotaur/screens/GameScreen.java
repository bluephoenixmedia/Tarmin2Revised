package com.bpm.minotaur.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.ScreenUtils;
import com.bpm.minotaur.Tarmin2;
import com.bpm.minotaur.gamedata.Door;
import com.bpm.minotaur.gamedata.Item;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.Player;
import com.bpm.minotaur.managers.DebugManager;
import com.bpm.minotaur.rendering.DebugRenderer;
import com.bpm.minotaur.rendering.FirstPersonRenderer;
import com.bpm.minotaur.rendering.ItemRenderer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * GameScreen is the main screen for gameplay. It handles the rendering of the maze,
 * player, and items, as well as processing player input and managing game state updates.
 */
public class GameScreen extends BaseScreen implements InputProcessor {

    // --- Core Dependencies ---
    private final DebugManager debugManager = DebugManager.getInstance();
    private final ShapeRenderer shapeRenderer = new ShapeRenderer();
    private final BitmapFont font = new BitmapFont();

    // --- Renderers ---
    private final DebugRenderer debugRenderer = new DebugRenderer();
    private final FirstPersonRenderer firstPersonRenderer = new FirstPersonRenderer();
    private final ItemRenderer itemRenderer = new ItemRenderer();

    // --- Game State ---
    private Player player;
    private Maze maze;
    private final Random random = new Random();

    // --- Maze Content Tile Definitions ---
    String[] tile1 = new String[]{
        //123456789101112
        "#####.#.####",//1
        "#...#.#D##.#",//2
        "#.#D#....#.#",//3
        "#.D...P..D.#",//4
        "###..###.###",//5
        ".....#.D....",//6
        "###..#.#####",//7
        "#.#..#.#####",//8
        "#D#..#.D....",//9
        "#.#..###....",//10
        "#.....##....",//11
        "#####.######"//12
    };

    String[] tile2 = new String[]{
        //123456789101112
        "#####.######",//1
        "#...D.D....#",//2
        "#####.######",//3
        "#...D.D....#",//4
        "#####.######",//5
        "....D.D.....",//6
        "#####.######",//7
        "#...D.D....#",//8
        "#####.######",//9
        "#...D.D....#",//10
        "#####.######",//11
        "#####.######"//12
    };

    String[] tile3 = new String[]{
        //123456789101112
        "#####.######",//1
        "#..........#",//2
        "###D###D####",//3
        "............",//4
        "#.#D#..###.#",//5
        "..#.#..#.#..",//6
        "#.#.#..#.#.#",//7
        "#.#.#..#.D.#",//8
        "#.#.#..#.#.#",//9
        "#.#.#..#.#.#",//10
        "#.#.####.#.#",//11
        "#.#......#.#"//12
    };

    String[] tile4 = new String[]{
        //123456789101112
        "#####..#####",//1
        "#...#..#...#",//2
        "#...#..#...#",//3
        "#...D..#...#",//4
        "#...#..#...#",//5
        ".####D###D#.",//6
        "....D...D...",//7
        "###D#D######",//8
        "#...#..D...#",//9
        "#...#..#...#",//10
        "#...#..#...#",//11
        "#####..#####"//12
    };

    String[] tile5 = new String[]{
        //123456789101112
        "#####.###...",//1
        "#.##....#.##",//2
        "#D##D##D#.D.",//3
        "#.#...#.#.##",//4
        "#D#...#D#...",//5
        "........D...",//6
        "#D#...#D#...",//7
        "#.#...#.#...",//8
        "#D#...#D#.##",//9
        "#.##D##.#.D.",//10
        "####...##.##",//11
        "#####..##...."//12
    };

    String[] tile6 = new String[]{
        //123456789101112
        "#####..#####",//1
        "#....#.#...#",//2
        "#....#.#...#",//3
        "#....#.#...#",//4
        "#....D.D...#",//5
        ".#####.####.",//6
        "............",//7
        "######.#####",//8
        "#....D.D...#",//9
        "#....#.#...#",//10
        "#....#.#...#",//11
        "#####..#####"//12
    };

    String[] tile7 = new String[]{
        //123456789101112
        "#####.######",//1
        "#.#........#",//2
        "#.D.######.#",//3
        "###.#....#.#",//4
        "....#....#.#",//5
        ".#D##D####..",//6
        "#...#......#",//7
        "#...#......#",//8
        "#...#..###D#",//9
        "#...#..#...#",//10
        "#...#..#...#",//11
        "#####.######"//12
    };

    String[] tile8 = new String[]{
        //123456789101112
        "###.#..#.###",//1
        "#.#D#..#D#.#",//2
        "#.#.#..#.#.#",//3
        "....#..#...#",//4
        "###D#..#D###",//5
        "............",//6
        "######D#####",//7
        "#.#....#...#",//8
        "#.D....D...#",//9
        "#.#....#...#",//10
        "#.#....#...#",//11
        "###....#####"//12
    };

    String[] tile9 = new String[]{
        //123456789101112
        ".####.#....",//1
        ".#..D.#..###",//2
        ".####.#..#.#",//3
        "......#..#.#",//4
        "##D####..#D#",//5
        "....D.D.....",//6
        "....####D###",//7
        "#D#..#......",//8
        "#.#..#......",//9
        "#.#..#.####.",//10
        "###..#.D..#.",//11
        ".....#.####."//12
    };

    String[] tile10 = new String[]{
        //123456789101112
        "####........",//1
        "#..#.#D#####",//2
        "#..#.#..#..#",//3
        "#..#.#..#..#",//4
        "##D#######D#",//5
        "...D.D......",//6
        "##D#####D###",//7
        "#....#......",//8
        "#....#......",//9
        "#....#.####.",//10
        "#....#.D..#.",//11
        "######.####."//12
    };

    String[] tile11 = new String[]{
        //123456789101112
        "#####.######",//1
        "#.#.D.D.#..#",//2
        "#.###.###..#",//3
        "#.#.D.D.#..#",//4
        "#D###.###D##",//5
        "..D.D.#.#..#",//6
        "#D###.#D#..#",//7
        "#.#.D.D.##D#",//8
        "#.###.###..#",//9
        "#.#.D.D.D..#",//10
        "#.#.#.#.#..#",//11
        "#####.######"//12
    };

    String[] tile12 = new String[]{
        //123456789101112
        "############",//1
        "#...D.D....#",//2
        "#D###.####D#",//3
        "....#.#.....",//4
        "#D#.#.#..###",//5
        "#.#.#.#..#.#",//6
        "###.#.#..#D#",//7
        "....#.#.....",//8
        "#D###.####D#",//9
        "#...D.D....#",//10
        "#####.######",//11
        "............"//12
    };

    String[] tile13 = new String[]{
        //123456789101112
        "....#.######",//1
        "....D.D....#",//2
        "#D###.#....#",//3
        "#.#...#....#",//4
        "###.########",//5
        "....D.D.....",//6
        "#######.....",//7
        "....#....###",//8
        "....#....#.#",//9
        "....#.####D#",//10
        "....D.D....#",//11
        "....#.######"//12
    };
    String[] tile14 = new String[]{
        //123456789101112
        "############",//1
        "#...D.D..#.#",//2
        "#.#D#.##.D.#",//3
        "#D#.....##D#",//4
        "#...####...#",//5
        "....D..#....",//6
        "#...###....#",//7
        "#D#.....#D##",//8
        "#.D.....#..#",//9
        "#.###.#D#..#",//10
        "#...D.D....#",//11
        "############"//12
    };

    String[] tile15 = new String[]{
        //123456789101112
        "#####.######",//1
        "#...D.D....#",//2
        "#...#.#....#",//3
        "#...#.#....#",//4
        "##D##.##D###",//5
        "....D.......",//6
        "#####D######",//7
        "#...#.#....#",//8
        "#...D.D....#",//9
        "#...#.#....#",//10
        "#...#.#....#",//11
        "#####.######"//12
    };

    String[] tile16 = new String[]{
        //123456789101112
        "..##########",//1
        "..D........#",//2
        "..#####..###",//3
        "......D..#.#",//4
        "####D##..#.#",//5
        "......#..D.#",//6
        "......#..#.#",//7
        "..###.#..#.#",//8
        "..D.#.#..#.#",//9
        "..###.#..#.#",//10
        "......#..#.#",//11
        "......#..#.#"//12
    };

    // --- Corridor Tile Templates ---
    /** Template for the Upper-Left corner of the maze. */
    String[] corridorUL = new String[]{ "############", "#...........", "#.xxxxxxxxxx", "#.xxxxxxxxxx", "#.xxxxxxxxxx", "#.xxxxxxxxxx", "#.xxxxxxxxxx", "#.xxxxxxxxxx", "#.xxxxxxxxxx", "#.xxxxxxxxxx", "#.xxxxxxxxxx", "#.xxxxxxxxxx" };
    /** Template for the Top edge of the maze. */
    String[] corridorT = new String[]{ "############", "............", "xxxxxxxxxxxx", "xxxxxxxxxxxx", "xxxxxxxxxxxx", "xxxxxxxxxxxx", "xxxxxxxxxxxx", "xxxxxxxxxxxx", "xxxxxxxxxxxx", "xxxxxxxxxxxx", "xxxxxxxxxxxx", "xxxxxxxxxxxx" };
    /** Template for the Upper-Right corner of the maze. */
    String[] corridorUR = new String[]{ "############", "...........#", "xxxxxxxxxx.#", "xxxxxxxxxx.#", "xxxxxxxxxx.#", "xxxxxxxxxx.#", "xxxxxxxxxx.#", "xxxxxxxxxx.#", "xxxxxxxxxx.#", "xxxxxxxxxx.#", "xxxxxxxxxx.#", "xxxxxxxxxx.#" };
    /** Template for the Left edge of the maze. */
    String[] corridorL = new String[]{ "#.xxxxxxxxxx", "#.xxxxxxxxxx", "#.xxxxxxxxxx", "#.xxxxxxxxxx", "#.xxxxxxxxxx", "#.xxxxxxxxxx", "#.xxxxxxxxxx", "#.xxxxxxxxxx", "#.xxxxxxxxxx", "#.xxxxxxxxxx", "#.xxxxxxxxxx", "#.xxxxxxxxxx" };
    /** Template for the Right edge of the maze. */
    String[] corridorR = new String[]{ "xxxxxxxxxx.#", "xxxxxxxxxx.#", "xxxxxxxxxx.#", "xxxxxxxxxx.#", "xxxxxxxxxx.#", "xxxxxxxxxx.#", "xxxxxxxxxx.#", "xxxxxxxxxx.#", "xxxxxxxxxx.#", "xxxxxxxxxx.#", "xxxxxxxxxx.#", "xxxxxxxxxx.#" };
    /** Template for the Lower-Left corner of the maze. */
    String[] corridorLL = new String[]{ "#.xxxxxxxxxx", "#.xxxxxxxxxx", "#.xxxxxxxxxx", "#.xxxxxxxxxx", "#.xxxxxxxxxx", "#.xxxxxxxxxx", "#.xxxxxxxxxx", "#.xxxxxxxxxx", "#.xxxxxxxxxx", "#.xxxxxxxxxx", "#...........", "############" };
    /** Template for the Bottom edge of the maze. */
    String[] corridorB = new String[]{ "xxxxxxxxxxxx", "xxxxxxxxxxxx", "xxxxxxxxxxxx", "xxxxxxxxxxxx", "xxxxxxxxxxxx", "xxxxxxxxxxxx", "xxxxxxxxxxxx", "xxxxxxxxxxxx", "xxxxxxxxxxxx", "xxxxxxxxxxxx", "............", "############" };
    /** Template for the Lower-Right corner of the maze. */
    String[] corridorLR = new String[]{ "xxxxxxxxxx.#", "xxxxxxxxxx.#", "xxxxxxxxxx.#", "xxxxxxxxxx.#", "xxxxxxxxxx.#", "xxxxxxxxxx.#", "xxxxxxxxxx.#", "xxxxxxxxxx.#", "xxxxxxxxxx.#", "xxxxxxxxxx.#", "...........#", "############" };


    private static class TileInfo { int id; int rotation; TileInfo(int id, int rotation) { this.id = id; this.rotation = rotation; } }

    public GameScreen(Tarmin2 game) { super(game); }

    @Override
    public void show() {
        Gdx.input.setInputProcessor(this);
        createMazeFromArrayTiles();
        DebugRenderer.printMazeToConsole(maze);
    }

    /**
     * Constructs a large maze by first procedurally generating a layout of content tiles,
     * and then merging them into appropriate corridor templates to form a seamless outer boundary.
     */
    private void createMazeFromArrayTiles() {
        final int MAP_ROWS = 2;
        final int MAP_COLS = 2;
        final int CONNECTOR_INDEX = 5;

        List<String[]> allTiles = List.of(tile1, tile2, tile3, tile4, tile5, tile6, tile7, tile8, tile9, tile10, tile11, tile12, tile13, tile14, tile15, tile16);
        TileInfo[][] mapLayout = new TileInfo[MAP_ROWS][MAP_COLS];

        for (int mapY = 0; mapY < MAP_ROWS; mapY++) {
            for (int mapX = 0; mapX < MAP_COLS; mapX++) {
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
        int finalHeight = MAP_ROWS * tileHeight;
        String[] finalLayout = new String[finalHeight];

        for (int mapY = 0; mapY < MAP_ROWS; mapY++) {
            for (int tileY = 0; tileY < tileHeight; tileY++) {
                StringBuilder rowBuilder = new StringBuilder();
                for (int mapX = 0; mapX < MAP_COLS; mapX++) {
                    TileInfo info = mapLayout[mapY][mapX];
                    String[] contentTile = rotateTile(allTiles.get(info.id), info.rotation);
                    String[] corridorTile = getCorridorTemplate(mapX, mapY, MAP_COLS, MAP_ROWS);

                    // Merge the content tile into the corridor template.
                    String mergedRow = mergeTileRow(corridorTile[tileY], contentTile[tileY]);
                    rowBuilder.append(mergedRow);
                }
                finalLayout[mapY * tileHeight + tileY] = rowBuilder.toString();
            }
        }
        createMazeFromText(finalLayout);
    }

    /**
     * Merges a content tile row into a corridor template row by replacing 'x' characters.
     * @param corridorRow The row from the corridor template (e.g., "#.xxxxxxxxxx").
     * @param contentRow The row from the content tile (e.g., "#...#.#D##.#").
     * @return The merged row.
     */
    private String mergeTileRow(String corridorRow, String contentRow) {
        StringBuilder merged = new StringBuilder(corridorRow);
        for (int i = 0; i < corridorRow.length(); i++) {
            if (corridorRow.charAt(i) == 'x') {
                merged.setCharAt(i, contentRow.charAt(i));
            }
        }
        return merged.toString();
    }

    /**
     * Selects the correct corridor template based on a tile's position within the overall map grid.
     * @param x The column index of the tile in the map grid.
     * @param y The row index of the tile in the map grid.
     * @param cols The total number of columns in the map grid.
     * @param rows The total number of rows in the map grid.
     * @return The appropriate corridor template as a String array.
     */
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

        // If not on any edge, it's a center piece which has no corridor.
        // We return a tile made of 'x' placeholders so the content tile is used completely.
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

    private void createMazeFromText(String[] layout) {
        int height = layout.length;
        int width = layout[0].length();
        int[][] bitmaskedData = new int[height][width];
        maze = new Maze(1, bitmaskedData);
        int playerStartX = 1, playerStartY = 1;
        for (int y = 0; y < height; y++) {
            int layoutY = height - 1 - y;
            for (int x = 0; x < width; x++) {
                char c = layout[layoutY].charAt(x);
                if (c == 'P') { playerStartX = x; playerStartY = y; }
                else if (c == 'D') { maze.addGameObject(new Door(), x, y); }
                else if (c == 'S') { maze.addItem(new Item(Item.ItemType.POTION_STRENGTH, x, y)); }
                else if (c == 'H') { maze.addItem(new Item(Item.ItemType.POTION_HEALING, x, y)); }
            }
        }
        player = new Player(playerStartX, playerStartY);
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
    }

    @Override
    public void render(float delta) {
        maze.update(delta);
        ScreenUtils.clear(0, 0, 0, 1);
        shapeRenderer.setProjectionMatrix(game.viewport.getCamera().combined);
        firstPersonRenderer.render(shapeRenderer, player, maze, game.viewport);
        itemRenderer.render(shapeRenderer, player, maze, game.viewport, firstPersonRenderer.getDepthBuffer());
        if (debugManager.isDebugOverlayVisible()) {
            debugRenderer.render(shapeRenderer, player, maze, game.viewport);
            game.batch.setProjectionMatrix(game.viewport.getCamera().combined);
            game.batch.begin();
            font.draw(game.batch, "DEBUG MODE - (F1 to toggle)", 10, game.viewport.getWorldHeight() - 10);
            game.batch.end();
        }
    }

    @Override
    public void resize(int width, int height) { game.viewport.update(width, height, true); }

    @Override
    public boolean keyDown(int keycode) {
        switch (keycode) {
            case Input.Keys.UP: player.moveForward(maze); break;
            case Input.Keys.DOWN: player.moveBackward(maze); break;
            case Input.Keys.LEFT: player.turnLeft(); break;
            case Input.Keys.RIGHT: player.turnRight(); break;
            case Input.Keys.F1: debugManager.toggleOverlay(); break;
            case Input.Keys.O: player.interact(maze); break;
        }
        return true;
    }

    @Override public boolean keyUp(int keycode) { return false; }
    @Override public boolean keyTyped(char character) { return false; }
    @Override public boolean touchDown(int screenX, int screenY, int pointer, int button) { return false; }
    @Override public boolean touchUp(int screenX, int screenY, int pointer, int button) { return false; }
    @Override public boolean touchCancelled(int screenX, int screenY, int pointer, int button) { return false; }
    @Override public boolean touchDragged(int screenX, int screenY, int pointer) { return false; }
    @Override public boolean mouseMoved(int screenX, int screenY) { return false; }
    @Override public boolean scrolled(float amountX, float amountY) { return false; }
}

