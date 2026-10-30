package com.bpm.minotaur.gamedata.history.war.army;

import com.bpm.minotaur.gamedata.history.Figure;
import com.bpm.minotaur.gamedata.history.HistoryWorld;
import com.bpm.minotaur.gamedata.history.House;
import com.bpm.minotaur.gamedata.history.Trait;
import com.bpm.minotaur.gamedata.history.War;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Every army in the field: the snapshot saved beside the replayed history (ADR 0006). The history
 * never reads it. It answers to the history, and {@link #reconcile} makes it agree.
 */
public class WarRoster {

    public List<Army> armies = new ArrayList<>();
    public int nextArmyId = 1;
    /** History events already cried by heralds; -1 until armies first march (A15). */
    public int eventsHeard = -1;
    public List<ArmyBattle> battles = new ArrayList<>();
    /** What each war's houses stood at when it began, as warId, attacker's and defender's strength. */
    public List<float[]> warStarts = new ArrayList<>();

    /** Soldiers each house has lost to the land this Season, as parallel lists, not yet in a deed (A27). */
    public int[] attritionHouses = new int[0];
    public int[] attritionDead = new int[0];
    /** Where each house's armies last saw the player, as house, x, y (A12). */
    public List<int[]> playerLastSeen = new ArrayList<>();

    static int packed(int x, int y) {
        return (x + 4096) << 13 | (y + 4096);
    }

    /** Whether any army crossed this chunk this Season or last (A29). */
    public boolean onTrail(int x, int y) {
        int p = packed(x, y);
        for (int i = 0; i < armies.size(); i++) {
            Army a = armies.get(i);
            if (ArmyBattle.holds(a.trail, p) || ArmyBattle.holds(a.trailLast, p)) return true;
        }
        return false;
    }

    void addAttrition(int houseId, int dead) {
        for (int i = 0; i < attritionHouses.length; i++) {
            if (attritionHouses[i] == houseId) {
                attritionDead[i] += dead;
                return;
            }
        }
        attritionHouses = ArmyBattle.append(attritionHouses, houseId);
        attritionDead = ArmyBattle.append(attritionDead, dead);
    }

    /** Where house {@code houseId}'s armies last saw the player, or null. */
    int[] playerSeenBy(int houseId) {
        for (int i = 0; i < playerLastSeen.size(); i++) {
            if (playerLastSeen.get(i)[0] == houseId) return playerLastSeen.get(i);
        }
        return null;
    }

    void notePlayerSeen(int houseId, int x, int y) {
        int[] s = playerSeenBy(houseId);
        if (s == null) {
            playerLastSeen.add(new int[] {houseId, x, y});
        } else {
            s[1] = x;
            s[2] = y;
        }
    }

    /** Soldiers house {@code houseId} has in the field. */
    public int fielded(int houseId) {
        int n = 0;
        for (int i = 0; i < armies.size(); i++) {
            Army a = armies.get(i);
            if (a.houseId == houseId) n += a.soldiers;
        }
        return n;
    }

    /** Soldiers at home: its strength, less those in the field and those dead in battles not yet over. */
    public int garrison(HistoryWorld world, int houseId) {
        House h = world.house(houseId);
        if (h == null) return 0;
        int dead = 0;
        for (int i = 0; i < battles.size(); i++) dead += battles.get(i).deadOf(houseId);
        for (int i = 0; i < attritionHouses.length; i++) {
            if (attritionHouses[i] == houseId) dead += attritionDead[i];
        }
        return Math.max(0, (int) Math.floor(h.strength) - fielded(houseId) - dead);
    }

    /** What house {@code houseId} stood at when war {@code warId} began; records it if new. */
    float strengthAtWarStart(War w, int houseId, HistoryWorld world) {
        for (int i = 0; i < warStarts.size(); i++) {
            float[] s = warStarts.get(i);
            if ((int) s[0] == w.id) return houseId == w.attackerId ? s[1] : s[2];
        }
        House a = world.house(w.attackerId), d = world.house(w.defenderId);
        float[] s = {w.id, a == null ? 0f : a.strength, d == null ? 0f : d.strength};
        warStarts.add(s);
        return houseId == w.attackerId ? s[1] : s[2];
    }

    /** The army with this id, or null. */
    public Army army(int id) {
        for (int i = 0; i < armies.size(); i++) {
            if (armies.get(i).id == id) return armies.get(i);
        }
        return null;
    }

    /**
     * The history wins any disagreement (ADR 0006): armies of an extinct house are dropped, armies
     * of an ended war march home, a captain dead or held hostage is replaced, and a house's armies
     * shrink to fit its strength. Run on load, and each Season while the Season can still move strength.
     */
    public void reconcile(HistoryWorld world) {
        for (Iterator<Army> it = armies.iterator(); it.hasNext(); ) {
            Army a = it.next();
            House h = world.house(a.houseId);
            if (h == null || h.isExtinct()) {
                it.remove();
                continue;
            }
            if (a.battle) continue;
            boolean host = a.kind == Army.Kind.HOST || a.kind == Army.Kind.LEVY;
            if (host && a.objective != Army.Objective.DISBAND && !warStands(world, a)) {
                a.objective = Army.Objective.DISBAND;
                a.path = null;
                a.targetArmy = -1;
            }
            Figure c = world.figure(a.captainId);
            if (a.captainId >= 0 && (c == null || !c.isAlive() || c.hostageOf >= 0)) {
                a.captainId = -1;
                a.captainId = pickCaptain(world, h);
            }
        }
        for (House h : world.houses()) fitToStrength(h);
    }

    private boolean warStands(HistoryWorld world, Army a) {
        for (War w : world.wars()) {
            if (w.id == a.warId) return w.isActive();
        }
        return false;
    }

    /** A house's field force never outgrows what its strength leaves after the garrison. */
    private void fitToStrength(House h) {
        int limit = (int) Math.floor(h.strength * (1f - ArmyTuning.GARRISON_SHARE));
        int total = fielded(h.id);
        if (total <= limit) return;
        for (Iterator<Army> it = armies.iterator(); it.hasNext(); ) {
            Army a = it.next();
            if (a.houseId != h.id) continue;
            a.soldiers = limit <= 0 ? 0 : (int) ((long) a.soldiers * limit / total);
            if (a.soldiers <= 0) it.remove();
        }
    }

    /**
     * Who leads a new army of house {@code h} (Living War A6): the lord when their traits fit, else
     * kin, else a sworn sword, and never one who already leads an army. -1 leaves it nameless.
     */
    int pickCaptain(HistoryWorld world, House h) {
        Figure lord = world.lordOf(h);
        if (free(lord) && !lord.has(Trait.CRAVEN) && (lord.has(Trait.WRATHFUL) || lord.has(Trait.AMBITIOUS))) return lord.id;
        for (Figure f : world.livingMembers(h)) {
            if (f.id != h.lordId && free(f)) return f.id;
        }
        for (Figure f : world.swornSwords(h)) {
            if (free(f)) return f.id;
        }
        return -1;
    }

    private boolean free(Figure f) {
        if (f == null || !f.isAlive() || f.hostageOf >= 0) return false;
        for (int i = 0; i < armies.size(); i++) {
            if (armies.get(i).captainId == f.id) return false;
        }
        return true;
    }
}
