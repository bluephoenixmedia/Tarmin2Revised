package com.bpm.minotaur.lwjgl3;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.ObjectMap;
import com.bpm.minotaur.gamedata.Difficulty;
import com.bpm.minotaur.gamedata.item.ItemDataManager;
import com.bpm.minotaur.gamedata.monster.MonsterDataManager;
import com.bpm.minotaur.gamedata.spawntables.SpawnTableData;
import com.bpm.minotaur.gamedata.spawntables.SpawnTableEntry;
import com.bpm.minotaur.gamedata.spawntables.WeightedRandomList;
import com.bpm.minotaur.managers.SpawnManager;

import java.lang.reflect.Field;
import java.io.PrintStream;

public class SpawnDistributionTest {

    public static void main(String[] args) {
        try {
            com.badlogic.gdx.backends.lwjgl3.Lwjgl3NativesLoader.load();
        } catch (Throwable t) {
            System.err.println("Natives loading failed or not needed: " + t.getMessage());
        }

        com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration config = new com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration();
        config.disableAudio(true);
        config.setWindowedMode(100, 100);

        new com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application(new com.badlogic.gdx.ApplicationAdapter() {
            @Override
            public void create() {
                try {
                    runTest();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                com.badlogic.gdx.Gdx.app.exit();
            }
        }, config);
    }

    private static void runTest() throws Exception {
        System.out.println("==========================================");
        System.out.println("STARTING SPAWN DISTRIBUTION ANALYSIS");
        System.out.println("==========================================");

        com.badlogic.gdx.assets.AssetManager assetManager = new com.badlogic.gdx.assets.AssetManager();

        MonsterDataManager monsterData = new MonsterDataManager();
        monsterData.load();

        ItemDataManager itemData = new ItemDataManager();
        itemData.load();
        itemData.loadWeapons();
        itemData.loadArmor(); // Load armor as well

        // Queue and Load Assets
        System.out.println("Loading Assets...");
        monsterData.queueAssets(assetManager);
        itemData.queueAssets(assetManager);
        assetManager.finishLoading();
        System.out.println("Assets Loaded.");

        SpawnTableData spawnTableData = loadSpawnTableData();

        String[] layout = new String[10];
        for (int i = 0; i < 10; i++)
            layout[i] = "..........";

        for (int level = 1; level <= 50; level++) {
            analyzeLevel(level, monsterData, itemData, spawnTableData, layout, assetManager);
        }
    }

    private static void analyzeLevel(int level, MonsterDataManager monsterData, ItemDataManager itemData,
            SpawnTableData spawnTableData, String[] layout, com.badlogic.gdx.assets.AssetManager assetManager)
            throws Exception {
        System.out.println("\n--- LEVEL " + level + " ---");

        int[][] wallData = new int[layout.length][layout[0].length()];
        com.bpm.minotaur.gamedata.Maze maze = new com.bpm.minotaur.gamedata.Maze(level, wallData);

        SpawnManager manager = new SpawnManager(monsterData, itemData, assetManager, maze, Difficulty.MEDIUM, level,
                level,
                0, layout, spawnTableData, 12345L, null);

        // printPool(manager, "monsterPool", "Monsters");
        // printPool(manager, "itemPool", "Items");

        // Execute spawning to generate logs
        System.out.println("Running spawnEntities() for debug log generation...");
        manager.spawnEntities();
        System.out.println("Finished spawnEntities().");
    }

    private static void printPool(Object target, String fieldName, String label) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        WeightedRandomList<SpawnTableEntry> pool = (WeightedRandomList<SpawnTableEntry>) field.get(target);

        Field itemsField = WeightedRandomList.class.getDeclaredField("entries");
        itemsField.setAccessible(true);
        java.util.List<SpawnTableEntry> items = (java.util.List<SpawnTableEntry>) itemsField.get(pool);

        if (items.isEmpty()) {
            System.out.println(label + ": [EMPTY]");
            return;
        }

        System.out.print(label + ": ");

        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (SpawnTableEntry entry : items) {
            sb.append(entry.type).append("(").append(entry.weight).append(") ");
            count++;
            if (count > 20) {
                sb.append("... (" + (items.size() - 20) + " more)");
                break;
            }
        }
        System.out.println(sb.toString());
    }

    private static SpawnTableData loadSpawnTableData() {
        Json json = new Json();
        json.setIgnoreUnknownFields(true);
        FileHandle file = com.badlogic.gdx.Gdx.files.internal("data/spawntables.json");
        SpawnTableData data = json.fromJson(SpawnTableData.class, file);

        if (data.containerLoot != null) {
            for (ObjectMap.Entry<String, Array<SpawnTableEntry>> entry : data.containerLoot) {
                Array<SpawnTableEntry> fixedArray = new Array<>();
                if (entry.value != null) {
                    for (int i = 0; i < entry.value.size; i++) {
                        Object obj = entry.value.get(i);
                        if (obj instanceof JsonValue) {
                            fixedArray.add(json.readValue(SpawnTableEntry.class, (JsonValue) obj));
                        } else if (obj instanceof SpawnTableEntry) {
                            fixedArray.add((SpawnTableEntry) obj);
                        }
                    }
                }
                entry.value = fixedArray;
            }
        }
        return data;
    }
}
