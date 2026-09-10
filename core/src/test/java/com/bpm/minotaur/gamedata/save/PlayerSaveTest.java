package com.bpm.minotaur.gamedata.save;

import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonWriter;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.ItemColor;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.*;

public class PlayerSaveTest {

    private Json json;

    @Before
    public void setUp() {
        json = new Json();
        json.setOutputType(JsonWriter.OutputType.json);
    }

    @Test
    public void testPlayerSaveDataDefaults() {
        PlayerSaveData data = new PlayerSaveData();
        assertEquals(20, data.currentHP);
        assertEquals(20, data.maxHP);
        assertEquals(10, data.currentMP);
        assertEquals(10, data.maxMP);
        assertEquals("NORTH", data.facing);
        assertEquals(1, data.level);
        assertNotNull(data.quickSlots);
        assertNotNull(data.backpack);
        assertNotNull(data.knownSpells);
    }

    @Test
    public void testPlayerSaveDataJsonRoundtrip() {
        PlayerSaveData original = new PlayerSaveData();
        original.x = 12.5f;
        original.y = 7.25f;
        original.facing = "EAST";
        original.currentHP = 35;
        original.maxHP = 50;
        original.currentMP = 18;
        original.maxMP = 25;
        original.strength = 16;
        original.level = 4;
        original.experience = 1200;
        original.satiety = 65f;
        original.hydration = 90f;
        original.knownSpells.add("MAGIC_ARROW");
        original.knownSpells.add("IRON_SKIN");

        // Equipment items
        ItemSaveData helm = new ItemSaveData(Item.ItemType.HELMET, ItemColor.GRAY);
        original.wornHelmet = helm;

        ItemSaveData sword = new ItemSaveData(Item.ItemType.SWORD, ItemColor.GRAY);
        original.rightHand = sword;

        // Quickslot
        ItemSaveData potion = new ItemSaveData(Item.ItemType.POTION_BLUE, ItemColor.BLUE);
        original.quickSlots.add(potion);

        // Backpack
        ItemSaveData food = new ItemSaveData(Item.ItemType.FOOD, ItemColor.TAN);
        original.backpack.add(food);

        // Serialize
        String jsonStr = json.toJson(original);
        assertNotNull(jsonStr);
        assertTrue(jsonStr.contains("EAST"));
        assertTrue(jsonStr.contains("16"));

        // Deserialize
        PlayerSaveData restored = json.fromJson(PlayerSaveData.class, jsonStr);
        assertNotNull(restored);
        assertEquals(12.5f, restored.x, 0.001f);
        assertEquals(7.25f, restored.y, 0.001f);
        assertEquals("EAST", restored.facing);
        assertEquals(35, restored.currentHP);
        assertEquals(50, restored.maxHP);
        assertEquals(16, restored.strength);
        assertEquals(4, restored.level);
        assertEquals(2, restored.knownSpells.size());
        assertTrue(restored.knownSpells.contains("MAGIC_ARROW"));
        assertTrue(restored.knownSpells.contains("IRON_SKIN"));

        assertNotNull(restored.wornHelmet);
        assertEquals(Item.ItemType.HELMET, restored.wornHelmet.type);
        assertEquals(ItemColor.GRAY, restored.wornHelmet.color);

        assertNotNull(restored.rightHand);
        assertEquals(Item.ItemType.SWORD, restored.rightHand.type);

        assertEquals(1, restored.quickSlots.size());
        assertEquals(Item.ItemType.POTION_BLUE, restored.quickSlots.get(0).type);

        assertEquals(1, restored.backpack.size());
        assertEquals(Item.ItemType.FOOD, restored.backpack.get(0).type);
    }

    @Test
    public void testWorldSaveDataJsonRoundtrip() {
        WorldSaveData original = new WorldSaveData();
        original.masterSeed = 987654321L;
        original.currentLevel = 3;
        original.playerChunkX = 1;
        original.playerChunkY = -2;
        original.turnCount = 450;
        original.dayNightClock = 0.65f;
        original.weatherType = "RAIN";
        original.weatherIntensity = "HEAVY";
        original.gameMode = "MODERN";
        original.tormentLevel = 3;
        original.activeTormentModifiers.addAll(Arrays.asList("FRENZIED_FOES", "DEADLY_BLOWS"));

        String jsonStr = json.toJson(original);
        assertNotNull(jsonStr);

        WorldSaveData restored = json.fromJson(WorldSaveData.class, jsonStr);
        assertNotNull(restored);
        assertEquals(987654321L, restored.masterSeed);
        assertEquals(3, restored.currentLevel);
        assertEquals(1, restored.playerChunkX);
        assertEquals(-2, restored.playerChunkY);
        assertEquals(450, restored.turnCount);
        assertEquals(0.65f, restored.dayNightClock, 0.001f);
        assertEquals("RAIN", restored.weatherType);
        assertEquals("HEAVY", restored.weatherIntensity);
        assertEquals(3, restored.tormentLevel);
        assertEquals(2, restored.activeTormentModifiers.size());
        assertTrue(restored.activeTormentModifiers.contains("FRENZIED_FOES"));
    }
}
