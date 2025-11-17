package com.bpm.minotaur.managers;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.gamedata.*;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.ItemColor;
import com.bpm.minotaur.gamedata.item.ItemDataManager;
import com.bpm.minotaur.gamedata.item.ItemModifier;
import com.bpm.minotaur.gamedata.item.ItemVariant;
import com.bpm.minotaur.gamedata.monster.Monster;
import com.bpm.minotaur.gamedata.monster.MonsterDataManager;
import com.bpm.minotaur.gamedata.monster.MonsterVariant;
import com.bpm.minotaur.gamedata.spawntables.LevelBudget;
import com.bpm.minotaur.gamedata.spawntables.SpawnTableData;
import com.bpm.minotaur.gamedata.spawntables.SpawnTableEntry;
import com.bpm.minotaur.gamedata.spawntables.WeightedRandomList;
import com.badlogic.gdx.utils.Array; // <-- ADD THIS IMPORT

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;
import com.bpm.minotaur.gamedata.item.ItemCategory;
import java.util.stream.Stream;

public class SpawnManager {
    private final Maze maze;
    private final Difficulty difficulty; // Still kept for potential future use
    private final int level;
    private final Random random = new Random();
    private final List<GridPoint2> validSpawnPoints = new ArrayList<>();
    private final MonsterDataManager dataManager;
    private final ItemDataManager itemDataManager;
    private final AssetManager assetManager;
    private final SpawnTableData spawnTableData;

    // --- NEW: Data-Driven Fields ---
    private final LevelBudget budget;
    private final WeightedRandomList<SpawnTableEntry> monsterPool;
    private final WeightedRandomList<SpawnTableEntry> itemPool;
    private final WeightedRandomList<SpawnTableEntry> containerPool;
    private final WeightedRandomList<SpawnTableEntry> containerLootPool;
    private final Map<Integer, LevelBudget> budgetMap = new HashMap<>();

    public static boolean DEBUG_FORCE_MODIFIERS = true; // Toggle to force spawn

    private static final float BASE_MODIFIER_CHANCE = 0.15f;
    private static final float COLOR_MULTIPLIER_BONUS = 0.1f;
    private static final float SECOND_MODIFIER_CHANCE = 0.25f;
    private static final float THIRD_MODIFIER_CHANCE = 0.10f;

    public SpawnManager(MonsterDataManager dataManager, ItemDataManager itemDataManager, AssetManager assetManager,
                        Maze maze, Difficulty difficulty, int level, String[] layout,
                        SpawnTableData spawnTableData) { // <-- Added spawnTableData
        this.maze = maze;
        this.difficulty = difficulty;
        this.level = level;
        this.dataManager = dataManager;
        this.itemDataManager = itemDataManager;
        this.assetManager = assetManager;
        this.spawnTableData = spawnTableData;

        findValidSpawnPoints(layout);

        // --- NEW: Build data-driven lists ---
        // 1. Build budget map for fast lookup
        for (LevelBudget b : spawnTableData.levelBudgets) {
            budgetMap.put(b.level, b);
        }
        // 2. Get the budget for this level
        this.budget = budgetMap.getOrDefault(level, spawnTableData.defaultBudget);

        // --- [MODIFIED] ---
        // 3. Build level-specific weighted spawn pools
        // Swapped stream() for a standard for-loop to iterate over the LibGDX Array
        // This avoids the ClassCastException
        this.monsterPool = new WeightedRandomList<>();
        for (SpawnTableEntry entry : spawnTableData.monsterSpawnTable) {
            if (level >= entry.minLevel && level <= entry.maxLevel) {
                monsterPool.add(entry);
            }
        }

        this.itemPool = new WeightedRandomList<>();
        for (SpawnTableEntry entry : spawnTableData.itemSpawnTable) {
            if (level >= entry.minLevel && level <= entry.maxLevel) {
                itemPool.add(entry);
            }
        }

        this.containerPool = new WeightedRandomList<>();
        for (SpawnTableEntry entry : spawnTableData.containerSpawnTable) {
            if (level >= entry.minLevel && level <= entry.maxLevel) {
                containerPool.add(entry);
            }
        }

        // 4. Build container loot pool (we'll just use "default" for now)
        // 4. Build container loot pool (we'll just use "default" for now)
        this.containerLootPool = new WeightedRandomList<>();
        Array<SpawnTableEntry> loot = spawnTableData.containerLoot.get("default");
        if (loot != null) {
            // CRITICAL FIX: Handle JsonValue objects in the array
            for (int i = 0; i < loot.size; i++) {
                Object obj = loot.get(i);
                SpawnTableEntry entry;

                if (obj instanceof com.badlogic.gdx.utils.JsonValue) {
                    // Convert JsonValue to SpawnTableEntry
                    com.badlogic.gdx.utils.Json json = new com.badlogic.gdx.utils.Json();
                    entry = json.readValue(SpawnTableEntry.class, (com.badlogic.gdx.utils.JsonValue) obj);
                } else if (obj instanceof SpawnTableEntry) {
                    entry = (SpawnTableEntry) obj;
                } else {
                    Gdx.app.error("SpawnManager", "Unknown type in containerLoot: " + obj.getClass().getName());
                    continue;
                }

                if (level >= entry.minLevel && level <= entry.maxLevel) {
                    containerLootPool.add(entry);
                }
            }
        } else {
            Gdx.app.error("SpawnManager", "No 'default' loot table found in spawntables.json!");
        }
        // --- [END MODIFIED] ---
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
        // Use the new data-driven budgets
        spawnMonsters(budget.monsterBudget);
        spawnItems(budget.itemBudget);
        spawnContainers(budget.containerBudget); // <-- NEW CALL
    }

