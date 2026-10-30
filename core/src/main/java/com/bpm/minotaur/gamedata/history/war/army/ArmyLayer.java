package com.bpm.minotaur.gamedata.history.war.army;

import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.gamedata.history.Figure;
import com.bpm.minotaur.gamedata.history.HistoryWorld;
import com.bpm.minotaur.gamedata.history.House;
import com.bpm.minotaur.gamedata.history.PlayerDeed;
import com.bpm.minotaur.gamedata.history.Trait;
import com.bpm.minotaur.gamedata.history.War;
import com.bpm.minotaur.gamedata.history.war.SeatMap;

import java.util.List;
import java.util.SplittableRandom;
import java.util.function.Consumer;
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
    /** Views of the world's lists, held so that asking for them every turn makes no garbage (A59). */
    private final List<War> wars;
    private final List<House> houses;
    private final Terrain terrain;
    private final Supplier<SeatMap> seatsOf;
    private final WarRoster roster;
    private final Pathfinder paths;
    private final Consumer<PlayerDeed> deeds;
    private long battlesEnded;
    private SeatMap seats;
    /** Where each house sits, by id, so that asking every turn makes no garbage; {@link #NO_SEAT} if nowhere. */
    private int[] seatXs = new int[0];
    private int[] seatYs = new int[0];
    private static final int NO_SEAT = Integer.MIN_VALUE;
    private int season;

    /** @param deeds where what the armies do reaches the history: the only way it does (A16) */
    public ArmyLayer(HistoryWorld world, Terrain terrain, Supplier<SeatMap> seatsOf, WarRoster roster, Consumer<PlayerDeed> deeds) {
        this.deeds = deeds;
        this.world = world;
        this.wars = world.wars();
        this.houses = world.houses();
        this.terrain = terrain;
        this.seatsOf = seatsOf;
        this.roster = roster;
        this.paths = new Pathfinder(terrain);
        this.season = world.liveSeasons();
        refreshSeats();
    }

    public WarRoster roster() {
        return roster;
    }

    /** Battles ended so far, for the war simulation's report. */
    public long battlesEnded() {
        return battlesEnded;
    }

    public SeatMap seats() {
        return seats;
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
            refreshSeats();
            roster.reconcile(world);
        }
        if (seats == null) return;
        if (newSeason) sueForPeace();
        if (newSeason || clock % ArmyTuning.MUSTER_EVERY == 0 || roster.nextArmyId == 1) muster(clock);
        List<Army> armies = roster.armies;
        for (int i = 0; i < armies.size(); i++) {
            Army a = armies.get(i);
            if (a.battle) continue;
            if (newSeason) decide(a, clock);
            march(a, clock);
            if (armies.size() <= i || armies.get(i) != a) i--; // it disbanded
        }
        contacts();
        for (int i = roster.battles.size() - 1; i >= 0; i--) stepBattle(roster.battles.get(i), clock);
    }

    // ---------- battles: one state, watched or not (A20) ----------

    /** Hostile armies in one chunk fight; a free army in a battle's chunk joins the side it is friend to. */
    private void contacts() {
        List<Army> armies = roster.armies;
        for (int i = 0; i < armies.size(); i++) {
            Army a = armies.get(i);
            if (a.battle) continue;
            ArmyBattle here = battleAt(a.x, a.y);
            if (here != null) {
                join(here, a);
                continue;
            }
            for (int j = i + 1; j < armies.size(); j++) {
                Army b = armies.get(j);
                if (!b.battle && b.x == a.x && b.y == a.y && hostile(a, b)) {
                    start(a, b);
                    break;
                }
            }
        }
    }

    private ArmyBattle battleAt(int x, int y) {
        for (int i = 0; i < roster.battles.size(); i++) {
            ArmyBattle b = roster.battles.get(i);
            if (b.x == x && b.y == y) return b;
        }
        return null;
    }

    private void start(Army a, Army b) {
        ArmyBattle battle = new ArmyBattle();
        battle.x = a.x;
        battle.y = a.y;
        battle.houseA = a.side();
        battle.houseB = b.side();
        roster.battles.add(battle);
        enlist(battle, a, true);
        enlist(battle, b, false);
        alliesMarchIn(battle);
    }

    /** An army reaches an enemy seat: its garrison comes out to meet it, behind its walls (A21). */
    private void assault(Army a, House holder) {
        ArmyBattle battle = new ArmyBattle();
        battle.x = a.x;
        battle.y = a.y;
        battle.houseA = a.side();
        battle.houseB = holder.id;
        battle.garrisonOf = holder.id;
        battle.startB = roster.garrison(world, holder.id);
        roster.battles.add(battle);
        enlist(battle, a, true);
        alliesMarchIn(battle);
    }

    private void enlist(ArmyBattle battle, Army a, boolean sideA) {
        a.battle = true;
        a.path = null;
        a.progress = 0;
        if (sideA) {
            battle.armiesA = ArmyBattle.append(battle.armiesA, a.id);
            battle.startA += a.soldiers;
        } else {
            battle.armiesB = ArmyBattle.append(battle.armiesB, a.id);
            battle.startB += a.soldiers;
        }
    }

    /** Allies don't march to help, but one in or next to the chunk joins its friend (A7). */
    private void alliesMarchIn(ArmyBattle battle) {
        for (int i = 0; i < roster.armies.size(); i++) {
            Army o = roster.armies.get(i);
            if (!o.battle && cheb(o.x, o.y, battle.x, battle.y) <= 1) join(battle, o);
        }
    }

    private void join(ArmyBattle battle, Army a) {
        int side = a.side();
        if (side == battle.houseA || world.stance(side, battle.houseA) == HistoryWorld.Stance.ALLIED) {
            if (world.activeWarBetween(side, battle.houseB) != null || side == battle.houseA) enlist(battle, a, true);
        } else if (side == battle.houseB || world.stance(side, battle.houseB) == HistoryWorld.Stance.ALLIED) {
            if (world.activeWarBetween(side, battle.houseA) != null || side == battle.houseB) enlist(battle, a, false);
        }
    }

    private int alive(ArmyBattle b, boolean sideA) {
        int[] ids = sideA ? b.armiesA : b.armiesB;
        int n = 0;
        for (int id : ids) {
            Army a = roster.army(id);
            if (a != null) n += a.soldiers;
        }
        if (!sideA && b.garrisonOf >= 0) n += roster.garrison(world, b.garrisonOf);
        return n;
    }

    private static int hits(int alive, float edge, SplittableRandom rng) {
        int line = Math.min(alive, ArmyTuning.LINE), h = 0;
        float chance = ArmyTuning.KILL_RATE * edge;
        for (int i = 0; i < line; i++) if (rng.nextDouble() < chance) h++;
        return h;
    }

    /** The side's armies lose {@code n} soldiers in order, and its garrison the rest. */
    private int kill(ArmyBattle b, boolean sideA, int n) {
        int left = n;
        for (int id : sideA ? b.armiesA : b.armiesB) {
            Army a = roster.army(id);
            if (a == null || left <= 0) continue;
            int d = Math.min(a.soldiers, left);
            if (d == 0) continue;
            a.soldiers -= d;
            left -= d;
            b.addDead(a.houseId, d);
        }
        if (left > 0 && !sideA && b.garrisonOf >= 0) {
            int d = Math.min(roster.garrison(world, b.garrisonOf), left);
            if (d > 0) {
                b.addDead(b.garrisonOf, d);
                left -= d;
            }
        }
        return n - left;
    }

    private void stepBattle(ArmyBattle b, long clock) {
        b.turn++;
        SplittableRandom rng = new SplittableRandom(world.seed ^ (clock * 0x9E3779B97F4A7C15L) ^ ((b.x * 31L + b.y) * 0xC2B2AE3D27D4EB4FL));
        int aliveA = alive(b, true), aliveB = alive(b, false);
        float edgeB = b.garrisonOf >= 0 ? ArmyTuning.WALL_EDGE : 1f;
        int lostB = kill(b, false, Math.min(aliveB, hits(aliveA, 1f, rng)));
        int lostA = kill(b, true, Math.min(aliveA, hits(aliveB, edgeB, rng)));
        b.moraleA = moraleAfter(b.moraleA, lostA, b.startA);
        b.moraleB = moraleAfter(b.moraleB, lostB, b.startB);
        b.moraleA -= captainsFall(b, b.armiesA, rng);
        b.moraleB -= captainsFall(b, b.armiesB, rng);
        boolean aBroke = b.moraleA <= 0 || alive(b, true) <= 0;
        boolean bBroke = b.moraleB <= 0 || alive(b, false) <= 0;
        if (aBroke || bBroke) {
            endBattle(b, aBroke && (!bBroke || b.moraleA <= b.moraleB), clock, rng);
        } else if (b.turn >= ArmyTuning.TURN_CAP) {
            endBattle(b, alive(b, true) * Math.max(1, b.moraleA) < alive(b, false) * Math.max(1, b.moraleB), clock, rng);
        }
    }

    private static int moraleAfter(int morale, int lost, int start) {
        if (lost == 0) return Math.min(100, morale + 1);
        return morale - lost * ArmyTuning.MORALE_PER_LOSS * 30 / Math.max(30, start);
    }

    private int captainsFall(ArmyBattle b, int[] ids, SplittableRandom rng) {
        int cost = 0;
        for (int id : ids) {
            Army a = roster.army(id);
            if (a != null && a.captainId >= 0 && !ArmyBattle.holds(b.captainFell, id) && rng.nextDouble() < ArmyTuning.CAPTAIN_FALL) {
                b.captainFell = ArmyBattle.append(b.captainFell, id);
                cost += ArmyTuning.MORALE_CAPTAIN;
            }
        }
        return cost;
    }

    private void endBattle(ArmyBattle b, boolean aLost, long clock, SplittableRandom rng) {
        roster.battles.remove(b);
        battlesEnded++;
        int[] won = aLost ? b.armiesB : b.armiesA, lost = aLost ? b.armiesA : b.armiesB;
        int winner = aLost ? b.houseB : b.houseA, loser = aLost ? b.houseA : b.houseB;
        PlayerDeed deed = new PlayerDeed(PlayerDeed.Kind.ARMY_BATTLE, winner, world.liveSeasons());
        deed.other = loser;
        deed.chunkX = b.x;
        deed.chunkY = b.y;
        deed.deadHouses = b.deadHouses;
        deed.deadCounts = b.deadCounts;
        deed.skirmish = b.startA + b.startB < ArmyTuning.SKIRMISH_UNDER;
        deed.atSeat = b.garrisonOf >= 0;
        deed.seatFell = b.garrisonOf >= 0 && !aLost;
        int[] slain = new int[0], taken = new int[0];
        for (int side = 0; side < 2; side++) {
            for (int id : side == 0 ? won : lost) {
                Army a = roster.army(id);
                if (a == null || a.captainId < 0) continue;
                if (ArmyBattle.holds(b.captainFell, id)) {
                    slain = ArmyBattle.append(slain, a.captainId);
                    a.captainId = -1;
                } else if (side == 1) {
                    double r = rng.nextDouble();
                    if (r < ArmyTuning.ROUT_SLAIN) slain = ArmyBattle.append(slain, a.captainId);
                    else if (r < ArmyTuning.ROUT_SLAIN + ArmyTuning.ROUT_TAKEN) taken = ArmyBattle.append(taken, a.captainId);
                    else continue;
                    a.captainId = -1;
                }
            }
        }
        deed.captainsSlain = slain;
        deed.captainsTaken = taken;
        deeds.accept(deed);
        if (deed.seatFell) refreshSeats();

        for (int id : won) {
            Army a = roster.army(id);
            if (a != null) {
                a.battle = false;
                a.morale = Math.min(100, a.morale + 30);
            }
        }
        for (int id : lost) {
            Army a = roster.army(id);
            if (a == null) continue;
            a.battle = false;
            if (a.soldiers < ArmyTuning.MIN_ARMY) {
                roster.armies.remove(a);
            } else {
                a.morale = 30;
                goHome(a, Army.Objective.RECOVER);
            }
        }
        roster.reconcile(world);
        for (int id : won) {
            Army a = roster.army(id);
            if (a != null && !a.battle) decide(a, clock);
        }
    }

    /** Every battle under way ends now, by the numbers: the world is being reborn (A14). */
    public void resolveBattles(long clock) {
        long turn = clock;
        while (!roster.battles.isEmpty()) {
            for (int i = roster.battles.size() - 1; i >= 0; i--) stepBattle(roster.battles.get(i), ++turn);
        }
    }

    /**
     * The world was reborn (A14): each army stands at the same fraction of its way from its seat
     * to its objective on the new map, with its strength, captain and objective.
     */
    public void shiftFrom(SeatMap oldSeats) {
        if (oldSeats == null || seats == null) return;
        for (int i = 0; i < roster.armies.size(); i++) {
            Army a = roster.armies.get(i);
            GridPoint2 was = oldSeats.seat(a.houseId), now = seats.seat(a.houseId);
            if (was == null || now == null) continue;
            int whole = cheb(was.x, was.y, a.targetX, a.targetY);
            float along = whole == 0 ? 0f : Math.min(1f, (float) cheb(was.x, was.y, a.x, a.y) / whole);
            GridPoint2 to = newTarget(a, now);
            GridPoint2 at = SeatMap.nearestOpen(new GridPoint2(Math.round(now.x + (to.x - now.x) * along),
                    Math.round(now.y + (to.y - now.y) * along)), c -> terrain.passable(c.x, c.y));
            a.x = at.x;
            a.y = at.y;
            a.targetX = to.x;
            a.targetY = to.y;
            a.marchFromX = at.x;
            a.marchFromY = at.y;
            a.marchStep = 0;
            a.progress = 0;
            a.path = null;
        }
    }

    private GridPoint2 newTarget(Army a, GridPoint2 home) {
        if (a.objective == Army.Objective.SEAT || a.objective == Army.Objective.HUNT) {
            War war = war(a);
            GridPoint2 s = war == null ? null : seats.seat(war.enemyOf(a.side()));
            if (s != null) return s;
        }
        if (a.objective == Army.Objective.FOLLOW) {
            GridPoint2 s = seats.seat(a.levyOf);
            if (s != null) return s;
        }
        return home;
    }

    // ---------- peace (A25) ----------

    /** A house that has lost half its strength and can raise no host sues for peace. */
    private void sueForPeace() {
        for (int i = 0; i < wars.size(); i++) {
            War w = wars.get(i);
            if (!w.isActive()) continue;
            if (beaten(w, w.attackerId)) sue(w.attackerId, w.defenderId);
            else if (beaten(w, w.defenderId)) sue(w.defenderId, w.attackerId);
        }
    }

    private boolean beaten(War w, int houseId) {
        House h = world.house(houseId);
        float began = roster.strengthAtWarStart(w, houseId, world);
        return h != null && !h.holdsCastle && h.strength < began * ArmyTuning.SUE_BELOW
                && hostsOf(houseId) == 0 && spare(h) < ArmyTuning.MIN_ARMY;
    }

    private void sue(int beaten, int victor) {
        PlayerDeed deed = new PlayerDeed(PlayerDeed.Kind.SUED_FOR_PEACE, beaten, world.liveSeasons());
        deed.other = victor;
        deeds.accept(deed);
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
            else if (t.x != a.targetX || t.y != a.targetY) goTo(a, t.x, t.y);
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

    private void refreshSeats() {
        seats = seatsOf.get();
        int n = houses.size();
        seatXs = new int[n];
        seatYs = new int[n];
        for (int id = 0; id < n; id++) {
            GridPoint2 s = seats == null ? null : seats.seat(id);
            seatXs[id] = s == null ? NO_SEAT : s.x;
            seatYs[id] = s == null ? NO_SEAT : s.y;
        }
    }

    private boolean hasSeat(int houseId) {
        return houseId >= 0 && houseId < seatXs.length && seatXs[houseId] != NO_SEAT;
    }

    private int seatX(Army a) {
        return hasSeat(a.houseId) ? seatXs[a.houseId] : a.x;
    }

    private int seatY(Army a) {
        return hasSeat(a.houseId) ? seatYs[a.houseId] : a.y;
    }

    /** The enemy whose seat this army stands on, or null. */
    private House holderOfSeatAt(Army a) {
        War war = war(a);
        if (war == null || !war.isActive()) return null;
        int enemy = war.enemyOf(a.side());
        House h = world.house(enemy);
        return hasSeat(enemy) && h != null && !h.holdsCastle && seatXs[enemy] == a.x && seatYs[enemy] == a.y ? h : null;
    }

    private boolean atSeat(Army a) {
        return a.x == seatX(a) && a.y == seatY(a);
    }

    private void arrive(Army a, long clock) {
        if (a.objective == Army.Objective.SEAT) {
            House holder = holderOfSeatAt(a);
            if (holder != null) {
                assault(a, holder);
                return;
            }
        }
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
        for (int i = 0; i < wars.size(); i++) {
            War w = wars.get(i);
            if (w.isActive()) roster.strengthAtWarStart(w, w.attackerId, world);
        }
        for (int i = 0; i < houses.size(); i++) {
            House h = houses.get(i);
            if (h.isExtinct() || !hasSeat(h.id)) continue;
            int atWar = world.activeWarCount(h.id);
            if (atWar > 0) musterHosts(h, clock);
            else if (h.liegeId >= 0) musterLevy(h, clock);
        }
    }

    private int spare(House h) {
        return roster.garrison(world, h.id) - (int) Math.ceil(h.strength * ArmyTuning.GARRISON_SHARE);
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
        for (War w : wars) {
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
        for (War w : wars) {
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
            float garrison = roster.garrison(world, enemyId);
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
        for (War w : wars) {
            if (w.isActive() && w.involves(side)) {
                a.warId = w.id;
                return w;
            }
        }
        return null;
    }

    // ---------- what an army knows (A5) ----------

    private boolean hostile(Army a, Army e) {
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
        int home = a.side();
        if (hasSeat(home) && cheb(seatXs[home], seatYs[home], e.x, e.y) <= ArmyTuning.SIGHT) return true;
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
