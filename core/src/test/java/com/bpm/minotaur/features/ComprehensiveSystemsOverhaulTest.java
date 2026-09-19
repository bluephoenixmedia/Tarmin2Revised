package com.bpm.minotaur.features;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.ShopInventory;
import com.bpm.minotaur.gamedata.ShopkeeperNpc;
import com.bpm.minotaur.gamedata.effects.StatusEffectType;
import com.bpm.minotaur.gamedata.encounters.Encounter;
import com.bpm.minotaur.gamedata.encounters.EncounterManager;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.Item.ItemType;
import com.bpm.minotaur.gamedata.item.ItemColor;
import com.bpm.minotaur.gamedata.item.ItemDataManager;
import com.bpm.minotaur.gamedata.item.ItemTemplate;
import com.bpm.minotaur.gamedata.player.Player;
import com.bpm.minotaur.gamedata.player.PlayerStats;
import com.bpm.minotaur.gamedata.progression.ShelterAltar;
import com.bpm.minotaur.generation.MazeChunkGenerator;
import com.bpm.minotaur.managers.CraftingManager;
import com.bpm.minotaur.managers.DivinityManager;
import com.bpm.minotaur.managers.GameEventManager;
import com.bpm.minotaur.managers.TurnManager;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * Integration coverage for the "Comprehensive Systems Overhaul" plan:
 * Traveling Merchant, Statue Encounters, Alchemy Cauldron, Spell Inscription
 * & Arcane Attunement, and Weather Survival Exposure.
 */
public class ComprehensiveSystemsOverhaulTest {

    @Before
    public void setUp() {
        if (Gdx.app == null) {
            Gdx.app = (Application) Proxy.newProxyInstance(
                    Application.class.getClassLoader(),
                    new Class<?>[]{Application.class},
                    (proxy, method, args) -> null
            );
        }
        if (Gdx.files == null) {
            Gdx.files = (com.badlogic.gdx.Files) Proxy.newProxyInstance(
                    com.badlogic.gdx.Files.class.getClassLoader(),
                    new Class<?>[]{com.badlogic.gdx.Files.class},
                    (proxy, method, args) -> {
                        if ("local".equals(method.getName()) || "internal".equals(method.getName())) {
                            String path = (String) args[0];
                            java.io.File file = new java.io.File(path);
                            if (!file.exists()) file = new java.io.File("assets/" + path);
                            if (!file.exists()) file = new java.io.File("../assets/" + path);
                            return new com.badlogic.gdx.files.FileHandle(file);
                        }
                        return null;
                    }
            );
        }
        ShelterAltar.getInstance().reset();
    }

    @org.junit.After
    public void tearDown() {
        Gdx.app = null;
        Gdx.files = null;
        ShelterAltar.getInstance().reset();
    }

    private ItemDataManager mockItemDataManager() {
        return new ItemDataManager() {
            @Override
            public Item createItem(ItemType type, int x, int y, ItemColor color, com.badlogic.gdx.assets.AssetManager assetManager) {
                ItemTemplate t = new ItemTemplate();
                t.friendlyName = type.name();
                t.baseValue = 50;
                return Item.fromTemplate(type, t);
            }
        };
    }

    // ── Component 1: Traveling Merchant ─────────────────────────────────────

    @Test
    public void testShopkeeperSpawnsOnlyOnStrataOneAndTwo() {
        MazeChunkGenerator generator = new MazeChunkGenerator();
        Maze deepMaze = new Maze(5, new int[16][16]);
        Set<GridPoint2> reachable = buildOpenReachableSet(16);
        String[] layout = buildOpenLayout(16);

        generator.spawnShopkeeper(deepMaze, layout, reachable, mockItemDataManager(), null);
        assertNull("Merchant should not spawn below Strata 2", deepMaze.getShopkeeper());

        Maze shallowMaze = new Maze(1, new int[16][16]);
        generator.spawnShopkeeper(shallowMaze, layout, reachable, mockItemDataManager(), null);
        assertNotNull("Merchant should spawn on Strata 1", shallowMaze.getShopkeeper());
        assertTrue(shallowMaze.getShopkeeper().isAlive());
    }

    @Test
    public void testShopkeeperDeathDropsGoldAndWares() {
        ShopkeeperNpc shopkeeper = new ShopkeeperNpc(3, 3, null);
        ItemDataManager idm = mockItemDataManager();
        new ShopInventory().stock(shopkeeper, idm, null, 1);
        assertFalse("Merchant should be stocked with wares", shopkeeper.getInventory().getAllItems().isEmpty());

        shopkeeper.takeDamage(9999);
        assertFalse("Merchant should be dead after lethal damage", shopkeeper.isAlive());

        List<Item> drops = shopkeeper.createDeathDrops(idm, null);
        assertFalse("Death drops should include a gold pouch and wares", drops.isEmpty());
    }

