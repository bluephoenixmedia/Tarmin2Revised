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

public class GameScreen extends BaseScreen implements InputProcessor {

    private final DebugManager debugManager = DebugManager.getInstance();
    private final ShapeRenderer shapeRenderer = new ShapeRenderer();
    private final BitmapFont font = new BitmapFont();
    private final DebugRenderer debugRenderer = new DebugRenderer();
    private final FirstPersonRenderer firstPersonRenderer = new FirstPersonRenderer();
    private final ItemRenderer itemRenderer = new ItemRenderer();

    private Player player;
    private Maze maze;

    public GameScreen(Tarmin2 game) {
        super(game);
    }

    @Override
    public void show() {
        Gdx.input.setInputProcessor(this);
        createMazeFromText(new String[]{
            "############",
            "#S..#....#.#",
            "#P.......#.#",
            "#...#......#",
            "#####.####.#",
            "#...#......#",
            "#.#.#####.##",
            "#.#........#",
            "#.#####.####",
            "#.#...#....#",
            "#..H#......#",
            "############"
        });
        DebugRenderer.printMazeToConsole(maze);
    }

    private void createMazeFromText(String[] layout) {
        int height = layout.length;
        int width = layout[0].length();
        int[][] bitmaskedData = new int[height][width];

        // Instantiate the maze first
        maze = new Maze(1, bitmaskedData);
        int playerStartX = 1, playerStartY = 1;

        // First pass: Set up walls, doors, items, and player start
        for (int y = 0; y < height; y++) {
            int layoutY = height - 1 - y;
            for (int x = 0; x < width; x++) {
                char c = layout[layoutY].charAt(x);

                if (c == '#') {
                    bitmaskedData[y][x] = 0; // Solid walls have no openings
                }

                if (c == 'P') {
                    playerStartX = x;
                    playerStartY = y;
                } else if (c == 'D') {
                    maze.addGameObject(new Door(), x, y);
                } else if (c == 'S') {
                    maze.addItem(new Item(Item.ItemType.POTION_STRENGTH, x, y));
                } else if (c == 'H') {
                    maze.addItem(new Item(Item.ItemType.POTION_HEALING, x, y));
                }
            }
        }
        player = new Player(playerStartX, playerStartY);

        // Second pass: Calculate wall bitmasks based on neighbors
        for (int y = 0; y < height; y++) {
            int layoutY = height - 1 - y;
            for (int x = 0; x < width; x++) {
                if (layout[layoutY].charAt(x) != '#') {
                    int mask = 0;
                    // Check North
                    if (y + 1 >= height || layout[layoutY - 1].charAt(x) == '#') mask |= 0b01000000;
                    else if (layout[layoutY - 1].charAt(x) == 'D') mask |= 0b10000000;
                    // Check East
                    if (x + 1 >= width || layout[layoutY].charAt(x + 1) == '#') mask |= 0b00000100;
                    else if (layout[layoutY].charAt(x + 1) == 'D') mask |= 0b00001000;
                    // Check South
                    if (y - 1 < 0 || layout[layoutY + 1].charAt(x) == '#') mask |= 0b00010000;
                    else if (layout[layoutY + 1].charAt(x) == 'D') mask |= 0b00100000;
                    // Check West
                    if (x - 1 < 0 || layout[layoutY].charAt(x - 1) == '#') mask |= 0b00000001;
                    else if (layout[layoutY].charAt(x - 1) == 'D') mask |= 0b00000010;

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
        itemRenderer.render(shapeRenderer, player, maze, game.viewport);

        if (debugManager.isDebugOverlayVisible()) {
            debugRenderer.render(shapeRenderer, player, maze, game.viewport);
            game.batch.setProjectionMatrix(game.viewport.getCamera().combined);
            game.batch.begin();
            font.draw(game.batch, "DEBUG MODE - (F1 to toggle)", 10, game.viewport.getWorldHeight() - 10);
            game.batch.end();
        }
    }

    @Override
    public void resize(int width, int height) {
        game.viewport.update(width, height, true);
    }

    @Override
    public boolean keyDown(int keycode) {
        switch (keycode) {
            case Input.Keys.UP:
                player.moveForward(maze);
                break;
            case Input.Keys.DOWN:
                player.moveBackward(maze);
                break;
            case Input.Keys.LEFT:
                player.turnLeft();
                break;
            case Input.Keys.RIGHT:
                player.turnRight();
                break;
            case Input.Keys.F1:
                debugManager.toggleOverlay();
                break;
            case Input.Keys.O:
                player.interact(maze);
                break;
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

