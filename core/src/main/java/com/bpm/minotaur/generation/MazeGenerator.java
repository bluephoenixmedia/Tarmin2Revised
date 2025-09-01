package com.bpm.minotaur.generation;

import com.badlogic.gdx.math.Rectangle;
import com.bpm.minotaur.gamedata.Door;
import com.bpm.minotaur.gamedata.Maze;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * A modern, robust maze generator inspired by classic roguelike algorithms (like NetHack).
 * 1. Places rooms.
 * 2. Connects rooms with L-shaped tunnels to guarantee connectivity.
 * 3. Fills remaining space with a perfect maze algorithm.
 * 4. Places doors at room entrances.
 */
public class MazeGenerator {
    private final Random random = new Random();
    private int[][] map; // Using a simple int map: 0 for floor, 1 for wall, 2 for door
    private List<Rectangle> rooms;

    private static final int MIN_ROOM_SIZE = 3;
    private static final int MAX_ROOM_SIZE = 7;
    private static final int NUM_ROOMS_ATTEMPTS = 30;

    public MazeGenerator() {}

    public Maze generate(int level) {
        map = new int[Maze.MAZE_HEIGHT][Maze.MAZE_WIDTH];
        rooms = new ArrayList<>();

        for (int y = 0; y < Maze.MAZE_HEIGHT; y++) {
            for (int x = 0; x < Maze.MAZE_WIDTH; x++) {
                map[y][x] = 1; // 1 represents a wall/rock
            }
        }

        generateRooms();

        if (rooms.size() > 1) {
            for (int i = 0; i < rooms.size() - 1; i++) {
                carveTunnel(rooms.get(i), rooms.get(i + 1));
            }
        }

        for (int y = 1; y < Maze.MAZE_HEIGHT; y += 2) {
            for (int x = 1; x < Maze.MAZE_WIDTH; x += 2) {
                if (map[y][x] == 1) {
                    carvePassagesFrom(x, y);
                }
            }
        }

        placeDoors();

        int[][] finalWallData = finalizeWalls();

        Maze newMaze = new Maze(level, finalWallData);
        populateDoorObjects(newMaze);

        return newMaze;
    }

    private void generateRooms() {
        for (int i = 0; i < NUM_ROOMS_ATTEMPTS; i++) {
            int width = random.nextInt((MAX_ROOM_SIZE - MIN_ROOM_SIZE) / 2 + 1) * 2 + 1;
            int height = random.nextInt((MAX_ROOM_SIZE - MIN_ROOM_SIZE) / 2 + 1) * 2 + 1;
            int x = random.nextInt((Maze.MAZE_WIDTH - width - 2) / 2) * 2 + 1;
            int y = random.nextInt((Maze.MAZE_HEIGHT - height - 2) / 2) * 2 + 1;

            Rectangle newRoom = new Rectangle(x, y, width, height);
            boolean overlaps = false;
            for (Rectangle otherRoom : rooms) {
                if (newRoom.overlaps(otherRoom)) {
                    overlaps = true;
                    break;
                }
            }
            if (!overlaps) {
                rooms.add(newRoom);
                carveRoom(newRoom);
            }
        }
        Collections.sort(rooms, (a, b) -> (int)(a.x - b.x));
    }

    private void carveRoom(Rectangle room) {
        for (int y = (int)room.y; y < room.y + room.height; y++) {
            for (int x = (int)room.x; x < room.x + room.width; x++) {
                map[y][x] = 0;
            }
        }
    }

    private void carveTunnel(Rectangle room1, Rectangle room2) {
        int startX = (int)(room1.x + room1.width / 2);
        int startY = (int)(room1.y + room1.height / 2);
        int endX = (int)(room2.x + room2.width / 2);
        int endY = (int)(room2.y + room2.height / 2);

        if (random.nextBoolean()) {
            for (int x = Math.min(startX, endX); x <= Math.max(startX, endX); x++) map[startY][x] = 0;
            for (int y = Math.min(startY, endY); y <= Math.max(startY, endY); y++) map[y][endX] = 0;
        } else {
            for (int y = Math.min(startY, endY); y <= Math.max(startY, endY); y++) map[y][startX] = 0;
            for (int x = Math.min(startX, endX); x <= Math.max(startX, endX); x++) map[endY][x] = 0;
        }
    }

    private void carvePassagesFrom(int cx, int cy) {
        Integer[] directions = {0, 1, 2, 3};
        List<Integer> dirs = new ArrayList<>();
        for(Integer i : directions) dirs.add(i);
        Collections.shuffle(dirs, random);

        map[cy][cx] = 0;

        for (int direction : dirs) {
            int nx = cx, ny = cy, betweenX = cx, betweenY = cy;

            if (direction == 0) { ny += 2; betweenY++; }
            if (direction == 1) { ny -= 2; betweenY--; }
            if (direction == 2) { nx += 2; betweenX++; }
            if (direction == 3) { nx -= 2; betweenX--; }

            if (isok(nx, ny) && map[ny][nx] == 1) {
                map[betweenY][betweenX] = 0;
                carvePassagesFrom(nx, ny);
            }
        }
    }

