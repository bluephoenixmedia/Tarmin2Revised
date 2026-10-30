package com.bpm.minotaur.gamedata.history.war.army;

import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.gamedata.history.Figure;
import com.bpm.minotaur.gamedata.history.HistoryWorld;
import com.bpm.minotaur.gamedata.history.House;
import com.bpm.minotaur.gamedata.history.Trait;
import com.bpm.minotaur.gamedata.history.War;
import com.bpm.minotaur.gamedata.history.war.SeatMap;

import java.util.List;
import java.util.SplittableRandom;
import java.util.function.Supplier;

/**
 * The war as a simulation (Living War A1-A7): each war turn, houses at war muster armies and armies
 * march on what they have chosen, whether or not anyone is near. It knows nothing of the player.
 *
 * <p>Randomness comes from the history's seed, the war clock and the army's id, never a saved
 * generator, so a reload walks the same future (A19). No {@code Gdx.*} calls, and a turn with
 * nothing to decide allocates nothing (A59).
 */
public final class ArmyLayer {

    private final HistoryWorld world;
    private final Terrain terrain;
    private final Supplier<SeatMap> seatsOf;
    private final WarRoster roster;
    private final Pathfinder paths;
    private SeatMap seats;
    private int season;

    public ArmyLayer(HistoryWorld world, Terrain terrain, Supplier<SeatMap> seatsOf, WarRoster roster) {
        this.world = world;
        this.terrain = terrain;
        this.seatsOf = seatsOf;
        this.roster = roster;
        this.paths = new Pathfinder(terrain);
        this.season = world.liveSeasons();
        this.seats = seatsOf.get();
    }

    public WarRoster roster() {
        return roster;
    }

    /** Searches for a route so far, for the budget in Living War A59. */
    public long pathCalls() {
        return paths.calls;
    }

    /** One war turn. */
    public void tick(long clock) {
        boolean newSeason = season != world.liveSeasons();
        if (newSeason) {
            season = world.liveSeasons();
            seats = seatsOf.get();
            roster.reconcile(world);
        }
        if (seats == null) return;
        if (newSeason || clock % ArmyTuning.MUSTER_EVERY == 0 || roster.nextArmyId == 1) muster(clock);
        List<Army> armies = roster.armies;
        for (int i = 0; i < armies.size(); i++) {
            Army a = armies.get(i);
            if (newSeason) decide(a, clock);
            march(a, clock);
            if (armies.size() <= i || armies.get(i) != a) i--; // it disbanded
        }
    }

    // ---------- marching ----------

    private void march(Army a, long clock) {
        if (a.path == null) restorePath(a);
        if (a.marchStep < a.path.length) {
            a.progress++;
            int next = a.path[a.marchStep];
            int cost = terrain.turnsAt(next);
            if (a.progress >= (cost == 0 ? Terrain.OFF_ROAD_TURNS : cost)) {
                a.progress = 0;
                a.x = terrain.xOf(next);
                a.y = terrain.yOf(next);
                a.marchStep++;
                if (a.marchStep == a.path.length) arrive(a, clock);
            }
        } else if ((clock + a.id) % ArmyTuning.IDLE_EVERY == 0) {
            arrive(a, clock);
        }
        if (!roster.armies.contains(a)) return;
        if ((a.objective == Army.Objective.HUNT || a.objective == Army.Objective.FOLLOW)
                && (clock + a.id) % ArmyTuning.RETARGET_EVERY == 0) {
            Army t = roster.army(a.targetArmy);
            if (t == null) decide(a, clock);
            else goTo(a, t.x, t.y);
        }
        if (a.levyOf < 0 && a.kind == Army.Kind.HOST && (clock + a.id) % ArmyTuning.SIGHT_EVERY == 0 && seesNewEnemy(a)) {
            decide(a, clock);
        }
    }

    private void goTo(Army a, int x, int y) {
        a.targetX = x;
        a.targetY = y;
        a.marchFromX = a.x;
        a.marchFromY = a.y;
        a.marchStep = 0;
        a.progress = 0;
        a.path = paths.find(a.x, a.y, x, y);
    }

    /** Searches a saved march again from where it began; the same start and target give the same way. */
    private void restorePath(Army a) {
        if (a.objective == Army.Objective.DISBAND && !(a.targetX == seatX(a) && a.targetY == seatY(a))) {
            goTo(a, seatX(a), seatY(a));
            return;
        }
        a.path = paths.find(a.marchFromX, a.marchFromY, a.targetX, a.targetY);
    }

