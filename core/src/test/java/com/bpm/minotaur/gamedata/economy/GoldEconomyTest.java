package com.bpm.minotaur.gamedata.economy;

import com.bpm.minotaur.gamedata.ChunkData;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.ItemColor;
import com.bpm.minotaur.generation.NetHackRNG;
import org.junit.Test;

import java.util.Random;

import static org.junit.Assert.*;

/**
 * Gold at NetHack's rarity (#197).
 *
 * <p>Target: about one basic merchant purchase every 1-2 expeditions. A basic purchase is
 * 40-50 gold (a potion, base 20, or a sword, base 25, at the 2x markup). A depth-1
 * expedition is the starter chunk plus one more. Expected gold in it:
 * <ul>
 *   <li>floor: 2 chunks x 1 roll x 1/3 x 31 (rnd(3) averages 2, rnd(30) 15.5) = 20.7;</li>
 *   <li>containers: about 11 per depth-1 chunk (spawn chart), x 2 = 22;</li>
 *   <li>monsters: 0, since no depth-1 monster has an intelligence above 5.</li>
 * </ul>
 * About 43 gold: one basic purchase per one to two expeditions.
 */
public class GoldEconomyTest {

    private static final double DEPTH1_CONTAINER_GOLD_PER_CHUNK = 11.0;
    private static final int CHUNKS_PER_DEPTH1_EXPEDITION = 2;

    @Test
    public void floorPilesFollowNetHacksFormula() {
        NetHackRNG rng = new NetHackRNG(new Random(42));
        for (int depth = 1; depth <= 10; depth++) {
            int lo = Integer.MAX_VALUE, hi = 0;
            for (int i = 0; i < 5000; i++) {
                int g = Gold.floorPileAmount(depth, rng);
                lo = Math.min(lo, g);
                hi = Math.max(hi, g);
            }
            assertEquals(1, lo);
            assertEquals((depth + 2) * 30, hi);
        }
        assertEquals(31.0, Gold.expectedFloorPile(1), 1e-9);
    }

    @Test
    public void aChunkRollsAPileOneTimeInThree() {
        NetHackRNG rng = new NetHackRNG(new Random(7));
        int piles = 0, chunks = 30000;
        for (int i = 0; i < chunks; i++) piles += Gold.rollFloorPiles(rng);
        assertEquals(1.0 / 3.0, piles / (double) chunks, 0.02);
    }

    @Test
    public void aDepthOneExpeditionPaysForAboutOneBasicPurchase() {
        double floor = CHUNKS_PER_DEPTH1_EXPEDITION * Gold.PILE_ROLLS_PER_CHUNK
                * (1.0 / Gold.PILE_CHANCE_ONE_IN) * Gold.expectedFloorPile(1);
        double containers = CHUNKS_PER_DEPTH1_EXPEDITION * DEPTH1_CONTAINER_GOLD_PER_CHUNK;
        double perExpedition = floor + containers;
        assertEquals(20.7, floor, 0.1);
        // One 40-50 gold purchase every one to two expeditions: 20-50 gold each.
        assertTrue("gold per depth-1 expedition " + perExpedition, perExpedition >= 20 && perExpedition <= 50);
    }

    @Test
    public void onlyIntelligentMonstersCarryGold() {
        NetHackRNG rng = new NetHackRNG(new Random(3));
        assertFalse(Gold.likesGold(5));
        assertTrue(Gold.likesGold(6));
        for (int i = 0; i < 200; i++) {
            assertEquals(0, Gold.rollCarriedGold(false, 5, rng));
        }
        int carriers = 0;
        for (int i = 0; i < 9000; i++) {
            int g = Gold.rollCarriedGold(true, 4, rng);
            if (g > 0) {
                carriers++;
                assertTrue(g >= 4 + 4 && g <= 4 * 10 + 4); // d(4,10) + 4
            }
        }
        assertEquals(1.0 / 3.0, carriers / 9000.0, 0.02);
    }

    @Test
    public void aPileIsWorthItsRolledGoldAndSurvivesAChunkUnload() {
        Item pile = new Item(Item.ItemType.COINS, 3, 4, ItemColor.GOLD, null, null);
        pile.setGoldAmount(37);
        assertEquals(37, pile.getTreasureValue());
        assertEquals("37 gold pieces", pile.getSingleDisplayName());

        ChunkData.ItemData saved = new ChunkData.ItemData(pile);
        Item reloaded = new Item(Item.ItemType.COINS, 3, 4, ItemColor.GOLD, null, null);
        saved.applyTo(reloaded);
        assertEquals(37, reloaded.getTreasureValue());
    }

    @Test
    public void anOrdinaryTreasureKeepsItsBaseValue() {
        Item coins = new Item(Item.ItemType.COINS, 0, 0, ItemColor.GOLD, null, null);
        assertEquals(0, coins.getGoldAmount());
        assertEquals(coins.getBaseValue(), coins.getTreasureValue());
    }
}