    private void spawnMonsters(int budget) {
        if (monsterPool.isEmpty()) {
            Gdx.app.log("SpawnManager", "No valid monsters to spawn for level " + level);
            return;
        }

        for (int i = 0; i < budget; i++) {
            GridPoint2 spawnPoint = getEmptySpawnPoint();
            if (spawnPoint == null) break;

            // 1. Get a type from the weighted pool (e.g., "GIANT_ANT")
            SpawnTableEntry entry = monsterPool.getRandomEntry();
            if (entry == null) continue;

            // 2. Convert string to enum
            Monster.MonsterType type;
            try {
                type = Monster.MonsterType.valueOf(entry.type);
            } catch (IllegalArgumentException e) {
                Gdx.app.error("SpawnManager", "Invalid monster type in spawntables.json: " + entry.type);
                continue;
            }

            // 3. Get a random *variant* (color) for this type
            MonsterVariant variant = dataManager.getRandomVariantForMonster(type, level);
            if (variant == null) {
                Gdx.app.error("SpawnManager", "Failed to get variant for " + type.name() + " at level " + level);
                continue;
            }

            // 4. Create the monster
            Monster monster = new Monster(type, spawnPoint.x, spawnPoint.y, variant.color, dataManager, assetManager);
            monster.scaleStats(level);
            maze.addMonster(monster);
        }
    }

    private void spawnItems(int budget) {
        if (itemPool.isEmpty()) {
            Gdx.app.log("SpawnManager", "No valid items to spawn for level " + level);
            return;
        }

        for (int i = 0; i < budget; i++) {
            GridPoint2 spawnPoint = getEmptySpawnPoint();
            if (spawnPoint == null) break;

            spawnRegularItem(spawnPoint);
        }
    }

    private void spawnRegularItem(GridPoint2 spawnPoint) {
        // 1. Get an item type from the weighted pool (e.g., "BOW")
        SpawnTableEntry entry = itemPool.getRandomEntry();
        if (entry == null) return;

        // 2. Convert string to enum
        Item.ItemType type;
        try {
            type = Item.ItemType.valueOf(entry.type);
        } catch (IllegalArgumentException e) {
            Gdx.app.error("SpawnManager", "Invalid item type in spawntables.json: " + entry.type);
            return;
        }

        // 3. Get a random *variant* (color) for this type
        ItemVariant variant = itemDataManager.getRandomVariantForItem(type, level);
        if (variant == null) {
            Gdx.app.error("SpawnManager", "Failed to get variant for " + type.name() + " at level " + level);
            return;
        }

        // 4. Create the item
        Item item = itemDataManager.createItem(type, spawnPoint.x, spawnPoint.y, variant.color, assetManager);
        attemptToModifyItem(item, variant.color);
        maze.addItem(item);
    }

