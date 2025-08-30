package com.bpm.minotaur.rendering;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.bpm.minotaur.gamedata.Direction;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.Player;

public class DebugRenderer {

    public void render(ShapeRenderer shapeRenderer, Player player, Maze maze, Viewport viewport) {
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);

        // Define maze rendering area (e.g., top-right quadrant)
        float totalMazeHeight = viewport.getWorldHeight() / 2;
        float cellHeight = totalMazeHeight / maze.getHeight();
        float cellWidth = cellHeight; // Maintain aspect ratio
        float totalMazeWidth = cellWidth * maze.getWidth();
        float mazeStartX = viewport.getWorldWidth() - totalMazeWidth - 10;
        float mazeStartY = viewport.getWorldHeight() - totalMazeHeight - 10;


        // Draw maze walls
        shapeRenderer.setColor(Color.WHITE);
        for (int y = 0; y < maze.getHeight(); y++) {
            for (int x = 0; x < maze.getWidth(); x++) {
                int mask = maze.getWallDataAt(x, y);

                float drawX = mazeStartX + x * cellWidth;
                float drawY = mazeStartY + y * cellHeight;

                if ((mask & Direction.NORTH.getWallMask()) != 0) {
                    shapeRenderer.line(drawX, drawY + cellHeight, drawX + cellWidth, drawY + cellHeight);
                }
                if ((mask & Direction.EAST.getWallMask()) != 0) {
                    shapeRenderer.line(drawX + cellWidth, drawY, drawX + cellWidth, drawY + cellHeight);
                }
                if ((mask & Direction.SOUTH.getWallMask()) != 0) {
                    shapeRenderer.line(drawX, drawY, drawX + cellWidth, drawY);
                }
                if ((mask & Direction.WEST.getWallMask()) != 0) {
                    shapeRenderer.line(drawX, drawY, drawX, drawY + cellHeight);
                }
            }
        }

        // Draw Player
        shapeRenderer.setColor(Color.GREEN);
        float playerDrawX = mazeStartX + player.getPosition().x * cellWidth;
        float playerDrawY = mazeStartY + player.getPosition().y * cellHeight;
        shapeRenderer.circle(playerDrawX, playerDrawY, cellWidth * 0.25f, 20);


        // Draw player direction
        shapeRenderer.setColor(Color.YELLOW);
        Vector2 directionVector = player.getFacing().getVector();
        shapeRenderer.line(playerDrawX, playerDrawY, playerDrawX + directionVector.x * cellWidth * 0.4f, playerDrawY + directionVector.y * cellHeight * 0.4f);

        shapeRenderer.end();
    }
}

