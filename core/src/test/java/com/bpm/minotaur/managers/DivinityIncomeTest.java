package com.bpm.minotaur.managers;

import com.bpm.minotaur.gamedata.progression.ShelterAltar;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Divinity income against the #197 target: about 15-25 per depth-1 expedition, the first
 * station (Lantern 10 / Bed 15) after expedition 1-2, and a mid-tier one after about 5.
 *
 * <p>Arithmetic at depth 1 (every depth-1 monster has base level 1 or 2):
 * <ul>
 *   <li>per kill: max(1, (1 + 1) / 2) = 1, and max(1, (2 + 1) / 2) = 1;</li>
 *   <li>per new chunk: max(1, 1) = 1;</li>
 *   <li>the starter chunk holds 8 monsters, any other depth-1 chunk 14
 *       ({@code SpawnManager.spawnEntities}).</li>
 * </ul>
 * A depth-1 expedition that clears the starter chunk and half of a second (8 + 7 kills)
 * and enters 2 chunks earns 15 + 2 = 17. Clearing both earns 8 + 14 + 2 = 24. Both sit
 * inside 15-25, so the amounts stay as they are; this test holds them there.
 */
public class DivinityIncomeTest {

    private static final int STARTER_CHUNK_MONSTERS = 8;
    private static final int DEPTH1_CHUNK_MONSTERS = 14;

    private static int depth1Expedition(int kills, int chunksEntered) {
        return kills * DivinityManager.baseKillAward(1, 1) + chunksEntered * DivinityManager.baseChunkAward(1);
    }

    @Test
    public void depthOneKillAndChunkEachPayOne() {
        assertEquals(1, DivinityManager.baseKillAward(1, 1));
        assertEquals(1, DivinityManager.baseKillAward(2, 1));
        assertEquals(1, DivinityManager.baseChunkAward(1));
    }

    @Test
    public void aDepthOneExpeditionEarnsFifteenToTwentyFive() {
        int partial = depth1Expedition(STARTER_CHUNK_MONSTERS + DEPTH1_CHUNK_MONSTERS / 2, 2);
        int full = depth1Expedition(STARTER_CHUNK_MONSTERS + DEPTH1_CHUNK_MONSTERS, 2);
        assertEquals(17, partial);
        assertEquals(24, full);
        assertTrue(partial >= 15 && full <= 25);
    }

    @Test
    public void theFirstStationIsAffordableAfterTheFirstOrSecondExpedition() {
        int perExpedition = depth1Expedition(STARTER_CHUNK_MONSTERS + DEPTH1_CHUNK_MONSTERS / 2, 2);
        assertTrue(ShelterAltar.Station.LANTERN.getCost() <= perExpedition);
        assertTrue(ShelterAltar.Station.BED.getCost() <= perExpedition);
        // Lantern and Bed together take the second expedition.
        int both = ShelterAltar.Station.LANTERN.getCost() + ShelterAltar.Station.BED.getCost();
        assertTrue(both > perExpedition && both <= 2 * perExpedition);
    }

    @Test
    public void retiredLootRetentionIsRefundedWhatItCost() {
        // Levels cost 20, 40, 60, 80, 100.
        assertEquals(0, DivinityManager.retiredLootRetentionRefund(0));
        assertEquals(20, DivinityManager.retiredLootRetentionRefund(1));
        assertEquals(60, DivinityManager.retiredLootRetentionRefund(2));
        assertEquals(300, DivinityManager.retiredLootRetentionRefund(5));

        DivinityManager.SaveData old = new DivinityManager.SaveData();
        old.currentDivinities = 7;
        old.lootRetentionUpgradeLevel = 2;
        assertEquals(60, DivinityManager.refundRetiredLootRetention(old));
        assertEquals(67, old.currentDivinities);
        assertEquals(0, old.lootRetentionUpgradeLevel);
        // Loading it again pays nothing more.
        assertEquals(0, DivinityManager.refundRetiredLootRetention(old));
        assertEquals(67, old.currentDivinities);
    }
}
