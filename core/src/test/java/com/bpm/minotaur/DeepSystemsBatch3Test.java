package com.bpm.minotaur;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.bpm.minotaur.gamedata.ChunkData;
import com.bpm.minotaur.gamedata.DamageType;
import com.bpm.minotaur.gamedata.Scenery;
import com.bpm.minotaur.gamedata.ShopInventory;
import com.bpm.minotaur.gamedata.ShopkeeperNpc;
import com.bpm.minotaur.gamedata.item.GearComparison;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.ItemColor;
import com.bpm.minotaur.gamedata.item.ItemDataManager;
import com.bpm.minotaur.gamedata.monster.Monster;
import com.bpm.minotaur.gamedata.player.Player;
import com.bpm.minotaur.managers.UnlockManager;
import com.bpm.minotaur.rendering.animation.AnimationArchetype;
import com.bpm.minotaur.rendering.animation.CombatMotionProfile;
import com.bpm.minotaur.rendering.animation.SpecialMoveRegistry;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.List;

import static org.junit.Assert.*;

public class DeepSystemsBatch3Test {

    private static Application mockApp;
    private static com.badlogic.gdx.Files mockFiles;
    private ItemDataManager itemDataManager;

    @BeforeClass
    public static void setUpClass() {
        if (Gdx.app == null) {
            mockApp = (Application) Proxy.newProxyInstance(
                    Application.class.getClassLoader(),
                    new Class<?>[]{Application.class},
                    (proxy, method, methodArgs) -> {
                        if ("getType".equals(method.getName())) {
                            return Application.ApplicationType.HeadlessDesktop;
                        }
                        return null;
                    });
            Gdx.app = mockApp;
        }
        if (Gdx.files == null) {
            mockFiles = (com.badlogic.gdx.Files) Proxy.newProxyInstance(
                    com.badlogic.gdx.Files.class.getClassLoader(),
                    new Class<?>[]{com.badlogic.gdx.Files.class},
                    (proxy, method, args) -> {
                        if ("local".equals(method.getName()) || "internal".equals(method.getName())) {
                            String path = (String) args[0];
                            java.io.File file = new java.io.File(path);
                            if (file.isAbsolute()) {
                                return new com.badlogic.gdx.files.FileHandle(file);
                            }
                            if (!file.exists()) {
                                java.io.File assetsFile = new java.io.File("assets/" + path);
                                if (assetsFile.exists()) {
                                    return new com.badlogic.gdx.files.FileHandle(assetsFile);
                                }
                                java.io.File parentAssetsFile = new java.io.File("../assets/" + path);
                                if (parentAssetsFile.exists()) {
                                    return new com.badlogic.gdx.files.FileHandle(parentAssetsFile);
                                }
                            }
                            return new com.badlogic.gdx.files.FileHandle(file);
                        }
                        return null;
                    }
            );
            Gdx.files = mockFiles;
        }
    }

    @AfterClass
    public static void tearDownClass() {
        if (Gdx.app == mockApp) {
            Gdx.app = null;
        }
        if (Gdx.files == mockFiles) {
            Gdx.files = null;
        }
    }

    @Before
    public void setUp() {
        itemDataManager = new ItemDataManager();
        itemDataManager.load();
        itemDataManager.loadWeapons();
        itemDataManager.loadArmor();
        UnlockManager.getInstance().setItemDataManager(itemDataManager);
    }

    @Test
    public void testItem36_MerchantStockFiltersUnlockedPool() {
        ShopkeeperNpc shopkeeper = new ShopkeeperNpc(1f, 1f, null);
        ShopInventory shopInventory = new ShopInventory();
        shopInventory.stock(shopkeeper, itemDataManager, null, 1);

        List<Item> stock = shopkeeper.getInventory().getAllItems();
        assertFalse("Shopkeeper inventory must not be empty", stock.isEmpty());
        for (Item item : stock) {
            assertTrue("Item in merchant stock must be unlocked: " + item.getType().name(),
                    UnlockManager.getInstance().isUnlocked(item.getType().name()));
        }
    }

