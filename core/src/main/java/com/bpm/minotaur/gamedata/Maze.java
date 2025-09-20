package com.bpm.minotaur.gamedata;

import com.badlogic.gdx.math.GridPoint2;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Maze {
    public static final int MAZE_WIDTH = 20;
    public static final int MAZE_HEIGHT = 20;

    private final int level;
    private final int[][] wallData;
    private final Map<GridPoint2, Object> gameObjects = new HashMap<>();
    private final Map<GridPoint2, Item> items = new HashMap<>();
    private final Map<GridPoint2, Monster> monsters = new HashMap<>();
    private final Map<GridPoint2, Ladder> ladders = new HashMap<>();
    private final List<Projectile> projectiles = new ArrayList<>();


    public Maze(int level, int[][] wallData) {
        this.level = level;
        this.wallData = wallData;
    }

    public int getLevel() {
        return level;
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

    public Map<GridPoint2, Monster> getMonsters() {
        return monsters;
    }

    public void addMonster(Monster monster) {
        monsters.put(new GridPoint2((int)monster.getPosition().x, (int)monster.getPosition().y), monster);
    }

    public Map<GridPoint2, Ladder> getLadders() {
        return ladders;
    }

    public void addLadder(Ladder ladder) {
        ladders.put(new GridPoint2((int)ladder.getPosition().x, (int)ladder.getPosition().y), ladder);
    }

    public List<Projectile> getProjectiles() {
        return projectiles;
    }

    public void addProjectile(Projectile projectile) {
        projectiles.add(projectile);
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
        projectiles.removeIf(projectile -> {
            projectile.update(delta);
            return !projectile.isAlive();
        });
    }
}
