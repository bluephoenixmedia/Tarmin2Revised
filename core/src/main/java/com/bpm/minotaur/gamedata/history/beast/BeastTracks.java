package com.bpm.minotaur.gamedata.history.beast;

import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.gamedata.history.HistoryWorld;
import com.bpm.minotaur.gamedata.history.Megabeast;

import java.util.Random;

/**
 * Where each megabeast is (plan D34, T3.3). A beast lairs at a chunk of its lair's level and
 * roams the chunks around it as the war clock turns; a beast hunting the player follows them
 * through a gate or down a ladder a few turns behind. A beast is never saved in a chunk: it is
 * wherever these rules put it, and only its wounds and its hunt are kept.
 */
public final class BeastTracks {

    /** How far from its lair a beast roams, in chunks. */
    public static final int ROAM_RADIUS = 2;
    /** War-clock turns for one circuit of its range. */
    public static final long ROAM_PERIOD = 1200;
    /** Turns behind the player a hunting beast arrives. */
    public static final long HUNT_DELAY = 3;
    /** Turns a hunt lasts before the beast gives up and goes home. */
    public static final long HUNT_LENGTH = 300;

    static final int LAIR_MIN = 2;
    static final int LAIR_MAX = 24;

    /** A beast following the player: where it is going, when it gets there, when it gives up. */
    public static final class Hunt {
        public int beastId = -1;
        public int chunkX;
        public int chunkY;
        public int level;
        public long readyAt;
        public long until;

        public GridPoint2 chunk() {
            return new GridPoint2(chunkX, chunkY);
        }
    }

    private BeastTracks() {
    }

    public static GridPoint2 lairChunk(long worldSeed, Megabeast b) {
        Random rng = new Random(worldSeed ^ (b.id * 0x2545F4914F6CDD1DL) ^ 0xBEA57L);
        double angle = rng.nextDouble() * Math.PI * 2;
        int dist = LAIR_MIN + rng.nextInt(LAIR_MAX - LAIR_MIN + 1);
        return new GridPoint2((int) Math.round(Math.cos(angle) * dist), (int) Math.round(Math.sin(angle) * dist));
    }

    public static GridPoint2 roamChunk(long worldSeed, Megabeast b, long clock) {
        GridPoint2 lair = lairChunk(worldSeed, b);
        double phase = ((b.id * 0x9E3779B9L + worldSeed) & 0xFFFF) / 65536.0 * Math.PI * 2;
        double t = Math.PI * 2 * clock / ROAM_PERIOD + phase;
        return new GridPoint2(lair.x + (int) Math.round(ROAM_RADIUS * Math.cos(t)),
                lair.y + (int) Math.round(ROAM_RADIUS * Math.sin(t)));
    }

    /** The beast in this chunk at this level right now, or null. */
    public static Megabeast presentAt(HistoryWorld world, long worldSeed, long clock, GridPoint2 chunk, int level, Hunt hunt) {
        if (chunk == null) return null;
        for (Megabeast b : world.megabeasts()) {
            if (!b.isAlive() || !b.isAwake(world.season())) continue;
            if (hunt != null && hunt.beastId == b.id && clock < hunt.until) {
                if (clock >= hunt.readyAt && hunt.level == level && hunt.chunk().equals(chunk)) return b;
                continue;
            }
            if (level == b.lairLevel && roamChunk(worldSeed, b, clock).equals(chunk)) return b;
        }
        return null;
    }
}
