package com.bpm.minotaur.gamedata.history.favour;

import com.bpm.minotaur.gamedata.history.Figure;
import com.bpm.minotaur.gamedata.history.House;
import com.bpm.minotaur.gamedata.history.HistoryWorld;
import com.bpm.minotaur.gamedata.history.PlayerDeed;
import com.bpm.minotaur.gamedata.history.War;
import com.bpm.minotaur.gamedata.history.war.army.Army;
import com.bpm.minotaur.gamedata.history.war.army.SeatSiege;
import com.bpm.minotaur.gamedata.history.war.army.WarRoster;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * A task a sworn house gives the player (Living War A37): each names a real thing in the roster. If
 * that thing disappears the contract is void, with no penalty and no pay, unless it fell in a battle
 * the player fought in for their liege, which counts as done (A38). A hard contract, one of the three
 * the seal-grant chain asks for (W26), pays more.
 */
public class Contract {

    public enum Kind {
        /** Break a named host. */
        BREAK_HOST,
        /** Kill or take a named host's captain. */
        TAKE_CAPTAIN,
        /** See the liege's supply column through. */
        ESCORT_SUPPLY,
        /** Cut off an enemy's supply column. */
        CUT_SUPPLY,
        /** Break a war camp. */
        BREAK_CAMP,
        /** Relieve the liege's besieged seat. */
        RELIEVE_SEAT,
        /** Join the assault on an enemy seat. */
        JOIN_ASSAULT,
        /** Stand with a named host through its next battle. */
        STAND_WITH_HOST,
        /** The house besieges Castle Tarmin: join the assault, through the breach into its halls (W37). */
        SIEGE_ASSAULT,
        // The kinds before A37, for reading old saves: a contract of these is void on load.
        HOLD_CHUNK, KILL_CAPTAIN, ESCORT_COLUMN, BURN_CAMP
    }

    public static final int FAVOUR = 10;
    public static final int HARD_FAVOUR = 20;
    /** Coin a contract pays, on top of favour; a hard one pays twice. */
    public static final int COIN = 60;

    public Kind kind;
    /** The sworn house that gave it. */
    public int giverId = -1;
    /** The enemy house it is against, where it has one. */
    public int targetId = -1;
    /** The army, captain or siege it names, where it names one. */
    public int targetArmy = -1;
    public int targetFigure = -1;
    public int targetSiege = -1;
    /** The target's last known chunk, as far as the liege knows. */
    public int chunkX;
    public int chunkY;
    /** How long a chunk was held, in a contract from before A37: read from an old save and no more. */
    public int held;
    public boolean hard;
    public boolean done;

    /** For the save reader. */
    public Contract() {
    }

    /**
     * The next task house {@code giverId} sets: a thing its war has now, chosen from the seed and how
     * many it has set before. Null when it has nothing to ask.
     */
    public static Contract offer(HistoryWorld world, WarRoster roster, int giverId, int count, boolean hard) {
        Contract base = new Contract();
        base.giverId = giverId;
        base.hard = hard;
        War castle = world.activeSiege();
        if (castle != null && world.tarminHouse() != null && castle.enemyOf(world.tarminHouse().id) == giverId) {
            // A house laying siege wants its sworn on the walls before anything else.
            base.kind = Kind.SIEGE_ASSAULT;
            base.targetId = world.tarminHouse().id;
            return base;
        }
        List<Contract> open = new ArrayList<>();
        if (roster != null) {
            for (SeatSiege s : roster.sieges) {
                if (s.holder == giverId) open.add(aimed(base, Kind.RELIEVE_SEAT, s.besieger, -1, -1, s.id, s.x, s.y));
                else if (s.besieger == giverId) open.add(aimed(base, Kind.JOIN_ASSAULT, s.holder, -1, -1, s.id, s.x, s.y));
            }
            for (Army a : roster.armies) {
                if (a.soldiers <= 0) continue;
                if (a.side() == giverId) {
                    if (a.kind == Army.Kind.SUPPLY) open.add(aimed(base, Kind.ESCORT_SUPPLY, -1, a.id, -1, -1, a.x, a.y));
                    else if (a.isHost()) open.add(aimed(base, Kind.STAND_WITH_HOST, -1, a.id, -1, -1, a.x, a.y));
                    continue;
                }
                if (world.activeWarBetween(a.side(), giverId) == null) continue;
                if (a.kind == Army.Kind.SUPPLY) {
                    open.add(aimed(base, Kind.CUT_SUPPLY, a.side(), a.id, -1, -1, a.x, a.y));
                } else if (a.isHost()) {
                    open.add(aimed(base, Kind.BREAK_HOST, a.side(), a.id, -1, -1, a.x, a.y));
                    if (a.captainId >= 0) open.add(aimed(base, Kind.TAKE_CAPTAIN, a.side(), a.id, a.captainId, -1, a.x, a.y));
                    if (a.halted() && !a.battle) open.add(aimed(base, Kind.BREAK_CAMP, a.side(), a.id, -1, -1, a.x, a.y));
                }
            }
        }
        if (open.isEmpty()) return null;
        Random r = new Random(world.seed ^ (giverId * 0x9E3779B97F4A7C15L) ^ (count * 0xC2B2AE3D27D4EB4FL) ^ 0xC0A7L);
        return open.get(r.nextInt(open.size()));
    }

    private static Contract aimed(Contract base, Kind kind, int enemy, int army, int figure, int siege, int x, int y) {
        Contract c = new Contract();
        c.giverId = base.giverId;
        c.hard = base.hard;
        c.kind = kind;
        c.targetId = enemy;
        c.targetArmy = army;
        c.targetFigure = figure;
        c.targetSiege = siege;
        c.chunkX = x;
        c.chunkY = y;
        return c;
    }

