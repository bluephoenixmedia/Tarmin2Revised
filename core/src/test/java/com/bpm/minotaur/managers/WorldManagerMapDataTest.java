package com.bpm.minotaur.managers;

import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.gamedata.ChunkData;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.Set;

import static org.junit.Assert.*;

public class WorldManagerMapDataTest {

    private static final int TEST_SLOT = 3;
    private WorldManager worldManager;

    @Before
    public void setUp() {
        SaveManager.getInstance().setActiveSlotIndex(TEST_SLOT);
        SaveManager.getInstance().deleteSlot(TEST_SLOT);
        worldManager = new WorldManager(null, null, 1, null, null, null, null, null, null);
    }

    @After
    public void tearDown() {
        SaveManager.getInstance().deleteSlot(TEST_SLOT);
    }

    private void writeChunkFile(int level, int x, int y, ChunkData data) throws Exception {
        data.level = level;
        File dir = new File(SaveManager.getInstance().getActiveChunkSaveDirectory());
        dir.mkdirs();
        File f = new File(dir, "chunk_L" + level + "_" + x + "_" + y + ".json");
        SaveManager.getInstance().atomicWriteJson(new com.badlogic.gdx.files.FileHandle(f), data);
    }

    @Test
    public void testGetVisitedChunkIdsEmptyWhenNoSaves() {
        assertTrue(worldManager.getVisitedChunkIds(1).isEmpty());
    }

    @Test
    public void testGetVisitedChunkIdsFiltersByLevelAndParsesNegativeCoords() throws Exception {
        writeChunkFile(1, 0, 0, new ChunkData());
        writeChunkFile(1, -2, 3, new ChunkData());
        writeChunkFile(2, 0, 0, new ChunkData());

        Set<GridPoint2> level1 = worldManager.getVisitedChunkIds(1);
        assertEquals(2, level1.size());
        assertTrue(level1.contains(new GridPoint2(0, 0)));
        assertTrue(level1.contains(new GridPoint2(-2, 3)));

        Set<GridPoint2> level2 = worldManager.getVisitedChunkIds(2);
        assertEquals(1, level2.size());
        assertTrue(level2.contains(new GridPoint2(0, 0)));
    }

    @Test
    public void testGetMaxVisitedLevelDefaultsToOneWithNoSaves() {
        assertEquals(1, worldManager.getMaxVisitedLevel());
    }

    @Test
    public void testGetMaxVisitedLevelReturnsDeepestSavedLevel() throws Exception {
        writeChunkFile(1, 0, 0, new ChunkData());
        writeChunkFile(4, 1, 1, new ChunkData());
        writeChunkFile(2, 0, 0, new ChunkData());

        assertEquals(4, worldManager.getMaxVisitedLevel());
    }

    @Test
    public void testLoadChunkDataReadOnlyReturnsNullWhenMissing() {
        assertNull(worldManager.loadChunkDataReadOnly(1, new GridPoint2(5, 5)));
    }

    @Test
    public void testLoadChunkDataReadOnlyParsesWithoutMutatingLoadedChunks() throws Exception {
        ChunkData source = new ChunkData();
        source.wallData = new int[][]{{0, 0}, {0, 0}};
        source.homeTiles = new java.util.ArrayList<>(java.util.Arrays.asList(new GridPoint2(1, 1)));
        writeChunkFile(1, 2, 2, source);

        ChunkData data = worldManager.loadChunkDataReadOnly(1, new GridPoint2(2, 2));
        assertNotNull(data);
        assertEquals(1, data.level);
        assertTrue(data.hasShelter());

        // Reading map data must not pollute the live gameplay chunk cache.
        assertNull(worldManager.getChunk(new GridPoint2(2, 2)));
    }
}
