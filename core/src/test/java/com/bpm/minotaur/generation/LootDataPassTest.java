package com.bpm.minotaur.generation;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.bpm.minotaur.gamedata.Alignment;
import com.bpm.minotaur.gamedata.item.Item.ItemType;
import com.bpm.minotaur.gamedata.item.ItemCategory;
import com.bpm.minotaur.gamedata.item.ItemDataManager;
import com.bpm.minotaur.gamedata.item.ItemTemplate;
import com.bpm.minotaur.gamedata.monster.Monster.MonsterType;
import com.bpm.minotaur.gamedata.monster.MonsterDataManager;
import com.bpm.minotaur.gamedata.monster.MonsterTemplate;
import com.bpm.minotaur.gamedata.spawntables.SpawnTableData;
import com.bpm.minotaur.managers.UnlockManager;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.*;

/**
 * The loot data pass (#195): the spawn slips from docs/spawn-chart-by-depth.md section 7
 * and the 0.0.2 weapon floor, checked against the real data files.
 */
public class LootDataPassTest {

    private File tempDir;
    private ItemDataManager itemDataManager;

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
        tempDir = Files.createTempDirectory("loot_pass_").toFile();
        UnlockManager.getInstance().setSaveFile(new File(tempDir, "unlocks.json").getAbsolutePath());
        UnlockManager.getInstance().resetForTesting(); // a fresh save: nothing unlocked

        itemDataManager = new ItemDataManager();
        itemDataManager.load();
        itemDataManager.loadWeapons();
        itemDataManager.loadArmor();
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

    // --- The spawn-sampling check: no category roll comes back empty ---

    @Test
    public void worldItemRollNeverComesBackEmptyOnAFreshSave() {
        ItemSpawner spawner = new ItemSpawner(registry(), new NetHackRNG(new Random(195)));
        for (int edl : new int[] { 1, 3, 6, 10, 15, 20, 30 }) {
            for (int i = 0; i < 1500; i++) {
                assertNotNull("world item roll came back empty at EDL " + edl,
                        spawner.spawnItem(ctx(Math.max(1, edl / 2), edl)));
            }
        }
    }

    @Test
    public void anEmptyCategoryIsRerolledRatherThanPlacingNothing() {
        Map<String, ItemTemplate> registry = new HashMap<>();
        ItemTemplate food = new ItemTemplate();
        food.friendlyName = "Food";
        food.isFood = true;
        food.probability = 10;
        registry.put("FOOD", food);
        ItemSpawner spawner = new ItemSpawner(registry, new NetHackRNG(new Random(7)));
        for (int i = 0; i < 300; i++) {
            Map.Entry<String, ItemTemplate> e = spawner.spawnItem(ctx(1, 1));
            if (e != null) {
                // SCROLL / BOOK may still come back as dynamic spell items.
                assertTrue(e.getKey(), Set.of("FOOD", "SCROLL", "BOOK").contains(e.getKey()));
            }
            assertNotNull("roll returned nothing although FOOD can always be placed", e);
        }
    }

    @Test
    public void amuletGoldAndToolCategoriesCanProduceItems() {
        Map<String, ItemTemplate> registry = new HashMap<>();
        ItemTemplate amulet = new ItemTemplate();
        amulet.isAmulet = true;
        amulet.probability = 10;
        registry.put("AMULET", amulet);
        ItemTemplate coins = new ItemTemplate();
        coins.isTreasure = true;
        coins.probability = 10;
        registry.put("COINS", coins);
        ItemTemplate lantern = new ItemTemplate();
        lantern.category = "TOOL";
        lantern.probability = 10;
        registry.put("BRASS_LANTERN", lantern);
        ItemSpawner spawner = new ItemSpawner(registry, new NetHackRNG(new Random(3)));
        assertEquals("AMULET", spawner.spawnItemByCategory(ItemCategory.AMULET).getKey());
        assertEquals("COINS", spawner.spawnItemByCategory(ItemCategory.GOLD).getKey());
        assertEquals("BRASS_LANTERN", spawner.spawnItemByCategory(ItemCategory.TOOL).getKey());
    }

    // --- Individual slips ---

    @Test
    public void unlockScoreParsesDiceWithAModifier() {
        assertEquals(300, UnlockManager.diceScore("1d6+1"));
        assertEquals(200, UnlockManager.diceScore("1d4+1"));
        assertEquals(400, UnlockManager.diceScore("2d4+2"));
        assertEquals(200, UnlockManager.diceScore("1d4"));
        assertEquals(0, UnlockManager.diceScore(null));
        ItemTemplate disk = itemDataManager.getTemplate(ItemType.DISK_CROSSBOW);
        assertTrue("1d6+1 weapons are no longer scored as baseValue alone", disk.unlockGated);
    }

    @Test
    public void aCrossbowIsNotAHolyWeapon() {
        Map<String, ItemTemplate> registry = new HashMap<>();
        ItemTemplate disk = new ItemTemplate();
        disk.friendlyName = "Disk, Crossbow";
        disk.isWeapon = true;
        disk.damageType = "PIERCING";
        disk.probability = 10;
        registry.put("DISK_CROSSBOW", disk);
        ItemSpawner spawner = new ItemSpawner(registry, new NetHackRNG(new Random(1)));
        assertNull(spawner.spawnItemByCategory(ItemCategory.SPIRITUAL_WEAPON));
        assertNotNull(spawner.spawnItemByCategory(ItemCategory.WAR_WEAPON));

        ItemTemplate cross = new ItemTemplate();
        cross.friendlyName = "Wooden Cross";
        cross.isWeapon = true;
        cross.probability = 10;
        registry.clear();
        registry.put("WOODEN_CROSS", cross);
        spawner = new ItemSpawner(registry, new NetHackRNG(new Random(1)));
        assertNotNull(spawner.spawnItemByCategory(ItemCategory.SPIRITUAL_WEAPON));
    }