    /** A contract from before A37, which names nothing that can be found now. */
    public boolean oldStyle() {
        return kind == null || kind == Kind.HOLD_CHUNK || kind == Kind.KILL_CAPTAIN || kind == Kind.ESCORT_COLUMN || kind == Kind.BURN_CAMP;
    }

    public int favour() {
        return hard ? HARD_FAVOUR : FAVOUR;
    }

    public int coin() {
        return hard ? COIN * 2 : COIN;
    }

    /** A32's sight: how near one of the liege's armies must be to report where the target is. */
    private static final int LIEGE_SIGHT = 6;

    /** The liege's scouts: the target's chunk, if one of its armies is near enough to see it. A siege never moves. */
    public void reportWhereItIs(WarRoster roster) {
        Army target = roster.army(targetArmy);
        if (target == null) return;
        for (Army a : roster.armies) {
            if (a.side() == giverId && Math.max(Math.abs(a.x - target.x), Math.abs(a.y - target.y)) <= LIEGE_SIGHT) {
                chunkX = target.x;
                chunkY = target.y;
                return;
            }
        }
    }

    /** Whether what it names has gone without it being done: then it is void, no penalty, no pay (A38). */
    public boolean targetGone(HistoryWorld world, WarRoster roster) {
        if (done) return false;
        if (kind == Kind.SIEGE_ASSAULT) return world.activeSiege() == null;
        if (targetSiege >= 0) {
            boolean there = false;
            for (SeatSiege s : roster.sieges) there |= s.id == targetSiege;
            if (!there) return true;
        }
        if (targetArmy >= 0) {
            Army a = roster.army(targetArmy);
            if (a == null || a.soldiers <= 0) return true;
            // Its host's going home, or its war's end, takes a camp or a stand with it too.
        }
        if (targetFigure >= 0) {
            Figure f = world.figure(targetFigure);
            if (f == null || !f.isAlive() || f.hostageOf >= 0) return true;
        }
        return targetId >= 0 && world.activeWarBetween(giverId, targetId) == null;
    }

    /**
     * A battle of the armies has ended. A target that fell in a battle the player fought in for their
     * liege counts as done, whoever struck the blow (A38); the rest ask for the player's presence too.
     * True if this kept it.
     */
    public boolean onBattle(PlayerDeed deed) {
        if (done || deed.kind != PlayerDeed.Kind.ARMY_BATTLE || deed.stoodWith != giverId) return false;
        boolean won = deed.target == giverId;
        switch (kind) {
            case BREAK_HOST:
            case CUT_SUPPLY:
            case BREAK_CAMP:
                done = won && has(deed.armiesRouted, targetArmy);
                break;
            case TAKE_CAPTAIN:
                done = has(deed.captainsSlain, targetFigure) || has(deed.captainsTaken, targetFigure);
                break;
            case RELIEVE_SEAT:
                done = won && deed.chunkX == chunkX && deed.chunkY == chunkY;
                break;
            case JOIN_ASSAULT:
                done = won && deed.seatFell && deed.chunkX == chunkX && deed.chunkY == chunkY;
                break;
            case STAND_WITH_HOST:
                // Through it, win or lose.
                done = has(deed.armiesRouted, targetArmy) || has(deed.armiesHeld, targetArmy);
                break;
            default:
                break;
        }
        return done;
    }

    private static boolean has(int[] ids, int id) {
        if (ids == null) return false;
        for (int i : ids) if (i == id) return true;
        return false;
    }

    /** The player killed a soldier of the enemy: the captain the contract names, if this was them. */
    public boolean onSoldierSlain(int figureId) {
        if (done || kind != Kind.TAKE_CAPTAIN || figureId < 0 || figureId != targetFigure) return false;
        done = true;
        return true;
    }

    /** What the captain asks, in the house's words. */
    public String describe(HistoryWorld world) {
        String enemy = name(world, targetId);
        String where = "; last known at chunk " + chunkX + ", " + chunkY + ".";
        String ask;
        switch (kind) {
            case BREAK_HOST:
                ask = "Stand with us and break a host of " + enemy + where;
                break;
            case TAKE_CAPTAIN: {
                Figure f = world.figure(targetFigure);
                ask = "Kill or take " + (f != null ? f.name : "a captain") + ", who leads a host of " + enemy + where;
                break;
            }
            case ESCORT_SUPPLY:
                ask = "See one of our supply columns through, from edge to edge, alive" + where;
                break;
            case CUT_SUPPLY:
                ask = "Cut off a supply column of " + enemy + where;
                break;
            case BREAK_CAMP:
                ask = "Break a war camp of " + enemy + ": cut down every sentry in it" + where;
                break;
            case RELIEVE_SEAT:
                ask = "Our seat is besieged by " + enemy + ": fight at its walls and drive them off, at chunk " + chunkX + ", " + chunkY + ".";
                break;
            case JOIN_ASSAULT:
                ask = "Join our assault on the seat of " + enemy + ", at chunk " + chunkX + ", " + chunkY + ".";
                break;
            case STAND_WITH_HOST:
                ask = "Stand with one of our hosts through its next battle" + where;
                break;
            case SIEGE_ASSAULT:
                ask = "Join our assault on Castle Tarmin: go in by the breach in its wall, into its halls. "
                        + "For one of ours, the breach asks a single seal.";
                break;
            default:
                ask = "Your task is forgotten.";
                break;
        }
        return (hard ? "A hard task: " : "") + ask;
    }

    private static String name(HistoryWorld world, int houseId) {
        House h = world.house(houseId);
        return h == null ? "our enemy" : h.name;
    }
}
