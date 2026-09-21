package com.bpm.minotaur.gamedata.item;

import com.bpm.minotaur.gamedata.ChunkData;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * The disguise flags live on the Item and must survive a chunk round trip.
 * ChunkData.ItemData is a hand-written whitelist, so anything not explicitly
 * carried there evaporates on reload -- and a non-persisting mimicSeen would
 * let a player leave and re-enter a chunk to re-roll the perception check.
 */
public class MimicChestStateTest {

    @Test
    public void testOrdinaryItemIsNotAMimic() {
        Item item = new Item();
        assertFalse(item.isMimic());
        assertFalse(item.isMimicSeen());
    }

    @Test
    public void testMimicFlagsAreSettable() {
        Item item = new Item();
        item.setMimic(true);
        assertTrue(item.isMimic());
        assertFalse("seeing through the disguise is a separate fact", item.isMimicSeen());

        item.setMimicSeen(true);
        assertTrue(item.isMimicSeen());
    }

    @Test
    public void testChunkDataCapturesMimicFlags() {
        Item item = new Item();
        item.setMimic(true);
        item.setMimicSeen(true);

        ChunkData.ItemData data = new ChunkData.ItemData(item);

        assertTrue(data.isMimic);
        assertTrue(data.mimicSeen);
    }

    @Test
    public void testChunkDataRestoresMimicFlags() {
        Item original = new Item();
        original.setMimic(true);
        original.setMimicSeen(true);

        ChunkData.ItemData data = new ChunkData.ItemData(original);

        Item rebuilt = new Item();
        data.applyTo(rebuilt);

        assertTrue("a mimic must still be a mimic after a chunk reload", rebuilt.isMimic());
        assertTrue("detection must persist, or re-entering the chunk re-rolls it", rebuilt.isMimicSeen());
    }

    @Test
    public void testChunkDataRoundTripLeavesOrdinaryChestAlone() {
        Item original = new Item();
        ChunkData.ItemData data = new ChunkData.ItemData(original);

        Item rebuilt = new Item();
        rebuilt.setMimic(true);
        rebuilt.setMimicSeen(true);
        data.applyTo(rebuilt);

        assertFalse(rebuilt.isMimic());
        assertFalse(rebuilt.isMimicSeen());
    }

    @Test
    public void testApplyToCarriesStudyProgress() {
        Item original = new Item();
        original.setStudyProgress(3);

        ChunkData.ItemData data = new ChunkData.ItemData(original);
        Item rebuilt = new Item();
        data.applyTo(rebuilt);

        assertEquals(3, rebuilt.getStudyProgress());
    }
}
