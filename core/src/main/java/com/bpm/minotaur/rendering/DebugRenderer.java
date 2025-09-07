package com.bpm.minotaur.rendering;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.GridPoint2;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.bpm.minotaur.gamedata.Door;
import com.bpm.minotaur.gamedata.Item;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.Monster;
import com.bpm.minotaur.gamedata.Player;

public class DebugRenderer {

    private static final int WALL_NORTH = 0b01000000;
    private static final int WALL_EAST  = 0b00000100;
    private static final int WALL_SOUTH = 0b00010000;
    private static final int WALL_WEST  = 0b00000001;
    private static final int DOOR_NORTH = 0b10000000;
    private static final int DOOR_EAST  = 0b00001000;
    private static final int DOOR_SOUTH = 0b00100000;
    private static final int DOOR_WEST  = 0b00000010;

    public void render(ShapeRenderer shapeRenderer, Player player, Maze maze, Viewport viewport) {
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);

        int mazeHeight = maze.getHeight();
        int mazeWidth = maze.getWidth();

        float maxMazeSize = Math.min(viewport.getWorldWidth(), viewport.getWorldHeight()) * 0.4f;
        float cellSize = maxMazeSize / Math.max(mazeWidth, mazeHeight);
        float totalMazeWidth = mazeWidth * cellSize;
        float totalMazeHeight = mazeHeight * cellSize;

        float mazeStartX = viewport.getWorldWidth() - totalMazeWidth - 20;
        float mazeStartY = viewport.getWorldHeight() - totalMazeHeight - 20;

        for (int y = 0; y < mazeHeight; y++) {
            for (int x = 0; x < mazeWidth; x++) {
                int mask = maze.getWallDataAt(x, y);
                float cellX = mazeStartX + x * cellSize;
                float cellY = mazeStartY + y * cellSize;

                shapeRenderer.setColor(Color.WHITE);
                if ((mask & WALL_NORTH) != 0) shapeRenderer.line(cellX, cellY + cellSize, cellX + cellSize, cellY + cellSize);
                if ((mask & WALL_EAST)  != 0) shapeRenderer.line(cellX + cellSize, cellY, cellX + cellSize, cellY + cellSize);
                if ((mask & WALL_SOUTH) != 0) shapeRenderer.line(cellX, cellY, cellX + cellSize, cellY);
                if ((mask & WALL_WEST)  != 0) shapeRenderer.line(cellX, cellY, cellX, cellY + cellSize);

                shapeRenderer.setColor(Color.ORANGE);
                if ((mask & DOOR_NORTH) != 0) shapeRenderer.line(cellX, cellY + cellSize, cellX + cellSize, cellY + cellSize);
                if ((mask & DOOR_EAST)  != 0) shapeRenderer.line(cellX + cellSize, cellY, cellX + cellSize, cellY + cellSize);
                if ((mask & DOOR_SOUTH) != 0) shapeRenderer.line(cellX, cellY, cellX + cellSize, cellY);
                if ((mask & DOOR_WEST)  != 0) shapeRenderer.line(cellX, cellY, cellX, cellY + cellSize);
            }
        }

        shapeRenderer.setColor(Color.LIME);
        float playerCenterX = mazeStartX + (player.getPosition().x * cellSize);
        float playerCenterY = mazeStartY + (player.getPosition().y * cellSize);
        shapeRenderer.circle(playerCenterX, playerCenterY, cellSize * 0.2f, 12);

        shapeRenderer.setColor(Color.CYAN);
        Vector2 dir = player.getDirectionVector();
        shapeRenderer.line(playerCenterX, playerCenterY, playerCenterX + dir.x * cellSize * 0.4f, playerCenterY + dir.y * cellSize * 0.4f);

        for (Monster monster : maze.getMonsters().values()) {
            shapeRenderer.setColor(Color.RED);
            float monsterCenterX = mazeStartX + (monster.getPosition().x * cellSize);
            float monsterCenterY = mazeStartY + (monster.getPosition().y * cellSize);
            shapeRenderer.circle(monsterCenterX, monsterCenterY, cellSize * 0.3f, 12);
        }

        for (Item item : maze.getItems().values()) {
            shapeRenderer.setColor(Color.YELLOW);
            float itemCenterX = mazeStartX + (item.getPosition().x * cellSize);
            float itemCenterY = mazeStartY + (item.getPosition().y * cellSize);
            shapeRenderer.circle(itemCenterX, itemCenterY, cellSize * 0.25f, 12);
        }

        shapeRenderer.end();
    }

    // A static method to print the maze layout to the console for debugging.
    public static void printMazeToConsole(Maze maze) {
        System.out.println("[MazeDebug] --- Printing Maze Layout ---");
        int height = maze.getHeight();
        int width = maze.getWidth();
        int[][] wallData = maze.getWallData();

        for (int y = height - 1; y >= 0; y--) {
            StringBuilder topWall = new StringBuilder();
            StringBuilder midWall = new StringBuilder();

            // Draw top walls and corners
            for (int x = 0; x < width; x++) {
                topWall.append("+");
                boolean hasNorthWall = (wallData[y][x] & (WALL_NORTH | DOOR_NORTH)) != 0;
                topWall.append(hasNorthWall ? "---" : "   ");
            }
            topWall.append("+");
            System.out.println("[MazeDebug] " + topWall);

            // Draw side walls and cell content
            for (int x = 0; x < width; x++) {
                boolean hasWestWall = (wallData[y][x] & (WALL_WEST | DOOR_WEST)) != 0;
                midWall.append(hasWestWall ? "| " : "  ");

                Object obj = maze.getGameObjectAt(x, y);
                if (obj instanceof Door) {
                    midWall.append("D ");
                } else if (maze.getMonsters().containsKey(new GridPoint2(x, y))) {
                    midWall.append("M ");
                } else if (maze.getItems().containsKey(new GridPoint2(x,y))) {
                    midWall.append("I ");
                }
                else {
                    midWall.append("  ");
                }
            }
            // Rightmost wall
            midWall.append("|");
            System.out.println("[MazeDebug] " + midWall);
        }

        // Draw the final bottom border
        StringBuilder bottomWall = new StringBuilder();
        for (int x = 0; x < width; x++) {
            bottomWall.append("+---");
        }
        bottomWall.append("+");
        System.out.println("[MazeDebug] " + bottomWall);

        System.out.println("[MazeDebug] --- End of Maze Layout ---");
    }
}
