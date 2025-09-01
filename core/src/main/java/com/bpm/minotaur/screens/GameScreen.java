package com.bpm.minotaur.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.ScreenUtils;
import com.bpm.minotaur.Tarmin2;
import com.bpm.minotaur.gamedata.Door;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.Player;
import com.bpm.minotaur.managers.DebugManager;
import com.bpm.minotaur.rendering.DebugRenderer;
import com.bpm.minotaur.rendering.FirstPersonRenderer;

public class GameScreen extends BaseScreen implements InputProcessor {

    private final DebugManager debugManager = DebugManager.getInstance();
    private final ShapeRenderer shapeRenderer = new ShapeRenderer();
    private final BitmapFont font = new BitmapFont();
    private final DebugRenderer debugRenderer = new DebugRenderer();
    private final FirstPersonRenderer firstPersonRenderer = new FirstPersonRenderer();

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
            "#...#....#.#",
            "#.P......#.#",
            "#...#......#",
            "#####.####.#",
            "#...#....#.#",
            "#.#.#####..#",
            "#.#........#",
            "#.##D##.####",
            "#.#...#....#",
            "#.....#....#",
            "############"
        });
        // Call the new console print method for debugging
        DebugRenderer.printMazeToConsole(maze);
    }

    private void createMazeFromText(String[] layout) {
        int width = layout[0].length();
        int height = layout.length;
        int[][] bitmaskedData = new int[height][width];
        int playerStartX = 1, playerStartY = 1;

        // This new logic defines walls from the perspective of walkable cells.
        // A walkable cell has a wall bitmask set if its neighbor is a solid block ('#') or a door ('D').
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int layoutY = height - 1 - y;
                char me = layout[layoutY].charAt(x);

                // Wall blocks themselves don't have paths, so we skip them.
                if (me == '#') {
                    continue;
                }

                if (me == 'P') {
                    playerStartX = x;
                    playerStartY = y;
                }

                int mask = 0;

                // Check North neighbor
                char north = (layoutY > 0) ? layout[layoutY - 1].charAt(x) : '#';
                if (north == '#') mask |= 0b01000000; // WALL_NORTH
                else if (north == 'D') mask |= 0b10000000; // DOOR_NORTH

                // Check South neighbor
                char south = (layoutY < height - 1) ? layout[layoutY + 1].charAt(x) : '#';
                if (south == '#') mask |= 0b00010000; // WALL_SOUTH
                else if (south == 'D') mask |= 0b00100000; // DOOR_SOUTH

                // Check East neighbor
                char east = (x < width - 1) ? layout[layoutY].charAt(x + 1) : '#';
                if (east == '#') mask |= 0b00000100; // WALL_EAST
                else if (east == 'D') mask |= 0b00001000; // DOOR_EAST

                // Check West neighbor
                char west = (x > 0) ? layout[layoutY].charAt(x - 1) : '#';
                if (west == '#') mask |= 0b00000001; // WALL_WEST
                else if (west == 'D') mask |= 0b00000010; // DOOR_WEST

                bitmaskedData[y][x] = mask;
            }
        }

        maze = new Maze(1, bitmaskedData);
        player = new Player(playerStartX, playerStartY);

        // Add the Door game objects at the 'D' locations.
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int layoutY = height - 1 - y;
                if (layout[layoutY].charAt(x) == 'D') {
                    maze.addGameObject(new Door(), x, y);
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
        Gdx.app.log("Input", "Key pressed: " + Input.Keys.toString(keycode));
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

    // --- Unused InputProcessor methods ---
    @Override public boolean keyUp(int keycode) { return false; }
    @Override public boolean keyTyped(char character) { return false; }
    @Override public boolean touchDown(int screenX, int screenY, int pointer, int button) { return false; }
    @Override public boolean touchUp(int screenX, int screenY, int pointer, int button) { return false; }
    @Override public boolean touchCancelled(int screenX, int screenY, int pointer, int button) { return false; }
    @Override public boolean touchDragged(int screenX, int screenY, int pointer) { return false; }
    @Override public boolean mouseMoved(int screenX, int screenY) { return false; }
    @Override public boolean scrolled(float amountX, float amountY) { return false; }
}

