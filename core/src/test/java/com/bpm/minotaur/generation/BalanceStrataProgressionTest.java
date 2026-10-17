package com.bpm.minotaur.generation;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.GridPoint2;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.bpm.minotaur.gamedata.Alignment;
import com.bpm.minotaur.gamedata.Difficulty;
import com.bpm.minotaur.gamedata.GameMode;
import com.bpm.minotaur.gamedata.history.war.Front;
import com.bpm.minotaur.gamedata.monster.Monster;
import com.bpm.minotaur.gamedata.monster.MonsterTemplate;
import com.bpm.minotaur.managers.WorldManager;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.io.FileReader;
import java.util.*;

import static org.junit.Assert.*;

public class BalanceStrataProgressionTest {

    private static Map<String, MonsterTemplate> registry;
    private WorldManager worldManager;

    @BeforeClass
    public static void loadTemplates() throws Exception {
        File f = new File("../assets/data/monsters.json");
        if (!f.exists()) {
            f = new File("assets/data/monsters.json");
        }
        JsonValue root = new JsonReader().parse(new FileReader(f));
        Json json = new Json();
        registry = new HashMap<>();
        for (JsonValue m = root.child; m != null; m = m.next) {
            registry.put(m.name, json.readValue(MonsterTemplate.class, m));
        }
    }

    @Before
    public void setUp() {
        if (Gdx.app == null) {
            Gdx.app = (com.badlogic.gdx.Application) java.lang.reflect.Proxy.newProxyInstance(
                    com.badlogic.gdx.Application.class.getClassLoader(),
                    new Class<?>[]{com.badlogic.gdx.Application.class},
                    (proxy, method, args) -> null
            );
        }
        com.bpm.minotaur.gamedata.monster.MonsterDataManager mdm = new com.bpm.minotaur.gamedata.monster.MonsterDataManager() {
            @Override
            public MonsterTemplate getTemplate(Monster.MonsterType type) {
                return new MonsterTemplate();
            }
        };
        worldManager = new WorldManager(GameMode.CLASSIC, Difficulty.MEDIUM, 1, mdm, null, null, null, null, null);
    }

    @Test
    public void testStrata1MonsterLevelNeverExceedsLevel3() {
        assertNotNull(registry);
        assertTrue("Monsters registry should contain Ogres and Umber Hulks", registry.containsKey("OGRE") && registry.containsKey("UMBER_HULK"));

        NetHackRNG rng = new NetHackRNG(new Random(9999));
        MonsterSpawner spawner = new MonsterSpawner(registry, rng);

        // Strata 1 (Depth 2, EDL 2) outside near-shelter bounds: chunk (5, 5)
        SpawnContext strata1Ctx = new SpawnContext(2, 6, false, Alignment.NEUTRAL, 0,
                Collections.emptySet(), false, 2, new GridPoint2(5, 5), 1);

        for (int i = 0; i < 500; i++) {
            Optional<Map.Entry<String, MonsterTemplate>> spawn = spawner.spawnRandomMonster(strata1Ctx);
            assertTrue(spawn.isPresent());
            MonsterTemplate t = spawn.get().getValue();
            assertTrue("Strata 1 monsters must never exceed baseLevel 3 (found " + spawn.get().getKey() + " lvl " + t.baseLevel + ")",
                    t.baseLevel <= 3);
            assertNotEquals("Ogre must never spawn on Strata 1", "OGRE", spawn.get().getKey());
            assertNotEquals("Owlbear must never spawn on Strata 1", "OWLBEAR", spawn.get().getKey());
            assertNotEquals("Umber Hulk must never spawn on Strata 1", "UMBER_HULK", spawn.get().getKey());
            assertNotEquals("Troll must never spawn on Strata 1", "TROLL", spawn.get().getKey());
        }
    }

    @Test
    public void testStrata1NearShelterBoundsToEarlyDelve() {
        NetHackRNG rng = new NetHackRNG(new Random(7777));
        MonsterSpawner spawner = new MonsterSpawner(registry, rng);

        // Strata 1 (Depth 2, EDL 2) directly under shelter column: chunk (0, 0)
        SpawnContext nearShelterCtx = new SpawnContext(2, 6, false, Alignment.NEUTRAL, 0,
                Collections.emptySet(), false, 2, new GridPoint2(0, 0), 1);

        for (int i = 0; i < 300; i++) {
            Optional<Map.Entry<String, MonsterTemplate>> spawn = spawner.spawnRandomMonster(nearShelterCtx);
            assertTrue(spawn.isPresent());
            MonsterTemplate t = spawn.get().getValue();
            assertTrue("Strata 1 near-shelter monsters must never exceed baseLevel 3", t.baseLevel <= 3);
            int mcr = MonsterSpawner.calculateMCR(t);
            assertTrue("Strata 1 near-shelter MCR must be <= 110 (found " + spawn.get().getKey() + " mcr " + mcr + ")", mcr <= 110);
        }
    }

