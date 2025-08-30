package com.bpm.minotaur.gamedata;

import com.badlogic.gdx.math.GridPoint2;
import java.util.HashMap;
import java.util.Map;

public class Maze {
    public static final int MAZE_WIDTH = 31;
    public static final int MAZE_HEIGHT = 31;

    private final int level;
    private final int[][] wallData;
    private final Map<GridPoint2, Object> gameObjects = new HashMap<>();

    public Maze(int level, int[][] wallData) {
        this.level = level;
        this.wallData = wallData;
    }

    public int getWidth() {
        return MAZE_WIDTH;
    }

    public int getHeight() {
        return MAZE_HEIGHT;
    }

    public int[][] getWallData() {
        return wallData;
    }

    public int getWallDataAt(int x, int y) {
        if (x < 0 || x >= MAZE_WIDTH || y < 0 || y >= MAZE_HEIGHT) {
            return 0b11111111; // Out of bounds is a solid wall
        }
        return wallData[y][x];
    }

    public Object getGameObjectAt(int x, int y) {
        return gameObjects.get(new GridPoint2(x, y));
    }

    public void addGameObject(Object object, int x, int y) {
        gameObjects.put(new GridPoint2(x, y), object);
    }

    public boolean isWallBlocking(int x, int y, Direction direction) {
        if (x < 0 || x >= MAZE_WIDTH || y < 0 || y >= MAZE_HEIGHT) {
            return true; // Out of bounds is always blocking
        }
        // Check for an object first
        Object obj = getGameObjectAt(x, y);
        if (obj instanceof Door) {
            // Doors only block if they are closed.
            return ((Door) obj).getState() == Door.DoorState.CLOSED;
        }

        // Check for a solid wall in the bitmask
        return (wallData[y][x] & direction.getWallMask()) != 0;
    }

    public void openDoorAt(int x, int y) {
        Object obj = getGameObjectAt(x, y);
        if (obj instanceof Door) {
            Door door = (Door) obj;
            if (door.getState() == Door.DoorState.CLOSED) {
                door.startOpening();
            }
        }
    }

    public void update(float delta) {
        for (Object object : gameObjects.values()) {
            if (object instanceof Door) {
                ((Door) object).update(delta);
            }
        }
    }
}