    private void spawnContainers(int budget) {
        if (containerPool.isEmpty()) {
            Gdx.app.log("SpawnManager", "No valid containers to spawn for level " + level);
            return;
        }

        for (int i = 0; i < budget; i++) {
            GridPoint2 spawnPoint = getEmptySpawnPoint();
            if (spawnPoint == null) break;

            // 1. Get a container type from the weighted pool
            SpawnTableEntry entry = containerPool.getRandomEntry();
            if (entry == null) continue;

            // 2. Convert string to enum
            Item.ItemType type;
            try {
                type = Item.ItemType.valueOf(entry.type);
            } catch (IllegalArgumentException e) {
                Gdx.app.error("SpawnManager", "Invalid container type in spawntables.json: " + entry.type);
                continue;
            }

            // 3. Get color *directly from the spawn table entry*
            ItemColor containerColor = entry.keyColor;
            if (containerColor == null) {
                containerColor = ItemColor.CONTAINER_TAN; // Fallback
            }

            // 4. Create the container
            Item container = itemDataManager.createItem(type, spawnPoint.x, spawnPoint.y, containerColor, assetManager);
            attemptToModifyItem(container, containerColor);

            // 5. Add loot
            addLootToContainer(container);

            maze.addItem(container);

            // 6. Spawn a key
            if (container.isLocked()) {
                spawnKey(containerColor);
            }
        }
    }

    private void addLootToContainer(Item container) {
        if (containerLootPool.isEmpty()) {
            Gdx.app.log("SpawnManager", "Container loot pool is empty for level " + level);
            return;
        }

        // 1. Get a loot type from the weighted pool
        SpawnTableEntry entry = containerLootPool.getRandomEntry();
        if (entry == null) return;

        // 2. Convert string to enum
        Item.ItemType type;
        try {
            type = Item.ItemType.valueOf(entry.type);
        } catch (IllegalArgumentException e) {
            Gdx.app.error("SpawnManager", "Invalid loot type in spawntables.json: " + entry.type);
            return;
        }

        // 3. Get a random *variant* (color) for this loot
        ItemVariant variant = itemDataManager.getRandomVariantForItem(type, level);
        if (variant == null) {
            Gdx.app.error("SpawnManager", "Failed to get variant for loot " + type.name() + " at level " + level);
            return;
        }

        // 4. Create the loot item
        Item loot = itemDataManager.createItem(type, 0, 0, variant.color, assetManager);
        attemptToModifyItem(loot, variant.color); // Treasures can be modified!

        container.getContents().add(loot);
    }

    private void spawnKey(ItemColor containerColor) {
        GridPoint2 spawnPoint = getEmptySpawnPoint();
        if (spawnPoint == null) return; // No space for the key

        Item key = itemDataManager.createItem(Item.ItemType.KEY, spawnPoint.x, spawnPoint.y, containerColor, assetManager);
        attemptToModifyItem(key, containerColor);
        maze.addItem(key);
    }

    private void attemptToModifyItem(Item item, ItemColor color) {
        float spawnChance = BASE_MODIFIER_CHANCE + ((color.getMultiplier() - 1.0f) * COLOR_MULTIPLIER_BONUS);

        if (!DEBUG_FORCE_MODIFIERS && random.nextFloat() > spawnChance) {
            return;
        }

        addRandomModifier(item);
        if (random.nextFloat() < SECOND_MODIFIER_CHANCE) {
            addRandomModifier(item);
        }
        if (random.nextFloat() < THIRD_MODIFIER_CHANCE) {
            addRandomModifier(item);
        }
    }

    private void addRandomModifier(Item item) {
        ItemCategory category = item.getCategory();

        // [MODIFIED] Simplified the stream logic
        List<LootTable.ModInfo> validMods = LootTable.MODIFIER_POOL.stream()
            .filter(mod -> mod.category == category)
            .filter(mod -> DEBUG_FORCE_MODIFIERS || (level >= mod.minLevel && level <= mod.maxLevel))
            .collect(Collectors.toList());
        // [END MODIFIED]

        if (validMods.isEmpty()) {
            return;
        }

        LootTable.ModInfo modInfo = validMods.get(random.nextInt(validMods.size()));

        int value = 0;
        if (modInfo.maxBonus > modInfo.minBonus) {
            value = random.nextInt(modInfo.maxBonus - modInfo.minBonus + 1) + modInfo.minBonus;
        } else {
            value = modInfo.minBonus;
        }

        String displayName = modInfo.displayName;
        if (displayName.contains("+")) {
            displayName = "+" + value;
        }

        item.addModifier(new ItemModifier(modInfo.type, value, displayName));
    }

    private GridPoint2 getEmptySpawnPoint() {
        if (validSpawnPoints.isEmpty()) {
            return null;
        }
        return validSpawnPoints.remove(0);
    }
}