    @Test
    public void explicitProbabilityZeroIsKeptAndAbsentDefaultsToTen() {
        ItemTemplate t = new ItemTemplate();
        ItemDataManager.applyDefaultProbability(new JsonReader().parse("{\"probability\": 0}"), t);
        assertEquals(0, t.probability);
        ItemDataManager.applyDefaultProbability(new JsonReader().parse("{}"), t);
        assertEquals(ItemDataManager.DEFAULT_PROBABILITY, t.probability);
        assertEquals("an ordinary weapon keeps the default weight",
                ItemDataManager.DEFAULT_PROBABILITY, itemDataManager.getTemplate(ItemType.DAGGER).probability);
    }

    @Test
    public void apostropheWeaponKeysLoad() {
        ItemTemplate bards = itemDataManager.getTemplate(ItemType.BARDS_FRIEND);
        assertNotNull(bards);
        assertTrue("BARDS_FRIEND loads from weapons.json rather than a stub", bards.isWeapon);
        assertTrue(itemDataManager.getTemplate(ItemType.MACE_FOOTMANS).isWeapon);
        assertTrue(itemDataManager.getTemplate(ItemType.MACE_HORSEMANS_WHISTLING).isWeapon);
    }

    @Test
    public void weaponsJsonNoLongerOverridesTheScroll() {
        ItemTemplate scroll = itemDataManager.getTemplate(ItemType.SCROLL);
        assertFalse(scroll.isWeapon);
        assertTrue(scroll.isScrollAppearance);
    }

    @Test
    public void cookedMeatIsFoodNotAPotion() {
        ItemTemplate meat = itemDataManager.getTemplate(ItemType.COOKED_MEAT);
        assertFalse(meat.isPotion);
        assertTrue(meat.isFood);
    }

    @Test
    public void itemsWithoutVariantsStillGetASpawnColour() {
        assertTrue(itemDataManager.getTemplate(ItemType.TINDER_BUNDLE).variants == null
                || itemDataManager.getTemplate(ItemType.TINDER_BUNDLE).variants.isEmpty());
        assertNotNull(itemDataManager.getSpawnColor(ItemType.TINDER_BUNDLE, 1));
        assertNotNull(itemDataManager.getSpawnColor(ItemType.MEAT, 6));
    }

    @Test
    public void defaultItemBudgetMatchesTheDeepestAuthoredLevel() {
        SpawnTableData data = new Json().fromJson(SpawnTableData.class, Gdx.files.internal("data/spawntables.json"));
        int deepest = 0;
        int deepestItemBudget = 0;
        for (com.bpm.minotaur.gamedata.spawntables.LevelBudget b : data.levelBudgets) {
            if (b.level > deepest) {
                deepest = b.level;
                deepestItemBudget = b.itemBudget;
            }
        }
        assertEquals(deepestItemBudget, data.defaultBudget.itemBudget);
    }

    @Test
    public void giantInsectsDoNotCarryWeapons() {
        assertTrue(MonsterFactory.isUserOfWeapons(MonsterType.GIANT));
        assertTrue(MonsterFactory.isUserOfWeapons(MonsterType.ORC));
        for (MonsterType t : MonsterType.values()) {
            if (t.name().startsWith("GIANT_")) {
                assertFalse(t + " should not be armed", MonsterFactory.isUserOfWeapons(t));
            }
        }
    }

    @Test
    public void generationFlagsAreSetInTheData() {
        MonsterDataManager mdm = new MonsterDataManager();
        mdm.load();
        MonsterTemplate mimic = mdm.getTemplate(MonsterType.MIMIC);
        assertTrue("mimics arrive disguised as chests, never from the random roll",
                (mimic.generationFlags & MonsterTemplate.G_NOGEN) != 0);
        MonsterTemplate minotaur = mdm.getTemplate(MonsterType.MINOTAUR);
        assertTrue((minotaur.generationFlags & MonsterTemplate.G_UNIQ) != 0);
    }

    // --- Weapon floor ---

    private static final Pattern DICE = Pattern.compile("(\\d+)d(\\d+)([+-]\\d+)?");

    private static double mean(String dice) {
        Matcher m = DICE.matcher(dice.trim());
        assertTrue("unparseable dice " + dice, m.matches());
        int n = Integer.parseInt(m.group(1));
        int s = Integer.parseInt(m.group(2));
        int k = m.group(3) == null ? 0 : Integer.parseInt(m.group(3));
        return n * (s + 1) / 2.0 + k;
    }

    @Test
    public void everyWeaponButTheFathersSwordAveragesAtLeastThreeAndAHalf() throws Exception {
        File file = new File("assets/data/weapons.json");
        if (!file.exists()) {
            file = new File("../assets/data/weapons.json");
        }
        JsonValue root = new JsonReader().parse(Files.readString(file.toPath()));
        List<String> below = new ArrayList<>();
        for (JsonValue e = root.child; e != null; e = e.next) {
            if (!e.getBoolean("isWeapon", false) || "RUSTY_SWORD".equals(e.name)) {
                continue; // RUSTY_SWORD is the Father's Sword, the one weapon kept weakest.
            }
            String dice = e.getString("damageDice", "1d4");
            if (mean(dice) < 3.5) {
                below.add(e.name + " " + dice);
            }
        }
        assertTrue("weapons below the 3.5 floor: " + below, below.isEmpty());
    }
}
