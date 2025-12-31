package com.bpm.minotaur.managers;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.math.GridPoint2;
import com.badlogic.gdx.utils.Array;
import com.bpm.minotaur.gamedata.*;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.Item.ItemType;
import com.bpm.minotaur.gamedata.item.ItemCategory;
import com.bpm.minotaur.gamedata.item.ItemColor;
import com.bpm.minotaur.gamedata.item.ItemDataManager;
import com.bpm.minotaur.gamedata.item.ItemModifier;
import com.bpm.minotaur.gamedata.item.ItemTemplate;
import com.bpm.minotaur.gamedata.item.ItemVariant;
import com.bpm.minotaur.gamedata.monster.Monster;
import com.bpm.minotaur.gamedata.monster.Monster.MonsterType;
import com.bpm.minotaur.gamedata.monster.MonsterColor;
import com.bpm.minotaur.gamedata.monster.MonsterDataManager;
import com.bpm.minotaur.gamedata.monster.MonsterTemplate;
import com.bpm.minotaur.gamedata.monster.MonsterVariant;
import com.bpm.minotaur.gamedata.player.Player;
import com.bpm.minotaur.gamedata.spawntables.LevelBudget;
import com.bpm.minotaur.gamedata.spawntables.SpawnTableData;
import com.bpm.minotaur.gamedata.spawntables.SpawnTableEntry;
import com.bpm.minotaur.gamedata.spawntables.WeightedRandomList;
import com.bpm.minotaur.generation.ItemSpawner;
import com.bpm.minotaur.generation.MonsterSpawner;
import com.bpm.minotaur.generation.MonsterFactory; // Added Import
import com.bpm.minotaur.generation.NetHackRNG;
import com.bpm.minotaur.generation.SpawnContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

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
    private final WeightedRandomList<SpawnTableEntry> containerPool;
    private final WeightedRandomList<SpawnTableEntry> containerLootPool;
    private final WeightedRandomList<SpawnTableEntry> debrisPool;
    private final Map<Integer, LevelBudget> budgetMap = new HashMap<>();

    public static boolean DEBUG_FORCE_MODIFIERS = false;

    private static final float BASE_MODIFIER_CHANCE = 0.15f;
    private static final float COLOR_MULTIPLIER_BONUS = 0.1f;
    private static final float SECOND_MODIFIER_CHANCE = 0.25f;
    private static final float THIRD_MODIFIER_CHANCE = 0.10f;

    // --- Unique Tracking ---
    public static final Set<String> uniqueMonstersSpawned = new HashSet<>();
    // ----------------------------

    private final int playerLevel;
    private final int playerLuck;

    // --- New Spawner Services ---
    private final MonsterSpawner monsterSpawner;
    private final ItemSpawner itemSpawner;
    private final MonsterFactory monsterFactory; // New
    private final NetHackRNG rng;

    public SpawnManager(MonsterDataManager dataManager, ItemDataManager itemDataManager, AssetManager assetManager,
            Maze maze, Difficulty difficulty, int level, int playerLevel, int playerLuck, String[] layout,
            SpawnTableData spawnTableData, long seed) {
        this.maze = maze;
        this.playerLevel = playerLevel;
        this.playerLuck = playerLuck;
        this.random.setSeed(seed);
        this.difficulty = difficulty;
        this.level = level;
        this.dataManager = dataManager;
        this.itemDataManager = itemDataManager;
        this.assetManager = assetManager;
        this.spawnTableData = spawnTableData;

        // Initialize UnlockManager with Data Manager for planning
        UnlockManager.getInstance().setItemDataManager(itemDataManager);

        // Initialize NetHack Services
        // 1. RNG
        this.rng = new NetHackRNG(new Random(seed));

        // 2. Build Registries
        Map<String, MonsterTemplate> monsterRegistry = new HashMap<>();
        for (MonsterType type : MonsterType.values()) {
            try {
                MonsterTemplate t = dataManager.getTemplate(type);
                monsterRegistry.put(type.name(), t);
            } catch (Exception e) {
                // Ignore missing templates
            }
        }

        Map<String, ItemTemplate> itemRegistry = new HashMap<>();
        for (ItemType type : ItemType.values()) {
            try {
                ItemTemplate t = itemDataManager.getTemplate(type);
                itemRegistry.put(type.name(), t);
            } catch (Exception e) {
                // Ignore missing templates
            }
        }

        // 3. Init Spawners
        this.monsterSpawner = new MonsterSpawner(monsterRegistry, rng);
        this.itemSpawner = new ItemSpawner(itemRegistry, rng);
        this.monsterFactory = new MonsterFactory(dataManager, itemDataManager, assetManager, rng, itemSpawner);

        findValidSpawnPoints(layout);

        // Build budget map
        for (LevelBudget b : spawnTableData.levelBudgets) {
            budgetMap.put(b.level, b);
        }
        this.budget = budgetMap.getOrDefault(level, spawnTableData.defaultBudget);

        // Pools (Legacy/Specific)
        this.containerPool = new WeightedRandomList<>();
        for (SpawnTableEntry entry : spawnTableData.containerSpawnTable) {
            if (level >= entry.minLevel && level <= entry.maxLevel) {
                containerPool.add(entry);
            }
        }

        this.debrisPool = new WeightedRandomList<>();
        if (spawnTableData.debrisSpawnTable != null) {
            for (SpawnTableEntry entry : spawnTableData.debrisSpawnTable) {
                if (entry.type.equals("MYSTERIOUS_PORTAL")) {
                    if (level < entry.minLevel || level > entry.maxLevel) {
                        continue;
                    }
                }
                debrisPool.add(entry);
            }
        }

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
        // Ensure logger is initialized
        SpawnLogger.getInstance();
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
        // Reduced global multiplier to avoid excessive density
        float globalMultiplier = 1.0f;

        spawnMonsters((int) (budget.monsterBudget * globalMultiplier)); // 1.0x monsters

        // Items were spamming (30+ on level 1). Reduced significantly.
        // Budget usually ~4-5. 2.0x = ~10 items.
        spawnItems((int) (budget.itemBudget * 2.0f * globalMultiplier));

        spawnContainers((int) (budget.containerBudget * 0.5f * globalMultiplier));

        // Debris was non-existent. Increased from 0.02 to 0.5.
        spawnDebris((int) (budget.debrisBudget * 0.5f * globalMultiplier));
    }

    // --- Helpers ---

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

    private SpawnContext createSpawnContext() {
        com.bpm.minotaur.gamedata.Alignment alignment = com.bpm.minotaur.gamedata.Alignment.NEUTRAL;
        boolean isGehennom = (level >= 25);
        boolean isContainer = false;

        return new SpawnContext(
                level,
                playerLevel,
                isGehennom,
                alignment,
                playerLuck,
                uniqueMonstersSpawned,
                isContainer);
    }

    private void spawnDebris(int budget) {
        if (debrisPool.isEmpty())
            return;

        for (int i = 0; i < budget; i++) {
            SpawnTableEntry entry = debrisPool.getRandomEntry(this.random);
            if (entry == null)
                continue;
            if (validSpawnPoints.isEmpty())
                continue;
            GridPoint2 spawnPoint = validSpawnPoints.get(random.nextInt(validSpawnPoints.size()));

            ItemType type;
            try {
                type = ItemType.valueOf(entry.type);
            } catch (IllegalArgumentException e) {
                continue;
            }

            ItemVariant variant = itemDataManager.getRandomVariantForItem(type, level);
            if (variant == null)
                continue;

            Item debris = itemDataManager.createItem(type, spawnPoint.x, spawnPoint.y, variant.color, assetManager);
            float offsetX = 0.2f + random.nextFloat() * 0.6f;
            float offsetY = 0.2f + random.nextFloat() * 0.6f;
            if (debris.getPosition() != null) {
                debris.getPosition().set(spawnPoint.x + offsetX, spawnPoint.y + offsetY);
            }
            maze.addItem(debris);

            SpawnLogger.getInstance().logItemSpawn(debris, "Debris Spawn");
        }
    }

    public static boolean predictPortalSpawn(long seed, int budget, WeightedRandomList<SpawnTableEntry> _debrisPool) {
        java.util.Random simRandom = new java.util.Random(seed);
        if (_debrisPool == null || _debrisPool.isEmpty())
            return false;
        for (int i = 0; i < budget; i++) {
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

        SpawnContext ctx = createSpawnContext();

        for (int i = 0; i < budget; i++) {
            GridPoint2 spawnPoint = getEmptySpawnPoint();
            if (spawnPoint == null)
                break;

            Optional<Map.Entry<String, MonsterTemplate>> result = monsterSpawner.spawnRandomMonster(ctx);
            if (result.isEmpty())
                continue;

            String monsterId = result.get().getKey();
            MonsterTemplate template = result.get().getValue();

            MonsterType type;
            try {
                type = MonsterType.valueOf(monsterId);
            } catch (Exception e) {
                continue;
            }

            // Use Factory
            Monster monster = monsterFactory.createMonster(type, level, ctx);

            // Set Position (Factory created it at 0,0)
            monster.getPosition().set(spawnPoint.x + 0.5f, spawnPoint.y + 0.5f);

            // Manual Scaling Removed - Factory handles it in createMonster via scaleStats
            // monster.scaleStats(level);

            maze.addMonster(monster);

            SpawnLogger.getInstance().logMonsterSpawn(monster, "Level Generation (Budget)");

            if ((template.generationFlags & MonsterTemplate.G_UNIQ) != 0) {
                uniqueMonstersSpawned.add(monsterId);
            }
        }
    }

    public void spawnPeriodicMonster(Player player) {
        GridPoint2 spawnPoint = findRuntimeSpawnPoint(player);
        if (spawnPoint == null)
            return;

        SpawnContext ctx = createSpawnContext();

        Optional<Map.Entry<String, MonsterTemplate>> result = monsterSpawner.spawnRandomMonster(ctx);
        if (result.isEmpty())
            return;

        String monsterId = result.get().getKey();
        MonsterTemplate template = result.get().getValue();

        MonsterType type;
        try {
            type = MonsterType.valueOf(monsterId);
        } catch (Exception e) {
            return;
        }

        if ((template.generationFlags & MonsterTemplate.G_UNIQ) != 0) {
            if (uniqueMonstersSpawned.contains(monsterId))
                return;
            uniqueMonstersSpawned.add(monsterId);
        }

        // Use Factory
        Monster monster = monsterFactory.createMonster(type, level, ctx);
        // Set Position
        monster.getPosition().set(spawnPoint.x + 0.5f, spawnPoint.y + 0.5f);

        maze.addMonster(monster);

        SpawnLogger.getInstance().logMonsterSpawn(monster, "Periodic Spawn (Runtime)");

        Gdx.app.log("SpawnManager", "Periodic Spawn: " + type + " at " + spawnPoint);
    }

    private GridPoint2 findRuntimeSpawnPoint(Player player) {
        int width = maze.getWidth();
        int height = maze.getHeight();
        int px = (int) player.getPosition().x;
        int py = (int) player.getPosition().y;
        for (int i = 0; i < 20; i++) {
            int x = random.nextInt(width);
            int y = random.nextInt(height);
            if (!maze.isPassable(x, y))
                continue;
            if (maze.getMonsters().containsKey(new GridPoint2(x, y)))
                continue;
            int dist = Math.abs(x - px) + Math.abs(y - py);
            if (dist < 8)
                continue;
            return new GridPoint2(x, y);
        }
        return null;
    }

    private void spawnItems(int budget) {
        SpawnContext ctx = createSpawnContext();

        for (int i = 0; i < budget; i++) {
            // --- NEW: Tarmin's Hunger Loot Decay ---
            if (random.nextFloat() > com.bpm.minotaur.managers.DoomManager.getInstance().getLootChanceMultiplier()) {
                continue; // Skip spawn due to hunger/decay
            }
            // ---------------------------------------

            GridPoint2 spawnPoint = getEmptySpawnPoint();
            if (spawnPoint == null)
                break;

            Map.Entry<String, ItemTemplate> result = itemSpawner.spawnItem(ctx);
            if (result == null)
                continue;

            String itemId = result.getKey();
            // ItemTemplate template = result.get().getValue();

            ItemType type;
            try {
                type = ItemType.valueOf(itemId);
            } catch (Exception e) {
                continue;
            }

            ItemVariant variant = itemDataManager.getRandomVariantForItem(type, level);
            if (variant == null)
                continue;

            Item item = itemDataManager.createItem(type, spawnPoint.x, spawnPoint.y, variant.color, assetManager);
            attemptToModifyItem(item, variant.color);
            maze.addItem(item);

            SpawnLogger.getInstance().logItemSpawn(item, "Level Generation (Budget)");
        }
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

            ItemType type;
            try {
                type = ItemType.valueOf(entry.type);
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

            SpawnLogger.getInstance().logItemSpawn(container, "Container (Level Gen)");

            if (container.isLocked()) {
                spawnKey(containerColor);
            }
        }
    }

    private void addLootToContainer(Item container) {
        // --- NEW: Tarmin's Hunger Loot Decay ---
        if (random.nextFloat() > com.bpm.minotaur.managers.DoomManager.getInstance().getLootChanceMultiplier()) {
            return; // Empty chest due to decay
        }
        // ---------------------------------------

        if (containerLootPool.isEmpty())
            return;
        SpawnTableEntry entry = containerLootPool.getRandomEntry();
        if (entry == null)
            return;
        ItemType type;
        try {
            type = ItemType.valueOf(entry.type);
        } catch (IllegalArgumentException e) {
            return;
        }
        ItemVariant variant = itemDataManager.getRandomVariantForItem(type, level);
        if (variant == null)
            return;
        Item loot = itemDataManager.createItem(type, 0, 0, variant.color, assetManager);
        attemptToModifyItem(loot, variant.color);
        container.getContents().add(loot);

        SpawnLogger.getInstance().logItemSpawn(loot, "Container Loot (Inside " + container.getDisplayName() + ")");
    }

    private void spawnKey(ItemColor containerColor) {
        GridPoint2 spawnPoint = getEmptySpawnPoint();
        if (spawnPoint == null)
            return;
        Item key = itemDataManager.createItem(ItemType.KEY, spawnPoint.x, spawnPoint.y, containerColor, assetManager);
        attemptToModifyItem(key, containerColor);
        maze.addItem(key);

        SpawnLogger.getInstance().logItemSpawn(key, "Container Key");
    }

    private void attemptToModifyItem(Item item, ItemColor color) {
        float bRoll = random.nextFloat();
        if (bRoll < 0.10f) {
            item.setBeatitude(Item.Beatitude.CURSED);
        } else if (bRoll > 0.90f) {
            item.setBeatitude(Item.Beatitude.BLESSED);
        } else {
            item.setBeatitude(Item.Beatitude.UNCURSED);
        }

        if (item.isWeapon() || item.isArmor() || item.isRing()) {
            if (random.nextFloat() < 0.15f) {
                int mag = random.nextInt(3) + 1;
                boolean positive = random.nextBoolean();
                if (level > 5 && random.nextFloat() < 0.7f)
                    positive = true;
                item.setEnchantment(positive ? mag : -mag);
            }
        }

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
