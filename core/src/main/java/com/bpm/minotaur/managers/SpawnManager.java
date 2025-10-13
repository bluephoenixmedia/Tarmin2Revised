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
            MonsterColor color = getRandomMonsterColor(type);

            Monster monster = new Monster(type, spawnPoint.x, spawnPoint.y, color);
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
        ItemColor color = getRandomItemColor(type);

        maze.addItem(new Item(type, spawnPoint.x, spawnPoint.y, color));
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

        // Determine container color based on level
        ItemColor containerColor = getContainerColorForLevel();
        Item container = new Item(type, spawnPoint.x, spawnPoint.y, containerColor);

        // Add a random treasure to the container
        SpawnData.TreasureSpawnInfo treasureInfo = SpawnData.TREASURES.get(random.nextInt(SpawnData.TREASURES.size()));
        Item treasure = new Item(treasureInfo.type(), 0, 0, ItemColor.YELLOW);
        treasure.setValue(treasureInfo.baseValue() + (level * treasureInfo.levelModifier()));
        container.addItem(treasure);

        maze.addItem(container);

        // Spawn a corresponding key if the container is locked
        if (container.isLocked()) {
            spawnKey(containerColor);
        }
    }
    private void spawnKey(ItemColor containerColor) {
        GridPoint2 spawnPoint = getEmptySpawnPoint();
        if (spawnPoint == null) return; // No space for the key

        Item key = new Item(Item.ItemType.KEY, spawnPoint.x, spawnPoint.y, containerColor);
        maze.addItem(key);
    }

    private ItemColor getContainerColorForLevel() {
        if (level < 5) {
            return ItemColor.CONTAINER_TAN;
        } else if (level < 12) {
            // 70% chance for Tan, 30% for Orange
            return random.nextFloat() < 0.7f ? ItemColor.CONTAINER_TAN : ItemColor.CONTAINER_ORANGE;
        } else {
            // 40% Tan, 40% Orange, 20% Blue
            float chance = random.nextFloat();
            if (chance < 0.4f) {
                return ItemColor.CONTAINER_TAN;
            } else if (chance < 0.8f) {
                return ItemColor.CONTAINER_ORANGE;
            } else {
                return ItemColor.CONTAINER_BLUE;
            }
        }
    }

    private MonsterColor getRandomMonsterColor(Monster.MonsterType type) {
        List<MonsterColor> possibleColors = new ArrayList<>();

        boolean tier1 = level < 5;
        boolean tier2 = level >= 3 && level < 10;
        boolean tier3 = level >= 8;

        switch (type) {
            case GIANT_ANT:
                if (tier1) possibleColors.add(MonsterColor.BLUE);
                if (tier2) possibleColors.add(MonsterColor.PINK);
                if (tier3) possibleColors.add(MonsterColor.PURPLE);
                break;
            case GIANT_SCORPION:
                if (tier1) possibleColors.add(MonsterColor.BLUE);
                if (tier2) possibleColors.add(MonsterColor.PINK);
                if (tier3) possibleColors.add(MonsterColor.PURPLE);
                break;
            case GIANT_SNAKE:
                if (tier1) possibleColors.add(MonsterColor.BLUE);
                if (tier2) possibleColors.add(MonsterColor.PINK);
                if (tier3) possibleColors.add(MonsterColor.PURPLE);
                break;
            case ALLIGATOR:
                if (tier1) possibleColors.add(MonsterColor.BLUE);
                if (tier2) possibleColors.add(MonsterColor.PINK);
                if (tier3) possibleColors.add(MonsterColor.PURPLE);
                break;
            case DRAGON:
                if (tier1) possibleColors.add(MonsterColor.BLUE);
                if (tier2) possibleColors.add(MonsterColor.PINK);
                if (tier3) possibleColors.add(MonsterColor.PURPLE);
                break;
            case WRAITH:
                if (tier1) possibleColors.add(MonsterColor.BLUE);
                if (tier2) possibleColors.add(MonsterColor.PINK);
                if (tier3) possibleColors.add(MonsterColor.PURPLE);
                break;
            case DWARF:
                if (tier1) possibleColors.add(MonsterColor.YELLOW);
                if (tier2) possibleColors.add(MonsterColor.TAN);
                if (tier3) possibleColors.add(MonsterColor.ORANGE);
                break;
            case GIANT:
                if (tier1) possibleColors.add(MonsterColor.YELLOW);
                if (tier2) possibleColors.add(MonsterColor.TAN);
                if (tier3) possibleColors.add(MonsterColor.ORANGE);
                break;
            case GHOUL:
                if (tier1) possibleColors.add(MonsterColor.WHITE);
                if (tier2) possibleColors.add(MonsterColor.GRAY);
                if (tier3) possibleColors.add(MonsterColor.ORANGE);
                break;

            case SKELETON:
                if (tier1) possibleColors.add(MonsterColor.WHITE);
                if (tier2) possibleColors.add(MonsterColor.GRAY);
                if (tier3) possibleColors.add(MonsterColor.ORANGE);
                break;

            case CLOAKED_SKELETON:
                if (tier1) possibleColors.add(MonsterColor.WHITE);
                if (tier2) possibleColors.add(MonsterColor.GRAY);
                if (tier3) possibleColors.add(MonsterColor.ORANGE);
                break;
            default:
                possibleColors.add(MonsterColor.TAN);
        }
        return possibleColors.get(random.nextInt(possibleColors.size()));
    }

    private ItemColor getRandomItemColor(Item.ItemType type) {
        List<ItemColor> possibleColors = new ArrayList<>();
        Item.ItemCategory category = new Item(type, 0, 0, ItemColor.TAN).getCategory(); // A bit hacky, but works

        boolean tier1 = level < 8;
        boolean tier2 = level >= 5 && level < 15;
        boolean tier3 = level >= 12;

        switch (category) {
            case WAR_WEAPON:
                if (tier1) {
                    possibleColors.add(ItemColor.TAN);
                    possibleColors.add(ItemColor.BLUE_STEEL);
                }
                if (tier2) {
                    possibleColors.add(ItemColor.ORANGE);
                    possibleColors.add(ItemColor.GRAY);
                }
                if (tier3) {
                    possibleColors.add(ItemColor.YELLOW);
                    possibleColors.add(ItemColor.WHITE);
                }
                break;
            case ARMOR:
                if (tier1) {
                    possibleColors.add(ItemColor.TAN);
                    possibleColors.add(ItemColor.BLUE_STEEL);
                }
                if (tier2) {
                    possibleColors.add(ItemColor.ORANGE);
                    possibleColors.add(ItemColor.GRAY);
                }
                if (tier3) {
                    possibleColors.add(ItemColor.YELLOW);
                    possibleColors.add(ItemColor.WHITE);
                }
                break;
            case SPIRITUAL_WEAPON:
                if (tier1) {
                    possibleColors.add(ItemColor.BLUE);
                    possibleColors.add(ItemColor.GRAY_SPIRITUAL);
                }
                if (tier2) {
                    possibleColors.add(ItemColor.WHITE_SPIRITUAL);
                    possibleColors.add(ItemColor.PINK);
                }
                if (tier3) {
                    possibleColors.add(ItemColor.RED);
                    possibleColors.add(ItemColor.PURPLE);
                }
                break;
            case RING:
                if (tier1) {
                    possibleColors.add(ItemColor.BLUE);
                    possibleColors.add(ItemColor.GRAY_SPIRITUAL);
                }
                if (tier2) {
                    possibleColors.add(ItemColor.WHITE_SPIRITUAL);
                    possibleColors.add(ItemColor.PINK);
                }
                if (tier3) {
                    possibleColors.add(ItemColor.RED);
                    possibleColors.add(ItemColor.PURPLE);
                }
                break;
            default:
                return ItemColor.TAN; // Default for other types
        }

        return possibleColors.get(random.nextInt(possibleColors.size()));
    }

    private GridPoint2 getEmptySpawnPoint() {
        if (validSpawnPoints.isEmpty()) {
            return null;
        }
        return validSpawnPoints.remove(0);
    }
}
