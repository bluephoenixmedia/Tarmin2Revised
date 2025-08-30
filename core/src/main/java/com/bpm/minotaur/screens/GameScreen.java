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
import com.bpm.minotaur.generation.MazeGenerator;
import com.bpm.minotaur.managers.DebugManager;
import com.bpm.minotaur.rendering.DebugRenderer;
import com.bpm.minotaur.rendering.FirstPersonRenderer;


public class GameScreen extends BaseScreen implements InputProcessor {

    private final DebugManager debugManager = DebugManager.getInstance();
    private final ShapeRenderer shapeRenderer = new ShapeRenderer();
    private final BitmapFont font = new BitmapFont();
    private final DebugRenderer debugRenderer = new DebugRenderer();
    private final FirstPersonRenderer firstPersonRenderer = new FirstPersonRenderer();
    private final MazeGenerator mazeGenerator = new MazeGenerator();

    private Player player;
    private Maze maze;
    private int currentLevel = 1;

    public GameScreen(Tarmin2 game) {
        super(game);
    }

    @Override
    public void show() {
        Gdx.input.setInputProcessor(this);
        generateNewLevel(currentLevel);
    }

    private void generateNewLevel(int level) {
        // Generate new maze with optional seed for reproducible results during development
        if (debugManager.isDebugOverlayVisible()) {
            mazeGenerator.setSeed(level * 12345L); // Reproducible levels for debugging
        }

        maze = mazeGenerator.generate(level);

        // Create player at the starting position provided by the generator
        player = new Player(mazeGenerator.getPlayerStartX(), mazeGenerator.getPlayerStartY());

        // Log generation info for debugging
        //Gdx.app.log("MazeGen", "Generated level " + level + " with " + mazeGenerator.getRoomCount() + " rooms");
        Gdx.app.log("MazeGen", "Player start: (" + player.getPositionAsGridX() + ", " + player.getPositionAsGridY() + ")");

        printMazeToConsole();
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
            font.draw(game.batch, "DEBUG MODE - (F1 to toggle, R to regenerate, N for next level)", 10, game.viewport.getWorldHeight() - 10);
           // font.draw(game.batch, "Level: " + currentLevel + " | Rooms: " + mazeGenerator.getRoomCount(), 10, game.viewport.getWorldHeight() - 30);
            font.draw(game.batch, "Player: (" + player.getPositionAsGridX() + ", " + player.getPositionAsGridY() + ")", 10, game.viewport.getWorldHeight() - 50);
            game.batch.end();
        }
    }

    private void printMazeToConsole() {
        System.out.println("--- Level " + currentLevel + " Maze Layout (Console View) ---");
        int[][] wallData = maze.getWallData();
        int width = maze.getWidth();
        int height = maze.getHeight();

        for (int y = height - 1; y >= 0; y--) {
            // Print the top wall of each cell in the row
            for (int x = 0; x < width; x++) {
                System.out.print("+");
                int topState = (wallData[y][x] >> 6) & 0b11;
                if (topState == 0b01) { // Wall only
                    System.out.print("---");
                } else if (topState == 0b10) { // Door only
                    System.out.print("-D-");
                } else if (topState == 0b11) { // Wall with door
                    System.out.print("-D-");
                } else {
                    System.out.print("   "); // No wall
                }
            }
            System.out.println("+");

            // Print the side walls and content of each cell in the row
            for (int x = 0; x < width; x++) {
                int leftState = wallData[y][x] & 0b11;
                if (leftState == 0b01) { // Wall only
                    System.out.print("|");
                } else if (leftState == 0b10) { // Door only
                    System.out.print("D");
                } else if (leftState == 0b11) { // Wall with door
                    System.out.print("D");
                } else {
                    System.out.print(" "); // No wall
                }

                // Cell content
                if (player.getPositionAsGridX() == x && player.getPositionAsGridY() == y) {
                    System.out.print(" P ");
                } else if (maze.getGameObjectAt(x, y) instanceof Door) {
                    System.out.print(" d "); // Show door object in cell
                } else {
                    System.out.print("   ");
                }
            }

            // Rightmost wall of the maze for this row
            int rightState = (wallData[y][width - 1] >> 4) & 0b11;
            if (rightState == 0b01) { // Wall only
                System.out.println("|");
            } else if (rightState == 0b10) { // Door only
                System.out.println("D");
            } else if (rightState == 0b11) { // Wall with door
                System.out.println("D");
            } else {
                System.out.println(" "); // No wall
            }
        }

        // Print the bottom-most line of the maze
        for (int x = 0; x < width; x++) {
            System.out.print("+");
            int bottomState = (wallData[0][x] >> 2) & 0b11;
            if (bottomState == 0b01) { // Wall only
                System.out.print("---");
            } else if (bottomState == 0b10) { // Door only
                System.out.print("-D-");
            } else if (bottomState == 0b11) { // Wall with door
                System.out.print("-D-");
            } else {
                System.out.print("   "); // No wall
            }
        }
        System.out.println("+");
       // System.out.println("Level: " + currentLevel + " | Rooms: " + mazeGenerator.getRoomCount() +
        //    " | Player: (" + player.getPositionAsGridX() + ", " + player.getPositionAsGridY() + ")");
        System.out.println("---------------------------------------------------------------------");
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
            case Input.Keys.R:
                // Regenerate current level (useful for testing)
                if (debugManager.isDebugOverlayVisible()) {
                    generateNewLevel(currentLevel);
                }
                break;
            case Input.Keys.N:
                // Generate next level (useful for testing different level seeds)
                if (debugManager.isDebugOverlayVisible()) {
                    currentLevel++;
                    generateNewLevel(currentLevel);
                }
                break;
            case Input.Keys.P:
                // Generate previous level
                if (debugManager.isDebugOverlayVisible() && currentLevel > 1) {
                    currentLevel--;
                    generateNewLevel(currentLevel);
                }
                break;
        }

        // Only reprint maze after movement or generation changes
        if (keycode == Input.Keys.UP || keycode == Input.Keys.DOWN ||
            keycode == Input.Keys.R || keycode == Input.Keys.N || keycode == Input.Keys.P) {
            printMazeToConsole();
        }

        return true;
    }

    @Override
    public void dispose() {
        shapeRenderer.dispose();
        font.dispose();
      //  debugRenderer.dispose();
      //  firstPersonRenderer.dispose();
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
