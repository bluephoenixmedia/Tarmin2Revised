package com.bpm.minotaur.generation;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.gamedata.Direction;
import com.bpm.minotaur.gamedata.GameMode;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.ItemCategory;
import com.bpm.minotaur.gamedata.item.ItemColor;
import com.bpm.minotaur.gamedata.item.ItemTemplate;
import com.bpm.minotaur.gamedata.player.Player;
import com.bpm.minotaur.gamedata.monster.Monster;
import com.bpm.minotaur.headless.HeadlessSoundManager;
import com.bpm.minotaur.managers.CombatManager;
import com.bpm.minotaur.managers.GameEventManager;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import static org.junit.Assert.*;

public class AmmunitionAndQuiverSpawningTest {

    @BeforeClass
    public static void setUpClass() {
        if (Gdx.app == null) {
            Gdx.app = (Application) Proxy.newProxyInstance(
                    Application.class.getClassLoader(),
                    new Class<?>[] { Application.class },
                    (proxy, method, methodArgs) -> null);
        }
    }

    @AfterClass
    public static void tearDownClass() {
        Gdx.app = null;
    }

    private File resolveDataFile(String relativePath) {
        File file = new File(relativePath);
        if (!file.exists()) {
            file = new File("../" + relativePath);
        }
        return file;
    }

    @Test
    public void testSpawntablesContainQuiverAndAmmunition() throws Exception {
        File file = resolveDataFile("assets/data/spawntables.json");
        assertTrue("spawntables.json must exist", file.exists());

        String content = Files.readString(file.toPath());
        assertTrue("Floor item table must include QUIVER", content.contains("\"type\": \"QUIVER\""));
        assertTrue("Floor item table must include QUARREL_LIGHT", content.contains("\"type\": \"QUARREL_LIGHT\""));
        assertTrue("Floor item table must include ARROW_FLIGHT", content.contains("\"type\": \"ARROW_FLIGHT\""));
        assertTrue("Floor item table must include QUARREL_HEAVY", content.contains("\"type\": \"QUARREL_HEAVY\""));
        assertTrue("Floor item table must include ARROW_WAR", content.contains("\"type\": \"ARROW_WAR\""));
    }

    @Test
    public void testItemsAndWeaponsJsonHaveAmmunitionFlag() throws Exception {
        File itemsFile = resolveDataFile("assets/data/items.json");
        assertTrue("items.json must exist", itemsFile.exists());
        String itemsContent = Files.readString(itemsFile.toPath());

        // QUIVER should be defined as ammunition with positive probability
        assertTrue("items.json must contain QUIVER definition", itemsContent.contains("\"QUIVER\":"));

        File weaponsFile = resolveDataFile("assets/data/weapons.json");
        assertTrue("weapons.json must exist", weaponsFile.exists());
        String weaponsContent = Files.readString(weaponsFile.toPath());

        assertTrue("weapons.json must define QUARREL_LIGHT", weaponsContent.contains("\"QUARREL_LIGHT\":"));
        assertTrue("weapons.json must define ARROW_WAR", weaponsContent.contains("\"ARROW_WAR\":"));
        assertTrue("weapons.json must define ARROW_FLIGHT", weaponsContent.contains("\"ARROW_FLIGHT\":"));
    }

    @Test
    public void testItemSpawnerProducesAmmunition() {
        Map<String, ItemTemplate> registry = new HashMap<>();

        ItemTemplate quiverTmpl = new ItemTemplate();
        quiverTmpl.friendlyName = "Quiver";
        quiverTmpl.isAmmunition = true;
        quiverTmpl.probability = 20;
        registry.put("QUIVER", quiverTmpl);

        ItemTemplate arrowTmpl = new ItemTemplate();
        arrowTmpl.friendlyName = "Flight Arrow";
        arrowTmpl.isWeapon = true;
        arrowTmpl.isAmmunition = true;
        arrowTmpl.probability = 15;
        registry.put("ARROW_FLIGHT", arrowTmpl);

        ItemTemplate quarrelTmpl = new ItemTemplate();
        quarrelTmpl.friendlyName = "Light Quarrel";
        quarrelTmpl.isWeapon = true;
        quarrelTmpl.isAmmunition = true;
        quarrelTmpl.probability = 15;
        registry.put("QUARREL_LIGHT", quarrelTmpl);

        ItemSpawner spawner = new ItemSpawner(registry, new NetHackRNG(new Random(12345)));
        Map.Entry<String, ItemTemplate> spawned = spawner.spawnItemByCategory(ItemCategory.AMMUNITION);

        assertNotNull("ItemSpawner should spawn an ammunition entry", spawned);
        Item.ItemType type = Item.ItemType.valueOf(spawned.getKey());
        Item item = new Item(type, 0, 0, null, null, null);
        assertTrue("Spawned item must be recognized as ammunition", item.isAmmunition());
    }

