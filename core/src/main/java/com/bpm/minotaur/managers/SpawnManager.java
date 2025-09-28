// Path: core/src/main/java/com/bpm/minotaur/managers/SpawnManager.java
package com.bpm.minotaur.managers;

import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.gamedata.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class SpawnManager {
    private final Maze maze;
    private final Difficulty difficulty;
    private final int level;
    private final Random random = new Random();
    private final List<GridPoint2> validSpawnPoints = new ArrayList<>();

    public SpawnManager(Maze maze, Difficulty difficulty, int level, String[] layout) {
        this.maze = maze;
        this.difficulty = difficulty;
        this.level = level;
        findValidSpawnPoints(layout);
    }

    private void findValidSpawnPoints(String[] layout) {
        for (int y = 0; y < maze.getHeight(); y++) {
            for (int x = 0; x < maze.getWidth(); x++) {
                // A valid spawn point is a floor tile '.'
                if (layout[maze.getHeight() - 1 - y].charAt(x) == '.') {
                    validSpawnPoints.add(new GridPoint2(x, y));
                }
            }
        }
    }

    public void spawnEntities() {
        // Adjust spawn counts based on difficulty and level
        int monstersToSpawn = 5 + (level / 4) + (difficulty.ordinal());
        int itemsToSpawn = 8 - difficulty.ordinal();
        int containersToSpawn = 2 + (level / 5);

        spawnMonsters(monstersToSpawn);
        spawnItems(itemsToSpawn);
        spawnContainers(containersToSpawn);
    }

    private void spawnMonsters(int count) {
        List<SpawnData.MonsterSpawnInfo> spawnPool = new ArrayList<>();
        spawnPool.addAll(SpawnData.BAD_MONSTERS);
        spawnPool.addAll(SpawnData.NASTY_MONSTERS);
        spawnPool.addAll(SpawnData.HORRIBLE_MONSTERS);

        // Filter the pool to only include monsters that can spawn on the current level.
        List<Monster.MonsterType> availableMonsters = spawnPool.stream()
            .filter(info -> level >= info.minLevel())
            .map(SpawnData.MonsterSpawnInfo::type)
            .collect(Collectors.toList());

        if (availableMonsters.isEmpty()) return;

        for (int i = 0; i < count; i++) {
            GridPoint2 spawnPoint = getEmptySpawnPoint();
            if (spawnPoint == null) break; // No more empty spots

            Monster.MonsterType type = availableMonsters.get(random.nextInt(availableMonsters.size()));
            maze.addMonster(new Monster(type, spawnPoint.x, spawnPoint.y));
        }
    }

    private void spawnItems(int count) {
        List<SpawnData.ItemSpawnInfo> spawnPool = new ArrayList<>();
        spawnPool.addAll(SpawnData.TIER1_ITEMS);
        spawnPool.addAll(SpawnData.TIER2_ITEMS);
        spawnPool.addAll(SpawnData.TIER3_ITEMS);

        List<Item.ItemType> availableItems = spawnPool.stream()
            .filter(info -> level >= info.minLevel())
            .map(SpawnData.ItemSpawnInfo::type)
            .collect(Collectors.toList());

        if (availableItems.isEmpty()) return;

        for (int i = 0; i < count; i++) {
            GridPoint2 spawnPoint = getEmptySpawnPoint();
            if (spawnPoint == null) break;

            Item.ItemType type = availableItems.get(random.nextInt(availableItems.size()));
            maze.addItem(new Item(type, spawnPoint.x, spawnPoint.y));
        }
    }

    private void spawnContainers(int count) {
        List<SpawnData.ItemSpawnInfo> spawnPool = new ArrayList<>();
        spawnPool.addAll(SpawnData.TIER1_CONTAINERS);
        spawnPool.addAll(SpawnData.TIER2_CONTAINERS);
        spawnPool.addAll(SpawnData.TIER3_CONTAINERS);

        List<Item.ItemType> availableContainers = spawnPool.stream()
            .filter(info -> level >= info.minLevel())
            .map(SpawnData.ItemSpawnInfo::type)
            .collect(Collectors.toList());

        if (availableContainers.isEmpty()) return;

        for (int i = 0; i < count; i++) {
            GridPoint2 spawnPoint = getEmptySpawnPoint();
            if (spawnPoint == null) break;

            Item.ItemType type = availableContainers.get(random.nextInt(availableContainers.size()));
            Item container = new Item(type, spawnPoint.x, spawnPoint.y);

            // Add a random treasure to the container
            Item.ItemType treasureType = SpawnData.TREASURES.get(random.nextInt(SpawnData.TREASURES.size()));
            container.addItem(new Item(treasureType, 0, 0));

            maze.addItem(container);
        }
    }

    private GridPoint2 getEmptySpawnPoint() {
        if (validSpawnPoints.isEmpty()) {
            return null;
        }
        int index = random.nextInt(validSpawnPoints.size());
        GridPoint2 point = validSpawnPoints.remove(index);
        return point;
    }
}
