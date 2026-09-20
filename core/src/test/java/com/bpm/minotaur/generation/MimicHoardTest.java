package com.bpm.minotaur.generation;

import org.junit.Test;

import java.util.Random;

import static org.junit.Assert.*;

/**
 * A slain mimic pays out a chest's worth of loot.
 *
 * <p>The disguise carries no contents while it stands -- rolling at death instead of at
 * spawn means nothing has to survive on a live monster, which matters because
 * ChunkData.MonsterData does not persist monster inventory.
 */
public class MimicHoardTest {

    @Test
    public void testAMimicAlwaysPaysOutSomething() {
        Random random = new Random(20260920L);
        for (int i = 0; i < 200; i++) {
            int count = MimicHoard.rollItemCount(random);
            assertTrue("a dead mimic must never be a total loss", count >= 1);
        }
    }

    @Test
    public void testHoardStaysChestSized() {
        Random random = new Random(20260920L);
        boolean sawMin = false;
        boolean sawMax = false;
        for (int i = 0; i < 500; i++) {
            int count = MimicHoard.rollItemCount(random);
            assertTrue(count <= MimicHoard.MAX_ITEMS);
            if (count == 1) sawMin = true;
            if (count == MimicHoard.MAX_ITEMS) sawMax = true;
        }
        assertTrue(sawMin);
        assertTrue(sawMax);
    }

    @Test
    public void testHoardBeatsAnOrdinaryChest() {
        // An ordinary chest gets exactly one roll from the container pool
        // (SpawnManager.addLootToContainer), and can roll nothing at all under Doom
        // decay. Surviving a mimic should be worth strictly more than opening a chest,
        // or the encounter is pure downside and players learn to stop opening chests.
        assertTrue(MimicHoard.MAX_ITEMS > 1);
    }
}
