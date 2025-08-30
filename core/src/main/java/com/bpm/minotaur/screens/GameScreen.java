package com.bpm.minotaur.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.ScreenUtils;
import com.bpm.minotaur.Tarmin2;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.Player;
import com.bpm.minotaur.managers.DebugManager;
import com.bpm.minotaur.rendering.DebugRenderer;

public class GameScreen extends BaseScreen implements InputProcessor {

    private BitmapFont debugFont;
    private ShapeRenderer shapeRenderer;
    private DebugRenderer debugRenderer;

    // Game state objects
    private Player player;
    private Maze maze;

    // Wall bitmask constants for console output and maze creation
    private static final int WALL_TOP = 0b01000000;
    private static final int WALL_RIGHT = 0b00010000;
    private static final int WALL_BOTTOM = 0b00000100;
    private static final int WALL_LEFT = 0b00000001;

    public GameScreen(Tarmin2 game) {
        super(game);
    }

    @Override
    public void show() {
        Gdx.input.setInputProcessor(this);

        debugFont = new BitmapFont();
        debugFont.setColor(Color.WHITE);
        shapeRenderer = new ShapeRenderer();
        debugRenderer = new DebugRenderer();

        // Create placeholder game objects for now.
        player = new Player();

        // A simple maze with a 3x3 room in the center for testing.
        int[][] testWallData = new int[Maze.MAZE_HEIGHT][Maze.MAZE_WIDTH];
        // Define a 3x3 room around (5,4)
        testWallData[5][4] = WALL_TOP | WALL_LEFT;     // Top-left
        testWallData[5][5] = WALL_TOP;                 // Top-middle
        testWallData[5][6] = WALL_TOP | WALL_RIGHT;    // Top-right
        testWallData[4][4] = WALL_LEFT;                // Middle-left
        // testWallData[4][5] is empty (center of room)
        testWallData[4][6] = WALL_RIGHT;               // Middle-right
        testWallData[3][4] = WALL_BOTTOM | WALL_LEFT;  // Bottom-left
        testWallData[3][5] = WALL_BOTTOM;              // Bottom-middle
        testWallData[3][6] = WALL_BOTTOM | WALL_RIGHT; // Bottom-right

        maze = new Maze(1, testWallData);
        player.getPosition().set(5, 4); // Place player in the middle of the room

        // Print the maze representation to the console
        printMazeToConsole();
    }

    @Override
    public void render(float delta) {
        ScreenUtils.clear(Color.BLACK);

        // Apply the viewport and update both projection matrices.
        game.viewport.apply();
        game.batch.setProjectionMatrix(game.viewport.getCamera().combined);
        shapeRenderer.setProjectionMatrix(game.viewport.getCamera().combined);

        if (DebugManager.INSTANCE.isDebugOverlayVisible) {
            // Render the 2D debug view, passing the viewport for correct scaling.
            debugRenderer.render(shapeRenderer, game.viewport, maze, player);

            // Render debug text over it.
            game.batch.begin();
            debugFont.draw(game.batch, "DEBUG MODE - (F1 to toggle)", 20, game.viewport.getWorldHeight() - 20);
            game.batch.end();
        }
    }

    private void printMazeToConsole() {
        System.out.println("--- Current Maze Layout (Console view) ---");
        for (int y = Maze.MAZE_HEIGHT - 1; y >= 0; y--) {
            StringBuilder topWalls = new StringBuilder();
            StringBuilder midRow = new StringBuilder();
            for (int x = 0; x < Maze.MAZE_WIDTH; x++) {
                int mask = maze.getWallData(x, y);

                // Top part of the cell
                topWalls.append("+");
                topWalls.append((mask & WALL_TOP) != 0 ? "---" : "   ");

                // Middle part of the cell
                midRow.append((mask & WALL_LEFT) != 0 ? "|" : " ");
                if (player.getPosition().x == x && player.getPosition().y == y) {
                    midRow.append(" P ");
                } else {
                    midRow.append("   ");
                }
            }
            // Add the final right wall for the last cell of the row
            int lastCellMask = maze.getWallData(Maze.MAZE_WIDTH - 1, y);
            topWalls.append("+");
            midRow.append((lastCellMask & WALL_RIGHT) != 0 ? "|" : " ");

            System.out.println(topWalls);
            System.out.println(midRow);
        }
        // Print bottom border for the entire maze
        StringBuilder bottomBorder = new StringBuilder();
        for (int x = 0; x < Maze.MAZE_WIDTH; x++) {
            bottomBorder.append("+");
            int mask = maze.getWallData(x, 0);
            bottomBorder.append((mask & WALL_BOTTOM) != 0 ? "---" : "   ");
        }
        bottomBorder.append("+");
        System.out.println(bottomBorder);
        System.out.println("---------------------------------------------------------------------");
    }

    @Override
    public void resize(int width, int height) {
        game.viewport.update(width, height, true);
    }

    @Override
    public void dispose() {
        if (debugFont != null) debugFont.dispose();
        if (shapeRenderer != null) shapeRenderer.dispose();
    }

    @Override
    public boolean keyDown(int keycode) {
        if (keycode == Input.Keys.F1) {
            DebugManager.INSTANCE.toggleOverlay();
            return true;
        }

        boolean moved = false;
        switch (keycode) {
            case Input.Keys.UP:
                player.moveForward(maze);
                moved = true;
                break;
            case Input.Keys.DOWN:
                player.moveBackward(maze);
                moved = true;
                break;
            case Input.Keys.LEFT:
                player.turnLeft();
                moved = true;
                break;
            case Input.Keys.RIGHT:
                player.turnRight();
                moved = true;
                break;
        }

        if (moved) {
            // If the player moved, print the new layout to the console for verification
            printMazeToConsole();
            return true;
        }

        return false;
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

