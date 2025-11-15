// Path: core/src/main/java/com/bpm/minotaur/managers/SpawnManager.java
package com.bpm.minotaur.managers;

import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.gamedata.*;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.ItemColor;
import com.bpm.minotaur.gamedata.item.ItemDataManager;
import com.bpm.minotaur.gamedata.item.ItemModifier;
import com.bpm.minotaur.gamedata.monster.Monster;
import com.bpm.minotaur.gamedata.monster.MonsterColor;
import com.bpm.minotaur.gamedata.monster.MonsterDataManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import com.bpm.minotaur.gamedata.item.ItemCategory; // <-- ADD THIS
import java.util.stream.Stream;

public class SpawnManager {
    private final Maze maze;
    private final Difficulty difficulty;
    private final int level;
    private final Random random = new Random();
    private final List<GridPoint2> validSpawnPoints = new ArrayList<>();
    private final MonsterDataManager dataManager;
    private final ItemDataManager itemDataManager; // <-- ADD THIS
    private final AssetManager assetManager;


    public static boolean DEBUG_FORCE_MODIFIERS = true; // Toggle to force spawn

    private static final float BASE_MODIFIER_CHANCE = 0.15f; // 15% base chance for an item to be modified
    private static final float COLOR_MULTIPLIER_BONUS = 0.1f; // Each 1.0 of multiplier adds 10% chance
    private static final float SECOND_MODIFIER_CHANCE = 0.25f; // 25% chance for a second modifier
    private static final float THIRD_MODIFIER_CHANCE = 0.10f; // 10% chance for a third

    public SpawnManager(MonsterDataManager dataManager, ItemDataManager itemDataManager, AssetManager assetManager, Maze maze, Difficulty difficulty, int level, String[] layout) {
        this.maze = maze;
        this.difficulty = difficulty;
        this.level = level;
        this.dataManager = dataManager;
        this.itemDataManager = itemDataManager; // <-- ADD THIS
        this.assetManager = assetManager;
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

            Monster monster = new Monster(type, spawnPoint.x, spawnPoint.y, color, dataManager, assetManager);
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

        Item item = new Item(type, spawnPoint.x, spawnPoint.y, color, itemDataManager, assetManager);


        attemptToModifyItem(item, color); // NEW CALL
        maze.addItem(item);
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

        Item container = new Item(type, spawnPoint.x, spawnPoint.y, containerColor, itemDataManager, assetManager);

        attemptToModifyItem(container, containerColor); // NEW CALL

        // Add a random treasure to the container
        //TODO make containers able to have any item or weapon type
        SpawnData.TreasureSpawnInfo treasureInfo = SpawnData.TREASURES.get(random.nextInt(SpawnData.TREASURES.size()));

        Item treasure = new Item(treasureInfo.type(), 0, 0, ItemColor.YELLOW, itemDataManager, assetManager);


        // Use a default color for treasure
       // treasure.setValue(treasureInfo.baseValue() + (level * treasureInfo.levelModifier()));
        attemptToModifyItem(treasure, ItemColor.YELLOW); // NEW CALL (Treasures can be modified too!)

        container.getContents().add(treasure);

        maze.addItem(container);

        // Spawn a corresponding key if the container is locked
        if (container.isLocked()) {
            spawnKey(containerColor);
        }
    }
    private void spawnKey(ItemColor containerColor) {
        GridPoint2 spawnPoint = getEmptySpawnPoint();
        if (spawnPoint == null) return; // No space for the key

        Item key = new Item(Item.ItemType.KEY, spawnPoint.x, spawnPoint.y, containerColor, itemDataManager, assetManager);

        attemptToModifyItem(key, containerColor); // NEW CALL (Keys can be magical... maybe "Skeleton Key"?)
        maze.addItem(key);
    }

