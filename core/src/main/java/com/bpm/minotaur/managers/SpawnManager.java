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
import com.badlogic.gdx.utils.Array;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;
import com.bpm.minotaur.gamedata.item.ItemCategory;

public class SpawnManager {
    private final Maze maze;
    private final Difficulty difficulty;
    private final int level;
    private final Random random = new Random();
    private final List<GridPoint2> validSpawnPoints = new ArrayList<>();
    private final MonsterDataManager dataManager;
    private final ItemDataManager itemDataManager;
    private final AssetManager assetManager;
    private final SpawnTableData spawnTableData;

    // --- Data-Driven Fields ---
    private final LevelBudget budget;
    private final WeightedRandomList<SpawnTableEntry> monsterPool;
    private final WeightedRandomList<SpawnTableEntry> itemPool;
    private final WeightedRandomList<SpawnTableEntry> containerPool;
    private final WeightedRandomList<SpawnTableEntry> containerLootPool;
    private final WeightedRandomList<SpawnTableEntry> debrisPool; // <-- NEW
    private final Map<Integer, LevelBudget> budgetMap = new HashMap<>();

    public static boolean DEBUG_FORCE_MODIFIERS = true;

    private static final float BASE_MODIFIER_CHANCE = 0.15f;
    private static final float COLOR_MULTIPLIER_BONUS = 0.1f;
    private static final float SECOND_MODIFIER_CHANCE = 0.25f;
    private static final float THIRD_MODIFIER_CHANCE = 0.10f;

    public SpawnManager(MonsterDataManager dataManager, ItemDataManager itemDataManager, AssetManager assetManager,
                        Maze maze, Difficulty difficulty, int level, String[] layout,
                        SpawnTableData spawnTableData) {
        this.maze = maze;
        this.difficulty = difficulty;
        this.level = level;
        this.dataManager = dataManager;
        this.itemDataManager = itemDataManager;
        this.assetManager = assetManager;
        this.spawnTableData = spawnTableData;

        findValidSpawnPoints(layout);

        // 1. Build budget map
        for (LevelBudget b : spawnTableData.levelBudgets) {
            budgetMap.put(b.level, b);
        }
        // 2. Get the budget
        this.budget = budgetMap.getOrDefault(level, spawnTableData.defaultBudget);

        // 3. Build pools
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

        // --- NEW: DEBRIS POOL ---
        this.debrisPool = new WeightedRandomList<>();
        if (spawnTableData.debrisSpawnTable != null) {
            for (SpawnTableEntry entry : spawnTableData.debrisSpawnTable) {
                if (level >= entry.minLevel && level <= entry.maxLevel) {
                    debrisPool.add(entry);
                }
            }
        }
        // ------------------------

        this.containerLootPool = new WeightedRandomList<>();
        Array<SpawnTableEntry> loot = spawnTableData.containerLoot.get("default");
        if (loot != null) {
            for (int i = 0; i < loot.size; i++) {
                Object obj = loot.get(i);
                SpawnTableEntry entry;
                if (obj instanceof com.badlogic.gdx.utils.JsonValue) {
                    com.badlogic.gdx.utils.Json json = new com.badlogic.gdx.utils.Json();
                    entry = json.readValue(SpawnTableEntry.class, (com.badlogic.gdx.utils.JsonValue) obj);
                } else if (obj instanceof SpawnTableEntry) {
                    entry = (SpawnTableEntry) obj;
                } else {
                    continue;
                }
                if (level >= entry.minLevel && level <= entry.maxLevel) {
                    containerLootPool.add(entry);
                }
            }
        }
    }

    private void findValidSpawnPoints(String[] layout) {
        for (int y = 0; y < maze.getHeight(); y++) {
            for (int x = 0; x < maze.getWidth(); x++) {

                boolean isFloor = layout[maze.getHeight() - 1 - y].charAt(x) == '.';
                boolean isSafeHomeTile = maze.isHomeTile(x, y);

                if (isFloor && !isSafeHomeTile) {
                    validSpawnPoints.add(new GridPoint2(x, y));
                }
            }
        }
        Collections.shuffle(validSpawnPoints, random);
    }

    public void spawnEntities() {
        spawnMonsters(budget.monsterBudget);
        spawnItems(budget.itemBudget);
        spawnContainers(budget.containerBudget);
        spawnDebris(budget.debrisBudget); // <-- NEW CALL
    }

    // --- NEW METHOD ---
    private void spawnDebris(int budget) {
        if (debrisPool.isEmpty() || validSpawnPoints.isEmpty()) return;

        for (int i = 0; i < budget; i++) {
            // Pick a random floor tile (reuse tiles allowed)
            GridPoint2 spawnPoint = validSpawnPoints.get(random.nextInt(validSpawnPoints.size()));

            SpawnTableEntry entry = debrisPool.getRandomEntry();
            if (entry == null) continue;

            Item.ItemType type;
            try {
                type = Item.ItemType.valueOf(entry.type);
            } catch (IllegalArgumentException e) {
                continue;
            }

            ItemVariant variant = itemDataManager.getRandomVariantForItem(type, level);
            if (variant == null) continue;

            Item debris = itemDataManager.createItem(type, spawnPoint.x, spawnPoint.y, variant.color, assetManager);

            // SCATTER LOGIC: Random offset within tile
            float offsetX = 0.2f + random.nextFloat() * 0.6f;
            float offsetY = 0.2f + random.nextFloat() * 0.6f;

            // Ensure Item.java has setPosition(float, float) or use this:
            if (debris.getPosition() != null) {
                debris.getPosition().set(spawnPoint.x + offsetX, spawnPoint.y + offsetY);
            }

            maze.addItem(debris);
        }
    }

