package com.bpm.minotaur.generation;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.math.GridPoint2;
import com.badlogic.gdx.utils.Json;
import com.bpm.minotaur.gamedata.Difficulty;
import com.bpm.minotaur.gamedata.GameMode;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.item.ItemDataManager;
import com.bpm.minotaur.gamedata.monster.Monster;
import com.bpm.minotaur.gamedata.monster.MonsterDataManager;
import com.bpm.minotaur.gamedata.monster.MonsterTemplate;
import com.bpm.minotaur.gamedata.spawntables.SpawnTableData;
import com.bpm.minotaur.rendering.RetroTheme;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.lang.reflect.Proxy;
import java.util.*;

import static org.junit.Assert.*;

public class MonsterDistributionTest {

    private MonsterDataManager monsterDataManager;
    private ItemDataManager itemDataManager;
    private SpawnTableData spawnTableData;

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
                            if (!file.exists()) {
                                file = new java.io.File("assets/" + path);
                            }
                            if (!file.exists()) {
                                file = new java.io.File("../assets/" + path);
                            }
                            return new com.badlogic.gdx.files.FileHandle(file);
                        }
                        return null;
                    }
            );
        }

        monsterDataManager = new MonsterDataManager();
        monsterDataManager.load();
        itemDataManager = new ItemDataManager();
        itemDataManager.load();

        FileHandle handle = Gdx.files.internal("data/spawntables.json");
        spawnTableData = new Json().fromJson(SpawnTableData.class, handle);
    }

    @Test
    public void testLakelandsChunkSpawnsMonstersAtHighDifficulty() {
        LakelandsChunkGenerator gen = new LakelandsChunkGenerator();
        Maze maze = gen.generateChunk(
                new GridPoint2(-12, 20), 1, 58, Difficulty.MEDIUM, GameMode.CLASSIC,
                RetroTheme.LAKELANDS_THEME, RetroTheme.LAKELANDS_THEME,
                monsterDataManager, itemDataManager, null, null, spawnTableData,
                123456789L, 0
        );

        assertTrue("Lakelands chunk (-12, 20) must have monsters spawned", maze.getMonsters().size() > 0);
    }

    @Test
    public void testDesertChunkSpawnsMonstersAtHighDifficulty() {
        DesertChunkGenerator gen = new DesertChunkGenerator();
        Maze maze = gen.generateChunk(
                new GridPoint2(-10, 20), 1, 57, Difficulty.MEDIUM, GameMode.CLASSIC,
                RetroTheme.DESERT_THEME, RetroTheme.DESERT_THEME,
                monsterDataManager, itemDataManager, null, null, spawnTableData,
                123456789L, 0
        );

        assertTrue("Desert chunk (-10, 20) must have monsters spawned", maze.getMonsters().size() > 0);
    }

    @Test
    public void testForestChunkSpawnsMonstersAtHighDifficulty() {
        ForestChunkGenerator gen = new ForestChunkGenerator();
        Maze maze = gen.generateChunk(
                new GridPoint2(-5, 15), 1, 45, Difficulty.MEDIUM, GameMode.CLASSIC,
                RetroTheme.FOREST_THEME, RetroTheme.FOREST_THEME,
                monsterDataManager, itemDataManager, null, null, spawnTableData,
                123456789L, 0
        );

        assertTrue("Forest chunk (-5, 15) must have monsters spawned", maze.getMonsters().size() > 0);
    }

    @Test
    public void testMonsterSpawnerDoesNotStarveAtExtremeEdl() {
        Map<String, MonsterTemplate> registry = new HashMap<>();
        for (Monster.MonsterType type : Monster.MonsterType.values()) {
            try {
                MonsterTemplate t = monsterDataManager.getTemplate(type);
                if (t != null) registry.put(type.name(), t);
            } catch (Exception ignored) {}
        }

        NetHackRNG rng = new NetHackRNG(new Random(42));
        MonsterSpawner spawner = new MonsterSpawner(registry, rng);

        // Extreme EDL = 80, far from shelter
        SpawnContext ctx = new SpawnContext(
                1, 80, true, com.bpm.minotaur.gamedata.Alignment.NEUTRAL, 0,
                new HashSet<>(), false, 80, new GridPoint2(20, 20), 1
        );

        int spawned = 0;
        for (int i = 0; i < 20; i++) {
            Optional<Map.Entry<String, MonsterTemplate>> res = spawner.spawnRandomMonster(ctx);
            if (res.isPresent()) spawned++;
        }

        assertTrue("MonsterSpawner must not starve even at extreme EDL = 80", spawned > 0);
    }
}
