package com.bpm.minotaur.gamedata;

import com.badlogic.gdx.math.GridPoint2;
import java.util.HashMap;
import java.util.Map;

public class Maze {
    public static final int MAZE_WIDTH = 12;
    public static final int MAZE_HEIGHT = 12;

    private final int level;
    private final int[][] wallData;
    private final Map<GridPoint2, Object> gameObjects; // For monsters, items, etc.

    // Bitmask constants for wall detection
    private static final int WALL_TOP = 0b01000000;
    private static final int WALL_RIGHT = 0b00010000;
    private static final int WALL_BOTTOM = 0b00000100;
    private static final int WALL_LEFT = 0b00000001;

    public Maze(int level, int[][] wallData) {
        this.level = level;
        this.wallData = wallData;
        this.gameObjects = new HashMap<>();
    }

    public int getWallData(int x, int y) {
        if (x < 0 || x >= MAZE_WIDTH || y < 0 || y >= MAZE_HEIGHT) {
            return 0; // Out of bounds is considered an open area for logic purposes
        }
        return wallData[y][x];
    }

    public boolean isWall(int x, int y, Direction direction) {
        int mask = getWallData(x, y);
        switch(direction) {
            case NORTH: return (mask & WALL_TOP) != 0;
            case EAST:  return (mask & WALL_RIGHT) != 0;
            case SOUTH: return (mask & WALL_BOTTOM) != 0;
            case WEST:  return (mask & WALL_LEFT) != 0;
        }
        return false;
    }
}

