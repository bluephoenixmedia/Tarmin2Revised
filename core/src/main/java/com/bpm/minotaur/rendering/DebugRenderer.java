package com.bpm.minotaur.rendering;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20; // Import for blending if needed explicitly, though ShapeRenderer handles alpha.
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.GridPoint2;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.bpm.minotaur.gamedata.*;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.monster.Monster;
import com.bpm.minotaur.gamedata.player.Player;

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

        int mazeHeight = maze.getHeight();
        int mazeWidth = maze.getWidth();

        float maxMazeSize = Math.min(viewport.getWorldWidth(), viewport.getWorldHeight()) * 0.4f;
        float cellSize = maxMazeSize / Math.max(mazeWidth, mazeHeight);
        float totalMazeWidth = mazeWidth * cellSize;
        float totalMazeHeight = mazeHeight * cellSize;

        float mazeStartX = viewport.getWorldWidth() - totalMazeWidth - 20;
        float mazeStartY = viewport.getWorldHeight() - totalMazeHeight - 20;

        // --- NEW: Draw Background Rectangle ---
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(0f, 0f, 0f, 0.5f); // 50% transparent black
        // Add a small padding (e.g., 5 pixels) around the maze
        float padding = 5f;
        shapeRenderer.rect(mazeStartX - padding, mazeStartY - padding, totalMazeWidth + padding * 2, totalMazeHeight + padding * 2);
        shapeRenderer.end();
        // --- END NEW ---

        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);

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

        if (maze.getScenery() != null) {
            for (Scenery scenery : maze.getScenery().values()) {
                shapeRenderer.setColor(Color.GREEN);
                float sceneryCenterX = mazeStartX + (scenery.getPosition().x * cellSize);
                float sceneryCenterY = mazeStartY + (scenery.getPosition().y * cellSize);
                shapeRenderer.circle(sceneryCenterX, sceneryCenterY, cellSize * 0.25f, 12);
            }
        }

        shapeRenderer.end();

        // The filled shapes (Ladders/Gates) need their own begin/end block
        // or can be part of the previous filled block if ordered correctly,
        // but kept separate here to preserve layering over lines if desired,
        // though usually lines over fill is better.
        // However, existing code had filled ladders at the end.

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        for (Ladder ladder : maze.getLadders().values()) {
            shapeRenderer.setColor(Color.BROWN);
            float ladderX = mazeStartX + (ladder.getPosition().x * cellSize) - (cellSize * 0.2f);
            float ladderY = mazeStartY + (ladder.getPosition().y * cellSize) - (cellSize * 0.2f);
            shapeRenderer.rect(ladderX, ladderY, cellSize * 0.4f, cellSize * 0.4f);
        }

        // --- START: New code for rendering gates ---
        for (Gate gate : maze.getGates().values()) {
            shapeRenderer.setColor(Color.CYAN);
            float gateX = mazeStartX + (gate.getPosition().x * cellSize) - (cellSize * 0.25f);
            float gateY = mazeStartY + (gate.getPosition().y * cellSize) - (cellSize * 0.25f);
            shapeRenderer.rect(gateX, gateY, cellSize * 0.5f, cellSize * 0.5f);
        }
        // --- END: New code for rendering gates ---

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

                GridPoint2 currentTile = new GridPoint2(x, y);
                Object obj = maze.getGameObjectAt(x, y);

                if (obj instanceof Door) {
                    midWall.append("D ");
                } else if (maze.getGates().containsKey(currentTile)) { // This is the new check for Gates
                    midWall.append("G ");
                } else if (maze.getMonsters().containsKey(currentTile)) {
                    midWall.append("M ");
                } else if (maze.getItems().containsKey(currentTile)) {
                    midWall.append("I ");
                } else if (maze.getLadders().containsKey(currentTile)) {
                    midWall.append("L ");
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

    /**
     * Prints the player's 'memory' of the maze to the console.
     * Unseen tiles are drawn as black blocks or dots.
     * Seen tiles are drawn with their actual wall/object characters.
     */
    public static void printExplorationToConsole(Maze maze) {
        System.out.println("[ExplorationDebug] --- Exploration State ---");
        int height = maze.getHeight();
        int width = maze.getWidth();
        byte[][] memory = maze.getExplorationState();
        int[][] wallData = maze.getWallData();

        for (int y = height - 1; y >= 0; y--) {
            StringBuilder rowBuilder = new StringBuilder();
            rowBuilder.append(String.format("%2d | ", y)); // Row number

            for (int x = 0; x < width; x++) {
                // CHECK VISIBILITY
                if (!maze.isVisited(x, y)) {
                    // Draw Fog of War
                    rowBuilder.append("░░ ");
                } else {
                    // Draw Revealed Tile
                    GridPoint2 pos = new GridPoint2(x, y);
                    Object obj = maze.getGameObjectAt(x, y);

                    if (obj instanceof com.bpm.minotaur.gamedata.Door) {
                        rowBuilder.append("[] "); // Door
                    } else if (maze.getGates().containsKey(pos)) {
                        rowBuilder.append("<> "); // Gate
                    } else {
                        // Check Walls
                        boolean hasWest = (wallData[y][x] & 0b00000001) != 0;
                        boolean hasSouth = (wallData[y][x] & 0b00010000) != 0;

                        // Simple ASCII representation
                        if (hasWest && hasSouth) rowBuilder.append("L. ");
                        else if (hasWest) rowBuilder.append("|. ");
                        else if (hasSouth) rowBuilder.append("_. ");
                        else rowBuilder.append(".. "); // Open Floor
                    }
                }
            }
            System.out.println(rowBuilder.toString());
        }
        System.out.println("[ExplorationDebug] ---------------------------");
    }

}
