package com.bpm.minotaur.gamedata;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.monster.Monster;
import com.bpm.minotaur.rendering.RetroTheme;

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
    private final Map<GridPoint2, Gate> gates = new HashMap<>();

    private final Map<GridPoint2, Scenery> scenery = new HashMap<>();

    private final Map<GridPoint2, Float> bloodMap = new HashMap<>();

    private RetroTheme.Theme theme;



    public Maze(int level, int[][] wallData) {
        this.level = level;
        this.wallData = wallData;
    }

    // Add these methods to the Maze class
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
        // Cap blood intensity at 1.0
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
            return 0b11111111; // Out of bounds is a solid wall
        }
        return wallData[y][x];
    }

    public Object getGameObjectAt(int x, int y) {
        // Create the position key once
        GridPoint2 pos = new GridPoint2(x, y);

        // First, check for a Door object
        Object obj = gameObjects.get(pos);
        if (obj != null) {
            return obj; // Found a Door
        }

        // If no Door, check for a Gate
        return gates.get(pos); // Returns the Gate or null
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

    /**
     * [NEW] Gets the visual and gameplay theme for this maze chunk.
     * @return The Theme object. Returns STANDARD_THEME if no theme is set.
     */
    public RetroTheme.Theme getTheme() {
        // Return a safe default if the theme is null to prevent render crashes
        return (this.theme != null) ? this.theme : RetroTheme.STANDARD_THEME;
    }

    public boolean isWallBlocking(int x, int y, Direction direction) {
        int wallMask = direction.getWallMask();
        int doorMask = wallMask << 1; // Door masks are bit-shifted versions of wall masks
        int currentCellData = getWallDataAt(x, y);

        // Check for a solid wall on the edge of the current tile
        if ((currentCellData & wallMask) != 0) {
            return true;
        }

        // Check for a door on the edge of the current tile
        if ((currentCellData & doorMask) != 0) {
            // A door exists. We need to find the actual Door object to check its state.
            // The door object is located on the tile it was placed on.
            int nextX = x + (int)direction.getVector().x;
            int nextY = y + (int)direction.getVector().y;

            Object obj = getGameObjectAt(x, y); // Check current tile for a door object
            if (obj instanceof Door) {
                // A door is blocking if it is not open.
                return ((Door) obj).getState() != Door.DoorState.OPEN;
            }

            obj = getGameObjectAt(nextX, nextY); // Check next tile for a door object
            if (obj instanceof Door) {
                // A door is blocking if it is not open.
                return ((Door) obj).getState() != Door.DoorState.OPEN;
            }

            // If a door bitmask was found but no Door object, treat it as a wall for safety.
            return true;
        }

        // No wall or closed door was found, so movement is not blocked.
        return false;
    }

    /**
     * [NEW] Checks if a specific tile is passable for movement.
     * This is used for seamless transitions to check the target tile.
     * @param x The x-coordinate of the tile.
     * @param y The y-coordinate of the tile.
     * @return True if the tile is passable, false otherwise.
     */
    public boolean isPassable(int x, int y) {
        // Check bounds
        if (x < 0 || x >= wallData[0].length || y < 0 || y >= wallData.length) {
           // Gdx.app.log("Maze_isPassable", "CHECK (" + x + "," + y + ") -> FAIL (Out of Bounds)");
            return false; // Out of bounds is not passable
        }

        // Check for wall
        // --- THIS IS THE FIX: Removed call to isImpassable() ---
        if (wallData[y][x] == 1) {
           // Gdx.app.log("Maze_isPassable", "CHECK (" + x + "," + y + ") -> FAIL (Wall value: " + wallData[y][x] + ")");
            return false;
        }

        // Check for closed doors or gates
        Object obj = getGameObjectAt(x, y);
        if (obj instanceof Door && ((Door) obj).getState() != Door.DoorState.OPEN) {
           // Gdx.app.log("Maze_isPassable", "CHECK (" + x + "," + y + ") -> FAIL (Closed Door)");
            return false;
        }
        if (obj instanceof Gate && ((Gate) obj).getState() != Gate.GateState.OPEN) {
           // Gdx.app.log("Maze_isPassable", "CHECK (" + x + "," + y + ") -> FAIL (Closed Gate)");
            return false;
        }

        // All checks passed, tile is passable
        //Gdx.app.log("Maze_isPassable", "CHECK (" + x + "," + y + ") -> SUCCESS");
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

    // --- NEW: Scenery getters/setters ---
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
    }
}