    private void placeDoors() {
        for (Rectangle room : rooms) {
            List<int[]> connectors = new ArrayList<>();
            for (int x = (int)room.x; x < room.x + room.width; x++) {
                if (isConnector(x, (int)room.y - 1, room)) connectors.add(new int[]{x, (int)room.y - 1});
                if (isConnector(x, (int)room.y + (int)room.height, room)) connectors.add(new int[]{x, (int)room.y + (int)room.height});
            }
            for (int y = (int)room.y; y < room.y + room.height; y++) {
                if (isConnector((int)room.x - 1, y, room)) connectors.add(new int[]{(int)room.x - 1, y});
                if (isConnector((int)room.x + (int)room.width, y, room)) connectors.add(new int[]{(int)room.x + (int)room.width, y});
            }

            if (!connectors.isEmpty()) {
                int[] doorLocation = connectors.get(random.nextInt(connectors.size()));
                map[doorLocation[1]][doorLocation[0]] = 2; // Use 2 as a door marker
            }
        }
    }

    private boolean isConnector(int x, int y, Rectangle room) {
        if (!isok(x, y) || map[y][x] != 1) return false;
        // Check for room on one side and corridor on the other
        boolean roomNorth = isok(x, y+1) && isRoom(x, y+1, room);
        boolean roomSouth = isok(x, y-1) && isRoom(x, y-1, room);
        boolean roomEast = isok(x+1, y) && isRoom(x+1, y, room);
        boolean roomWest = isok(x-1, y) && isRoom(x-1, y, room);

        boolean corridorNorth = isok(x, y+1) && map[y+1][x] == 0 && !isAnyRoom(x, y+1);
        boolean corridorSouth = isok(x, y-1) && map[y-1][x] == 0 && !isAnyRoom(x, y-1);
        boolean corridorEast = isok(x+1, y) && map[y][x+1] == 0 && !isAnyRoom(x+1, y);
        boolean corridorWest = isok(x-1, y) && map[y][x-1] == 0 && !isAnyRoom(x-1, y);

        return (roomNorth && corridorSouth) || (roomSouth && corridorNorth) || (roomEast && corridorWest) || (roomWest && corridorEast);
    }

    private boolean isRoom(int x, int y, Rectangle room) {
        return x >= room.x && x < room.x + room.width && y >= room.y && y < room.y + room.height;
    }

    private boolean isAnyRoom(int x, int y) {
        for (Rectangle room : rooms) {
            if (isRoom(x, y, room)) return true;
        }
        return false;
    }

    private int[][] finalizeWalls() {
        int[][] finalData = new int[Maze.MAZE_HEIGHT][Maze.MAZE_WIDTH];
        for (int y = 0; y < Maze.MAZE_HEIGHT; y++) {
            for (int x = 0; x < Maze.MAZE_WIDTH; x++) {
                if (map[y][x] == 1) { // Wall
                    finalData[y][x] = 0b01010101; // Solid wall
                } else if (map[y][x] == 2) { // Door
                    int mask = 0;
                    if (isok(x, y + 1) && map[y+1][x] == 0) mask |= 0b10000000;
                    if (isok(x, y - 1) && map[y-1][x] == 0) mask |= 0b00100000;
                    if (isok(x + 1, y) && map[y][x+1] == 0) mask |= 0b00001000;
                    if (isok(x - 1, y) && map[y][x-1] == 0) mask |= 0b00000010;
                    finalData[y][x] = mask;
                } else { // Floor
                    finalData[y][x] = 0;
                }
            }
        }
        // Second pass to connect walls correctly
        for (int y = 0; y < Maze.MAZE_HEIGHT; y++) {
            for (int x = 0; x < Maze.MAZE_WIDTH; x++) {
                if (map[y][x] == 1) {
                    int mask = 0;
                    if (!isok(y + 1, x) || map[y + 1][x] >= 1) mask |= 0b01000000;
                    if (!isok(y, x + 1) || map[y][x + 1] >= 1) mask |= 0b00000100;
                    if (!isok(y - 1, x) || map[y - 1][x] >= 1) mask |= 0b00010000;
                    if (!isok(y, x - 1) || map[y][x - 1] >= 1) mask |= 0b00000001;
                    finalData[y][x] = mask;
                }
            }
        }
        return finalData;
    }

    private void populateDoorObjects(Maze maze) {
        for (int y = 0; y < Maze.MAZE_HEIGHT; y++) {
            for (int x = 0; x < Maze.MAZE_WIDTH; x++) {
                if (map[y][x] == 2) {
                    maze.addGameObject(new Door(), x, y);
                }
            }
        }
    }

    private boolean isok(int x, int y){
        return x >= 0 && x < Maze.MAZE_WIDTH && y >= 0 && y < Maze.MAZE_HEIGHT;
    }

    public int getPlayerStartX() {
        if (rooms.isEmpty()) return 1;
        Rectangle firstRoom = rooms.get(0);
        return (int) (firstRoom.x + firstRoom.width / 2);
    }

    public int getPlayerStartY() {
        if (rooms.isEmpty()) return 1;
        Rectangle firstRoom = rooms.get(0);
        return (int) (firstRoom.y + firstRoom.height / 2);
    }
}

