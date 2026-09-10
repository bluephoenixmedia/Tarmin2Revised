package com.bpm.minotaur.gamedata.gore;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Vector3;
import com.bpm.minotaur.gamedata.Direction;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.monster.Monster;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class GoreManagerTest {

    private GoreManager goreManager;

    @Before
    public void setUp() {
        goreManager = new GoreManager();
    }

    @Test
    public void testCreatureGoreProfiles() {
        assertEquals(GoreProfile.SKELETAL, GoreProfile.fromMonsterType(Monster.MonsterType.SKELETON));
        assertEquals(GoreProfile.SKELETAL, GoreProfile.fromMonsterType(Monster.MonsterType.CLOAKED_SKELETON));
        assertEquals(GoreProfile.SKELETAL, GoreProfile.fromMonsterType(Monster.MonsterType.LICH));

        assertEquals(GoreProfile.SLIME, GoreProfile.fromMonsterType(Monster.MonsterType.GELATINOUS_CUBE));
        assertEquals(GoreProfile.INCORPOREAL, GoreProfile.fromMonsterType(Monster.MonsterType.WRAITH));

        assertEquals(GoreProfile.FLESH, GoreProfile.fromMonsterType(Monster.MonsterType.GHOUL));
        assertEquals(GoreProfile.FLESH, GoreProfile.fromMonsterType(Monster.MonsterType.MINOTAUR));

        assertFalse(GoreProfile.SKELETAL.hasBlood);
        assertTrue(GoreProfile.SKELETAL.hasGibs);

        assertTrue(GoreProfile.SLIME.hasBlood);
        assertFalse(GoreProfile.SLIME.hasGibs);

        assertFalse(GoreProfile.INCORPOREAL.hasBlood);
        assertFalse(GoreProfile.INCORPOREAL.hasGibs);

        assertTrue(GoreProfile.FLESH.hasBlood);
        assertTrue(GoreProfile.FLESH.hasGibs);
    }

    @Test
    public void testParticlePoolLimits() {
        // Spawning 50 intensity with multiplier 6 = 300 particles, should be capped at MAX_ACTIVE_PARTICLES (250)
        goreManager.spawnBloodSpray(new Vector3(5, 0.5f, 5), Vector3.X, 50, GoreProfile.FLESH);
        assertTrue("Particles should not exceed budget: " + goreManager.getActiveParticles().size,
                goreManager.getActiveParticles().size <= GoreManager.MAX_ACTIVE_PARTICLES);
        assertEquals(GoreManager.MAX_ACTIVE_PARTICLES, goreManager.getActiveParticles().size);
    }

    @Test
    public void testGibPoolLimits() {
        // Spawn multiple explosions exceeding MAX_ACTIVE_GIBS (40)
        for (int i = 0; i < 10; i++) {
            goreManager.spawnGibExplosion(new Vector3(5, 0.5f, 5), Vector3.Y, 2, GoreProfile.FLESH);
        }
        assertTrue("Gibs should not exceed budget: " + goreManager.getActiveGibs().size,
                goreManager.getActiveGibs().size <= GoreManager.MAX_ACTIVE_GIBS);
    }

    @Test
    public void testSurfaceDecalExpansionAndOxidation() {
        SurfaceDecal decal = new SurfaceDecal();
        Color crimson = new Color(0.77f, 0.12f, 0.12f, 1f);
        decal.init(new Vector3(10, 0, 10), crimson, 0.20f, null);

        assertEquals(decal.initialSize, decal.size, 0.001f);
        assertTrue(decal.size < decal.targetSize);

        // Update past 0.4s expansion time
        decal.update(0.5f);
        assertEquals(decal.targetSize, decal.size, 0.001f);

        // Update 30s to verify color oxidation (should become darker maroon)
        decal.update(30.0f);
        assertTrue("Red channel should darken with oxidation", decal.color.r < crimson.r);
        assertTrue(decal.color.g < crimson.g || decal.color.g == 0f);

        // Update until final 5s fadeout
        decal.update(12.0f); // 45s - 42.5s = 2.5s remaining
        assertTrue("Alpha should fade out near end of life", decal.color.a < 1.0f);
    }

    @Test
    public void testIncorporealEmitsNoPhysicalGore() {
        goreManager.spawnBloodSpray(new Vector3(5, 0.5f, 5), Vector3.X, 10, GoreProfile.INCORPOREAL);
        assertEquals("Incorporeal should not emit physical blood", 0, goreManager.getActiveParticles().size);

        goreManager.spawnGibExplosion(new Vector3(5, 0.5f, 5), Vector3.Y, 2, GoreProfile.INCORPOREAL);
        assertEquals("Incorporeal should not emit physical gibs", 0, goreManager.getActiveGibs().size);
    }

    @Test
    public void testDecalPoolLimits() {
        // Adding 250 surface decals should cap at MAX_ACTIVE_SURFACE_DECALS (200) via FIFO
        for (int i = 0; i < 250; i++) {
            goreManager.spawnSurfaceDecal(new Vector3(i, 0, i), Color.RED, 0.20f);
        }
        assertEquals(GoreManager.MAX_ACTIVE_SURFACE_DECALS, goreManager.getActiveDecals().size);

        // Adding 150 wall decals should cap at MAX_ACTIVE_WALL_DECALS (100) via FIFO
        for (int i = 0; i < 150; i++) {
            goreManager.spawnWallDecal(i, i, Direction.NORTH, 0.5f, 0.5f, 0.15f, Color.RED);
        }
        assertEquals(GoreManager.MAX_ACTIVE_WALL_DECALS, goreManager.getActiveWallDecals().size);
    }

    @Test
    public void testChunkGoreSerializationRoundtrip() {
        com.badlogic.gdx.math.GridPoint2 chunk0 = new com.badlogic.gdx.math.GridPoint2(0, 0);

        // Spawn 2 surface decals and 1 wall decal in chunk 0 (coords < 36)
        goreManager.spawnSurfaceDecal(new Vector3(10, 0, 10), Color.RED, 0.25f);
        goreManager.spawnSurfaceDecal(new Vector3(15, 0, 12), Color.FIREBRICK, 0.30f);
        goreManager.spawnWallDecal(10, 10, Direction.NORTH, 0.5f, 0.5f, 0.20f, Color.RED);

        // Spawn a decal in adjacent chunk 1 (x >= 36)
        goreManager.spawnSurfaceDecal(new Vector3(45, 0, 10), Color.RED, 0.25f);

        com.bpm.minotaur.gamedata.ChunkData chunkData = new com.bpm.minotaur.gamedata.ChunkData();
        goreManager.exportChunkGore(chunk0, chunkData);

        assertEquals("Should export exactly 2 surface decals for chunk 0", 2, chunkData.surfaceDecals.size());
        assertEquals("Should export exactly 1 wall decal for chunk 0", 1, chunkData.wallDecals.size());

        // Restore into fresh manager
        GoreManager freshManager = new GoreManager();
        freshManager.importChunkGore(chunk0, chunkData);

        assertEquals(2, freshManager.getActiveDecals().size);
        assertEquals(1, freshManager.getActiveWallDecals().size);
    }
}
