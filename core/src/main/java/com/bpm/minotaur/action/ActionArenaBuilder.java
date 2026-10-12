package com.bpm.minotaur.action;

import com.badlogic.gdx.math.Vector3;
import com.bpm.minotaur.gamedata.Maze;

import java.util.ArrayList;
import java.util.List;

/**
 * Generates "The Crucible": a dedicated 32x32 stone combat arena designed for high-speed
 * continuous 3D circle-strafing, mob mobbing, wall hazards, and weapon pickups.
 */
public class ActionArenaBuilder {

    public static final int ARENA_SIZE = 32;

    public static class HazardSpike {
        public final Vector3 position;
        public final float damage;

        public HazardSpike(float x, float y, float z, float damage) {
            this.position = new Vector3(x, y, z);
            this.damage = damage;
        }
    }

    public static class ArenaData {
        public Maze maze;
        public final Vector3 playerSpawn;
        public final List<Vector3> monsterSpawnPoints = new ArrayList<>();
        public final List<HazardSpike> spikeHazards = new ArrayList<>();
        public final boolean[][] solidGrid = new boolean[ARENA_SIZE][ARENA_SIZE];

        public ArenaData(Vector3 playerSpawn) {
            this.playerSpawn = playerSpawn;
        }
    }

    /**
     * Builds the Crucible Arena layout.
     * '.' = Open stone floor
     * '#' = Solid stone wall / pillar
     * 'S' = Spike hazard along walls
     */
    public static ArenaData buildCrucibleArena() {
        Vector3 playerSpawn = new Vector3(ARENA_SIZE * 0.5f, 0.65f, -ARENA_SIZE * 0.5f);
        ArenaData data = new ArenaData(playerSpawn);

        char[][] map = new char[ARENA_SIZE][ARENA_SIZE];

        // 1. Initialize with open floor
        for (int y = 0; y < ARENA_SIZE; y++) {
            for (int x = 0; x < ARENA_SIZE; x++) {
                map[y][x] = '.';
            }
        }

        // 2. Perimeter outer walls
        for (int i = 0; i < ARENA_SIZE; i++) {
            map[0][i] = '#';
            map[ARENA_SIZE - 1][i] = '#';
            map[i][0] = '#';
            map[i][ARENA_SIZE - 1] = '#';
        }

        // 3. Four massive corner cover pillars (4x4 blocks at corners for circle-strafing)
        buildPillar(map, 6, 6, 3, 3);
        buildPillar(map, ARENA_SIZE - 9, 6, 3, 3);
        buildPillar(map, 6, ARENA_SIZE - 9, 3, 3);
        buildPillar(map, ARENA_SIZE - 9, ARENA_SIZE - 9, 3, 3);

        // 4. Center blood dais low obstacles (two 2x2 side monoliths)
        buildPillar(map, 11, 15, 2, 2);
        buildPillar(map, 19, 15, 2, 2);

        // 5. Perimeter Wall Spikes (Hazard zones where kicking enemies causes instant splatters)
        for (int x = 3; x < ARENA_SIZE - 3; x += 3) {
            data.spikeHazards.add(new HazardSpike(x + 0.5f, 0.5f, -1.5f, 150f)); // North wall
            data.spikeHazards.add(new HazardSpike(x + 0.5f, 0.5f, -(ARENA_SIZE - 2.5f), 150f)); // South wall
        }
        for (int y = 3; y < ARENA_SIZE - 3; y += 3) {
            data.spikeHazards.add(new HazardSpike(1.5f, 0.5f, -(y + 0.5f), 150f)); // West wall
            data.spikeHazards.add(new HazardSpike(ARENA_SIZE - 2.5f, 0.5f, -(y + 0.5f), 150f)); // East wall
        }

        // 6. Monster spawn pedestals (distributed around arena perimeter)
        data.monsterSpawnPoints.add(new Vector3(4f, 0.65f, -4f));
        data.monsterSpawnPoints.add(new Vector3(ARENA_SIZE - 4f, 0.65f, -4f));
        data.monsterSpawnPoints.add(new Vector3(4f, 0.65f, -(ARENA_SIZE - 4f)));
        data.monsterSpawnPoints.add(new Vector3(ARENA_SIZE - 4f, 0.65f, -(ARENA_SIZE - 4f)));
        data.monsterSpawnPoints.add(new Vector3(ARENA_SIZE * 0.5f, 0.65f, -4f));
        data.monsterSpawnPoints.add(new Vector3(ARENA_SIZE * 0.5f, 0.65f, -(ARENA_SIZE - 4f)));
        data.monsterSpawnPoints.add(new Vector3(4f, 0.65f, -ARENA_SIZE * 0.5f));
        data.monsterSpawnPoints.add(new Vector3(ARENA_SIZE - 4f, 0.65f, -ARENA_SIZE * 0.5f));

        // 7. Apply to solidGrid and Maze
        int[][] wallData = new int[ARENA_SIZE][ARENA_SIZE];
        for (int y = 0; y < ARENA_SIZE; y++) {
            for (int x = 0; x < ARENA_SIZE; x++) {
                boolean solid = (map[y][x] == '#');
                data.solidGrid[y][x] = solid;
                wallData[y][x] = solid ? 1 : 0;
            }
        }

        data.maze = new Maze(1, wallData);

        return data;
    }

    private static void buildPillar(char[][] map, int startX, int startY, int width, int height) {
        for (int dy = 0; dy < height; dy++) {
            for (int dx = 0; dx < width; dx++) {
                int x = startX + dx;
                int y = startY + dy;
                if (x >= 0 && x < ARENA_SIZE && y >= 0 && y < ARENA_SIZE) {
                    map[y][x] = '#';
                }
            }
        }
    }
}
