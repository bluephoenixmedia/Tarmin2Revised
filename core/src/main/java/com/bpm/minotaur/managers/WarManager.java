package com.bpm.minotaur.managers;

import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.history.DoctrineCatalog;
import com.bpm.minotaur.gamedata.history.war.SeatMap;
import com.bpm.minotaur.gamedata.history.war.army.ArmyBattle;
import com.bpm.minotaur.gamedata.history.war.army.ArmyLayer;
import com.bpm.minotaur.gamedata.monster.Monster;

import java.util.ArrayList;
import java.util.List;

/**
 * Brings the armies' battles to the player (Living War A20-A26): the battle under way in the chunk
 * they stand in appears around them, the map being its truth for as long as they stay. A battle
 * that begins with them there sounds the horns; one walked into does not. One at a time, and going
 * away only stops the watching: the battle goes on in its numbers without them.
 */
public final class WarManager {

    /** What the game needs to know about where the player stands, this turn. */
    public static final class Ground {
        public GridPoint2 chunk;
        public int level;
        /** A shelter: no battle is fought on sanctuary ground. */
        public boolean sanctuary;
        /** The armies of this world, and the battle in this chunk; either may be null. */
        public ArmyLayer layer;
        public ArmyBattle battle;
        public SeatMap seats;
        public Maze maze;
        public GridPoint2 playerTile;
    }

    /** The war's sounds (plan T2.9): horns at the warning, drums as the lines close, horns at the rout. */
    public enum Cue { HORNS, JOINED, ROUT }

    /** What happened this turn. */
    public static final class Turn {
        public final List<String> messages = new ArrayList<>();
        public final List<Cue> cues = new ArrayList<>();
        public int volleyDamage;
        /** When the battle breaks: who held the field, and whom the player fought for (W19); -1 otherwise. */
        public int winner = -1;
        public int foughtFor = -1;
        /** The lines have just appeared, and the player has not said where they stand (A26). */
        public boolean offerSides;
    }

    private final DoctrineCatalog catalog;
    private BattleDirector active;

    public WarManager(DoctrineCatalog catalog) {
        this.catalog = catalog;
    }

    public BattleDirector active() {
        return active;
    }

    public Turn onTurn(HistoryManager history, Ground g, BattleDirector.Recruiter recruiter, BattleDirector.Spoils spoils) {
        Turn t = new Turn();
        long clock = history.warClock();
        if (active != null) {
            boolean left = g.level != 1 || g.chunk == null || !active.chunk.equals(g.chunk);
            boolean fighting = active.phase() == BattleDirector.Phase.WARNING || active.phase() == BattleDirector.Phase.BATTLE;
            // The world was reborn, or a sleep passed, and the numbers have gone on alone.
            boolean letGo = fighting && (g.layer != active.layer() || active.layer().watchedBattle() != active.battle);
            if (left && fighting) t.messages.add(active.abandoned());
            if (left || letGo) {
                active.collapse(clock);
                active = null;
            }
        }
        if (active == null) {
            if (g.level != 1 || g.chunk == null || g.sanctuary || g.layer == null || g.battle == null || g.maze == null) return t;
            // A battle a turn old began with the player here; any older they walked into (A21).
            boolean underWay = g.battle.turn > 1;
            int edgeA = BattleDirector.edgeToward(g.chunk, g.seats == null ? null : g.seats.seat(g.battle.houseA));
            long seed = history.world().seed ^ (clock * 0x9E3779B97F4A7C15L) ^ (g.chunk.x * 31L + g.chunk.y);
            active = new BattleDirector(g.battle, g.layer, g.chunk, edgeA, history.world(), catalog, seed, underWay);
            if (!underWay) {
                t.messages.add(active.warning());
                t.cues.add(Cue.HORNS);
                return t;
            }
            t.offerSides = g.battle.playerSide == 0;
        }
        BattleDirector.Phase was = active.phase();
        BattleDirector.Report r = active.tick(g.maze, g.playerTile, recruiter, spoils, clock);
        t.messages.addAll(r.messages);
        t.volleyDamage = r.volleyDamage;
        if (was == BattleDirector.Phase.WARNING && active.phase() == BattleDirector.Phase.BATTLE) {
            t.cues.add(Cue.JOINED);
            t.offerSides = g.battle != null && g.battle.playerSide == 0;
        }
        if (r.winner >= 0) {
            t.winner = r.winner;
            t.foughtFor = active.playerFoughtFor();
            t.cues.add(Cue.ROUT);
        }
        if (active.phase() == BattleDirector.Phase.DONE) active = null;
        return t;
    }

    /** Whether this soldier stands on the side the player chose: an ally until the fight ends (A26). */
    public boolean alliedWith(Monster m) {
        return active != null && active.alliedWith(m);
    }
}
