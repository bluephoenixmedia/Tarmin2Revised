// Path: core/src/main/java/com/bpm/minotaur/managers/SpawnManager.java
package com.bpm.minotaur.managers;

import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.gamedata.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

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
                if (layout[maze.getHeight() - 1 - y].charAt(x) == '.') {
                    validSpawnPoints.add(new GridPoint2(x, y));
                }
            }
        }
        Collections.shuffle(validSpawnPoints, random);
    }

    public void spawnEntities() {
        // More controlled spawning based on a "budget" system
        int monsterBudget = 3 + (level / 3) + difficulty.ordinal();
        int itemBudget = 5 + (level / 4) - difficulty.ordinal();

        spawnMonsters(monsterBudget);
        spawnItems(itemBudget);
    }

    private void spawnMonsters(int budget) {
        List<SpawnData.MonsterSpawnInfo> spawnPool = new ArrayList<>();
        spawnPool.addAll(SpawnData.BAD_MONSTERS);
        spawnPool.addAll(SpawnData.NASTY_MONSTERS);
        spawnPool.addAll(SpawnData.HORRIBLE_MONSTERS);

        List<Monster.MonsterType> availableMonsters = spawnPool.stream()
            .filter(info -> level >= info.minLevel() && level <= info.maxLevel())
            .map(info -> info.type())
            .collect(Collectors.toList());

        if (availableMonsters.isEmpty()) return;

        for (int i = 0; i < budget; i++) {
            GridPoint2 spawnPoint = getEmptySpawnPoint();
            if (spawnPoint == null) break;

            Monster.MonsterType type = availableMonsters.get(random.nextInt(availableMonsters.size()));
            Monster monster = new Monster(type, spawnPoint.x, spawnPoint.y);
            monster.scaleStats(level);
            maze.addMonster(monster);
        }
    }

    private void spawnItems(int budget) {
        for (int i = 0; i < budget; i++) {
            GridPoint2 spawnPoint = getEmptySpawnPoint();
            if (spawnPoint == null) break;

            if (random.nextFloat() < 0.25f) { // 25% chance for a container
                spawnContainer(spawnPoint);
            } else {
                spawnRegularItem(spawnPoint);
            }
        }
    }

    private void spawnRegularItem(GridPoint2 spawnPoint) {
        List<SpawnData.ItemSpawnInfo> spawnPool = new ArrayList<>();
        spawnPool.addAll(SpawnData.TIER1_ITEMS);
        spawnPool.addAll(SpawnData.TIER2_ITEMS);
        spawnPool.addAll(SpawnData.TIER3_ITEMS);

        List<Item.ItemType> availableItems = spawnPool.stream()
            .filter(info -> level >= info.minLevel() && level <= info.maxLevel())
            .map(info -> info.type())
            .collect(Collectors.toList());

        if (availableItems.isEmpty()) return;

        Item.ItemType type = availableItems.get(random.nextInt(availableItems.size()));
        maze.addItem(new Item(type, spawnPoint.x, spawnPoint.y));
    }

    private void spawnContainer(GridPoint2 spawnPoint) {
        List<SpawnData.ItemSpawnInfo> spawnPool = new ArrayList<>();
        spawnPool.addAll(SpawnData.TIER1_CONTAINERS);
        spawnPool.addAll(SpawnData.TIER2_CONTAINERS);
        spawnPool.addAll(SpawnData.TIER3_CONTAINERS);

        List<Item.ItemType> availableContainers = spawnPool.stream()
            .filter(info -> level >= info.minLevel() && level <= info.maxLevel())
            .map(info -> info.type())
            .collect(Collectors.toList());

        if (availableContainers.isEmpty()) return;

        Item.ItemType type = availableContainers.get(random.nextInt(availableContainers.size()));
        Item container = new Item(type, spawnPoint.x, spawnPoint.y);

        // Add a random treasure to the container
        SpawnData.TreasureSpawnInfo treasureInfo = SpawnData.TREASURES.get(random.nextInt(SpawnData.TREASURES.size()));
        Item treasure = new Item(treasureInfo.type(), 0, 0);
        treasure.setValue(treasureInfo.baseValue() + (level * treasureInfo.levelModifier()));
        container.addItem(treasure);

        maze.addItem(container);
    }

    private GridPoint2 getEmptySpawnPoint() {
        if (validSpawnPoints.isEmpty()) {
            return null;
        }
        return validSpawnPoints.remove(0);
    }
}
