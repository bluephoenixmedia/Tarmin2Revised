package com.bpm.minotaur.generation;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.GridPoint2;
import com.badlogic.gdx.utils.Json;
import com.bpm.minotaur.gamedata.Alignment;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.item.Item.ItemType;
import com.bpm.minotaur.gamedata.item.ItemCategory;
import com.bpm.minotaur.gamedata.item.ItemDataManager;
import com.bpm.minotaur.gamedata.item.ItemTemplate;
import com.bpm.minotaur.gamedata.item.LootTiers;
import com.bpm.minotaur.gamedata.spawntables.LevelBudget;
import com.bpm.minotaur.gamedata.spawntables.SpawnTableData;
import com.bpm.minotaur.gamedata.spawntables.SpawnTableEntry;
import com.bpm.minotaur.gamedata.spawntables.WeightedRandomList;
import com.bpm.minotaur.managers.SpawnManager;
import com.bpm.minotaur.managers.UnlockManager;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.util.*;

import static org.junit.Assert.*;

/**
 * The loot pool (#196): a base pool from life one, unlock tiers, the early weapon, and the
 * consumable targets per depth-1 expedition, checked by sampling the real spawn data.
 */
public class LootPoolTest {

    /**
     * The expedition the targets are read against: four surface chunks near the shelter (EDL 1).
     * A fed start lasts about 750 turns (#179), which is about four chunks explored.
     */
    static final int CHUNKS_PER_EXPEDITION = 4;
    private static final int EXPEDITIONS = 2000;

    private File tempDir;
    private ItemDataManager itemDataManager;
    private SpawnTableData spawnTables;

    @Before
    public void setUp() throws Exception {
        if (Gdx.app == null) {
            Gdx.app = (Application) Proxy.newProxyInstance(Application.class.getClassLoader(),
                    new Class<?>[] { Application.class }, (proxy, method, args) -> null);
        }
        if (Gdx.files == null) {
            Gdx.files = (com.badlogic.gdx.Files) Proxy.newProxyInstance(
                    com.badlogic.gdx.Files.class.getClassLoader(),
                    new Class<?>[] { com.badlogic.gdx.Files.class },
                    (proxy, method, args) -> {
                        if ("local".equals(method.getName()) || "internal".equals(method.getName())) {
                            File file = new File((String) args[0]);
                            if (file.isAbsolute()) {
                                return new com.badlogic.gdx.files.FileHandle(file);
                            }
                            if (!file.exists()) {
                                file = new File("assets/" + args[0]);
                            }
                            if (!file.exists()) {
                                file = new File("../assets/" + args[0]);
                            }
                            return new com.badlogic.gdx.files.FileHandle(file);
                        }
                        return null;
                    });
        }
        tempDir = Files.createTempDirectory("loot_pool_").toFile();
        UnlockManager.getInstance().setSaveFile(new File(tempDir, "unlocks.json").getAbsolutePath());
        UnlockManager.getInstance().resetForTesting(); // a fresh save: nothing unlocked

        itemDataManager = new ItemDataManager();
        itemDataManager.load();
        itemDataManager.loadWeapons();
        itemDataManager.loadArmor();
        UnlockManager.getInstance().setItemDataManager(itemDataManager);
        spawnTables = new Json().fromJson(SpawnTableData.class, Gdx.files.internal("data/spawntables.json"));
    }

    @After
    public void tearDown() {
        UnlockManager.getInstance().setSaveFile(UnlockManager.DEFAULT_SAVE_FILE);
        UnlockManager.getInstance().resetForTesting();
        if (tempDir != null) {
            File[] files = tempDir.listFiles();
            if (files != null) {
                for (File f : files) {
                    f.delete();
                }
            }
            tempDir.delete();
        }
    }

    private Map<String, ItemTemplate> registry() {
        Map<String, ItemTemplate> registry = new HashMap<>();
        for (ItemType type : ItemType.values()) {
            try {
                ItemTemplate t = itemDataManager.getTemplate(type);
                if (t != null) {
                    registry.put(type.name(), t);
                }
            } catch (Exception ignored) {
                // Same as SpawnManager: a type without a template is skipped.
            }
        }
        return registry;
    }

    private static SpawnContext ctx(int depth, int edl) {
        return new SpawnContext(depth, 1, edl >= 25, Alignment.NEUTRAL, 0, new HashSet<>(), false, edl,
                null, 1);
    }