    // ── Component 2: Statue Encounters ──────────────────────────────────────

    @Test
    public void testAllFifteenStatueEncountersLoadWithValidChoices() {
        EncounterManager manager = new EncounterManager();
        manager.load();

        String[] expectedIds = {
                "EVENT_WEEPING_STATUE", "EVENT_BROKEN_MIRROR", "EVENT_DRAGON_STATUE",
                "EVENT_TARMIN_STATUE", "EVENT_SKELETON_STATUE", "EVENT_DWARF_STATUE",
                "EVENT_GHOST_STATUE", "EVENT_GHOUL_STATUE", "EVENT_SNAKE_STATUE",
                "EVENT_GIANT_STATUE",
                "EVENT_ANT_STATUE", "EVENT_CROC_STATUE", "EVENT_SCORPION_STATUE",
                "EVENT_WRAITH_STATUE", "EVENT_OLD_DWARF_STATUE"
        };
        assertEquals(15, expectedIds.length);

        for (String id : expectedIds) {
            Encounter enc = manager.getEncounter(id);
            assertNotNull("Encounter " + id + " should be loaded", enc);
            assertNotNull("Encounter " + id + " should have choices", enc.choices);
            assertFalse("Encounter " + id + " should offer at least one choice", enc.choices.isEmpty());
        }
    }

    // ── Component 3: Alchemy Cauldron ───────────────────────────────────────

    @Test
    public void testAlchemyRecipesCoverAllFourCategories() {
        CraftingManager craftingManager = new CraftingManager(mockItemDataManager(), null);
        List<CraftingManager.AlchemyRecipe> recipes = craftingManager.getAlchemyRecipes();
        assertFalse(recipes.isEmpty());

        Set<CraftingManager.AlchemyCategory> seen = new HashSet<>();
        for (CraftingManager.AlchemyRecipe r : recipes) {
            seen.add(r.category);
        }
        for (CraftingManager.AlchemyCategory cat : CraftingManager.AlchemyCategory.values()) {
            assertTrue("Recipe codex should include a " + cat + " recipe", seen.contains(cat));
        }
    }

    @Test
    public void testBrewPotionConsumesPooledReagentsAndDepositsPotion() {
        CraftingManager craftingManager = new CraftingManager(mockItemDataManager(), null);
        Player player = new Player(2, 2);

        CraftingManager.AlchemyRecipe healing = craftingManager.getAlchemyRecipes().stream()
                .filter(r -> r.output == ItemType.POTION_OF_HEALING)
                .findFirst().orElseThrow();

        assertFalse("Should not be able to brew without reagents", craftingManager.canBrew(player, healing));

        for (java.util.Map.Entry<ItemType, Integer> req : healing.reagents.entrySet()) {
            for (int i = 0; i < req.getValue(); i++) {
                player.getInventory().pickupToBackpack(mockItemDataManager().createItem(req.getKey(), 0, 0, null, null));
            }
        }
        assertTrue("Should be able to brew with reagents on hand", craftingManager.canBrew(player, healing));

        Item potion = craftingManager.brewPotion(player, healing);
        assertNotNull("Brewing should produce a potion", potion);
        assertEquals(ItemType.POTION_OF_HEALING, potion.getType());
        assertFalse("Reagents should be consumed", craftingManager.canBrew(player, healing));
    }

    // ── Component 4: Spell Inscription & Arcane Attunement ──────────────────

    private Item magicMissileScroll(Player player) {
        ItemTemplate scrollTmpl = new ItemTemplate();
        scrollTmpl.friendlyName = "Scroll of Magic Missile";
        Item scroll = Item.fromTemplate(ItemType.SCROLL_MAGIC_MISSILE, scrollTmpl);
        player.getInventory().pickup(scroll);
        return scroll;
    }

    @Test
    public void testInscribeScrollLearnsIntoSpellbookWithoutAFreeSlot() {
        Player player = new Player(2, 2);
        GameEventManager eventManager = new GameEventManager();
        Item scroll = magicMissileScroll(player);

        player.getStats().setCurrentMP(0);
        assertFalse("Inscription should fail without enough MP", player.inscribeScroll(scroll, eventManager, null));
        assertFalse(player.getKnownSpellIds().contains("MAGIC_MISSILE"));

        // Slot 1 holds the starting cantrip and is the only unlocked slot.
        player.getStats().setCurrentMP(player.getStats().getMaxMP());
        int mpBefore = player.getStats().getCurrentMP();
        assertTrue("Inscription should succeed with enough MP even when every slot is full",
                player.inscribeScroll(scroll, eventManager, null));
        assertTrue(player.getKnownSpellIds().contains("MAGIC_MISSILE"));
        assertEquals("The full slot keeps its spell", "MOTE_OF_LIGHT", player.getPreparedSpell(0));
        assertTrue("Full MP cost is paid", player.getStats().getCurrentMP() < mpBefore);
        assertFalse(player.getInventory().getMainInventory().contains(scroll));
    }

