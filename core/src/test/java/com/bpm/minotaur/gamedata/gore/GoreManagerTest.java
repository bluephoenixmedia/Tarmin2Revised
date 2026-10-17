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

        assertTrue(GoreProfile.SKELETAL.hasBlood);
        assertTrue(GoreProfile.SKELETAL.hasGibs);
        assertTrue(GoreProfile.SKELETAL.createsFloorStains);

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
    public void testSkeletalEmitsBloodAndGibs() {
        goreManager.spawnBloodSpray(new Vector3(5, 0.5f, 5), Vector3.X, 10, GoreProfile.SKELETAL);
        assertTrue("Skeletal should emit physical blood", goreManager.getActiveParticles().size > 0);

        goreManager.spawnGibExplosion(new Vector3(5, 0.5f, 5), Vector3.Y, 2, GoreProfile.SKELETAL);
        assertTrue("Skeletal should emit bone gibs", goreManager.getActiveGibs().size > 0);
        assertTrue("Skeletal should create floor blood stains", goreManager.getActiveDecals().size > 0);
    }

    @Test
    public void testDecalPoolLimits() {
        // Overfilling the floor caps at MAX_ACTIVE_SURFACE_DECALS
        for (int i = 0; i < GoreManager.MAX_ACTIVE_SURFACE_DECALS + 50; i++) {
            goreManager.spawnSurfaceDecal(new Vector3(i, 0, i), Color.RED, 0.20f);
        }
        assertEquals(GoreManager.MAX_ACTIVE_SURFACE_DECALS, goreManager.getActiveDecals().size);

        // Overfilling the walls caps at MAX_ACTIVE_WALL_DECALS
        for (int i = 0; i < GoreManager.MAX_ACTIVE_WALL_DECALS + 50; i++) {
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

    @Test
    public void goreOffSpawnsNoBloodGibsOrStainsButKeepsScorch() {
        GoreLevel.setCurrent(GoreLevel.OFF);
        try {
            Vector3 at = new Vector3(5, 0.5f, 5);
            goreManager.spawnBloodSpray(at, Vector3.X, 8, GoreProfile.FLESH);
            goreManager.spawnArterialFountain(at, Vector3.Y, 2f, GoreProfile.FLESH);
            goreManager.spawnGibExplosion(at, Vector3.Y, 2, GoreProfile.FLESH);
            assertEquals(0, goreManager.getActiveParticles().size);
            assertEquals(0, goreManager.getActiveGibs().size);
            assertEquals(0, goreManager.getActiveDecals().size);

            goreManager.spawnElementalScorch(at, Color.BLACK, 0.4f);
            assertTrue(goreManager.getActiveDecals().size > 0);
        } finally {
            GoreLevel.setCurrent(GoreLevel.NORMAL);
        }
    }

    @Test
    public void onNormalBloodDriesButNeverFades() {
        goreManager.spawnSurfaceDecal(new Vector3(3, 0, 3), Color.RED, 0.25f);
        goreManager.spawnWallDecal(3, 3, Direction.NORTH, 0.5f, 0.5f, 0.15f, Color.RED);
        goreManager.getActiveWallDecals().first().startDrip(0f, 0.1f, false);
        goreManager.update(600f, null);

        assertEquals(1, goreManager.getActiveDecals().size);
        assertEquals(1, goreManager.getActiveWallDecals().size);
        SurfaceDecal d = goreManager.getActiveDecals().first();
        assertEquals(1f, d.color.a, 0.001f);
        assertTrue("dried darker", d.color.r < Color.RED.r);
    }

    @Test
    public void onLowBloodFadesAsItUsedTo() {
        GoreLevel.setCurrent(GoreLevel.LOW);
        try {
            goreManager.spawnSurfaceDecal(new Vector3(3, 0, 3), Color.RED, 0.25f);
            goreManager.update(SurfaceDecal.MAX_DECAL_LIFE + 1f, null);
            assertEquals(0, goreManager.getActiveDecals().size);
        } finally {
            GoreLevel.setCurrent(GoreLevel.NORMAL);
        }
    }

    @Test
    public void aFullFloorRecyclesTheStainFarthestFromThePlayer() {
        goreManager.setViewer(0f, 0f);
        int budget = GoreManager.MAX_ACTIVE_SURFACE_DECALS;
        Vector3 far = new Vector3(30.5f, 0, 30.5f);
        for (int i = 0; i < budget; i++) {
            if (i == budget / 2) {
                goreManager.spawnSurfaceDecal(far, Color.RED, 0.2f);
            } else {
                goreManager.spawnSurfaceDecal(new Vector3(0.5f + (i % 25), 0, 0.5f + (i / 25) * 0.9f), Color.RED, 0.2f);
            }
        }
        assertEquals(budget, goreManager.getActiveDecals().size);

        goreManager.spawnSurfaceDecal(new Vector3(12.0f, 0, 25.0f), Color.RED, 0.2f);

        assertEquals(budget, goreManager.getActiveDecals().size);
        for (SurfaceDecal d : goreManager.getActiveDecals()) {
            assertFalse("the far stain went first", d.position.x == far.x && d.position.z == far.z);
        }
    }

    @Test
    public void floorStainsSaveWithTheChunkTheyLieIn() {
        // z is the maze's second axis; y is height off the floor.
        goreManager.spawnSurfaceDecal(new Vector3(10, 0, 50), Color.RED, 0.25f);
        com.bpm.minotaur.gamedata.ChunkData data = new com.bpm.minotaur.gamedata.ChunkData();
        goreManager.exportChunkGore(new com.badlogic.gdx.math.GridPoint2(0, 1), data);
        assertEquals(1, data.surfaceDecals.size());
    }

    @Test
    public void reimportingAChunkDoesNotDoubleItsBlood() {
        com.badlogic.gdx.math.GridPoint2 chunk = new com.badlogic.gdx.math.GridPoint2(0, 0);
        goreManager.spawnSurfaceDecal(new Vector3(10, 0, 10), Color.RED, 0.25f);
        goreManager.spawnWallDecal(10, 10, Direction.NORTH, 0.5f, 0.5f, 0.20f, Color.RED);
        com.bpm.minotaur.gamedata.ChunkData data = new com.bpm.minotaur.gamedata.ChunkData();
        goreManager.exportChunkGore(chunk, data);

        goreManager.importChunkGore(chunk, data);

        assertEquals(1, goreManager.getActiveDecals().size);
        assertEquals(1, goreManager.getActiveWallDecals().size);
    }

    @Test
    public void aDropLandingInAPuddleGrowsIt() {
        goreManager.spawnSurfaceDecal(new Vector3(5f, 0, 5f), Color.RED, 0.2f);
        float before = goreManager.getActiveDecals().first().targetSize;

        goreManager.spawnSurfaceDecal(new Vector3(5.1f, 0, 5.1f), Color.RED, 0.2f);

        assertEquals(1, goreManager.getActiveDecals().size);
        assertTrue(goreManager.getActiveDecals().first().targetSize > before);
    }

    @Test
    public void aDropClearOfEveryPuddleStartsANewOne() {
        goreManager.spawnSurfaceDecal(new Vector3(5f, 0, 5f), Color.RED, 0.2f);
        goreManager.spawnSurfaceDecal(new Vector3(6f, 0, 5f), Color.RED, 0.2f);
        assertEquals(2, goreManager.getActiveDecals().size);
    }

    @Test
    public void aPuddleStopsGrowingAtItsCap() {
        for (int i = 0; i < 200; i++) {
            goreManager.spawnSurfaceDecal(new Vector3(5f, 0, 5f), Color.RED, 0.25f);
        }
        assertEquals(1, goreManager.getActiveDecals().size);
        assertTrue(goreManager.getActiveDecals().first().targetSize <= GoreManager.MAX_PUDDLE_RADIUS + 0.001f);
    }

    @Test
    public void scorchMarksDoNotMergeIntoBlood() {
        goreManager.spawnSurfaceDecal(new Vector3(5f, 0, 5f), Color.RED, 0.2f);
        goreManager.spawnElementalScorch(new Vector3(5f, 0, 5f), Color.BLACK, 0.2f);
        assertTrue(goreManager.getActiveDecals().size > 1);
    }

    @Test
    public void aDripThatReachesTheFloorPoolsAtTheFootOfItsWall() {
        goreManager.spawnWallDecal(4, 4, Direction.NORTH, 0.5f, 0.6f, 0.15f, Color.RED);
        WallDecal wd = goreManager.getActiveWallDecals().first();
        wd.startDrip(wd.floorGap(), 0.2f, true);
        int stainsBefore = goreManager.getActiveDecals().size;

        goreManager.update(30f, null);
        goreManager.update(1f, null);

        assertEquals(wd.floorGap(), wd.dripLength, 0.001f);
        assertEquals(stainsBefore + 1, goreManager.getActiveDecals().size);
        SurfaceDecal pool = goreManager.getActiveDecals().peek();
        assertTrue("against the north face", pool.position.z > 4.85f && pool.position.z < 5f);
    }

    @Test
    public void aShortDripNeverReachesTheFloor() {
        goreManager.spawnWallDecal(4, 4, Direction.NORTH, 0.5f, 0.6f, 0.15f, Color.RED);
        WallDecal wd = goreManager.getActiveWallDecals().first();
        wd.startDrip(wd.floorGap() * 0.5f, 0.2f, false);
        goreManager.update(30f, null);
        assertEquals(0, goreManager.getActiveDecals().size);
    }

    @Test
    public void lowGoreNeverDrips() {
        GoreLevel.setCurrent(GoreLevel.LOW);
        try {
            for (int i = 0; i < 60; i++) {
                goreManager.spawnWallDecal(i, 0, Direction.NORTH, 0.5f, 0.6f, 0.15f, Color.RED);
            }
            for (WallDecal wd : goreManager.getActiveWallDecals()) {
                assertEquals(0f, wd.dripTarget, 0f);
            }
        } finally {
            GoreLevel.setCurrent(GoreLevel.NORMAL);
        }
    }

    @Test
    public void mistHangsAndFadesWithoutLeavingAMark() {
        goreManager.spawnBloodMist(new Vector3(5, 0.5f, 5), Vector3.X, 8, GoreProfile.FLESH);
        assertTrue(goreManager.getActiveParticles().size > 0);
        for (int i = 0; i < 120; i++) goreManager.update(1f / 30f, null);
        assertEquals(0, goreManager.getActiveParticles().size);
        assertEquals(0, goreManager.getActiveDecals().size);
    }

    @Test
    public void onlyABadlyWoundedBleederLeavesATrail() {
        assertTrue(GoreManager.leavesBloodTrail(29, 100, GoreProfile.FLESH));
        assertFalse(GoreManager.leavesBloodTrail(30, 100, GoreProfile.FLESH));
        assertFalse(GoreManager.leavesBloodTrail(0, 100, GoreProfile.FLESH));
        assertFalse(GoreManager.leavesBloodTrail(5, 100, GoreProfile.INCORPOREAL));
    }

    @Test
    public void aWoundTrailStainsTheTileLeft() {
        goreManager.spawnWoundTrail(7.5f, 9.5f, GoreProfile.FLESH);
        assertEquals(1, goreManager.getActiveDecals().size);
        SurfaceDecal d = goreManager.getActiveDecals().first();
        assertEquals(7.5f, d.position.x, 0.26f);
        assertEquals(9.5f, d.position.z, 0.26f);
    }
}
