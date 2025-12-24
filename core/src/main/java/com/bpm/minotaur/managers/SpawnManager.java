package com.bpm.minotaur.managers;

import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.gamedata.*;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.ItemColor;
import com.bpm.minotaur.gamedata.item.ItemDataManager;
import com.bpm.minotaur.gamedata.item.ItemModifier;
import com.bpm.minotaur.gamedata.item.ItemVariant;
import com.bpm.minotaur.gamedata.monster.Monster;
import com.bpm.minotaur.gamedata.monster.MonsterColor;
import com.bpm.minotaur.gamedata.monster.MonsterDataManager;
import com.bpm.minotaur.gamedata.monster.MonsterTemplate;
import com.bpm.minotaur.gamedata.monster.MonsterVariant;
import com.bpm.minotaur.gamedata.player.Player;
import com.badlogic.gdx.Gdx;
import com.bpm.minotaur.gamedata.spawntables.LevelBudget;
import com.bpm.minotaur.gamedata.spawntables.SpawnTableData;
import com.bpm.minotaur.gamedata.spawntables.SpawnTableEntry;
import com.bpm.minotaur.gamedata.spawntables.WeightedRandomList;
import com.badlogic.gdx.utils.Array;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet; // NEW
import java.util.List;
import java.util.Map;
import java.util.Set; // NEW
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

    public static boolean DEBUG_FORCE_MODIFIERS = false;

    private static final float BASE_MODIFIER_CHANCE = 0.15f;
    private static final float COLOR_MULTIPLIER_BONUS = 0.1f;
    private static final float SECOND_MODIFIER_CHANCE = 0.25f;
    private static final float THIRD_MODIFIER_CHANCE = 0.10f;

    // --- NEW: Unique Tracking ---
    public static final Set<String> uniqueMonstersSpawned = new HashSet<>();
    // ----------------------------

    private final int playerLevel; // Added field

    public SpawnManager(MonsterDataManager dataManager, ItemDataManager itemDataManager, AssetManager assetManager,
            Maze maze, Difficulty difficulty, int level, int playerLevel, String[] layout,
            SpawnTableData spawnTableData, long seed) {
        this.maze = maze;
        this.playerLevel = playerLevel; // Initialize
        this.random.setSeed(seed); // Initialize with deterministic seed
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
                // User Request: Completely random debris, NO TIER SYSTEM (Ignore min/max level)
                // EXCEPTION: Portals must respect level limits to prevent skipping content or
                // spawning on L1
                if (entry.type.equals("MYSTERIOUS_PORTAL")) {
                    if (level < entry.minLevel || level > entry.maxLevel) {
                        continue;
                    }
                }
                debrisPool.add(entry);
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
        if (layout == null)
            return;
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
        spawnDebris(budget.debrisBudget); // RE-ENABLED
    }

    // --- NEW: Difficulty Calculation ---
    public static int calculateTargetDifficulty(int dungeonDepth, int playerLevel) {
        // NetHack Style: Average of Depth and Player Level
        // Prevents high-level players from farming easy areas efficiently,
        // and prevents low-level players from being instantly crushed in deep areas.
        return Math.max(1, (dungeonDepth + playerLevel) / 2);
    }
    // -----------------------------------

    // --- NEW: Helper for Prediction ---
    public static WeightedRandomList<SpawnTableEntry> buildDebrisPool(SpawnTableData data, int level) {
        WeightedRandomList<SpawnTableEntry> pool = new WeightedRandomList<>();
        if (data.debrisSpawnTable != null) {
            for (SpawnTableEntry entry : data.debrisSpawnTable) {
                if (level >= entry.minLevel && level <= entry.maxLevel) {
                    pool.add(entry);
                }
            }
        }
        return pool;
    }

    public static int getDebrisBudget(SpawnTableData data, int level) {
        LevelBudget budget = data.defaultBudget;
        for (LevelBudget b : data.levelBudgets) {
            if (b.level == level) {
                budget = b;
                break;
            }
        }
        return budget.debrisBudget;
    }

    // --- NEW METHOD ---
    private void spawnDebris(int budget) {
        if (debrisPool.isEmpty())
            return;

        for (int i = 0; i < budget; i++) {
            // [REFACTORED] What before Where
            // 1. Pick Item Type FIRST
            SpawnTableEntry entry = debrisPool.getRandomEntry(this.random); // Ensure WeightedRandomList uses OUR random
            if (entry == null)
                continue;

            // 2. Then pick Spot
            if (validSpawnPoints.isEmpty())
                continue;
            GridPoint2 spawnPoint = validSpawnPoints.get(random.nextInt(validSpawnPoints.size()));

            Item.ItemType type;
            try {
                type = Item.ItemType.valueOf(entry.type);
            } catch (IllegalArgumentException e) {
                continue;
            }

            ItemVariant variant = itemDataManager.getRandomVariantForItem(type, level);
            if (variant == null)
                continue;

            Item debris = itemDataManager.createItem(type, spawnPoint.x, spawnPoint.y, variant.color, assetManager);

            // SCATTER LOGIC: Random offset within tile
            float offsetX = 0.2f + random.nextFloat() * 0.6f;
            float offsetY = 0.2f + random.nextFloat() * 0.6f;

            if (debris.getPosition() != null) {
                debris.getPosition().set(spawnPoint.x + offsetX, spawnPoint.y + offsetY);
            }

            maze.addItem(debris);
        }
    }

    /**
     * STATIC PREDICTION METHOD
     * Simulates debris spawning to fail-fast check if a portal exists.
     */
    public static boolean predictPortalSpawn(long seed, int budget, WeightedRandomList<SpawnTableEntry> _debrisPool) {
        java.util.Random simRandom = new java.util.Random(seed);

        if (_debrisPool == null || _debrisPool.isEmpty())
            return false;

        for (int i = 0; i < budget; i++) {
            // We must simulate the exact same random calls as spawnDebris
            SpawnTableEntry entry = _debrisPool.getRandomEntry(simRandom);
            if (entry != null && "MYSTERIOUS_PORTAL".equals(entry.type)) {
                return true;
            }
        }
        return false;
    }

    private void spawnMonsters(int budget) {
        if (dataManager == null)
            return;

        // 1. Calculate Target Difficulty
        int targetDifficulty = calculateTargetDifficulty(level, playerLevel);

        List<Monster.MonsterType> validTypes = new ArrayList<>();
        List<Integer> weights = new ArrayList<>();
        int totalWeight = 0;

        // Alignment Context (Defaulting to Neutral if not found)
        com.bpm.minotaur.gamedata.Alignment dungeonAlignment = com.bpm.minotaur.gamedata.Alignment.NEUTRAL;

        // 2. Pre-calculate valid pool for this level
        for (Monster.MonsterType type : Monster.MonsterType.values()) {
            try {
                MonsterTemplate t = dataManager.getTemplate(type);
                if (t == null)
                    continue;

                // Difficulty Filter
                // Reject if Archetype.Difficulty > TargetDifficulty + Variance (2)
                if (t.baseLevel > targetDifficulty + 2) {
                    // 5% Chance for Out of Depth (High-level variance)
                    if (random.nextInt(20) != 0)
                        continue;
                }

                // Flag Checks
                if ((t.generationFlags & MonsterTemplate.G_UNIQ) != 0) {
                    if (uniqueMonstersSpawned.contains(type.name()))
                        continue;
                }
                // Hell Checks (Deep levels assumed Hell > 25)
                if ((t.generationFlags & MonsterTemplate.G_HELL) != 0) {
                    if (level < 25)
                        continue;
                }
                if ((t.generationFlags & MonsterTemplate.G_NOHELL) != 0) {
                    if (level >= 25)
                        continue;
                }

                if (t.frequency <= 0)
                    continue;

                // Weighting Phase
                int weight = t.frequency;

                // Alignment Bonus (4x)
                if (t.alignment == dungeonAlignment) {
                    weight *= 4;
                }

                validTypes.add(type);
                weights.add(weight);
                totalWeight += weight;

            } catch (Exception e) {
                continue; // Skip if template missing
            }
        }

        if (validTypes.isEmpty())
            return;

        // 3. Spawn Loop
        for (int i = 0; i < budget; i++) {
            GridPoint2 spawnPoint = getEmptySpawnPoint();
            if (spawnPoint == null)
                break;

            // Weighted Pick
            int roll = random.nextInt(totalWeight);
            int current = 0;
            Monster.MonsterType selectedType = validTypes.get(0);

            for (int k = 0; k < validTypes.size(); k++) {
                current += weights.get(k);
                if (roll < current) {
                    selectedType = validTypes.get(k);
                    break;
                }
            }

            // Unique Tracking
            MonsterTemplate t = dataManager.getTemplate(selectedType);
            if ((t.generationFlags & MonsterTemplate.G_UNIQ) != 0) {
                if (uniqueMonstersSpawned.contains(selectedType.name())) {
                    // Already spawned in this batch? Skip.
                    continue;
                }
                uniqueMonstersSpawned.add(selectedType.name());
            }

            MonsterVariant variant = dataManager.getRandomVariantForMonster(selectedType, level);
            if (variant == null) {
                Monster monster = new Monster(selectedType, spawnPoint.x, spawnPoint.y, MonsterColor.WHITE, dataManager,
                        assetManager);
                monster.scaleStats(level);
                maze.addMonster(monster);
            } else {
                Monster monster = new Monster(selectedType, spawnPoint.x, spawnPoint.y, variant.color, dataManager,
                        assetManager);
                monster.scaleStats(level);
                maze.addMonster(monster);
            }
        }
    }

    /**
     * Tries to spawn a single monster at an off-screen/edge location.
     * Should be called periodically (e.g. every 100 turns).
     */
    public void spawnPeriodicMonster(Player player) {
        // Reuse the same logic but for 1 monster
        // We need to find a valid spot that is NOT in the player's immediate view if
        // possible.

        GridPoint2 spawnPoint = findRuntimeSpawnPoint(player);
        if (spawnPoint == null)
            return;

        // 1. Calculate Target Difficulty (Dynamic with Player Level)
        int targetDifficulty = calculateTargetDifficulty(level, player.getLevel());

        List<Monster.MonsterType> validTypes = new ArrayList<>();
        List<Integer> weights = new ArrayList<>();
        int totalWeight = 0;

        com.bpm.minotaur.gamedata.Alignment dungeonAlignment = com.bpm.minotaur.gamedata.Alignment.NEUTRAL;

        for (Monster.MonsterType type : Monster.MonsterType.values()) {
            try {
                MonsterTemplate t = dataManager.getTemplate(type);
                if (t == null)
                    continue;

                // Difficulty Filter
                if (t.baseLevel > targetDifficulty + 2) {
                    if (random.nextInt(20) != 0)
                        continue;
                }

                // Flag Checks
                if ((t.generationFlags & MonsterTemplate.G_UNIQ) != 0) {
                    if (uniqueMonstersSpawned.contains(type.name()))
                        continue;
                }
                if ((t.generationFlags & MonsterTemplate.G_HELL) != 0) {
                    if (level < 25)
                        continue;
                }
                if ((t.generationFlags & MonsterTemplate.G_NOHELL) != 0) {
                    if (level >= 25)
                        continue;
                }

                if (t.frequency <= 0)
                    continue;

                // Weighting Phase
                int weight = t.frequency;
                if (t.alignment == dungeonAlignment) {
                    weight *= 4;
                }

                validTypes.add(type);
                weights.add(weight);
                totalWeight += weight;
            } catch (Exception e) {
                continue;
            }
        }

        if (validTypes.isEmpty())
            return;

        int roll = random.nextInt(totalWeight);
        int current = 0;
        Monster.MonsterType selectedType = validTypes.get(0);
        for (int k = 0; k < validTypes.size(); k++) {
            current += weights.get(k);
            if (roll < current) {
                selectedType = validTypes.get(k);
                break;
            }
        }

        // Unique Check & Add
        MonsterTemplate t = dataManager.getTemplate(selectedType);
        if ((t.generationFlags & MonsterTemplate.G_UNIQ) != 0) {
            if (uniqueMonstersSpawned.contains(selectedType.name()))
                return;
            uniqueMonstersSpawned.add(selectedType.name());
        }

        MonsterVariant variant = dataManager.getRandomVariantForMonster(selectedType, level);
        if (variant == null) {
            Monster monster = new Monster(selectedType, spawnPoint.x, spawnPoint.y, MonsterColor.WHITE, dataManager,
                    assetManager);
            monster.scaleStats(level);
            maze.addMonster(monster);
        } else {
            Monster monster = new Monster(selectedType, spawnPoint.x, spawnPoint.y, variant.color, dataManager,
                    assetManager);
            monster.scaleStats(level);
            maze.addMonster(monster);
        }

        Gdx.app.log("SpawnManager", "Periodic Spawn: " + selectedType + " at " + spawnPoint);
    }

    private GridPoint2 findRuntimeSpawnPoint(Player player) {
        int width = maze.getWidth();
        int height = maze.getHeight();
        int px = (int) player.getPosition().x;
        int py = (int) player.getPosition().y;

        // Try random spots
        for (int i = 0; i < 20; i++) {
            int x = random.nextInt(width);
            int y = random.nextInt(height);

            // Check bounds and wall
            if (!maze.isPassable(x, y))
                continue;

            // Check occupancy
            if (maze.getMonsters().containsKey(new GridPoint2(x, y)))
                continue;
            // (Optional: Check items? Usually monsters can spawn on items)

            // Check Distance (don't spawn on top of player)
            int dist = Math.abs(x - px) + Math.abs(y - py);
            if (dist < 8)
                continue; // Minimum distance

            // (Optional: Check Visibility / FOV if available)
            // For now, distance > 8 is a good enough proxy for "probably not in immediate
            // face"

            return new GridPoint2(x, y);
        }
        return null;
    }

    private void spawnItems(int budget) {
        if (itemPool.isEmpty())
            return;

        for (int i = 0; i < budget; i++) {
            GridPoint2 spawnPoint = getEmptySpawnPoint();
            if (spawnPoint == null)
                break;
            spawnRegularItem(spawnPoint);
        }
    }

    private void spawnRegularItem(GridPoint2 spawnPoint) {
        SpawnTableEntry entry = itemPool.getRandomEntry();
        if (entry == null)
            return;

        Item.ItemType type;
        try {
            type = Item.ItemType.valueOf(entry.type);
        } catch (IllegalArgumentException e) {
            return;
        }

        ItemVariant variant = itemDataManager.getRandomVariantForItem(type, level);
        if (variant == null)
            return;

        Item item = itemDataManager.createItem(type, spawnPoint.x, spawnPoint.y, variant.color, assetManager);
        attemptToModifyItem(item, variant.color);
        maze.addItem(item);
    }

    private void spawnContainers(int budget) {
        if (containerPool.isEmpty())
            return;

        for (int i = 0; i < budget; i++) {
            GridPoint2 spawnPoint = getEmptySpawnPoint();
            if (spawnPoint == null)
                break;

            SpawnTableEntry entry = containerPool.getRandomEntry();
            if (entry == null)
                continue;

            Item.ItemType type;
            try {
                type = Item.ItemType.valueOf(entry.type);
            } catch (IllegalArgumentException e) {
                continue;
            }

            ItemColor containerColor = entry.keyColor;
            if (containerColor == null)
                containerColor = ItemColor.CONTAINER_TAN;

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
        if (containerLootPool.isEmpty())
            return;

        SpawnTableEntry entry = containerLootPool.getRandomEntry();
        if (entry == null)
            return;

        Item.ItemType type;
        try {
            type = Item.ItemType.valueOf(entry.type);
        } catch (IllegalArgumentException e) {
            return;
        }

        ItemVariant variant = itemDataManager.getRandomVariantForItem(type, level);
        if (variant == null)
            return;

        Item loot = itemDataManager.createItem(type, 0, 0, variant.color, assetManager);
        attemptToModifyItem(loot, variant.color);
        container.getContents().add(loot);
    }

    private void spawnKey(ItemColor containerColor) {
        GridPoint2 spawnPoint = getEmptySpawnPoint();
        if (spawnPoint == null)
            return;

        Item key = itemDataManager.createItem(Item.ItemType.KEY, spawnPoint.x, spawnPoint.y, containerColor,
                assetManager);
        attemptToModifyItem(key, containerColor);
        maze.addItem(key);
    }

    private void attemptToModifyItem(Item item, ItemColor color) {
        // --- NEW: Random Beatitude ---
        float bRoll = random.nextFloat();
        if (bRoll < 0.10f) {
            item.setBeatitude(Item.Beatitude.CURSED);
        } else if (bRoll > 0.90f) {
            item.setBeatitude(Item.Beatitude.BLESSED);
        } else {
            item.setBeatitude(Item.Beatitude.UNCURSED);
        }
        // -----------------------------

        // --- NEW: Random Enchantment ---
        if (item.isWeapon() || item.isArmor() || item.isRing()) {
            // 15% Chance for enchantment (scaled with difficulty? kept simple for now)
            if (random.nextFloat() < 0.15f) {
                int mag = random.nextInt(3) + 1; // 1 to 3
                boolean positive = random.nextBoolean();

                // Bias positive at deeper levels
                if (level > 5 && random.nextFloat() < 0.7f)
                    positive = true;

                item.setEnchantment(positive ? mag : -mag);
            }
        }
        // -------------------------------

        float spawnChance = BASE_MODIFIER_CHANCE + ((color.getMultiplier() - 1.0f) * COLOR_MULTIPLIER_BONUS);
        if (!DEBUG_FORCE_MODIFIERS && random.nextFloat() > spawnChance)
            return;

        addRandomModifier(item);
        if (random.nextFloat() < SECOND_MODIFIER_CHANCE)
            addRandomModifier(item);
        if (random.nextFloat() < THIRD_MODIFIER_CHANCE)
            addRandomModifier(item);
    }

    private void addRandomModifier(Item item) {
        ItemCategory category = item.getCategory();
        List<LootTable.ModInfo> validMods = LootTable.MODIFIER_POOL.stream()
                .filter(mod -> mod.category == category)
                .filter(mod -> DEBUG_FORCE_MODIFIERS || (level >= mod.minLevel && level <= mod.maxLevel))
                .collect(Collectors.toList());

        if (validMods.isEmpty())
            return;

        LootTable.ModInfo modInfo = validMods.get(random.nextInt(validMods.size()));
        int value = 0;
        if (modInfo.maxBonus > modInfo.minBonus) {
            value = random.nextInt(modInfo.maxBonus - modInfo.minBonus + 1) + modInfo.minBonus;
        } else {
            value = modInfo.minBonus;
        }

        String displayName = modInfo.displayName;
        if (displayName.contains("+"))
            displayName = "+" + value;

        item.addModifier(new ItemModifier(modInfo.type, value, displayName));
    }

    private GridPoint2 getEmptySpawnPoint() {
        if (validSpawnPoints.isEmpty())
            return null;
        return validSpawnPoints.remove(0);
    }
}
