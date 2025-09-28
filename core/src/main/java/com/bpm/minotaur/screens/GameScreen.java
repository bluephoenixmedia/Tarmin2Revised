package com.bpm.minotaur.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.GridPoint2;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.ScreenUtils;
import com.bpm.minotaur.Tarmin2;
import com.bpm.minotaur.gamedata.*;
import com.bpm.minotaur.managers.CombatManager;
import com.bpm.minotaur.managers.DebugManager;
import com.bpm.minotaur.managers.GameEventManager;
import com.bpm.minotaur.managers.SpawnManager; // Import the new manager
import com.bpm.minotaur.rendering.*;
;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class GameScreen extends BaseScreen implements InputProcessor, Disposable {

    // --- Core Dependencies ---
    private final DebugManager debugManager = DebugManager.getInstance();
    private final ShapeRenderer shapeRenderer = new ShapeRenderer();
    private final BitmapFont font = new BitmapFont();

    private boolean needsAsciiRender = false;


    // --- Renderers ---
    private final DebugRenderer debugRenderer = new DebugRenderer();
    private final FirstPersonRenderer firstPersonRenderer = new FirstPersonRenderer();
    private final EntityRenderer entityRenderer = new EntityRenderer();
    private final Difficulty difficulty; // Add this line

    private Hud hud;
    private AnimationManager animationManager;
    private GameEventManager eventManager;

    // --- Game State ---
    private Player player;
    private Maze maze;
    private int currentLevel = 1;
    private final Random random = new Random();
    private String[] finalLayout;
    private CombatManager combatManager;

    // --- Maze Content Tile Definitions ---
    String[] tile1 = new String[]{ "#####.#.####", "#...#.#D##.#", "#.#D#....#.#", "#.D...P..D.#", "###..###.###", ".....#.D....", "###..#.#####", "#.#..#.#####", "#D#..#.D....", "#.#..###....", "#.....##....", "#####.######" };
    String[] tile2 = new String[]{ "#####.######", "#...D.D....#", "#####.######", "#...D.D....#", "#####.######", "....D.D.....", "#####.######", "#...D.D....#", "#####.######", "#...D.D....#", "#####.######", "#####.######" };
    String[] tile3 = new String[]{ "#####.######", "#..........#", "###D###D####", "............", "#.#D#..###.#", "..#.#..#.#..", "#.#.#..#.#.#", "#.#.#..#.D.#", "#.#.#..#.#.#", "#.#.#..#.#.#", "#.#.####.#.#", "#.#......#.#" };
    String[] tile4 = new String[]{ "#####..#####", "#...#..#...#", "#...#..#...#", "#...D..#...#", "#...#..#...#", ".####D###D#.", "....D...D...", "###D#D######", "#...#..D...#", "#...#..#...#", "#...#..#...#", "#####..#####" };
    String[] tile5 = new String[]{ "#####.###...", "#.##....#.##", "#D##D##D#.D.", "#.#...#.#.##", "#D#...#D#...", "........D...", "#D#...#D#...", "#.#...#.#...", "#D#...#D#.##", "#.##D##.#.D.", "####...##.##", "#####..##...." };
    String[] tile6 = new String[]{ "#####..#####", "#....#.#...#", "#....#.#...#", "#....#.#...#", "#....D.D...#", ".#####.####.", "............", "######.#####", "#....D.D...#", "#....#.#...#", "#....#.#...#", "#####..#####" };
    String[] tile7 = new String[]{ "#####.######", "#.#........#", "#.D.######.#", "###.#....#.#", "....#....#.#", ".#D##D####..", "#...#......#", "#...#......#", "#...#..###D#", "#...#..#...#", "#...#..#...#", "#####.######" };
    String[] tile8 = new String[]{ "###.#..#.###", "#.#D#..#D#.#", "#.#.#..#.#.#", "....#..#...#", "###D#..#D###", "............", "######D#####", "#.#....#...#", "#.D....D...#", "#.#....#...#", "#.#....#...#", "###....#####" };
    String[] tile9 = new String[]{
        ".####.#.....",
        ".#..D.#..###",
        ".####.#..#.#",
        "......#..#.#",
        "##D####..#D#",
        "....D.D.....",
        "....####D###",
        "#D#..#......",
        "#.#..#......",
        "#.#..#.####.",
        "###..#.D..#.",
        ".....#.####." };
    String[] tile10 = new String[]{ "####........", "#..#.#D#####", "#..#.#..#..#", "#..#.#..#..#", "##D#######D#", "...D.D......", "##D#####D###", "#....#......", "#....#......", "#....#.####.", "#....#.D..#.", "######.####." };
    String[] tile11 = new String[]{ "#####.######", "#.#.D.D.#..#", "#.###.###..#", "#.#.D.D.#..#", "#D###.###D##", "..D.D.#.#..#", "#D###.#D#..#", "#.#.D.D.##D#", "#.###.###..#", "#.#.D.D.D..#", "#.#.#.#.#..#", "#####.######" };
    String[] tile12 = new String[]{ "############", "#...D.D....#", "#D###.####D#", "....#.#.....", "#D#.#.#..###", "#.#.#.#..#.#", "###.#.#..#D#", "....#.#.....", "#D###.####D#", "#...D.D....#", "#####.######", "............" };
    String[] tile13 = new String[]{ "....#.######", "....D.D....#", "#D###.#....#", "#.#...#....#", "###.########", "....D.D.....", "#######.....", "....#....###", "....#....#.#", "....#.####D#", "....D.D....#", "....#.######" };
    String[] tile14 = new String[]{ "############", "#...D.D..#.#", "#.#D#.##.D.#", "#D#.....##D#", "#...####...#", "....D..#....", "#...###....#", "#D#.....#D##", "#.D.....#..#", "#.###.#D#..#", "#...D.D....#", "############" };
    String[] tile15 = new String[]{ "#####.######", "#...D.D....#", "#...#.#....#", "#...#.#....#", "##D##.##D###", "....D.......", "#####D######", "#...#.#....#", "#...D.D....#", "#...#.#....#", "#...#.#....#", "#####.######" };
    String[] tile16 = new String[]{ "..##########", "..D........#", "..#####..###", "......D..#.#", "####D##..#.#", "......#..D.#", "......#..#.#", "..###.#..#.#", "..D.#.#..#.#", "..###.#..#.#", "......#..#.#", "......#..#.#" };

    // --- Corridor Tile Templates ---
    // --- Corridor Tile Templates ---
    String[] corridorUL = new String[]{
        "############",
        "#...........",
        "#.xxxxxxxxxx",
        "#.xxxxxxxxxx",
        "#.xxxxxxxxxx",
        "#.xxxxxxxxxx",
        "#.xxxxxxxxxx",
        "#.xxxxxxxxxx",
        "#.xxxxxxxxxx",
        "#.xxxxxxxxxx",
        "#.xxxxxxxxxx",
        "#.xxxxxxxxxx"
    };
    String[] corridorT = new String[]{ "############", "............", "xxxxxxxxxxxx", "xxxxxxxxxxxx", "xxxxxxxxxxxx", "xxxxxxxxxxxx", "xxxxxxxxxxxx", "xxxxxxxxxxxx", "xxxxxxxxxxxx", "xxxxxxxxxxxx", "xxxxxxxxxxxx", "xxxxxxxxxxxx" };
    String[] corridorUR = new String[]{ "############", "...........#", "xxxxxxxxxx.#", "xxxxxxxxxx.#", "xxxxxxxxxx.#", "xxxxxxxxxx.#", "xxxxxxxxxx.#", "xxxxxxxxxx.#", "xxxxxxxxxx.#", "xxxxxxxxxx.#", "xxxxxxxxxx.#", "xxxxxxxxxx.#" };
    String[] corridorL = new String[]{ "#.xxxxxxxxxx", "#.xxxxxxxxxx", "#.xxxxxxxxxx", "#.xxxxxxxxxx", "#.xxxxxxxxxx", "#.xxxxxxxxxx", "#.xxxxxxxxxx", "#.xxxxxxxxxx", "#.xxxxxxxxxx", "#.xxxxxxxxxx", "#.xxxxxxxxxx", "#.xxxxxxxxxx" };
    String[] corridorR = new String[]{ "xxxxxxxxxx.#", "xxxxxxxxxx.#", "xxxxxxxxxx.#", "xxxxxxxxxx.#", "xxxxxxxxxx.#", "xxxxxxxxxx.#", "xxxxxxxxxx.#", "xxxxxxxxxx.#", "xxxxxxxxxx.#", "xxxxxxxxxx.#", "xxxxxxxxxx.#", "xxxxxxxxxx.#" };
    String[] corridorLL = new String[]{ "#.xxxxxxxxxx", "#.xxxxxxxxxx", "#.xxxxxxxxxx", "#.xxxxxxxxxx", "#.xxxxxxxxxx", "#.xxxxxxxxxx", "#.xxxxxxxxxx", "#.xxxxxxxxxx", "#.xxxxxxxxxx", "#.xxxxxxxxxx", "#...........", "############" };
    String[] corridorB = new String[]{ "xxxxxxxxxxxx", "xxxxxxxxxxxx", "xxxxxxxxxxxx", "xxxxxxxxxxxx", "xxxxxxxxxxxx", "xxxxxxxxxxxx", "xxxxxxxxxxxx", "xxxxxxxxxxxx", "xxxxxxxxxxxx", "xxxxxxxxxxxx", "............", "############" };
    String[] corridorLR = new String[]{ "xxxxxxxxxx.#", "xxxxxxxxxx.#", "xxxxxxxxxx.#", "xxxxxxxxxx.#", "xxxxxxxxxx.#", "xxxxxxxxxx.#", "xxxxxxxxxx.#", "xxxxxxxxxx.#", "xxxxxxxxxx.#", "xxxxxxxxxx.#", "...........#", "############" };

    private static class TileInfo { int id; int rotation; TileInfo(int id, int rotation) { this.id = id; this.rotation = rotation; } }

    public GameScreen(Tarmin2 game, Difficulty difficulty) {
        super(game);
        this.difficulty = difficulty; // Store the selected difficulty
    }

    @Override
    public void show() {
        Gdx.input.setInputProcessor(this);
        animationManager = new AnimationManager();
        eventManager = new GameEventManager();
        generateLevel(currentLevel);
    }

    private void descendToNextLevel() {
        currentLevel++;
        Gdx.app.log("GameScreen", "Descending to level " + currentLevel);
        generateLevel(currentLevel);
    }

    private void generateLevel(int levelNumber) {
        createMazeFromArrayTiles();

        if (levelNumber > 1) { // Only spawn gates on level 2 and higher
            spawnGate();
        }
        SpawnManager spawnManager = new SpawnManager(maze, difficulty, levelNumber, finalLayout);
        spawnManager.spawnEntities();
        spawnLadder();
        if (player == null) {
            createPlayerAtStart();
        } else {
            resetPlayerPosition();
        }

        combatManager = new CombatManager(player, maze, game, animationManager, eventManager);
        hud = new Hud(game.batch, player, maze, combatManager, eventManager);


        DebugRenderer.printMazeToConsole(maze);
    }

    private void createPlayerAtStart() {
        int playerStartX = 1, playerStartY = 1;
        for (int y = 0; y < finalLayout.length; y++) {
            for (int x = 0; x < finalLayout[0].length(); x++) {
                if (finalLayout[finalLayout.length - 1 - y].charAt(x) == 'P') {
                    playerStartX = x;
                    playerStartY = y;
                    // Pass the difficulty to the Player constructor
                    player = new Player(playerStartX, playerStartY, difficulty);
                    return;
                }
            }
        }
        // Fallback if 'P' is not in the layout
        player = new Player(playerStartX, playerStartY, difficulty);
    }


    private void resetPlayerPosition() {
        int playerStartX = 1, playerStartY = 1;
        for (int y = 0; y < finalLayout.length; y++) {
            for (int x = 0; x < finalLayout[0].length(); x++) {
                if (finalLayout[finalLayout.length - 1 - y].charAt(x) == 'P') {
                    playerStartX = x;
                    playerStartY = y;
                    break;
                }
            }
        }
        player.getPosition().set(playerStartX + 0.5f, playerStartY + 0.5f);
    }

    private void spawnGate() {
        if (finalLayout == null || finalLayout.length <= 2) return;

        List<GridPoint2> validGateLocations = new ArrayList<>();
        int height = finalLayout.length;
        int width = finalLayout[0].length();

        // Check top and bottom walls
        for (int x = 1; x < width - 1; x++) {
            if (finalLayout[0].charAt(x) == '#') validGateLocations.add(new GridPoint2(x, height - 1));
            if (finalLayout[height - 1].charAt(x) == '#') validGateLocations.add(new GridPoint2(x, 0));
        }
        // Check left and right walls
        for (int y = 1; y < height - 1; y++) {
            if (finalLayout[y].charAt(0) == '#') validGateLocations.add(new GridPoint2(0, height - 1 - y));
            if (finalLayout[y].charAt(width - 1) == '#') validGateLocations.add(new GridPoint2(width - 1, height - 1 - y));
        }

        if (validGateLocations.isEmpty()) return;

        // Pick a random valid location
        GridPoint2 gatePos = validGateLocations.get(random.nextInt(validGateLocations.size()));

        // Modify the layout to place the gate
        int layoutY = height - 1 - gatePos.y;
        char[] row = finalLayout[layoutY].toCharArray();
        row[gatePos.x] = 'G';
        finalLayout[layoutY] = new String(row);

        Gdx.app.log("GameScreen", "Gate spawned at (" + gatePos.x + ", " + gatePos.y + ")");
    }

    private void spawnLadder() {
        int x, y;
        do {
            x = random.nextInt(maze.getWidth());
            y = random.nextInt(maze.getHeight());
        } while (
            finalLayout[maze.getHeight() - 1 - y].charAt(x) != '.' ||
                maze.getItems().containsKey(new GridPoint2(x, y)) ||
                maze.getMonsters().containsKey(new GridPoint2(x, y))
        );
        maze.addLadder(new Ladder(x, y));
        Gdx.app.log("GameScreen", "Ladder spawned at (" + x + ", " + y + ")");
    }


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
        this.finalLayout = new String[finalHeight];

        for (int mapY = 0; mapY < MAP_ROWS; mapY++) {
            for (int tileY = 0; tileY < tileHeight; tileY++) {
                StringBuilder rowBuilder = new StringBuilder();
                for (int mapX = 0; mapX < MAP_COLS; mapX++) {
                    TileInfo info = mapLayout[mapY][mapX];
                    String[] contentTile = rotateTile(allTiles.get(info.id), info.rotation);
                    String[] corridorTile = getCorridorTemplate(mapX, mapY, MAP_COLS, MAP_ROWS);

                    String mergedRow = mergeTileRow(corridorTile[tileY], contentTile[tileY]);
                    rowBuilder.append(mergedRow);
                }
                this.finalLayout[mapY * tileHeight + tileY] = rowBuilder.toString();
            }
        }
        createMazeFromText(this.finalLayout);
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

    private void createMazeFromText(String[] layout) {
        int height = layout.length;
        int width = layout[0].length();
        int[][] bitmaskedData = new int[height][width];
        maze = new Maze(currentLevel, bitmaskedData);

        for (int y = 0; y < height; y++) {
            int layoutY = height - 1 - y;
            for (int x = 0; x < width; x++) {
                char c = layout[layoutY].charAt(x);
                if (c == 'D') { maze.addGameObject(new Door(), x, y); }
                else if (c == 'G') { maze.addGate(new Gate(x, y)); } // This line now works with the procedural gate
                else if (c == 'S') { maze.addItem(new Item(Item.ItemType.POTION_STRENGTH, x, y)); }
                else if (c == 'H') { maze.addItem(new Item(Item.ItemType.POTION_HEALING, x, y)); }
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
    }

    @Override
    public void render(float delta) {
        // --- UPDATE LOGIC ---
        combatManager.update(delta);
        animationManager.update(delta);
        maze.update(delta);
        hud.update(delta);
        eventManager.update(delta);

        // --- WORLD RENDERING (using ShapeRenderer) ---
        ScreenUtils.clear(0, 0, 0, 1);
        shapeRenderer.setProjectionMatrix(game.viewport.getCamera().combined);

        firstPersonRenderer.render(shapeRenderer, player, maze, game.viewport);

        if (combatManager.getCurrentState() == CombatManager.CombatState.INACTIVE) {
            entityRenderer.render(shapeRenderer, player, maze, game.viewport, firstPersonRenderer.getDepthBuffer());
        } else {
            entityRenderer.renderSingleMonster(shapeRenderer, player, combatManager.getMonster(), game.viewport, firstPersonRenderer.getDepthBuffer());
        }

        animationManager.render(shapeRenderer, player, game.viewport, firstPersonRenderer.getDepthBuffer());

        if (debugManager.isDebugOverlayVisible()) {
            debugRenderer.render(shapeRenderer, player, maze, game.viewport);
            if (needsAsciiRender) {
                firstPersonRenderer.renderAsciiViewToConsole(player, maze);
                needsAsciiRender = false;
            }
        }

        // --- HUD RENDERING ---
        // The HUD manages its own SpriteBatch calls for its Stage and background.
        hud.render();

        // --- SCREEN-LEVEL TEXT RENDERING (using SpriteBatch) ---
        // This is the final drawing step, consolidated into one block.
        game.batch.setProjectionMatrix(game.viewport.getCamera().combined);
        game.batch.begin(); // <-- Batch starts here

        font.setColor(Color.WHITE);
        font.draw(game.batch, "Level: " + currentLevel, 10, game.viewport.getWorldHeight() - 10);

        if (debugManager.isDebugOverlayVisible()) {
            font.draw(game.batch, "DEBUG MODE - (F1 to toggle)", 10, game.viewport.getWorldHeight() - 30);

            font.setColor(Color.YELLOW);
            String[] keyMappings = {
                "--- CONTROLS ---",
                "UP   : Move Forward",
                "DOWN : Move Backward",
                "LEFT : Turn Left",
                "RIGHT: Turn Right",
                "", // Spacer
                "O    : Open/Interact",
                "P    : Pickup/Drop Item",
                "U    : Use Item",
                "D    : Descend Ladder",
                "R    : Rest",
                "", // Spacer
                "S    : Swap Hands",
                "E    : Swap with Pack",
                "T    : Rotate Pack",
                "", // Spacer
                "A    : Attack (Combat)",
                "M    : Castle Map"
            };
            float y = game.viewport.getWorldHeight() - 60;
            for (String mapping : keyMappings) {
                font.draw(game.batch, mapping, 10, y);
                y -= 20;
            }
        }

        game.batch.end(); // <-- Batch ends here
    }

    @Override
    public void resize(int width, int height) { game.viewport.update(width, height, true); }

    @Override
    public boolean keyDown(int keycode) {
        if (combatManager.getCurrentState() == CombatManager.CombatState.PLAYER_TURN) {
            if (keycode == Input.Keys.A) {
                combatManager.playerAttack();
                return true;
            }
        }

        if (combatManager.getCurrentState() == CombatManager.CombatState.INACTIVE) {
            switch (keycode) {
                case Input.Keys.UP:
                    player.moveForward(maze);
                    combatManager.checkForAdjacentMonsters();
                    needsAsciiRender = true;
                    break;
                case Input.Keys.DOWN:
                    player.moveBackward(maze);
                    combatManager.checkForAdjacentMonsters();
                    needsAsciiRender = true;
                    break;
                case Input.Keys.LEFT:
                    player.turnLeft();
                    needsAsciiRender = true;
                    break;
                case Input.Keys.RIGHT:
                    player.turnRight();
                    needsAsciiRender = true;
                    break;
                case Input.Keys.F1:
                    debugManager.toggleOverlay();
                    break;
                case Input.Keys.O:
                    player.interact(maze, eventManager);
                    needsAsciiRender = true;
                    break;
                case Input.Keys.M:  // Add this case
                    game.setScreen(new CastleMapScreen(game, player, maze, this));
                    break;
                case Input.Keys.R:
                    player.rest();
                    break;
                case Input.Keys.S:
                    player.getInventory().swapHands();
                    break;
                case Input.Keys.E:
                    player.getInventory().swapWithPack();
                    break;
                case Input.Keys.T:
                    player.getInventory().rotatePack();
                    break;
                case Input.Keys.U:
                    player.useItem(eventManager);
                    break;
                case Input.Keys.P:
                    player.interactWithItem(maze, eventManager);
                    break;
                case Input.Keys.D:
                    GridPoint2 playerPos = new GridPoint2((int) player.getPosition().x, (int) player.getPosition().y);
                    if (maze.getLadders().containsKey(playerPos)) {
                        descendToNextLevel();
                    }
                    break;
            }
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

    @Override
    public void dispose() {
        shapeRenderer.dispose();
        font.dispose();
        if (hud != null) {
            hud.dispose();
        }
        if (entityRenderer != null) {
            entityRenderer.dispose();
        }
    }
}