    private void spawnMonsters(int budget) {
        if (monsterPool.isEmpty()) return;

        for (int i = 0; i < budget; i++) {
            GridPoint2 spawnPoint = getEmptySpawnPoint();
            if (spawnPoint == null) break;

            SpawnTableEntry entry = monsterPool.getRandomEntry();
            if (entry == null) continue;

            Monster.MonsterType type;
            try {
                type = Monster.MonsterType.valueOf(entry.type);
            } catch (IllegalArgumentException e) {
                continue;
            }

            MonsterVariant variant = dataManager.getRandomVariantForMonster(type, level);
            if (variant == null) continue;

            Monster monster = new Monster(type, spawnPoint.x, spawnPoint.y, variant.color, dataManager, assetManager);
            monster.scaleStats(level);
            maze.addMonster(monster);
        }
    }

    private void spawnItems(int budget) {
        if (itemPool.isEmpty()) return;

        for (int i = 0; i < budget; i++) {
            GridPoint2 spawnPoint = getEmptySpawnPoint();
            if (spawnPoint == null) break;
            spawnRegularItem(spawnPoint);
        }
    }

    private void spawnRegularItem(GridPoint2 spawnPoint) {
        SpawnTableEntry entry = itemPool.getRandomEntry();
        if (entry == null) return;

        Item.ItemType type;
        try {
            type = Item.ItemType.valueOf(entry.type);
        } catch (IllegalArgumentException e) {
            return;
        }

        ItemVariant variant = itemDataManager.getRandomVariantForItem(type, level);
        if (variant == null) return;

        Item item = itemDataManager.createItem(type, spawnPoint.x, spawnPoint.y, variant.color, assetManager);
        attemptToModifyItem(item, variant.color);
        maze.addItem(item);
    }

    private void spawnContainers(int budget) {
        if (containerPool.isEmpty()) return;

        for (int i = 0; i < budget; i++) {
            GridPoint2 spawnPoint = getEmptySpawnPoint();
            if (spawnPoint == null) break;

            SpawnTableEntry entry = containerPool.getRandomEntry();
            if (entry == null) continue;

            Item.ItemType type;
            try {
                type = Item.ItemType.valueOf(entry.type);
            } catch (IllegalArgumentException e) {
                continue;
            }

            ItemColor containerColor = entry.keyColor;
            if (containerColor == null) containerColor = ItemColor.CONTAINER_TAN;

            Item container = itemDataManager.createItem(type, spawnPoint.x, spawnPoint.y, containerColor, assetManager);
            attemptToModifyItem(container, containerColor);
            addLootToContainer(container);
            maze.addItem(container);

            if (container.isLocked()) {
                spawnKey(containerColor);
            }
        }
    }

    private void addLootToContainer(Item container) {
        if (containerLootPool.isEmpty()) return;

        SpawnTableEntry entry = containerLootPool.getRandomEntry();
        if (entry == null) return;

        Item.ItemType type;
        try {
            type = Item.ItemType.valueOf(entry.type);
        } catch (IllegalArgumentException e) {
            return;
        }

        ItemVariant variant = itemDataManager.getRandomVariantForItem(type, level);
        if (variant == null) return;

        Item loot = itemDataManager.createItem(type, 0, 0, variant.color, assetManager);
        attemptToModifyItem(loot, variant.color);
        container.getContents().add(loot);
    }

    private void spawnKey(ItemColor containerColor) {
        GridPoint2 spawnPoint = getEmptySpawnPoint();
        if (spawnPoint == null) return;

        Item key = itemDataManager.createItem(Item.ItemType.KEY, spawnPoint.x, spawnPoint.y, containerColor, assetManager);
        attemptToModifyItem(key, containerColor);
        maze.addItem(key);
    }

    private void attemptToModifyItem(Item item, ItemColor color) {
        float spawnChance = BASE_MODIFIER_CHANCE + ((color.getMultiplier() - 1.0f) * COLOR_MULTIPLIER_BONUS);
        if (!DEBUG_FORCE_MODIFIERS && random.nextFloat() > spawnChance) return;

        addRandomModifier(item);
        if (random.nextFloat() < SECOND_MODIFIER_CHANCE) addRandomModifier(item);
        if (random.nextFloat() < THIRD_MODIFIER_CHANCE) addRandomModifier(item);
    }

    private void addRandomModifier(Item item) {
        ItemCategory category = item.getCategory();
        List<LootTable.ModInfo> validMods = LootTable.MODIFIER_POOL.stream()
            .filter(mod -> mod.category == category)
            .filter(mod -> DEBUG_FORCE_MODIFIERS || (level >= mod.minLevel && level <= mod.maxLevel))
            .collect(Collectors.toList());

        if (validMods.isEmpty()) return;

        LootTable.ModInfo modInfo = validMods.get(random.nextInt(validMods.size()));
        int value = 0;
        if (modInfo.maxBonus > modInfo.minBonus) {
            value = random.nextInt(modInfo.maxBonus - modInfo.minBonus + 1) + modInfo.minBonus;
        } else {
            value = modInfo.minBonus;
        }

        String displayName = modInfo.displayName;
        if (displayName.contains("+")) displayName = "+" + value;

        item.addModifier(new ItemModifier(modInfo.type, value, displayName));
    }

    private GridPoint2 getEmptySpawnPoint() {
        if (validSpawnPoints.isEmpty()) return null;
        return validSpawnPoints.remove(0);
    }
}
