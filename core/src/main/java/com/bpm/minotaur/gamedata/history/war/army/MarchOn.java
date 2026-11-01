package com.bpm.minotaur.gamedata.history.war.army;

import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.gamedata.monster.Monster;
import com.bpm.minotaur.gamedata.monster.MonsterRole;

import java.util.function.ToIntFunction;

/**
 * The player marching with a host of their liege (Living War A36): time skips, the player keeping to
 * the host chunk by chunk, until the host meets anything. This only watches; a followed host goes
 * where it would have gone, and the player never steers it.
 */
public final class MarchOn {

    /** What ended a slice of the march. NONE and MOVED go on; the rest stop it. */
    public enum Stop {
        NONE, MOVED, GONE, BATTLE, SIEGE, ARMY, HALTED, NATIVES;

        public boolean goesOn() {
            return this == NONE || this == MOVED;
        }
    }

    /** The most turns a frame (A59). */
    public static final int SLICE = 250;
    /** How near a native Soldier or Heavy must be to the player, on coming into a chunk, to stop them. */
    static final int NATIVE_SIGHT = 8;

    private final int hostId;
    private int x;
    private int y;
    /** Armies already here when this began or last checked: a meeting is with one newly come. */
    private int[] known = new int[0];
    private Stop last = Stop.NONE;

    public MarchOn(Army host, WarRoster roster) {
        hostId = host.id;
        x = host.x;
        y = host.y;
        note(host, roster);
    }

    private void note(Army host, WarRoster roster) {
        known = new int[0];
        for (int i = 0; i < roster.armies.size(); i++) {
            Army o = roster.armies.get(i);
            if (o != host && o.x == host.x && o.y == host.y) known = ArmyBattle.append(known, o.id);
        }
    }

    /** What the last slice ended on. */
    public Stop last() {
        return last;
    }

    /** Called after each turn: what the host has come to. */
    public Stop after(WarRoster roster) {
        last = check(roster);
        return last;
    }

    private Stop check(WarRoster roster) {
        Army host = roster.army(hostId);
        if (host == null || host.soldiers <= 0) return Stop.GONE;
        if (host.battle) return Stop.BATTLE;
        if (host.sieging >= 0 || host.beforeTheCastle) return Stop.SIEGE;
        boolean moved = host.x != x || host.y != y;
        if (moved) {
            x = host.x;
            y = host.y;
            known = new int[0];
        }
        for (int i = 0; i < roster.armies.size(); i++) {
            Army o = roster.armies.get(i);
            if (o == host || o.x != host.x || o.y != host.y || o.side() == host.side()) continue;
            if (!ArmyBattle.holds(known, o.id)) {
                known = ArmyBattle.append(known, o.id);
                return Stop.ARMY;
            }
        }
        if (host.halted()) return Stop.HALTED;
        return moved ? Stop.MOVED : Stop.NONE;
    }

    /** Whether the player is still with the host: in its chunk or the next, on the surface. */
    public static boolean stillWith(Army host, GridPoint2 chunk, int level) {
        return level == 1 && chunk != null && Math.max(Math.abs(host.x - chunk.x), Math.abs(host.y - chunk.y)) <= 1;
    }

    /**
     * Native Fodder leave the column alone; Soldiers and Heavies may not (A36): whether one is near
     * enough, in the chunk the player has just come into, to stop the march.
     *
     * @param houseOf the house a monster fights for, below 0 for a native
     */
    public static boolean nativesBar(Iterable<Monster> monsters, ToIntFunction<Monster> houseOf, float px, float py) {
        for (Monster m : monsters) {
            if (m == null || !m.isAlive() || m.isWarBand() || houseOf.applyAsInt(m) >= 0 || m.getRole() == MonsterRole.FODDER) continue;
            if (m.getPosition().dst(px, py) <= NATIVE_SIGHT) return true;
        }
        return false;
    }

    /** Whether this monster leaves the player alone for marching with a host here. */
    public static boolean leavesTheColumnAlone(Monster m, int house) {
        return leavesTheColumnAlone(m.getRole(), m.isWarBand(), house);
    }

    static boolean leavesTheColumnAlone(MonsterRole role, boolean warBand, int house) {
        return house < 0 && !warBand && role == MonsterRole.FODDER;
    }
}
