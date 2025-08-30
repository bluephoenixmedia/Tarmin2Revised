package com.bpm.minotaur.rendering;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.Player;

public class DebugRenderer {
    // Bitmask constants for wall detection
    private static final int WALL_TOP = 0b01000000;
    private static final int WALL_RIGHT = 0b00010000;
    private static final int WALL_BOTTOM = 0b00000100;
    private static final int WALL_LEFT = 0b00000001;

    public void render(ShapeRenderer shapeRenderer, Viewport viewport, Maze maze, Player player) {
        // Calculate the total size and position for the maze rendering
        float totalMazeHeight = viewport.getWorldHeight() * 0.8f;
        float cellHeight = totalMazeHeight / Maze.MAZE_HEIGHT;
        float cellWidth = cellHeight; // Maintain aspect ratio
        float totalMazeWidth = cellWidth * Maze.MAZE_WIDTH;
        float mazeX = (viewport.getWorldWidth() - totalMazeWidth) / 2f;
        float mazeY = (viewport.getWorldHeight() - totalMazeHeight) / 2f;

        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
        shapeRenderer.setColor(Color.WHITE);

        // --- Render Maze Walls ---
        // Iterate through each cell of the maze data
        for (int y = 0; y < Maze.MAZE_HEIGHT; y++) {
            for (int x = 0; x < Maze.MAZE_WIDTH; x++) {
                int mask = maze.getWallData(x, y);

                // Calculate the bottom-left corner for the current cell's drawing position
                final float drawX = mazeX + (x * cellWidth);
                final float drawY = mazeY + (y * cellHeight);

                // Draw walls based on the bitmask for each cell.
                if ((mask & WALL_TOP) != 0) {
                    shapeRenderer.line(drawX, drawY + cellHeight, drawX + cellWidth, drawY + cellHeight);
                }
                if ((mask & WALL_RIGHT) != 0) {
                    shapeRenderer.line(drawX + cellWidth, drawY, drawX + cellWidth, drawY + cellHeight);
                }
                if ((mask & WALL_BOTTOM) != 0) {
                    shapeRenderer.line(drawX, drawY, drawX + cellWidth, drawY);
                }
                if ((mask & WALL_LEFT) != 0) {
                    shapeRenderer.line(drawX, drawY, drawX, drawY + cellHeight);
                }
            }
        }

        // --- Render Player ---
        float playerDrawX = mazeX + (player.getPosition().x * cellWidth) + (cellWidth / 2f);
        float playerDrawY = mazeY + (player.getPosition().y * cellHeight) + (cellHeight / 2f);
        float playerRadius = cellWidth / 4f;

        // Draw player circle
        shapeRenderer.setColor(Color.GREEN);
        shapeRenderer.circle(playerDrawX, playerDrawY, playerRadius);

        // Draw player direction indicator
        shapeRenderer.setColor(Color.YELLOW);
        Vector2 directionVector = player.getFacing().getVector();
        shapeRenderer.line(playerDrawX, playerDrawY,
            playerDrawX + directionVector.x * playerRadius,
            playerDrawY + directionVector.y * playerRadius);

        shapeRenderer.end();
    }
}

