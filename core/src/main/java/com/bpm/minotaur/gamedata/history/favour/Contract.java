package com.bpm.minotaur.gamedata.history.favour;

import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.gamedata.history.House;
import com.bpm.minotaur.gamedata.history.HistoryWorld;
import com.bpm.minotaur.gamedata.history.War;
import com.bpm.minotaur.gamedata.history.war.Front;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * A task a sworn house gives the player (Living War W25): hold a chunk of its front, kill an
 * enemy captain, see one of its columns through, or break an enemy camp. A hard contract -- one of
 * the three the seal-grant chain asks for (W26) -- asks more and pays more.
 */
public class Contract {

    public enum Kind { HOLD_CHUNK, KILL_CAPTAIN, ESCORT_COLUMN, BURN_CAMP }

    public static final int HOLD_TURNS = 30;
    public static final int HARD_HOLD_TURNS = 60;
    public static final int FAVOUR = 10;
    public static final int HARD_FAVOUR = 20;
    /** Coin a contract pays, on top of favour; a hard one pays twice. */
    public static final int COIN = 60;

    public Kind kind;
    /** The sworn house that gave it. */
    public int giverId = -1;
    /** The enemy house it is against, where it has one. */
    public int targetId = -1;
    /** HOLD_CHUNK: the chunk to hold. */
    public int chunkX;
    public int chunkY;
    public int held;
    public boolean hard;
    public boolean done;

    /** For the save reader. */
    public Contract() {
    }

    /**
     * The next task house {@code giverId} sets: chosen from the seed and how many it has set before,
     * against one of its enemies at war. Null when it is at war with no one.
     */
    public static Contract offer(HistoryWorld world, int giverId, int count, boolean hard, List<Front> fronts,
            GridPoint2 player) {
        List<War> wars = new ArrayList<>();
        for (War w : world.activeWars()) if (w.involves(giverId)) wars.add(w);
        if (wars.isEmpty()) return null;
        Random r = new Random(world.seed ^ (giverId * 0x9E3779B97F4A7C15L) ^ (count * 0xC2B2AE3D27D4EB4FL) ^ 0xC0A7L);
        War w = wars.get(r.nextInt(wars.size()));
        Contract c = new Contract();
        c.giverId = giverId;
        c.targetId = w.enemyOf(giverId);
        c.hard = hard;
        c.kind = Kind.values()[r.nextInt(Kind.values().length)];
        if (c.kind == Kind.HOLD_CHUNK) {
            Front front = null;
            if (fronts != null) for (Front f : fronts) if (f.warId == w.id) front = f;
            if (front == null) {
                c.kind = Kind.KILL_CAPTAIN;
            } else {
                c.chunkX = front.center.x;
                c.chunkY = front.center.y;
            }
        }
        return c;
    }

    public int turnsToHold() {
        return hard ? HARD_HOLD_TURNS : HOLD_TURNS;
    }

    public int favour() {
        return hard ? HARD_FAVOUR : FAVOUR;
    }

    public int coin() {
        return hard ? COIN * 2 : COIN;
    }

    /** A turn in {@code chunk}: holding counts while the player stands on the chunk asked for. True when it is done. */
    public boolean held(GridPoint2 chunk) {
        if (done || kind != Kind.HOLD_CHUNK || chunk == null || chunk.x != chunkX || chunk.y != chunkY) return false;
        held++;
        if (held >= turnsToHold()) done = true;
        return done;
    }

    /** What the captain asks, in the house's words. */
    public String describe(HistoryWorld world) {
        String enemy = name(world, targetId);
        String ask;
        switch (kind) {
            case HOLD_CHUNK:
                ask = "Hold the ground of our front (chunk " + chunkX + ", " + chunkY + ") for " + turnsToHold()
                        + " turns against " + enemy + ".";
                break;
            case KILL_CAPTAIN:
                ask = "Kill a captain of " + enemy + ", or one of its sworn swords.";
                break;
            case ESCORT_COLUMN:
                ask = "See one of our columns through, from edge to edge, alive.";
                break;
            default:
                ask = "Break a war camp of " + enemy + ": cut down every sentry in it.";
                break;
        }
        return (hard ? "A hard task: " : "") + ask;
    }

    private static String name(HistoryWorld world, int houseId) {
        House h = world.house(houseId);
        return h == null ? "our enemy" : h.name;
    }
}
