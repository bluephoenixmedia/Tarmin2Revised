package com.bpm.minotaur.generation.theme;

import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.assets.loaders.resolvers.InternalFileHandleResolver;
import com.bpm.minotaur.gamedata.ChunkData;
import com.bpm.minotaur.gamedata.prop.PropCatalog;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.monster.GuardianScaling;
import com.bpm.minotaur.gamedata.monster.Monster;
import com.bpm.minotaur.gamedata.monster.MonsterDataManager;
import com.bpm.minotaur.gamedata.monster.MonsterRole;
import com.bpm.minotaur.gamedata.monster.MonsterTemplate;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

/** The Bringer's Isle's Guardian, placed by the decorator at a shallow stratum (#302). */
public class GuardianPlacementTest {

    private MonsterDataManager data;
    private AssetManager assets;

    @Before
    public void setUp() {
        if (com.badlogic.gdx.Gdx.app == null) {
            com.badlogic.gdx.Gdx.app = (com.badlogic.gdx.Application) java.lang.reflect.Proxy.newProxyInstance(
                    com.badlogic.gdx.Application.class.getClassLoader(),
                    new Class<?>[]{com.badlogic.gdx.Application.class},
                    (proxy, method, args) -> null);
        }
        ThemeDataManager.resetInstance();
        PropCatalog.resetInstance();
        final MonsterTemplate bringer = template(18, 140, "4d12+10");
        final MonsterTemplate heavy = template(3, 40, "1d10+2"); // a Heavy of a shallow stratum
        final MonsterTemplate common = template(1, 10, "1d4");
        common.role = MonsterRole.SOLDIER;
        data = new MonsterDataManager() {
            @Override
            public MonsterTemplate getTemplate(Monster.MonsterType type) {
                if (type == Monster.MonsterType.BRINGER_OF_DEATH) return bringer;
                if (type == Monster.MonsterType.OGRE) return heavy;
                return common;
            }

            @Override
            public List<Monster.MonsterType> getLoadedTypes() {
                return Arrays.asList(Monster.MonsterType.BRINGER_OF_DEATH, Monster.MonsterType.OGRE,
                        Monster.MonsterType.SKELETON);
            }
        };
        assets = new AssetManager(new InternalFileHandleResolver(), false);
    }

    private static MonsterTemplate template(int level, int hp, String dice) {
        MonsterTemplate t = new MonsterTemplate();
        t.baseLevel = level;
        t.maxHP = hp;
        t.damageDice = dice;
        t.role = MonsterRole.HEAVY;
        return t;
    }

    private Maze isleAt(int edl) {
        Maze maze = CarveGeometryTest.mazeChunk();
        ChunkThemeDecorator.decorate(maze, ChunkTheme.BRINGERS_ISLE, 7L, edl, data, null, assets);
        return maze;
    }

    private static Monster guardian(Maze maze) {
        for (Monster m : maze.getMonsters().values()) if (m.isThemeChampion()) return m;
        return null;
    }

    @Test
    public void atDepthTwoTheBringerIsMetAtLevelFourWithHisBlowCut() {
        Monster bringer = guardian(isleAt(2));
        assertNotNull(bringer);
        assertEquals(4, bringer.getLevel());
        assertTrue("36 a hit is cut: " + bringer.getDamageDice(), GuardianScaling.averageOf(bringer.getDamageDice()) < 12);
        assertTrue("but never below the floor", bringer.getMaxHP() >= Math.round(GuardianScaling.FLOOR * 40));
        assertEquals("the Bringer of Death, Keeper of the Isle", bringer.getName());
    }

    @Test
    public void deepEnoughHeIsHimself() {
        Monster bringer = guardian(isleAt(20));
        assertEquals(18, bringer.getLevel());
        assertEquals("4d12+10", bringer.getDamageDice());
    }

    @Test
    public void aReloadedGuardianStaysCutAndTitled() {
        Maze maze = isleAt(2);
        Monster before = guardian(maze);
        Maze loaded = new ChunkData(maze).buildMaze(data, null, assets);
        Monster after = guardian(loaded);
        assertNotNull("still a Guardian after the reload", after);
        assertEquals(before.getLevel(), after.getLevel());
        assertEquals(before.getDamageDice(), after.getDamageDice());
        assertEquals(before.getMaxHP(), after.getMaxHP());
        assertEquals(before.getName(), after.getName());
        assertEquals(before.getDamageScale(), after.getDamageScale(), 1e-6f);
    }

    @Test
    public void aGuardianSavedBeforeItWasFlaggedIsRecognisedOnLoad() {
        Maze maze = isleAt(2);
        ChunkData old = new ChunkData(maze);
        for (ChunkData.MonsterData m : old.monsters) {
            if (m.themeChampion) { // as a save from before #302 recorded it: a common monster of its kind
                m.themeChampion = false;
                m.level = 0;
                m.damageDice = null;
                m.guardianName = null;
                m.damageScale = 0f;
                m.maxHP = 0;
            }
        }
        Monster loaded = guardian(old.buildMaze(data, null, assets));
        assertNotNull("its slay objective can complete again", loaded);
        assertEquals("the Bringer of Death, Keeper of the Isle", loaded.getName());
        assertTrue("cut to the chunk's depth: level " + loaded.getLevel(), loaded.getLevel() < 18);
    }

    @Test
    public void anOldSaveThatSaysBridgeOfSoulsLoadsAsTheBringersIsle() {
        assertEquals(ChunkTheme.BRINGERS_ISLE, ChunkTheme.parse("BRIDGE_OF_SOULS"));
        assertEquals(ChunkTheme.BRINGERS_ISLE, ChunkTheme.parse("BRINGERS_ISLE"));
        assertNull(ChunkTheme.parse("NO_SUCH_THEME"));

        Maze maze = isleAt(2);
        ChunkData old = new ChunkData(maze);
        old.chunkTheme = "BRIDGE_OF_SOULS";
        assertEquals(ChunkTheme.BRINGERS_ISLE, old.buildMaze(data, null, assets).getChunkTheme());
    }
}