    @Test
    public void testInscribeScrollFillsAnEmptyUnlockedSlot() {
        Player player = new Player(2, 2);
        GameEventManager eventManager = new GameEventManager();
        Item scroll = magicMissileScroll(player);
        player.setUnlockedSpellSlots(2);
        player.getStats().setCurrentMP(player.getStats().getMaxMP());

        assertTrue(player.inscribeScroll(scroll, eventManager, null));
        assertEquals("MAGIC_MISSILE", player.getPreparedSpell(1));
    }

    @Test
    public void testArcaneAttunementUnsealsCircleWithoutGrantingASlot() {
        Player player = new Player(2, 2);
        DivinityManager.getInstance().addDivinities(100);
        ShelterAltar altar = ShelterAltar.getInstance();

        int slotsBefore = player.getUnlockedSpellSlots();
        assertTrue(altar.isSpellSealed("MAGIC_MISSILE"));

        assertTrue(altar.purchaseUpgrade(ShelterAltar.Tree.ARCANE_ATTUNEMENT));
        assertEquals("Only Tomes unlock Spell Slots", slotsBefore, player.getUnlockedSpellSlots());
        assertEquals(1, altar.getTier(ShelterAltar.Tree.ARCANE_ATTUNEMENT));
        assertFalse("Circle 1 spells should unseal at Tier 1", altar.isSpellSealed("MAGIC_MISSILE"));
        assertTrue("Circle 2 spells remain sealed at Tier 1", altar.isSpellSealed("MISTY_STEP"));
    }

    // ── Component 6: Weather Survival Exposure ──────────────────────────────

    @Test
    public void testChilledAppliesMoveSpeedPenalty() {
        Player player = new Player(2, 2);
        int baseSpeed = player.getEffectiveSpeed();

        player.getStatusManager().addEffect(StatusEffectType.CHILLED, 999, 1, false);
        int chilledSpeed = player.getEffectiveSpeed();

        assertTrue("CHILLED should reduce effective speed", chilledSpeed < baseSpeed);
        assertEquals((int) (baseSpeed * 0.8f), chilledSpeed);
    }

    @Test
    public void testExposureTiersTriggerBelowThresholds() {
        TurnManager turnManager = new TurnManager();
        Player player = new Player(2, 2);
        PlayerStats stats = player.getStats();
        GameEventManager eventManager = new GameEventManager();

        turnManager.applyExposureTiers(player, stats, eventManager, 34.0f);
        assertTrue("CHILLED should trigger below 35.0C", player.getStatusManager().hasEffect(StatusEffectType.CHILLED));
        assertFalse(player.getStatusManager().hasEffect(StatusEffectType.HYPOTHERMIA));

        int hpBefore = player.getCurrentHP();
        turnManager.applyExposureTiers(player, stats, eventManager, 31.0f);
        assertTrue("HYPOTHERMIA should trigger below 32.0C", player.getStatusManager().hasEffect(StatusEffectType.HYPOTHERMIA));
        assertTrue("HYPOTHERMIA should deal damage on the configured turn interval", player.getCurrentHP() <= hpBefore);

        turnManager.applyExposureTiers(player, stats, eventManager, 39.0f);
        assertTrue("HEATSTROKE should trigger above 38.0C", player.getStatusManager().hasEffect(StatusEffectType.HEATSTROKE));
        assertTrue("HEATSTROKE should also apply stamina exhaustion", player.getStatusManager().hasEffect(StatusEffectType.EXHAUSTED));
        assertFalse("HYPOTHERMIA should clear once warmed up", player.getStatusManager().hasEffect(StatusEffectType.HYPOTHERMIA));
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private Set<GridPoint2> buildOpenReachableSet(int size) {
        Set<GridPoint2> reachable = new HashSet<>();
        for (int y = 1; y < size - 1; y++) {
            for (int x = 1; x < size - 1; x++) {
                reachable.add(new GridPoint2(x, y));
            }
        }
        return reachable;
    }

    private String[] buildOpenLayout(int size) {
        String[] layout = new String[size];
        for (int y = 0; y < size; y++) {
            StringBuilder row = new StringBuilder();
            for (int x = 0; x < size; x++) {
                row.append((x == 0 || x == size - 1 || y == 0 || y == size - 1) ? '#' : '.');
            }
            layout[y] = row.toString();
        }
        return layout;
    }
}
