package com.bpm.minotaur.gamedata;

import com.badlogic.gdx.math.GridPoint2;
import java.util.HashMap;
import java.util.Map;

public class Maze {
    public static final int MAZE_WIDTH = 20;
    public static final int MAZE_HEIGHT = 20;

    private final int level;
    private final int[][] wallData;
    private final Map<GridPoint2, Object> gameObjects = new HashMap<>();
    private final Map<GridPoint2, Item> items = new HashMap<>();

    public Maze(int level, int[][] wallData) {
        this.level = level;
        this.wallData = wallData;
    }

    public int getWidth() {
        if (wallData == null || wallData.length == 0) return 0;
        return wallData[0].length;
    }

    public int getHeight() {
        if (wallData == null) return 0;
        return wallData.length;
    }

    public int[][] getWallData() {
        return wallData;
    }

    public int getWallDataAt(int x, int y) {
        if (x < 0 || x >= getWidth() || y < 0 || y >= getHeight()) {
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

    public Map<GridPoint2, Item> getItems() {
        return items;
    }

    public void addItem(Item item) {
        items.put(new GridPoint2((int)item.getPosition().x, (int)item.getPosition().y), item);
    }


    public boolean isWallBlocking(int x, int y, Direction direction) {
        int wallMask = direction.getWallMask();
        int currentCellData = getWallDataAt(x, y);

        if ((currentCellData & wallMask) != 0) {
            Object obj = getGameObjectAt(x, y);
            if (obj instanceof Door) {
                return ((Door) obj).getState() != Door.DoorState.OPEN;
            }
            return true;
        }

        return false;
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

