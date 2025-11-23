package com.bpm.minotaur.gamedata;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.gamedata.gore.GoreManager;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.monster.Monster;
import com.bpm.minotaur.rendering.RetroTheme;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
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
    private final Map<GridPoint2, Gate> gates = new HashMap<>();

    private final Map<GridPoint2, Scenery> scenery = new HashMap<>();
    private final Map<GridPoint2, Float> bloodMap = new HashMap<>();

    // --- Debris Only ---
    private final List<CorpsePart> corpses = new ArrayList<>();

    private final GoreManager goreManager; // New Manager
    // Removed: List<BloodSpray> bloodSprays
    // -------------------

    private RetroTheme.Theme theme;

    public Maze(int level, int[][] wallData) {
        this.level = level;
        this.wallData = wallData;
        this.goreManager = new GoreManager(); // Initialize
    }

    public List<CorpsePart> getCorpses() {
        return corpses;
    }
    // Removed: getBloodSprays()

    public void addGate(Gate gate) {
        gates.put(new GridPoint2((int)gate.getPosition().x, (int)gate.getPosition().y), gate);
    }

    public Map<GridPoint2, Gate> getGates() {
        return gates;
    }
    public int getLevel() {
        return level;
    }

    public int getWidth() {
        if (wallData == null || wallData.length == 0) return 0;
        return wallData[0].length;
    }

    public void addBlood(int x, int y, float amount) {
        GridPoint2 pos = new GridPoint2(x, y);
        float current = bloodMap.getOrDefault(pos, 0f);
        bloodMap.put(pos, Math.min(1.0f, current + amount));
    }

    public float getBloodIntensity(int x, int y) {
        return bloodMap.getOrDefault(new GridPoint2(x, y), 0f);
    }

    public Map<GridPoint2, Float> getBloodMap() {
        return bloodMap;
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
            return 0b11111111;
        }
        return wallData[y][x];
    }

    public Object getGameObjectAt(int x, int y) {
        GridPoint2 pos = new GridPoint2(x, y);
        Object obj = gameObjects.get(pos);
        if (obj != null) {
            return obj;
        }
        return gates.get(pos);
    }

    public void addGameObject(Object object, int x, int y) {
        if (object instanceof Door) {
            ((Door) object).setMaze(this);
        }
        gameObjects.put(new GridPoint2(x, y), object);
    }

    public Map<GridPoint2, Object> getGameObjects() {
        return gameObjects;
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

    public void setTheme(RetroTheme.Theme theme) {
        this.theme = theme;
    }

    public RetroTheme.Theme getTheme() {
        return (this.theme != null) ? this.theme : RetroTheme.STANDARD_THEME;
    }

    public boolean isWallBlocking(int x, int y, Direction direction) {
        int wallMask = direction.getWallMask();
        int doorMask = wallMask << 1;
        int currentCellData = getWallDataAt(x, y);

        if ((currentCellData & wallMask) != 0) {
            return true;
        }

        if ((currentCellData & doorMask) != 0) {
            int nextX = x + (int)direction.getVector().x;
            int nextY = y + (int)direction.getVector().y;

            Object obj = getGameObjectAt(x, y);
            if (obj instanceof Door) {
                return ((Door) obj).getState() != Door.DoorState.OPEN;
            }

            obj = getGameObjectAt(nextX, nextY);
            if (obj instanceof Door) {
                return ((Door) obj).getState() != Door.DoorState.OPEN;
            }
            return true;
        }
        return false;
    }

    public boolean isPassable(int x, int y) {
        if (x < 0 || x >= wallData[0].length || y < 0 || y >= wallData.length) {
            return false;
        }

        if (wallData[y][x] == 1) {
            return false;
        }

        Object obj = getGameObjectAt(x, y);
        if (obj instanceof Door && ((Door) obj).getState() != Door.DoorState.OPEN) {
            return false;
        }
        if (obj instanceof Gate && ((Gate) obj).getState() != Gate.GateState.OPEN) {
            return false;
        }

        return true;
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

    public Map<GridPoint2, Scenery> getScenery() {
        return scenery;
    }

    public void addScenery(Scenery s) {
        if (s == null) return;
        GridPoint2 pos = new GridPoint2((int)s.getPosition().x, (int)s.getPosition().y);
        scenery.put(pos, s);
    }

    public void update(float delta) {
        for (Object object : gameObjects.values()) {
            if (object instanceof Door) {
                ((Door) object).update(delta);
            }
        }

        for (Gate gate : gates.values()) {
            gate.update(delta);
        }

        projectiles.removeIf(projectile -> {
            projectile.update(delta);
            return !projectile.isAlive();
        });

        // Update Corpses
        for (CorpsePart part : corpses) {
            part.update(delta);
        }

        goreManager.update(delta, this); // PASS 'this'
        // Removed: Update Blood Sprays
    }
    public GoreManager getGoreManager() { return goreManager; } // Getter

}
