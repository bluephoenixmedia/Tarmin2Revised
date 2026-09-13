package com.bpm.minotaur.gamedata;

import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.ItemCategory;
import com.bpm.minotaur.gamedata.item.ItemTemplate;
import com.bpm.minotaur.gamedata.player.PlayerStats;
import com.bpm.minotaur.generation.ItemSpawner;
import com.bpm.minotaur.generation.NetHackRNG;
import com.bpm.minotaur.generation.SpawnContext;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.Assert.*;

public class StatueAmmoCraftingKitsTest {

    @Test
    public void testStatueSceneryAndChunkDataPersistence() {
        // 1. Verify Scenery constructor and properties for STATUE
        Scenery statue = new Scenery(Scenery.SceneryType.STATUE, 5, 8, "images/events/statue/dragon_statue.png");
        assertEquals(Scenery.SceneryType.STATUE, statue.getType());
        assertEquals("images/events/statue/dragon_statue.png", statue.getTexturePath());
        assertFalse("Statues should be passable so player can step onto/trigger them", statue.isImpassable());
        assertEquals(1.0f, statue.getScale().x, 0.001f);
        assertEquals(1.25f, statue.getScale().y, 0.001f);

        // 2. Add to Maze
        int[][] walls = new int[16][16];
        Maze maze = new Maze(1, walls);
        maze.addScenery(statue);
        maze.addEvent(5, 8, "EVENT_DRAGON_STATUE");

        // 3. Serialize to ChunkData
        ChunkData chunkData = new ChunkData(maze);
        assertEquals(1, chunkData.scenery.size());
        ChunkData.SceneryData scData = chunkData.scenery.get(0);
        assertEquals(Scenery.SceneryType.STATUE, scData.type);
        assertEquals(5, scData.x);
        assertEquals(8, scData.y);
        assertEquals("images/events/statue/dragon_statue.png", scData.texturePath);

        // 4. Restore via buildMaze
        Maze restored = chunkData.buildMaze(null, null, null);
        assertNotNull(restored.getScenery());
        assertEquals(1, restored.getScenery().size());
        Scenery restoredStatue = restored.getScenery().values().iterator().next();
        assertEquals(Scenery.SceneryType.STATUE, restoredStatue.getType());
        assertEquals("images/events/statue/dragon_statue.png", restoredStatue.getTexturePath());
        assertEquals("EVENT_DRAGON_STATUE", restored.getEventAt(5, 8));
    }

    @Test
    public void testAmmunitionCategoryAndIdentification() {
        // Arrow
        Item arrowWar = new Item(Item.ItemType.ARROW_WAR, 0, 0, null, null, null);
        assertTrue(arrowWar.isAmmunition());
        assertEquals(ItemCategory.AMMUNITION, arrowWar.getCategory());

        // Quarrel
        Item quarrel = new Item(Item.ItemType.QUARREL_HEAVY, 0, 0, null, null, null);
        assertTrue(quarrel.isAmmunition());
        assertEquals(ItemCategory.AMMUNITION, quarrel.getCategory());

        // Quiver
        Item quiver = new Item(Item.ItemType.QUIVER, 0, 0, null, null, null);
        assertTrue(quiver.isAmmunition());
        assertEquals(ItemCategory.AMMUNITION, quiver.getCategory());

        // Non-ammunition weapon (Sword)
        Item sword = new Item(Item.ItemType.SWORD, 0, 0, null, null, null);
        assertFalse(sword.isAmmunition());
        assertNotEquals(ItemCategory.AMMUNITION, sword.getCategory());
    }

