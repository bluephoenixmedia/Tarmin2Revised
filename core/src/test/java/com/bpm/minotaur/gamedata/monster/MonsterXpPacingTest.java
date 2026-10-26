package com.bpm.minotaur.gamedata.monster;

import com.badlogic.gdx.math.GridPoint2;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.bpm.minotaur.gamedata.Alignment;
import com.bpm.minotaur.gamedata.Difficulty;
import com.bpm.minotaur.gamedata.player.PlayerStats;
import com.bpm.minotaur.generation.MonsterSpawner;
import com.bpm.minotaur.generation.NetHackRNG;
import com.bpm.minotaur.generation.SpawnContext;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.io.FileReader;
import java.util.*;

import static org.junit.Assert.*;

/**
 * The progression pacing check from #183 (targets in #162 and docs/balance/0.0.2-targets.md):
 * walks the level-banded monster pool, depth by depth, against the player's XP curve.
 *
 * <p>The walk models one expedition per depth with {@link #KILLS_PER_DEPTH} kills, and an EDL
 * equal to the depth (EDL is "depth and distance only", #161). Monsters are drawn by the real
 * {@link MonsterSpawner}; colours by the same weighted variant windows the factory uses.
 */
public class MonsterXpPacingTest {

    private static final int KILLS_PER_DEPTH = 12;
    private static final int ENDGAME_DEPTH = 12;
    /** Outside every near-shelter threat bound, so the pool is the depth's plain band. */
    private static final GridPoint2 AWAY_FROM_SHELTER = new GridPoint2(5, 5);

    private static Map<String, MonsterTemplate> registry;

    @BeforeClass
    public static void load() throws Exception {
        File f = new File("../assets/data/monsters.json");
        if (!f.exists()) f = new File("assets/data/monsters.json");
        JsonValue root = new JsonReader().parse(new FileReader(f));
        Json json = new Json();
        registry = new TreeMap<>();
        for (JsonValue m = root.child; m != null; m = m.next) {
            registry.put(m.name, json.readValue(MonsterTemplate.class, m));
        }
    }

    @Test
    public void monsterXpRisesWithLevelAndNeverFallsBehindALowerMonster() {
        for (int level = 2; level <= 20; level++) {
            assertTrue("level " + level, MonsterXp.forLevel(level) > MonsterXp.forLevel(level - 1));
        }
        for (Map.Entry<String, MonsterTemplate> a : registry.entrySet()) {
            for (Map.Entry<String, MonsterTemplate> b : registry.entrySet()) {
                MonsterTemplate ta = a.getValue(), tb = b.getValue();
                if (ta.xpMultiplier == 1f && tb.xpMultiplier == 1f && ta.baseLevel > tb.baseLevel) {
                    assertTrue(a.getKey() + " (level " + ta.baseLevel + ") must be worth more than "
                                    + b.getKey() + " (level " + tb.baseLevel + ")",
                            MonsterXp.forTemplate(ta) > MonsterXp.forTemplate(tb));
                }
            }
        }
    }

    @Test
    public void eliteMultiplierLiftsATemplateAboveItsLevel() {
        MonsterTemplate plain = new MonsterTemplate();
        plain.baseLevel = 5;
        MonsterTemplate elite = new MonsterTemplate();
        elite.baseLevel = 5;
        elite.xpMultiplier = 3f;
        assertEquals(MonsterXp.forLevel(5), MonsterXp.forTemplate(plain));
        assertEquals(Math.round(MonsterXp.forLevel(5) * 3f), MonsterXp.forTemplate(elite));
    }