    @Test
    public void testStrata2MonsterLevelNeverExceedsLevel4() {
        NetHackRNG rng = new NetHackRNG(new Random(5555));
        MonsterSpawner spawner = new MonsterSpawner(registry, rng);

        // Strata 2 (Depth 3, EDL 3) outside near-shelter: chunk (5, 5)
        SpawnContext strata2Ctx = new SpawnContext(3, 6, false, Alignment.NEUTRAL, 0,
                Collections.emptySet(), false, 3, new GridPoint2(5, 5), 1);

        for (int i = 0; i < 500; i++) {
            Optional<Map.Entry<String, MonsterTemplate>> spawn = spawner.spawnRandomMonster(strata2Ctx);
            assertTrue(spawn.isPresent());
            MonsterTemplate t = spawn.get().getValue();
            assertTrue("Strata 2 monsters must never exceed baseLevel 4 (found " + spawn.get().getKey() + " lvl " + t.baseLevel + ")",
                    t.baseLevel <= 4);
            assertNotEquals("Umber Hulk must never spawn on Strata 2", "UMBER_HULK", spawn.get().getKey());
            assertNotEquals("Troll must never spawn on Strata 2", "TROLL", spawn.get().getKey());
            assertNotEquals("Ogre must never spawn on Strata 2", "OGRE", spawn.get().getKey());
        }
    }

    @Test
    public void testWorldManagerSanctuaryBufferCapsLevel6PlayerOnStrata1And2() {
        // Player level 6:
        // Overland (0, 0) Z=1: EDL is 1 (depth 1, D_xy=0)
        assertEquals(1, worldManager.calculateEffectiveDifficulty(new GridPoint2(0, 0), 1, 6));

        // Strata 1 (0, 0) Z=2: EDL capped at 2
        assertEquals(2, worldManager.calculateEffectiveDifficulty(new GridPoint2(0, 0), 2, 6));

        // Strata 1 (1, 0) Z=2: EDL capped at 2
        assertEquals(2, worldManager.calculateEffectiveDifficulty(new GridPoint2(1, 0), 2, 6));

        // Strata 2 (0, 0) Z=3: EDL capped at 3
        assertEquals(3, worldManager.calculateEffectiveDifficulty(new GridPoint2(0, 0), 3, 6));

        // Strata 2 (1, 0) Z=3: EDL capped at 3
        assertEquals(3, worldManager.calculateEffectiveDifficulty(new GridPoint2(1, 0), 3, 6));
    }

    @Test
    public void testFrontNeverCoversStarterShelterChunk() {
        Front frontAtOrigin = new Front(1, 1, 2, new GridPoint2(0, 0));
        assertFalse("Front centered at (0,0) must not cover shelter chunk (0,0)", frontAtOrigin.covers(new GridPoint2(0, 0)));
        assertTrue("Front centered at (0,0) covers adjacent chunk (1,0)", frontAtOrigin.covers(new GridPoint2(1, 0)));

        Front frontAdjacent = new Front(1, 1, 2, new GridPoint2(1, 0));
        assertFalse("Front centered at (1,0) must not cover shelter chunk (0,0)", frontAdjacent.covers(new GridPoint2(0, 0)));
        assertTrue("Front centered at (1,0) covers its own chunk (1,0)", frontAdjacent.covers(new GridPoint2(1, 0)));
    }

    @Test
    public void testRecruitSubstitutesEndgameUnitsNearShelter() {
        // Current chunk defaults to (0, 0), level defaults to 1 (surface)
        assertEquals(new GridPoint2(0, 0), worldManager.getCurrentPlayerChunkId());
        assertEquals(1, worldManager.getCurrentLevel());

        Monster m1 = worldManager.recruit("MINOTAUR", 1, 1);
        assertNotNull(m1);
        assertEquals("MINOTAUR must be replaced with ORC near shelter", "ORC", m1.getMonsterType());

        Monster m2 = worldManager.recruit("FALL_ANGEL", 1, 1);
        assertNotNull(m2);
        assertEquals("FALL_ANGEL must be replaced with LIZARD_WARRIOR near shelter", "LIZARD_WARRIOR", m2.getMonsterType());

        Monster m3 = worldManager.recruit("UMBER_HULK", 1, 1);
        assertNotNull(m3);
        assertEquals("UMBER_HULK must be replaced with SKELETON near shelter", "SKELETON", m3.getMonsterType());
    }
}
