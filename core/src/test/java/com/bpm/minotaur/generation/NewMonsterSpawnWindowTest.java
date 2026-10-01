package com.bpm.minotaur.generation;

import com.badlogic.gdx.math.GridPoint2;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.bpm.minotaur.gamedata.monster.MonsterTemplate;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.io.FileReader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * Live spawning is NetHack-style: a monster is eligible when its baseLevel lies between a quarter of
 * the effective dungeon level and that level, and its weight comes from frequency. The
 * monsterSpawnTable in spawntables.json is not read by anything, so a monster is only as present
 * in the game as these two fields make it. These run the real spawner against the real data.
 */
public class NewMonsterSpawnWindowTest {

    /** The lowest level each new species should appear at, from the approved proposal. */
    private static final Map<String, Integer> FIRST_LEVEL = new HashMap<>();

    static {
        FIRST_LEVEL.put("BAT", 1);
        FIRST_LEVEL.put("GIANT_BEE", 2);
        FIRST_LEVEL.put("GIANT_SNAIL", 2);
        FIRST_LEVEL.put("GIANT_CENTIPEDE", 3);
        FIRST_LEVEL.put("LANDSTALKER", 4);
        FIRST_LEVEL.put("COCKATRICE", 5);
        FIRST_LEVEL.put("LIZARD_WARRIOR", 5);
        FIRST_LEVEL.put("BEETLESCRATCH", 6);
        FIRST_LEVEL.put("JESTER", 6);
        FIRST_LEVEL.put("SPECTER", 7);
        FIRST_LEVEL.put("SAGE", 8);
        FIRST_LEVEL.put("SKELETAL_WIZARD", 9);
    }

    private static Map<String, MonsterTemplate> registry;

    @BeforeClass
    public static void load() throws Exception {
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

    /** Draws many monsters at one effective level, far from the shelter so its threat cap is not in play. */
    private static Set<String> drawn(int edl, int draws) {
        MonsterSpawner spawner = new MonsterSpawner(registry, new NetHackRNG(new java.util.Random(42)));
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < draws; i++) {
            SpawnContext ctx = new SpawnContext(1, edl, false, com.bpm.minotaur.gamedata.Alignment.NEUTRAL, 0,
                    new HashSet<>(), false, edl, new GridPoint2(20, 20), 1);
            Optional<Map.Entry<String, MonsterTemplate>> pick = spawner.spawnRandomMonster(ctx);
            pick.ifPresent(e -> seen.add(e.getKey()));
        }
        return seen;
    }

    @Test
    public void everyNewSpeciesStartsAtTheLevelTheProposalGaveIt() {
        for (Map.Entry<String, Integer> e : FIRST_LEVEL.entrySet()) {
            assertEquals(e.getKey() + " baseLevel", (int) e.getValue(), registry.get(e.getKey()).baseLevel);
        }
    }

    @Test
    public void everyNewSpeciesIsActuallyDrawnOnceItsLevelIsReached() {
        for (Map.Entry<String, Integer> e : FIRST_LEVEL.entrySet()) {
            Set<String> seen = drawn(e.getValue() + 1, 6000);
            assertTrue(e.getKey() + " never spawns at level " + (e.getValue() + 1), seen.contains(e.getKey()));
        }
    }

    @Test
    public void noNewSpeciesSpawnsBeforeItsLevel() {
        for (Map.Entry<String, Integer> e : FIRST_LEVEL.entrySet()) {
            if (e.getValue() <= 1) {
                continue;
            }
            assertFalse(e.getKey() + " spawned below level " + e.getValue(),
                    drawn(e.getValue() - 1, 3000).contains(e.getKey()));
        }
    }

    @Test
    public void theNewSpeciesAreNotSwampedByTheOldRoster() {
        // frequency 0 leaves only the small alignment and level bonuses; the older common monsters
        // carry 6-10. A new species should carry a frequency of its own.
        for (String name : FIRST_LEVEL.keySet()) {
            assertTrue(name + " has no frequency", registry.get(name).frequency >= 2);
        }
    }
}