    private int seatX(Army a) {
        GridPoint2 s = seats.seat(a.houseId);
        return s == null ? a.x : s.x;
    }

    private int seatY(Army a) {
        GridPoint2 s = seats.seat(a.houseId);
        return s == null ? a.y : s.y;
    }

    private boolean atSeat(Army a) {
        return a.x == seatX(a) && a.y == seatY(a);
    }

    private void arrive(Army a, long clock) {
        if (a.objective == Army.Objective.DISBAND && atSeat(a)) {
            roster.armies.remove(a);
            return;
        }
        if (a.objective == Army.Objective.RECOVER && atSeat(a)) {
            House h = world.house(a.houseId);
            int room = Math.min(ArmyTuning.ARMY_SIZE - a.soldiers, spare(h));
            if (room > 0) a.soldiers += room;
            a.startSoldiers = Math.max(a.soldiers, ArmyTuning.MIN_ARMY);
            a.morale = 100;
            if (a.soldiers < ArmyTuning.MIN_ARMY) {
                roster.armies.remove(a);
                return;
            }
        }
        decide(a, clock);
    }

    // ---------- mustering (A3, A7) ----------

    private void muster(long clock) {
        List<House> houses = world.houses();
        for (int i = 0; i < houses.size(); i++) {
            House h = houses.get(i);
            if (h.isExtinct() || seats.seat(h.id) == null) continue;
            int atWar = world.activeWarCount(h.id);
            if (atWar > 0) musterHosts(h, clock);
            else if (h.liegeId >= 0) musterLevy(h, clock);
        }
    }

    private int spare(House h) {
        return (int) Math.floor(h.strength) - roster.fielded(h.id) - (int) Math.ceil(h.strength * ArmyTuning.GARRISON_SHARE);
    }

    private void musterHosts(House h, long clock) {
        int wanted = Math.max(1, Math.round(h.strength / ArmyTuning.STRENGTH_PER_ARMY));
        while (true) {
            int avail = spare(h);
            if (avail < ArmyTuning.MIN_ARMY || hostsOf(h.id) >= wanted) return;
            Army a = raise(h, Math.min(ArmyTuning.ARMY_SIZE, avail), Army.Kind.HOST);
            a.warId = warFor(h, a).id;
            decide(a, clock);
        }
    }

    private void musterLevy(House h, long clock) {
        House liege = world.house(h.liegeId);
        if (liege == null || liege.isExtinct() || world.activeWarCount(liege.id) == 0) return;
        for (int i = 0; i < roster.armies.size(); i++) {
            Army o = roster.armies.get(i);
            if (o.houseId == h.id && o.levyOf >= 0) return;
        }
        int n = Math.round(h.strength * ArmyTuning.LEVY_SHARE);
        if (n < ArmyTuning.MIN_ARMY || spare(h) < n) return;
        Army a = raise(h, n, Army.Kind.LEVY);
        a.levyOf = liege.id;
        for (War w : world.wars()) {
            if (w.isActive() && w.involves(liege.id)) {
                a.warId = w.id;
                break;
            }
        }
        decide(a, clock);
    }

    private Army raise(House h, int soldiers, Army.Kind kind) {
        Army a = new Army();
        a.id = roster.nextArmyId++;
        a.houseId = h.id;
        a.kind = kind;
        a.soldiers = soldiers;
        a.startSoldiers = soldiers;
        a.captainId = roster.pickCaptain(world, h);
        GridPoint2 s = seats.seat(h.id);
        a.x = s.x;
        a.y = s.y;
        a.targetX = s.x;
        a.targetY = s.y;
        a.marchFromX = s.x;
        a.marchFromY = s.y;
        a.path = new int[0];
        roster.armies.add(a);
        return a;
    }

    private int hostsOf(int houseId) {
        int n = 0;
        for (int i = 0; i < roster.armies.size(); i++) {
            Army a = roster.armies.get(i);
            if (a.houseId == houseId && a.kind == Army.Kind.HOST) n++;
        }
        return n;
    }

