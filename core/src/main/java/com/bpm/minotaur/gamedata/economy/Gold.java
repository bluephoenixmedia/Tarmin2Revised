package com.bpm.minotaur.gamedata.economy;

import com.badlogic.gdx.assets.AssetManager;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.ItemColor;
import com.bpm.minotaur.gamedata.item.ItemDataManager;

import com.bpm.minotaur.generation.NetHackRNG;

/**
 * Gold at NetHack's rarity (#197, decided in #160). Gold is for buying from the merchant,
 * and it comes from three places: rolled floor piles, the gold some monsters carry, and
 * containers (unchanged, see {@code spawntables.json}).
 *
 * <p><b>Floor piles.</b> NetHack rolls one pile in about a third of its rooms, sized
 * {@code rnd(depth+2) * rnd(30)}. The size formula is used as is. The "room" is the
 * judgement call: a Tarmin chunk has 25-30 door-bounded floor regions (four or so per
 * 12x12 template block), so rolling every one of them would put roughly 300 gold on a
 * depth-1 chunk and buy the merchant out in one trip. The decision's own target is about
 * one basic merchant purchase (40-50 gold: a potion or a sword at the 2x markup) every
 * one or two expeditions, so a chunk rolls as {@link #PILE_ROLLS_PER_CHUNK} room. The
 * arithmetic for a depth-1 expedition (the starter chunk plus one more) is pinned in
 * {@code GoldEconomyTest}.
 *
 * <p><b>Monster gold.</b> The rule {@code MonsterFactory} already had, now kept rather than
 * thrown away (#157): a monster with an intelligence above 5 carries
 * {@code d(level,10) + level} one time in three, and drops it as a pile when it dies. No
 * depth-1 or depth-2 monster is that clever (the first is the Ghast, base level 3), so
 * monster gold starts at the strata, where the merchant's prices also start to climb.
 *
 * <p>A pile is a COINS item carrying its rolled amount ({@link Item#getGoldAmount()});
 * picking it up adds that amount to the purse.
 */
public final class Gold {

    /** NetHack: {@code if (!rn2(3)) mkgold(...)} per room. */
    public static final int PILE_CHANCE_ONE_IN = 3;

    /** How many NetHack "rooms" a chunk counts as for the floor-pile roll. See the class note. */
    public static final int PILE_ROLLS_PER_CHUNK = 1;

    /** One in three gold-carrying kinds actually carries gold. */
    public static final int CARRIER_CHANCE_ONE_IN = 3;

    private Gold() {
    }

    /** NetHack's floor pile: {@code rnd(depth+2) * rnd(30)}, depth floored at 1. */
    public static int floorPileAmount(int depth, NetHackRNG rng) {
        int d = Math.max(1, depth);
        return rng.rnd(d + 2) * rng.rnd(30);
    }

    /** Expected floor-pile size at a depth: {@code (depth+3)/2 * 15.5}. */
    public static double expectedFloorPile(int depth) {
        int d = Math.max(1, depth);
        return ((d + 3) / 2.0) * 15.5;
    }

    /** How many floor piles one chunk gets: each room-roll succeeds one time in three. */
    public static int rollFloorPiles(NetHackRNG rng) {
        int piles = 0;
        for (int i = 0; i < PILE_ROLLS_PER_CHUNK; i++) {
            if (rng.rn2(PILE_CHANCE_ONE_IN) == 0) piles++;
        }
        return piles;
    }

    /** Whether a monster is the gold-carrying kind: intelligence above 5. */
    public static boolean likesGold(int intelligence) {
        return intelligence > 5;
    }

    /**
     * The gold a newly made monster carries, or 0. {@code d(level,10) + level} for one in
     * three of the monsters that like gold.
     */
    public static int rollCarriedGold(boolean likesGold, int level, NetHackRNG rng) {
        if (!likesGold || rng.rn2(CARRIER_CHANCE_ONE_IN) != 0) return 0;
        int lvl = Math.max(1, level);
        return rng.d(lvl, 10) + lvl;
    }

    /** Builds a pile worth {@code amount}, or null when there is nothing to build. */
    public static Item createPile(ItemDataManager itemDataManager, AssetManager assetManager,
            int amount, int x, int y) {
        if (amount <= 0 || itemDataManager == null) return null;
        Item pile = itemDataManager.createItem(Item.ItemType.COINS, x, y, ItemColor.GOLD, assetManager);
        if (pile != null) pile.setGoldAmount(amount);
        return pile;
    }

    /** "1 gold piece", "37 gold pieces". */
    public static String pileName(int amount) {
        return amount == 1 ? "1 gold piece" : amount + " gold pieces";
    }
}
