package com.bpm.minotaur.gamedata.history.war;

import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.gamedata.history.HistoryWorld;
import com.bpm.minotaur.gamedata.history.House;
import com.bpm.minotaur.gamedata.history.War;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Raids below ground (Living War W32): the one part of the war still dealt by slot, since armies
 * are surface only (A1). Pure: a function of the seed, the war clock and the chunk.
 */
public final class RaidsBelow {

    /** War turns a raid below ground holds a chunk. */
    public static final int SLOT = 100;
    private static final long SALT = 0x3A7C0FFEEL;

    private RaidsBelow() {
    }

    /** Chance a gash chunk holds raiders, each slot its holder is at war (W32). */
    static final float GASH_RAID = 0.35f;
    /** Chance a town's gate is raided, each slot (W32). */
    static final float TOWN_RAID = 0.12f;

    /**
     * Raiders come down into a gash while the house holding it is at war (W32): its enemy's soldiers,
     * in some of its chunks, a slot at a time. Null when none are here now.
     */
    public static Encounter gashRaid(HistoryWorld world, long clock, GridPoint2 chunk, int level, int holderId) {
        if (world == null || chunk == null || holderId < 0) return null;
        List<War> wars = new ArrayList<>();
        for (War w : world.activeWars()) if (w.involves(holderId)) wars.add(w);
        if (wars.isEmpty()) return null;
        long k = Math.floorDiv(clock, SLOT);
        Random r = new Random(world.seed ^ SALT ^ (k * 0x9E3779B97F4A7C15L) ^ (chunk.x * 0x632BE59BD9B4E019L)
                ^ (chunk.y * 0x85157AF5L) ^ (level * 0x2545F4914F6CDD1DL));
        if (r.nextFloat() >= GASH_RAID) return null;
        War w = wars.get(r.nextInt(wars.size()));
        return new Encounter(Encounter.Kind.RAID, k, w.id, w.enemyOf(holderId), holderId, k * SLOT, (k + 1) * SLOT, chunk, chunk);
    }

    /** Now and then a house falls on a town's gate (W32): a slot at a time, any living house. Null when none does. */
    public static Encounter townRaid(HistoryWorld world, long clock, String townKey, GridPoint2 chunk) {
        if (world == null || chunk == null || townKey == null) return null;
        List<House> houses = world.livingHouses();
        if (houses.isEmpty()) return null;
        long k = Math.floorDiv(clock, SLOT);
        Random r = new Random(world.seed ^ SALT ^ (k * 0xC2B2AE3D27D4EB4FL) ^ (townKey.hashCode() * 0x9E3779B97F4A7C15L));
        if (r.nextFloat() >= TOWN_RAID) return null;
        House h = houses.get(r.nextInt(houses.size()));
        return new Encounter(Encounter.Kind.RAID, k, -1, h.id, -1, k * SLOT, (k + 1) * SLOT, chunk, chunk);
    }
}