    @Test
    public void eightToTenOnLevelKillsEarnEveryLevelFromTheFourthOn() {
        // Levels 2-4 are front-loaded (#311): about 4, 6 and 8 kills, so a typical life levels up.
        PlayerStats stats = new PlayerStats(Difficulty.MEDIUM);
        for (int level = 1; level <= 20; level++) {
            assertEquals(level, stats.getLevel());
            int onLevelKill = MonsterXp.forKill(MonsterXp.forLevel(level), MonsterColor.WHITE);
            double kills = stats.getExperienceToNextLevel() / (double) onLevelKill;
            if (level >= 3) {
                assertTrue("level " + level + " costs " + kills + " on-level kills", kills >= 7.5 && kills <= 10);
            } else {
                assertTrue("level " + level + " costs " + kills + " on-level kills", kills >= 3.5 && kills < 8);
            }
            stats.addExperience(stats.getExperienceToNextLevel());
        }
    }

    @Test
    public void walkingThePoolMeetsThePacingTargets() {
        NetHackRNG rng = new NetHackRNG(new Random(183));
        Random colourRng = new Random(162);
        MonsterSpawner spawner = new MonsterSpawner(registry, rng);
        PlayerStats stats = new PlayerStats(Difficulty.MEDIUM);

        int kills = 0;
        int firstLevelUpKill = -1;
        int[] levelAfterDepth = new int[ENDGAME_DEPTH + 1];
        StringBuilder report = new StringBuilder("XP pacing walk (#183), " + KILLS_PER_DEPTH + " kills per depth\n");
        for (int depth = 1; depth <= ENDGAME_DEPTH; depth++) {
            long depthXp = 0;
            for (int k = 0; k < KILLS_PER_DEPTH; k++) {
                SpawnContext ctx = new SpawnContext(depth, stats.getLevel(), false, Alignment.NEUTRAL, 0,
                        Collections.emptySet(), false, depth, AWAY_FROM_SHELTER, 1);
                MonsterTemplate t = spawner.spawnRandomMonster(ctx).orElseThrow().getValue();
                int xp = MonsterXp.forKill(MonsterXp.forTemplate(t), colourFor(t, depth, colourRng));
                depthXp += xp;
                kills++;
                if (stats.addExperience(xp) && firstLevelUpKill < 0) firstLevelUpKill = kills;
            }
            levelAfterDepth[depth] = stats.getLevel();
            report.append(String.format("  depth %2d: level %2d, %5.1f XP a kill, next level costs %4d%n",
                    depth, stats.getLevel(), depthXp / (double) KILLS_PER_DEPTH, stats.getExperienceToNextLevel()));
        }
        report.append("  first level-up on kill ").append(firstLevelUpKill);
        System.out.println(report);

        assertTrue("first level-up after about 4 depth-1 kills (#311), was " + firstLevelUpKill,
                firstLevelUpKill >= 3 && firstLevelUpKill <= 6);
        for (int depth = 1; depth <= 4; depth++) {
            assertTrue("about one level per depth early on: level " + levelAfterDepth[depth] + " after depth " + depth,
                    levelAfterDepth[depth] >= depth && levelAfterDepth[depth] <= depth + 2);
        }
        assertTrue("level 5 around depth 3-4", levelAfterDepth[2] < 5 && levelAfterDepth[4] >= 5);
        assertTrue("level 11 around the endgame strata", levelAfterDepth[9] < 11 && levelAfterDepth[ENDGAME_DEPTH] >= 11);
    }

    /** The colour a spawned monster gets: the weighted variant windows of {@link MonsterDataManager}. */
    private static MonsterColor colourFor(MonsterTemplate t, int edl, Random random) {
        if (t.variants == null) return MonsterColor.WHITE;
        List<MonsterVariant> valid = new ArrayList<>();
        int total = 0;
        for (MonsterVariant v : t.variants) {
            if (edl >= v.minLevel && edl <= v.maxLevel) {
                valid.add(v);
                total += v.weight;
            }
        }
        if (valid.isEmpty() || total <= 0) return MonsterColor.WHITE;
        int roll = random.nextInt(total);
        for (MonsterVariant v : valid) {
            roll -= v.weight;
            if (roll < 0) return v.color;
        }
        return valid.get(0).color;
    }
}
