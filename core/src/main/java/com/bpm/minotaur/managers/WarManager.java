package com.bpm.minotaur.managers;

import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.history.DoctrineCatalog;
import com.bpm.minotaur.gamedata.history.war.Front;
import com.bpm.minotaur.gamedata.history.war.FrontPlanner;
import com.bpm.minotaur.gamedata.history.war.SeatMap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Brings the surface wars to the player (plan D14-D16, T2.3): when a front reaches the chunk
 * they stand in, the horns sound; leaving, or going below, declines the battle; staying fights
 * it. One battle at a time, and a declined or finished battle does not sound again on the same
 * ground until the front has moved on.
 */
public final class WarManager {

    /** War-clock turns before a front can bring a battle back to the same chunk. */
    static final long QUIET_TURNS = FrontPlanner.PERIOD / 4;

    /** What the game needs to know about where the player stands, this turn. */
    public static final class Ground {
        public GridPoint2 chunk;
        public int level;
        /** A shelter: no battle is fought on sanctuary ground. */
        public boolean sanctuary;
        public Front front;
        public SeatMap seats;
        public Maze maze;
        public GridPoint2 playerTile;
    }

    /** What happened this turn. */
    public static final class Turn {
        public final List<String> messages = new ArrayList<>();
        public int volleyDamage;
    }

    private final DoctrineCatalog catalog;
    private BattleDirector active;
    private final Map<String, Long> quietUntil = new HashMap<>();

    public WarManager(DoctrineCatalog catalog) {
        this.catalog = catalog;
    }

    public BattleDirector active() {
        return active;
    }

    public Turn onTurn(HistoryManager history, Ground g, BattleDirector.Recruiter recruiter, BattleDirector.Spoils spoils) {
        Turn t = new Turn();
        long clock = history.warClock();
        if (active != null && (g.level != 1 || g.chunk == null || !active.chunk.equals(g.chunk))) {
            t.messages.add(active.abandoned());
            quiet(active.front.warId, active.chunk, clock);
            active = null;
        }
        if (active == null) {
            if (g.level != 1 || g.chunk == null || g.sanctuary || g.front == null || g.seats == null || g.maze == null) return t;
            Long until = quietUntil.get(key(g.front.warId, g.chunk));
            if (until != null && clock < until) return t;
            int edgeA = BattleDirector.edgeToward(g.chunk, g.seats.seat(g.front.attackerId));
            long seed = history.world().seed ^ (clock * 0x9E3779B97F4A7C15L) ^ (g.chunk.x * 31L + g.chunk.y);
            active = new BattleDirector(g.front, g.chunk, edgeA, history.world(), catalog, seed);
            t.messages.add(active.warning());
            return t;
        }
        BattleDirector.Report r = active.tick(g.maze, g.playerTile, recruiter, spoils);
        t.messages.addAll(r.messages);
        t.volleyDamage = r.volleyDamage;
        if (r.winner >= 0) history.recordBattle(r.winner, r.loser);
        if (active.phase() == BattleDirector.Phase.DONE) {
            quiet(active.front.warId, active.chunk, clock);
            active = null;
        }
        return t;
    }

    private void quiet(int warId, GridPoint2 chunk, long clock) {
        quietUntil.put(key(warId, chunk), clock + QUIET_TURNS);
    }

    private static String key(int warId, GridPoint2 chunk) {
        return warId + ":" + chunk.x + ":" + chunk.y;
    }
}