    /** Split by threat, fewest armies first; a ZEALOT captain goes for the deepest grudge instead (A4). */
    private War warFor(House h, Army a) {
        War best = null;
        boolean zealot = has(a, Trait.ZEALOT);
        float bestGrudge = -1f;
        int bestCount = Integer.MAX_VALUE;
        for (War w : world.wars()) {
            if (!w.isActive() || !w.involves(h.id)) continue;
            if (zealot) {
                float g = world.grudgeWeight(h.id, w.enemyOf(h.id));
                if (g > bestGrudge) {
                    bestGrudge = g;
                    best = w;
                }
                continue;
            }
            int count = 0;
            for (int i = 0; i < roster.armies.size(); i++) {
                Army o = roster.armies.get(i);
                if (o != a && o.houseId == h.id && o.warId == w.id) count++;
            }
            if (count < bestCount) {
                bestCount = count;
                best = w;
            }
        }
        return best;
    }

    // ---------- choosing an objective (A4) ----------

    private boolean has(Army a, Trait t) {
        Figure c = world.figure(a.captainId);
        return a.captainId >= 0 && c != null && c.has(t);
    }

    private War war(Army a) {
        List<War> wars = world.wars();
        for (int i = 0; i < wars.size(); i++) {
            if (wars.get(i).id == a.warId) return wars.get(i);
        }
        return null;
    }

    private void goHome(Army a, Army.Objective why) {
        a.objective = why;
        a.targetArmy = -1;
        goTo(a, seatX(a), seatY(a));
    }

    /** Thinks again: only ever on an event, never on a timer of its own. */
    void decide(Army a, long clock) {
        House h = world.house(a.houseId);
        if (h == null) return;
        War war = war(a);
        if (war == null || !war.isActive()) {
            war = reassign(a, h);
            if (war == null) {
                goHome(a, Army.Objective.DISBAND);
                return;
            }
        }
        if (a.levyOf >= 0) {
            decideLevy(a);
            return;
        }
        float give = has(a, Trait.CRAVEN) ? 0.65f : has(a, Trait.WRATHFUL) ? 0.35f : 0.5f;
        if (a.soldiers < a.startSoldiers * give || a.morale < ArmyTuning.RECOVER_MORALE) {
            goHome(a, Army.Objective.RECOVER);
            return;
        }
        SplittableRandom rng = new SplittableRandom(world.seed ^ (clock * 0x9E3779B97F4A7C15L) ^ (a.id * 0xC2B2AE3D27D4EB4FL));
        int enemyId = war.enemyOf(a.side());
        House enemy = world.house(enemyId);

        Army.Objective best = Army.Objective.GUARD;
        float bestScore = 0.15f + (has(a, Trait.CRAVEN) ? 0.3f : 0f) + (float) rng.nextDouble() * 0.15f;
        int tx = seatX(a), ty = seatY(a);
        int target = -1;

        GridPoint2 enemySeat = enemy == null ? null : seats.seat(enemyId);
        if (enemy != null && !enemy.isExtinct() && !enemy.holdsCastle && enemySeat != null) {
            float garrison = Math.max(0f, enemy.strength - roster.fielded(enemyId));
            float ratio = a.soldiers / (garrison * ArmyTuning.WALL_EDGE + 1f);
            int d = cheb(a.x, a.y, enemySeat.x, enemySeat.y);
            float score = 0.6f + (has(a, Trait.AMBITIOUS) ? 0.5f : 0f) + 0.4f * Math.min(ratio, 2f) - d / 80f
                    - (has(a, Trait.PATIENT) && ratio < 1.5f ? 0.8f : 0f) + (has(a, Trait.ZEALOT) ? 0.2f : 0f)
                    + (float) rng.nextDouble() * 0.15f;
            if (score > bestScore) {
                bestScore = score;
                best = Army.Objective.SEAT;
                tx = enemySeat.x;
                ty = enemySeat.y;
            }
        }
        Army nearest = nearestKnownEnemy(a);
        if (nearest != null) {
            float ratio = (float) a.soldiers / Math.max(1, nearest.soldiers);
            int d = cheb(a.x, a.y, nearest.x, nearest.y);
            float score = 0.5f + (has(a, Trait.WRATHFUL) ? 0.5f : 0f) + 0.4f * Math.min(ratio, 2f) - d / 25f
                    - (has(a, Trait.CUNNING) && ratio > 0.7f && ratio < 1.4f ? 0.5f : 0f) + (float) rng.nextDouble() * 0.15f;
            if (score > bestScore) {
                bestScore = score;
                best = Army.Objective.HUNT;
                tx = nearest.x;
                ty = nearest.y;
                target = nearest.id;
            }
            if (enemyNearSeat(a)) {
                float guard = 0.7f + (has(a, Trait.CRAVEN) ? 0.6f : 0f) + (float) rng.nextDouble() * 0.15f;
                if (guard > bestScore) {
                    best = Army.Objective.GUARD;
                    tx = seatX(a);
                    ty = seatY(a);
                    target = -1;
                }
            }
        }
        boolean same = a.objective == best && a.targetArmy == target && a.targetX == tx && a.targetY == ty && a.path != null;
        a.objective = best;
        a.targetArmy = target;
        if (!same) goTo(a, tx, ty);
    }