    @Test
    public void testItem51_MonsterCorpsePersistenceAndScenery() {
        Scenery corpse = new Scenery(Scenery.SceneryType.DECOMPOSING_CORPSE, 5, 8, "images/scenery/decomposing_corpse.png");
        corpse.setImpassable(false);
        corpse.setCorpseMonsterName("GOBLIN");

        assertTrue("Should identify as corpse", corpse.isCorpse());
        assertEquals("GOBLIN", corpse.getCorpseMonsterName());
        assertFalse("Monster corpse must be passable so corridors are never blocked", corpse.isImpassable());

        // Test serialization round-trip in SceneryData
        ChunkData.SceneryData data = new ChunkData.SceneryData(corpse);
        assertEquals(Scenery.SceneryType.DECOMPOSING_CORPSE, data.type);
        assertEquals("GOBLIN", data.corpseMonsterName);
        assertFalse(data.impassable);

        Scenery restored = new Scenery(data.type, data.x, data.y, data.texturePath);
        if (data.corpseMonsterName != null) restored.setCorpseMonsterName(data.corpseMonsterName);
        if (data.impassable != null) restored.setImpassable(data.impassable);

        assertEquals(Scenery.SceneryType.DECOMPOSING_CORPSE, restored.getType());
        assertEquals("GOBLIN", restored.getCorpseMonsterName());
        assertFalse(restored.isImpassable());
    }

    @Test
    public void testItem48_KeyVarietyAndContainerLocks() {
        Item ironKey = new Item(Item.ItemType.IRON_KEY, 0, 0, ItemColor.GRAY, itemDataManager, null);
        Item silverKey = new Item(Item.ItemType.SILVER_KEY, 0, 0, ItemColor.WHITE, itemDataManager, null);
        Item goldKey = new Item(Item.ItemType.GOLD_KEY, 0, 0, ItemColor.GOLD, itemDataManager, null);
        Item skeletonKey = new Item(Item.ItemType.SKELETON_KEY, 0, 0, ItemColor.GRAY, itemDataManager, null);

        assertTrue(ironKey.isKey());
        assertTrue(silverKey.isKey());
        assertTrue(goldKey.isKey());
        assertTrue(skeletonKey.isKey());

        // Create a chest requiring a GOLD_KEY
        Item chest = new Item(Item.ItemType.REGULAR_CHEST, 0, 0, ItemColor.GOLD, itemDataManager, null);
        chest.setRequiredKeyType(Item.ItemType.GOLD_KEY);

        // Wrong keys should fail
        assertFalse("Iron key cannot open gold chest", chest.unlocks(ironKey));
        assertFalse("Silver key cannot open gold chest", chest.unlocks(silverKey));

        // Matching key should unlock
        assertTrue("Gold key opens gold chest", chest.unlocks(goldKey));

        // Skeleton key unlocks ANY container
        assertTrue("Skeleton key opens any locked chest", chest.unlocks(skeletonKey));
    }

    @Test
    public void testItem50_GearComparisonIndicators() {
        Player player = new Player(0, 0);

        // Equip a 1d8 weapon (Longsword: avg 4.5)
        Item longsword = new Item(Item.ItemType.SWORD_LONG, 0, 0, ItemColor.GRAY, itemDataManager, null);
        player.getInventory().setRightHand(longsword);

        // Compare a 2d6 weapon on the ground (Two-Handed Sword: avg 7.0, better)
        Item greatsword = new Item(Item.ItemType.SWORD_TWO_HANDED, 0, 0, ItemColor.GRAY, itemDataManager, null);
        GearComparison.ComparisonResult betterResult = GearComparison.compare(greatsword, player);

        assertNotNull(betterResult);
        assertTrue("Should be upgrade", betterResult.isUpgrade);
        assertFalse(betterResult.isDowngrade);
        // The badge used to be a black up-pointing triangle. intellivision.ttf has no glyph
        // for it, so on the world interaction card it rendered as a missing-glyph box -- the
        // one place this badge is ever shown. It says UP and DOWN now (RC4).
        assertTrue("Badge should mark an upgrade", betterResult.badge.contains("UP"));
        assertEquals("Badge must be renderable in the game font",
                -1, com.bpm.minotaur.ui.UiGlyphs.firstUnsupportedIndex(betterResult.badge));
        assertEquals(GearComparison.COLOR_UPGRADE, betterResult.color);

        // Compare a 1d4 weapon on the ground (Dagger: avg 2.5, worse)
        Item dagger = new Item(Item.ItemType.DAGGER, 0, 0, ItemColor.GRAY, itemDataManager, null);
        GearComparison.ComparisonResult worseResult = GearComparison.compare(dagger, player);

        assertNotNull(worseResult);
        assertFalse(worseResult.isUpgrade);
        assertTrue("Should be downgrade", worseResult.isDowngrade);
        assertTrue("Badge should mark a downgrade", worseResult.badge.contains("DOWN"));
        assertEquals("Badge must be renderable in the game font",
                -1, com.bpm.minotaur.ui.UiGlyphs.firstUnsupportedIndex(worseResult.badge));
        assertEquals(GearComparison.COLOR_DOWNGRADE, worseResult.color);

        // Compare identical weapon (equal)
        Item sameLongsword = new Item(Item.ItemType.SWORD_LONG, 0, 0, ItemColor.GRAY, itemDataManager, null);
        GearComparison.ComparisonResult equalResult = GearComparison.compare(sameLongsword, player);

        assertNotNull(equalResult);
        assertFalse(equalResult.isUpgrade);
        assertFalse(equalResult.isDowngrade);
        assertTrue("Badge should contain =", equalResult.badge.contains("="));
        assertEquals(GearComparison.COLOR_SIDEGRADE, equalResult.color);
    }