    // --- 1. The base pool ---

    @Test
    public void everyBasePoolEntryIsALoadedUngatedTemplate() {
        assertFalse("data/loot_pool.json lists the base pool", LootTiers.basePool().isEmpty());
        for (String name : LootTiers.basePool()) {
            ItemTemplate t = itemDataManager.getTemplate(ItemType.valueOf(name));
            assertNotNull(name, t);
            assertEquals(name + " is tier 0", 0, t.lootTier);
            assertFalse(name + " is not gated", t.unlockGated);
            assertTrue(name + " can spawn", t.probability > 0);
        }
    }

    @Test
    public void aFreshSaveFindsThePlainVersionOfEveryCategory() {
        ItemSpawner spawner = new ItemSpawner(registry(), new NetHackRNG(new Random(196)));
        for (ItemCategory c : new ItemCategory[] { ItemCategory.WAR_WEAPON, ItemCategory.SPIRITUAL_WEAPON,
                ItemCategory.ARMOR, ItemCategory.RING, ItemCategory.FOOD, ItemCategory.TOOL,
                ItemCategory.POTION, ItemCategory.AMMUNITION }) {
            assertNotNull(c + " has something on a fresh save", spawner.spawnItemByCategory(c));
        }

        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 20000; i++) {
            for (ItemCategory c : new ItemCategory[] { ItemCategory.WAR_WEAPON, ItemCategory.ARMOR }) {
                seen.add(spawner.spawnItemByCategory(c).getKey());
            }
        }
        int bodyArmour = 0;
        int swords = 0;
        boolean bow = false;
        boolean cloak = false;
        boolean shield = false;
        for (String id : seen) {
            ItemTemplate t = itemDataManager.getTemplate(ItemType.valueOf(id));
            if (t.isArmor && !t.isShield && !t.isHelmet && !t.isCloak && t.armorClassBonus >= 1) bodyArmour++;
            if (t.isWeapon && t.friendlyName.startsWith("Sword")) swords++;
            if (t.isWeapon && t.isRanged && t.friendlyName.startsWith("Bow")) bow = true;
            if (t.isCloak) cloak = true;
            if (t.isShield) shield = true;
        }
        assertTrue("light and medium body armour: " + bodyArmour, bodyArmour >= 3);
        assertTrue("a few sword types: " + swords, swords >= 3);
        assertTrue("a short bow", bow);
        assertTrue("a cloak", cloak);
        assertTrue("a buckler", shield);
        assertTrue("the Tinder Bundle is a tool",
                "TOOL".equals(itemDataManager.getTemplate(ItemType.TINDER_BUNDLE).category));
    }

    @Test
    public void everyFreshSaveWeaponMeetsTheFloor() {
        for (ItemType type : itemDataManager.getLoadedTypes()) {
            ItemTemplate t = itemDataManager.getTemplate(type);
            if (t.isWeapon && !t.isAmmunition && !t.unlockGated && t.probability > 0) {
                assertTrue(type + " " + t.damageDice + " is below 3.5",
                        EarlyWeapon.averageDamage(t.damageDice) >= EarlyWeapon.MIN_AVERAGE_DAMAGE);
            }
        }
    }

    // --- 2. Unlock tiers ---

    @Test
    public void tiersFollowScoreAboveTheBasePool() {
        assertEquals(0, LootTiers.tierForScore(UnlockManager.UNLOCK_SCORE_THRESHOLD - 1));
        assertEquals(1, LootTiers.tierForScore(UnlockManager.UNLOCK_SCORE_THRESHOLD));
        assertEquals(1, LootTiers.tierForScore(UnlockManager.UNLOCK_SCORE_THRESHOLD + 249));
        assertEquals(2, LootTiers.tierForScore(UnlockManager.UNLOCK_SCORE_THRESHOLD + 250));
        assertEquals("a long sword is a tier-2 upgrade", 2,
                itemDataManager.getTemplate(ItemType.SWORD_LONG).lootTier);
        assertTrue(itemDataManager.getTemplate(ItemType.SWORD_LONG).unlockGated);
        assertTrue("full plate is deep", itemDataManager.getTemplate(ItemType.FULL_PLATE).lootTier >= 4);
        assertEquals("the base pool overrides the score", 0,
                itemDataManager.getTemplate(ItemType.SWORD_SHORT).lootTier);
        assertEquals("the Father's Sword is never found", 1,
                itemDataManager.getTemplate(ItemType.RUSTY_SWORD).lootTier);
    }

    @Test
    public void aStrataOneRunUnlocksOnlyTierOne() {
        for (int run = 0; run < 30; run++) {
            for (ItemType type : UnlockManager.getInstance().rollRunUnlockTypes(null, 1)) {
                assertEquals(type + " granted after a Strata 1 run", 1,
                        itemDataManager.getTemplate(type).lootTier);
            }
        }
    }

    // --- 3. The early weapon ---

    @Test
    public void theEarlyWeaponIsAlwaysABasePoolMeleeWeaponOfAtLeastThreeAndAHalf() {
        assertFalse(EarlyWeapon.candidates(itemDataManager).isEmpty());
        Random r = new Random(1);
        Set<ItemType> picked = new HashSet<>();
        for (int i = 0; i < 500; i++) {
            ItemType type = EarlyWeapon.pick(itemDataManager, r);
            ItemTemplate t = itemDataManager.getTemplate(type);
            assertTrue(type + " is a weapon", t.isWeapon);
            assertFalse(type + " is melee", t.isRanged);
            assertFalse(type + " is in the base pool", t.unlockGated);
            assertTrue(type + " meets the floor",
                    EarlyWeapon.averageDamage(t.damageDice) >= EarlyWeapon.MIN_AVERAGE_DAMAGE);
            picked.add(type);
        }
        assertTrue("some variety: " + picked, picked.size() >= 3);
    }

    @Test
    public void theEarlyWeaponLiesOutsideTheShelterWithinAShortWalk() {
        for (int seed = 0; seed < 50; seed++) {
            Maze maze = new Maze(1, new int[24][24]);
            List<GridPoint2> home = new ArrayList<>();
            for (int x = 10; x <= 12; x++) {
                for (int y = 10; y <= 12; y++) {
                    home.add(new GridPoint2(x, y));
                }
            }
            maze.setHomeTiles(home);
            // Rock everywhere east of x = 18: never a target.
            for (int y = 0; y < 24; y++) {
                for (int x = 18; x < 24; x++) {
                    maze.getWallData()[y][x] = 0b01010101;
                }
            }
            GridPoint2 tile = EarlyWeapon.findTile(maze, new Random(seed));
            assertNotNull(tile);
            assertFalse("outside the shelter", maze.isHomeTile(tile.x, tile.y));
            assertTrue("not in rock", tile.x < 18);
            int steps = Math.max(0, Math.abs(tile.x - 11) - 1) + Math.max(0, Math.abs(tile.y - 11) - 1);
            assertTrue("a short walk from the shelter: " + tile, steps <= EarlyWeapon.MAX_STEPS);
        }
    }

    // --- 4. Consumable targets per depth-1 expedition (spawn sampling) ---

    /** Items one chunk places, rolled as SpawnManager rolls them (floor, containers, debris, herb). */
    private void sampleChunk(ItemSpawner spawner, Random random, int depth, int edl, Map<String, Integer> out) {
        LevelBudget budget = spawnTables.defaultBudget;
        for (LevelBudget b : spawnTables.levelBudgets) {
            if (b.level == edl) {
                budget = b;
            }
        }
        SpawnContext ctx = ctx(depth, edl);
        for (int i = 0; i < (int) (budget.itemBudget * 1.2f); i++) {
            Map.Entry<String, ItemTemplate> e = spawner.spawnItem(ctx);
            if (e == null || ItemType.SHOT_POUCH.name().equals(e.getKey())
                    || !FirearmSpawnRule.canSpawnAtDepth(e.getKey(), depth)) {
                continue;
            }
            out.merge(e.getKey(), 1, Integer::sum);
        }
        boolean containersHere = false;
        for (SpawnTableEntry c : spawnTables.containerSpawnTable) {
            containersHere |= edl >= c.minLevel && edl <= c.maxLevel;
        }
        WeightedRandomList<SpawnTableEntry> loot = SpawnManager.buildContainerLootPool(spawnTables, edl);
        for (int i = 0; containersHere && i < (int) (budget.containerBudget * 0.5f); i++) {
            out.merge(loot.getRandomEntry(random).type, 1, Integer::sum);
        }
        WeightedRandomList<SpawnTableEntry> debris = SpawnManager.buildLevelDebrisPool(spawnTables, edl);
        for (int i = 0; i < (int) (budget.debrisBudget * 0.5f); i++) {
            String type = debris.getRandomEntry(random).type;
            ItemTemplate t = itemDataManager.getTemplate(ItemType.valueOf(type));
            if (t.variants != null && !t.variants.isEmpty()) { // SpawnManager skips debris without variants
                out.merge(type, 1, Integer::sum);
            }
        }
        if (VitalityHerbRule.rollLichen(depth, random)) {
            out.merge(ItemType.GLOWING_LICHEN.name(), 1, Integer::sum);
        }
    }

    private List<Map<String, Integer>> sampleExpeditions(int depth, int edl, long seed) {
        ItemSpawner spawner = new ItemSpawner(registry(), new NetHackRNG(new Random(seed)));
        Random random = new Random(seed * 31 + 7);
        List<Map<String, Integer>> expeditions = new ArrayList<>();
        for (int e = 0; e < EXPEDITIONS; e++) {
            Map<String, Integer> found = new HashMap<>();
            for (int c = 0; c < CHUNKS_PER_EXPEDITION; c++) {
                sampleChunk(spawner, random, depth, edl, found);
            }
            expeditions.add(found);
        }
        return expeditions;
    }

    private static double mean(List<Map<String, Integer>> exps, String type) {
        return exps.stream().mapToInt(m -> m.getOrDefault(type, 0)).average().orElse(0);
    }

    @Test
    public void depthOneExpeditionsMeetTheConsumableTargets() {
        List<Map<String, Integer>> exps = sampleExpeditions(1, 1, 196);

        double rations = mean(exps, "FOOD");
        double healing = mean(exps, "POTION_OF_HEALING");
        double tinder = mean(exps, "TINDER_BUNDLE");
        double waterskins = mean(exps, "POTION_BLUE");
        double lichen = mean(exps, "GLOWING_LICHEN");
        double poultices = exps.stream().mapToInt(m -> Math.min(m.getOrDefault("MOSS_CLUMP", 0),
                m.getOrDefault("TWISTED_ROOT", 0)) + m.getOrDefault("GLOWING_LICHEN", 0)).average().orElse(0);
        double cloakChance = exps.stream().filter(m -> m.getOrDefault("CLOAK", 0) > 0).count() / (double) EXPEDITIONS;

        System.out.printf(Locale.ROOT, "#196 per depth-1 expedition (%d chunks, EDL 1): rations %.2f, "
                + "poultices %.2f (moss %.2f, root %.2f), healing potions %.2f, tinder %.2f, "
                + "cloak found in %.0f%%, waterskins %.2f, lichen %.2f%n",
                CHUNKS_PER_EXPEDITION, rations, poultices, mean(exps, "MOSS_CLUMP"), mean(exps, "TWISTED_ROOT"),
                healing, tinder, cloakChance * 100, waterskins, lichen);

        assertTrue("about 1 ration per 2 expeditions: " + rations, rations >= 0.3 && rations <= 0.7);
        assertTrue("herbs for about 1 Poultice: " + poultices, poultices >= 0.8 && poultices <= 1.5);
        assertTrue("about 1 healing potion per 2 expeditions: " + healing, healing >= 0.3 && healing <= 0.7);
        assertTrue("1-2 Tinder Bundles: " + tinder, tinder >= 1.0 && tinder <= 2.0);
        assertTrue("a cloak is likely: " + cloakChance, cloakChance >= 0.6);
        assertTrue("the Waterskin is replaceable: " + waterskins, waterskins >= 0.2);
        assertTrue("Vitality herbs are rare on the surface: " + lichen, lichen > 0 && lichen <= 0.3);
    }

    @Test
    public void vitalityHerbsComeAboutOncePerTwoStrataLevels() {
        List<Map<String, Integer>> levels = sampleExpeditions(2, 2, 197);
        double perLevel = mean(levels, "GLOWING_LICHEN");
        System.out.printf(Locale.ROOT, "#196 Glowing Lichen per strata level (%d chunks): %.2f%n",
                CHUNKS_PER_EXPEDITION, perLevel);
        assertTrue("about 1 per 2 strata levels: " + perLevel, perLevel >= 0.35 && perLevel <= 0.65);
    }
}