    private void decideLevy(Army a) {
        Army nearest = null;
        int bestD = Integer.MAX_VALUE;
        for (int i = 0; i < roster.armies.size(); i++) {
            Army o = roster.armies.get(i);
            if (o.houseId != a.levyOf || o.kind != Army.Kind.HOST) continue;
            int d = cheb(a.x, a.y, o.x, o.y);
            if (d < bestD) {
                bestD = d;
                nearest = o;
            }
        }
        if (nearest == null) {
            if (a.objective != Army.Objective.GUARD || a.path == null) goHome(a, Army.Objective.GUARD);
            return;
        }
        a.objective = Army.Objective.FOLLOW;
        a.targetArmy = nearest.id;
        goTo(a, nearest.x, nearest.y);
    }

    /** A war over: another of the house's wars takes the army, if it has one (A4). */
    private War reassign(Army a, House h) {
        int side = a.side();
        for (War w : world.wars()) {
            if (w.isActive() && w.involves(side)) {
                a.warId = w.id;
                return w;
            }
        }
        return null;
    }

    // ---------- what an army knows (A5) ----------

    private boolean hostile(Army a, Army e) {
        List<War> wars = world.wars();
        int x = a.side(), y = e.side();
        if (x == y) return false;
        for (int i = 0; i < wars.size(); i++) {
            War w = wars.get(i);
            if (w.isActive() && w.involves(x) && w.involves(y)) return true;
        }
        return false;
    }

    private boolean friendly(Army a, Army o) {
        return a.side() == o.side() || world.stance(a.side(), o.side()) == HistoryWorld.Stance.ALLIED;
    }

    /** Within sight of it, of a friend's army, or of its own seat. */
    private boolean known(Army a, Army e) {
        GridPoint2 home = seats.seat(a.side());
        if (home != null && cheb(home.x, home.y, e.x, e.y) <= ArmyTuning.SIGHT) return true;
        if (cheb(a.x, a.y, e.x, e.y) <= ArmyTuning.SIGHT) return true;
        for (int i = 0; i < roster.armies.size(); i++) {
            Army o = roster.armies.get(i);
            if (cheb(o.x, o.y, e.x, e.y) <= ArmyTuning.SIGHT && friendly(a, o)) return true;
        }
        return false;
    }

    private Army nearestKnownEnemy(Army a) {
        Army best = null;
        int bestD = Integer.MAX_VALUE;
        for (int i = 0; i < roster.armies.size(); i++) {
            Army e = roster.armies.get(i);
            if (!hostile(a, e) || !known(a, e)) continue;
            int d = cheb(a.x, a.y, e.x, e.y);
            if (d < bestD) {
                bestD = d;
                best = e;
            }
        }
        return best;
    }

    private boolean enemyNearSeat(Army a) {
        for (int i = 0; i < roster.armies.size(); i++) {
            Army e = roster.armies.get(i);
            if (hostile(a, e) && cheb(seatX(a), seatY(a), e.x, e.y) <= ArmyTuning.SIGHT) return true;
        }
        return false;
    }

    private boolean seesNewEnemy(Army a) {
        boolean fresh = false;
        for (int i = 0; i < roster.armies.size(); i++) {
            Army e = roster.armies.get(i);
            if (!a.hasSeen(e.id) && hostile(a, e) && known(a, e)) {
                a.see(e.id);
                fresh = true;
            }
        }
        return fresh;
    }

    private static int cheb(int ax, int ay, int bx, int by) {
        return Math.max(Math.abs(ax - bx), Math.abs(ay - by));
    }
}