    // --- NEW METHOD ---
    /**
     * Attempts to roll for and add modifiers to a newly spawned item.
     * @param item The item to modify.
     * @param color The item's color, used to calculate spawn chance.
     */
    private void attemptToModifyItem(Item item, ItemColor color) {
        // Calculate the chance for this item to be modified
        // e.g., Base 15% + (Multiplier 1.2 - 1.0) * 10% = 15% + 2% = 17%
        float spawnChance = BASE_MODIFIER_CHANCE + ((color.getMultiplier() - 1.0f) * COLOR_MULTIPLIER_BONUS);

        if (!DEBUG_FORCE_MODIFIERS && random.nextFloat() > spawnChance) {
            return; // Roll failed, no modifiers
        }

        // --- Roll 1: Success! Add one modifier ---
        addRandomModifier(item);

        // --- Roll 2: Diminishing returns ---
        if (random.nextFloat() < SECOND_MODIFIER_CHANCE) {
            addRandomModifier(item);
        }

        // --- Roll 3: Diminishing returns ---
        if (random.nextFloat() < THIRD_MODIFIER_CHANCE) {
            addRandomModifier(item);
        }
    }

    /**
     * Finds a valid modifier from the LootTable, rolls its value, and adds it to the item.
     * @param item The item to modify.
     */
    private void addRandomModifier(Item item) {

        ItemCategory category = item.getCategory();

        Stream<LootTable.ModInfo> modStream = LootTable.MODIFIER_POOL.stream()
            .filter(mod -> mod.category == category); // Matches category (e.g., ARMOR, WAR_WEAPON)

        if (!DEBUG_FORCE_MODIFIERS) {
            // If debug is not on, filter by level normally
            modStream = modStream.filter(mod -> level >= mod.minLevel && level <= mod.maxLevel); // Matches level (TIER)
        } else {
            // If debug IS on, we ignore the level filter to get all mods
            // (This will allow high-tier mods to spawn on level 1 for testing)
        }

        // 1. Filter the entire loot pool
        List<LootTable.ModInfo> validMods = LootTable.MODIFIER_POOL.stream()
            .filter(mod -> mod.category == category) // Matches category (e.g., ARMOR, WAR_WEAPON)
            .filter(mod -> level >= mod.minLevel && level <= mod.maxLevel) // Matches level (TIER)
            .collect(Collectors.toList());

        if (validMods.isEmpty()) {
            return; // No valid modifiers found for this item at this level
        }

        // 2. Pick one valid modifier at random
        LootTable.ModInfo modInfo = validMods.get(random.nextInt(validMods.size()));

        // 3. Roll the value
        int value = 0;
        if (modInfo.maxBonus > modInfo.minBonus) {
            value = random.nextInt(modInfo.maxBonus - modInfo.minBonus + 1) + modInfo.minBonus;
        } else {
            value = modInfo.minBonus; // min and max are the same (e.g., for Material tags)
        }

        // 4. Create and add the modifier
        // For "+N" bonuses, we need to include the value in the display name
        String displayName = modInfo.displayName;
        if (displayName.contains("+")) {
            displayName = "+" + value;
        }

        item.addModifier(new ItemModifier(modInfo.type, value, displayName));
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

        if (possibleColors.isEmpty()) {
            // Fallback if no tiers match (e.g., level 0)
            possibleColors.add(MonsterColor.BLUE);
            possibleColors.add(MonsterColor.YELLOW);
            possibleColors.add(MonsterColor.WHITE);
        }
        return possibleColors.get(random.nextInt(possibleColors.size()));
    }

    private ItemColor getRandomItemColor(Item.ItemType type) {
        List<ItemColor> possibleColors = new ArrayList<>();

        ItemCategory category = new Item(type, 0, 0, ItemColor.TAN, itemDataManager, assetManager).getCategory();

        boolean tier1 = level < 8;
        boolean tier2 = level >= 5 && level < 15;
        boolean tier3 = level >= 12;

        switch (category) {
            case WAR_WEAPON:
                if (tier1) {
                    possibleColors.add(ItemColor.TAN);
                    possibleColors.add(ItemColor.ORANGE);
                }
                if (tier2) {
                    possibleColors.add(ItemColor.BLUE);
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
                    possibleColors.add(ItemColor.ORANGE);
                }
                if (tier2) {
                    possibleColors.add(ItemColor.BLUE);
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

        if (possibleColors.isEmpty()) {
            // Fallback if no tiers match (e.g. level 0)
            if (category == ItemCategory.WAR_WEAPON || category == ItemCategory.ARMOR) {
                possibleColors.add(ItemColor.TAN);
            } else if (category == ItemCategory.SPIRITUAL_WEAPON || category == ItemCategory.RING) {
                possibleColors.add(ItemColor.BLUE);
            } else {
                possibleColors.add(ItemColor.TAN);
            }
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