    @Test
    public void testQuiverPickupAndAutoStepReplenishment() {
        int[][] walls = new int[10][10];
        Maze maze = new Maze(1, walls);
        Player player = new Player(5, 5);
        player.getPosition().set(5.5f, 5.5f);
        player.setFacing(Direction.NORTH);

        GameEventManager eventManager = new GameEventManager();
        HeadlessSoundManager soundManager = new HeadlessSoundManager();

        player.getStats().setArrows(0);
        assertEquals(0, player.getArrows());

        // 1. Manual interaction pickup at feet
        Item quiver = new Item(Item.ItemType.QUIVER, 5, 5, ItemColor.TAN, null, null);
        maze.addItem(quiver);
        assertTrue(maze.getItems().containsKey(new GridPoint2(5, 5)));

        player.interactWithItem(maze, eventManager, soundManager, null);
        assertFalse("Quiver should be removed from floor after pickup", maze.getItems().containsKey(new GridPoint2(5, 5)));
        assertTrue("Player should gain between 8 and 14 arrows from quiver", player.getArrows() >= 8 && player.getArrows() <= 14);

        int arrowsBeforeStep = player.getArrows();

        // 2. Auto-pickup on step
        Item quarrel = new Item(Item.ItemType.QUARREL_LIGHT, 5, 6, ItemColor.TAN, null, null);
        maze.addItem(quarrel);

        player.move(Direction.NORTH, maze, eventManager, GameMode.ADVANCED, soundManager);
        assertEquals("Player should have moved to (5, 6)", 5, (int) player.getPosition().x);
        assertEquals("Player should have moved to (5, 6)", 6, (int) player.getPosition().y);

        assertFalse("Quarrel on tile should be auto-collected", maze.getItems().containsKey(new GridPoint2(5, 6)));
        assertTrue("Player ammo should have increased further from stepping on quarrel", player.getArrows() >= arrowsBeforeStep + 8);
    }

    @Test
    public void testRangedCombatAmmoConsumptionAndDepletion() {
        Player player = new Player(5f, 5f);
        Maze maze = new Maze(1, new int[10][10]);
        com.bpm.minotaur.rendering.AnimationManager animationManager =
                new com.bpm.minotaur.rendering.AnimationManager() {};
        GameEventManager eventManager = new GameEventManager();
        HeadlessSoundManager soundManager = new HeadlessSoundManager();

        CombatManager combatManager = new CombatManager(player, maze, null, animationManager, eventManager,
                soundManager, null, null, null, null, null);

        ItemTemplate bowTemplate = new ItemTemplate();
        bowTemplate.friendlyName = "Bow";
        bowTemplate.isWeapon = true;
        bowTemplate.isRanged = true;
        Item bow = Item.fromTemplate(Item.ItemType.BOW, bowTemplate);
        player.getInventory().setRightHand(bow);
        assertTrue("Bow must be a ranged weapon", bow.isRanged());

        // 1. When ammo is 0, attack is blocked
        player.getStats().setArrows(0);
        assertEquals(0, player.getArrows());

        Monster target = new Monster(Monster.MonsterType.GOBLIN, 20, 10, 5f, 7f);
        combatManager.startCombat(target);

        // Instant attack when ammo is 0 fails/blocks
        combatManager.playerAttackInstant();
        assertEquals("Arrows should remain 0 when attack is blocked", 0, player.getArrows());

        // 2. Replenish ammo and attack
        player.getStats().setArrows(5);
        assertEquals(5, player.getArrows());

        combatManager.startCombat(target);
        combatManager.playerAttackInstant();
        assertEquals("Ranged attack must consume exactly 1 arrow/bolt", 4, player.getArrows());
    }
}