    @Test
    public void testAmmunitionSpawningViaItemSpawner() {
        Map<String, ItemTemplate> registry = new HashMap<>();

        ItemTemplate arrowTmpl = new ItemTemplate();
        arrowTmpl.friendlyName = "War Arrow";
        arrowTmpl.isWeapon = true;
        arrowTmpl.isAmmunition = true;
        arrowTmpl.probability = 15;
        registry.put("ARROW_WAR", arrowTmpl);

        ItemTemplate quarrelTmpl = new ItemTemplate();
        quarrelTmpl.friendlyName = "Heavy Quarrel";
        quarrelTmpl.isWeapon = true;
        quarrelTmpl.isAmmunition = true;
        quarrelTmpl.probability = 15;
        registry.put("QUARREL_HEAVY", quarrelTmpl);

        ItemSpawner spawner = new ItemSpawner(registry, new NetHackRNG(new Random(42)));
        Map.Entry<String, ItemTemplate> spawned = spawner.spawnItemByCategory(ItemCategory.AMMUNITION);

        assertNotNull("Ammunition category should spawn valid ammo item", spawned);
        assertTrue(spawned.getKey().startsWith("ARROW_") || spawned.getKey().startsWith("QUARREL_"));
    }

    @Test
    public void testTravelCraftingKitsProtectedOnDeath() {
        Inventory inventory = new Inventory();

        // Add regular loot items
        Item regularItem1 = new Item(Item.ItemType.SWORD, 0, 0, null, null, null);
        Item regularItem2 = new Item(Item.ItemType.POTION_BLUE, 0, 0, null, null, null);
        inventory.pickup(regularItem1);
        inventory.pickup(regularItem2);

        // Add travel kits
        Item craftingKit = new Item(Item.ItemType.CRAFTING_TOOLKIT, 0, 0, null, null, null);
        Item cookingKit = new Item(Item.ItemType.COOKING_KIT, 0, 0, null, null, null);
        inventory.pickup(craftingKit);
        inventory.pickup(cookingKit);

        // Simulate GameScreen death retention logic with retentionCap = 0 (no upgrades purchased)
        int retentionCap = 0;
        List<Item> allUnequipped = new ArrayList<>(inventory.getMainInventory());
        Item[] quickSlots = inventory.getQuickSlots();
        for (int i = 0; i < quickSlots.length; i++) {
            if (quickSlots[i] != null) {
                allUnequipped.add(quickSlots[i]);
                quickSlots[i] = null;
            }
        }
        inventory.getMainInventory().clear();

        List<Item> permanentKits = new ArrayList<>();
        List<Item> atRiskItems = new ArrayList<>();
        for (Item itm : allUnequipped) {
            if (itm != null && (itm.getType() == Item.ItemType.CRAFTING_TOOLKIT || itm.getType() == Item.ItemType.COOKING_KIT)) {
                permanentKits.add(itm);
            } else {
                atRiskItems.add(itm);
            }
        }

        // Travel kits are restored unconditionally
        for (Item kit : permanentKits) {
            inventory.pickup(kit);
        }

        // At-risk items are subject to retentionCap (0 here)
        int retainedCount = Math.min(retentionCap, atRiskItems.size());
        for (int i = 0; i < retainedCount; i++) {
            inventory.pickup(atRiskItems.get(i));
        }

        // Verification: regular items are lost, but BOTH travel kits are retained!
        assertEquals(2, inventory.getMainInventory().size());
        assertTrue(inventory.hasItemOfType(Item.ItemType.CRAFTING_TOOLKIT));
        assertTrue(inventory.hasItemOfType(Item.ItemType.COOKING_KIT));
        assertFalse(inventory.hasItemOfType(Item.ItemType.SWORD));
        assertFalse(inventory.hasItemOfType(Item.ItemType.POTION_BLUE));
    }

    @Test
    public void testPlayerArrowAmmoAddition() {
        PlayerStats stats = new PlayerStats(Difficulty.MEDIUM);
        stats.setArrows(10);
        assertEquals(10, stats.getArrows());

        stats.addArrows(8);
        assertEquals(18, stats.getArrows());

        // Test manual cap at 99
        stats.addArrows(150);
        assertEquals(99, stats.getArrows());
    }
}