    @Test
    public void testItem40_SpecialMoveRegistryVariety() {
        for (AnimationArchetype archetype : AnimationArchetype.values()) {
            String[] reg = SpecialMoveRegistry.getMovesForArchetype(archetype);
            String[] fin = SpecialMoveRegistry.getFinishersForArchetype(archetype);
            String[] crit = SpecialMoveRegistry.getCritsForArchetype(archetype);

            assertTrue("Archetype " + archetype + " must have regular moves", reg.length > 0);
            assertTrue("Archetype " + archetype + " must have finisher moves", fin.length > 0);
            assertTrue("Archetype " + archetype + " must have crit moves", crit.length > 0);

            // Test resolveMoveName
            CombatMotionProfile profile = new CombatMotionProfile();
            profile.isFinisher = true;
            String resolvedFinisher = SpecialMoveRegistry.resolveMoveName(profile, null, false);
            assertNotNull(resolvedFinisher);
            assertFalse(resolvedFinisher.isEmpty());

            String resolvedCrit = SpecialMoveRegistry.resolveMoveName(profile, null, true);
            assertNotNull(resolvedCrit);
            assertFalse(resolvedCrit.isEmpty());
        }
    }

    @Test
    public void testItem49_MonsterBleedAndStunAspects() {
        Monster monster = new Monster(Monster.MonsterType.GOBLIN, 30, 10);

        // Test Bleed
        monster.applyBleed(3, 4);
        assertEquals(3, monster.getBleedTurns());
        assertEquals(4, monster.getBleedDamagePerTurn());

        int dmg1 = monster.applyBleedTick();
        assertTrue("Bleed must deal damage", dmg1 > 0);
        assertEquals(2, monster.getBleedTurns());

        // Test Stun
        assertFalse(monster.isStunned());
        monster.applyStun(2);
        assertTrue(monster.isStunned());
        assertEquals(2, monster.getStunTurns());

        monster.decrementStun();
        assertTrue(monster.isStunned());
        assertEquals(1, monster.getStunTurns());

        monster.decrementStun();
        assertFalse(monster.isStunned());
        assertEquals(0, monster.getStunTurns());
    }

    @Test
    public void testItem49_PiercingArmorSoakBypass() {
        Monster armored = new Monster(Monster.MonsterType.GOBLIN, 30, 18);
        // AC 18 has soak of (18 - 14) / 2 = 2

        // Standard non-crit attack with 10 damage: deals 10 - 2 = 8
        int normalDamage = armored.takeDamage(10, false, false);
        assertEquals("Standard attack should have soak reduction of 2", 8, normalDamage);

        // Piercing attack with 10 damage: ignores soak, deals 10
        int piercingDamage = armored.takeDamage(10, false, true);
        assertEquals("Piercing attack should bypass armor soak", 10, piercingDamage);
    }

    @Test
    public void testItem49_SwordOpen5eCalibration() {
        Item sword = new Item(Item.ItemType.SWORD, 0, 0, ItemColor.GRAY, itemDataManager, null);
        assertEquals("1d8", sword.getDamageDice());
        assertTrue("Sword must be versatile", sword.isVersatile());
        assertEquals("1d10", sword.getVersatileDamageDice());
        assertEquals("SLASHING", sword.getDamageType());
    }
}
