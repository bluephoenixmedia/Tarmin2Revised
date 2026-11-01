package com.bpm.minotaur.gamedata.history.war.army;

import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.gamedata.history.Figure;
import com.bpm.minotaur.gamedata.history.HistoryWorld;
import com.bpm.minotaur.gamedata.history.House;
import com.bpm.minotaur.gamedata.history.PlayerDeed;
import com.bpm.minotaur.gamedata.history.Trait;
import com.bpm.minotaur.gamedata.history.War;
import com.bpm.minotaur.gamedata.history.war.Encounter;
import com.bpm.minotaur.gamedata.history.war.SeatMap;

import java.util.ArrayList;
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
    private long castleSiegesLaid;
    private long castleSiegesBroken;
    private long castleSiegesLifted;
    private long siegesLaid;
    private long siegesRaised;
    private long seatsFell;
    private long siegeTurns;
    private PlayerWatch watch;

    /**
     * What the one part of the war that aims at the player needs to know (A12): nothing else in
     * this layer reads the player, and a world with nobody watching runs the same without it.
     */
    public interface PlayerWatch {
        /** The beast following the player on the surface, as beast id, chunk x, chunk y; null if none (A28). */
        default int[] huntingBeast(long clock) {
            return null;
        }

        /** The chunk the player stands in on the surface, or null if they are below. */
        GridPoint2 surfaceChunk();

        boolean countsPlayerAnEnemy(int houseId);
    }
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

    /** The battle the player is standing in, whose numbers wait on the map (A22). */
    private ArmyBattle watched;

    public WarRoster roster() {
        return roster;
    }

    public void watch(PlayerWatch watch) {
        this.watch = watch;
    }

    /** Battles ended so far, for the war simulation's report. */
    /** Sieges laid, raised, and ended by the seat falling, and the war turns those ended lasted, for the batch. */
    public long[] siegeCounts() {
        return new long[] {siegesLaid, siegesRaised, seatsFell, siegeTurns};
    }

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
        if (newSeason) newSeason(clock);
        if (newSeason || clock % ArmyTuning.MUSTER_EVERY == 0 || roster.nextArmyId == 1) muster(clock);
        List<Army> armies = roster.armies;
        for (int i = 0; i < armies.size(); i++) {
            Army a = armies.get(i);
            if (a.battle) continue;
            if (newSeason) decide(a, clock);
            if (a.sieging >= 0 || a.beforeTheCastle) continue;
            march(a, clock);
            if (armies.size() <= i || armies.get(i) != a) i--; // it disbanded
        }
        logistics(clock);
        beastsMeetArmies(clock);
        sieges(clock, newSeason);
        castleSiege(clock);
        contacts();
        for (int i = roster.battles.size() - 1; i >= 0; i--) {
            if (roster.battles.get(i) != watched) stepBattle(roster.battles.get(i), clock);
        }
        noteThePlayer();
    }

    /** Where a beast's wounds are kept: given the beast, what it has left. */
    private java.util.function.IntUnaryOperator beastHpOf = id -> ArmyTuning.BEAST_WHOLE_HP;

    public void beastHealth(java.util.function.IntUnaryOperator beastHpOf) {
        this.beastHpOf = beastHpOf;
    }

    private void newSeason(long clock) {
        settleAttrition();
        beastsRaid();
        sueForPeace();
        for (int guard = 0; guard < ArmyTuning.WAR_FLOOR; guard++) {
            PlayerDeed declared = com.bpm.minotaur.gamedata.history.HistorySimulator.warPressure(world, ArmyTuning.WAR_FLOOR);
            if (declared == null) break;
            deeds.accept(declared);
        }
        for (int i = roster.armies.size() - 1; i >= 0; i--) {
            Army a = roster.armies.get(i);
            if (a.homeBySeason >= 0 && season >= a.homeBySeason && !a.battle) roster.armies.remove(i);
            else {
                a.trailLast = a.trail;
                a.trail = new int[0];
            }
        }
        peaceRaids(clock);
        sendHunters();
        sendHeralds(clock);
    }

    // ---------- what the player can meet of it (A1, A2) ----------

    /** Everything under way that is not a halted host, as it stands now. Nothing here looks at the player. */
    public List<Encounter> encounters() {
        List<Encounter> out = new ArrayList<>();
        for (int i = 0; i < roster.armies.size(); i++) {
            Army a = roster.armies.get(i);
            if (a.battle) continue;
            boolean halted = a.halted();
            boolean main = a.isHost();
            if (main && halted) continue;
            Encounter.Kind kind = a.kind == Army.Kind.HERALD ? Encounter.Kind.HERALD
                    : a.kind == Army.Kind.RAID && halted ? Encounter.Kind.RAID : Encounter.Kind.COLUMN;
            out.add(encounter(kind, a));
        }
        for (int i = 0; i < roster.battles.size(); i++) {
            ArmyBattle b = roster.battles.get(i);
            GridPoint2 at = new GridPoint2(b.x, b.y);
            War w = world.activeWarBetween(b.houseA, b.houseB);
            Encounter e = new Encounter(Encounter.Kind.SKIRMISH, b.id, w == null ? -1 : w.id, b.houseA, b.houseB, 0, Long.MAX_VALUE, at, at);
            e.soldiers = b.startA + b.startB;
            e.underArms = true;
            out.add(e);
        }
        return out;
    }

    /** The slot of the camp a house keeps at its seat, which is no army's. */
    public static final long SEAT_CAMP = -5;

    /** Every host that has halted, its tents pitched where it stands. */
    public List<Encounter> camps() {
        List<Encounter> out = new ArrayList<>();
        for (int i = 0; i < roster.armies.size(); i++) {
            Army a = roster.armies.get(i);
            boolean main = a.kind == Army.Kind.HOST || a.kind == Army.Kind.LEVY;
            if (main && !a.battle && (a.path == null || a.marchStep >= a.path.length)) {
                Encounter camp = encounter(Encounter.Kind.CAMP, a);
                camp.liege = a.side();
                out.add(camp);
            }
        }
        // A house keeps its banner at its seat, for its oath and its contracts (A39), unless the seat is under siege.
        for (int i = 0; i < houses.size(); i++) {
            House h = houses.get(i);
            if (h.isExtinct() || h.holdsCastle || !hasSeat(h.id) || roster.siegeAt(seatXs[h.id], seatYs[h.id]) != null) continue;
            GridPoint2 seat = new GridPoint2(seatXs[h.id], seatYs[h.id]);
            out.add(new Encounter(Encounter.Kind.CAMP, SEAT_CAMP, -1, h.id, -1, 0, Long.MAX_VALUE, seat, seat));
        }
        return out;
    }

    private Encounter encounter(Encounter.Kind kind, Army a) {
        GridPoint2 at = new GridPoint2(a.x, a.y);
        GridPoint2 next = at;
        if (a.path != null && a.marchStep < a.path.length) {
            next = new GridPoint2(terrain.xOf(a.path[a.marchStep]), terrain.yOf(a.path[a.marchStep]));
        }
        Encounter e = new Encounter(kind, a.id, a.warId, a.houseId, a.kind == Army.Kind.RAID ? a.raidOn : -1, 0, Long.MAX_VALUE, at, next);
        e.soldiers = a.soldiers;
        return e;
    }

    // ---------- heralds (A15) ----------

    /** A house with news sends a herald along the roads to another seat. */
    private void sendHeralds(long clock) {
        List<com.bpm.minotaur.gamedata.history.HistoryEvent> events = world.events();
        if (roster.eventsHeard < 0) roster.eventsHeard = events.size();
        int sent = 0;
        for (int i = roster.eventsHeard; i < events.size() && sent < ArmyTuning.HERALDS_A_SEASON; i++) {
            com.bpm.minotaur.gamedata.history.HistoryEvent e = events.get(i);
            if (e.type != com.bpm.minotaur.gamedata.history.EventType.WAR_DECLARED && e.type != com.bpm.minotaur.gamedata.history.EventType.PEACE
                    && e.type != com.bpm.minotaur.gamedata.history.EventType.SEAT_SEIZED) continue;
            House h = world.house(e.houseA);
            if (h == null || h.isExtinct() || !hasSeat(h.id)) continue;
            SplittableRandom rng = new SplittableRandom(world.seed ^ (clock * 0x9E3779B97F4A7C15L) ^ (e.id * 0xA0761D6478BD642FL));
            House to = houses.get(rng.nextInt(houses.size()));
            if (to == h || to.isExtinct() || !hasSeat(to.id)) continue;
            Army herald = party(h, Army.Kind.HERALD, 1, seatXs[h.id], seatYs[h.id]);
            herald.homeBySeason = season + ArmyTuning.HERALD_SEASONS;
            goTo(herald, seatXs[to.id], seatYs[to.id]);
            sent++;
        }
        roster.eventsHeard = events.size();
    }

    // ---------- the land's toll (A27) ----------

    private void settleAttrition() {
        for (int i = 0; i < roster.attritionHouses.length; i++) {
            PlayerDeed deed = new PlayerDeed(PlayerDeed.Kind.ARMY_ATTRITION, roster.attritionHouses[i], world.liveSeasons());
            deed.deadCounts = new int[] {roster.attritionDead[i]};
            deeds.accept(deed);
        }
        roster.attritionHouses = new int[0];
        roster.attritionDead = new int[0];
    }

    /** The chance per chunk rises with distance from the centre; the Blight triples it, but not for the Host that made it. */
    private void payTheLand(Army a, long clock, int chunk) {
        House h = world.house(a.houseId);
        boolean blight = terrain.blightedAt(chunk);
        if (a.kind == Army.Kind.HERALD || (blight && h != null && h.holdsCastle)) return;
        int d = Math.max(Math.abs(a.x), Math.abs(a.y));
        float t = Math.max(0f, Math.min(1f, (d - ArmyTuning.ATTRITION_NEAR_DISTANCE) / (float) (ArmyTuning.ATTRITION_FAR_DISTANCE - ArmyTuning.ATTRITION_NEAR_DISTANCE)));
        float chance = ArmyTuning.ATTRITION_NEAR + (ArmyTuning.ATTRITION_FAR - ArmyTuning.ATTRITION_NEAR) * t;
        if (blight) chance *= ArmyTuning.BLIGHT_ATTRITION;
        if (new SplittableRandom(world.seed ^ (clock * 0x9E3779B97F4A7C15L) ^ (a.id * 0xD6E8FEB86659FD93L)).nextDouble() < chance) {
            a.soldiers--;
            roster.addAttrition(a.houseId, 1);
        }
    }

    // ---------- raids and hunters (A9, A11, A12) ----------

    /** A party detached from a host, or raised at home. */
    private Army party(House h, Army.Kind kind, int soldiers, int fromX, int fromY) {
        Army p = raise(h, soldiers, kind);
        p.captainId = -1;
        p.x = fromX;
        p.y = fromY;
        p.marchFromX = fromX;
        p.marchFromY = fromY;
        return p;
    }

    private void sendRaid(Army host, House enemy, War war) {
        House h = world.house(host.houseId);
        boolean detached = host.soldiers >= ArmyTuning.MIN_ARMY + ArmyTuning.RAID_SIZE;
        if (detached) host.soldiers -= ArmyTuning.RAID_SIZE;
        host.raidSent = true;
        Army r = party(h, Army.Kind.RAID, ArmyTuning.RAID_SIZE, detached ? host.x : seatXs[h.id], detached ? host.y : seatYs[h.id]);
        r.warId = war.id;
        r.levyOf = host.levyOf;
        r.homeBySeason = season + 1;
        marchOnLands(r, enemy, host.x, host.y);
    }

    private void marchOnLands(Army r, House enemy, int fromX, int fromY) {
        r.objective = Army.Objective.RAID;
        r.raidOn = enemy.id;
        if (!hasSeat(enemy.id)) return;
        int ex = seatXs[enemy.id], ey = seatYs[enemy.id];
        GridPoint2 shelter = enemy.isGreat() ? terrain.shelterOnRoadTo(ex, ey, fromX, fromY) : null;
        if (shelter != null) {
            goTo(r, shelter.x, shelter.y);
            return;
        }
        for (int ring = 1; ring < 4; ring++) {
            for (int dy = -ring; dy <= ring; dy++) {
                for (int dx = -ring; dx <= ring; dx++) {
                    if ((dx != 0 || dy != 0) && terrain.passable(ex + dx, ey + dy)) {
                        goTo(r, ex + dx, ey + dy);
                        return;
                    }
                }
            }
        }
        goTo(r, ex, ey);
    }

    /** Peace is never total silence: a free house may burn the lands of one it hates (W7, A9). */
    private void peaceRaids(long clock) {
        for (int i = 0; i < houses.size(); i++) {
            House h = houses.get(i);
            if (h.isExtinct() || h.liegeId >= 0 || h.holdsCastle || !hasSeat(h.id) || world.activeWarCount(h.id) > 0) continue;
            if (spare(h) < ArmyTuning.RAID_SIZE) continue;
            House target = null;
            float worst = ArmyTuning.PEACE_RAID_GRUDGE;
            for (int j = 0; j < houses.size(); j++) {
                House t = houses.get(j);
                if (t == h || t.isExtinct() || world.stance(h.id, t.id) != HistoryWorld.Stance.NEUTRAL) continue;
                float g = world.grudgeWeight(h.id, t.id);
                if (g > worst) {
                    worst = g;
                    target = t;
                }
            }
            if (target == null) continue;
            SplittableRandom rng = new SplittableRandom(world.seed ^ (clock * 0x9E3779B97F4A7C15L) ^ (h.id * 0xA0761D6478BD642FL));
            if (rng.nextDouble() >= ArmyTuning.PEACE_RAID_CHANCE) continue;
            Army r = party(h, Army.Kind.RAID, ArmyTuning.RAID_SIZE, seatXs[h.id], seatYs[h.id]);
            r.peaceRaid = true;
            r.homeBySeason = season + 1;
            marchOnLands(r, target, r.x, r.y);
        }
    }

    /** The one place the player is read, beside {@link #noteThePlayer}: an Enemy's armies report where they saw them. */
    private void sendHunters() {
        if (watch == null) return;
        for (int i = 0; i < houses.size(); i++) {
            House h = houses.get(i);
            if (h.isExtinct() || !hasSeat(h.id) || !watch.countsPlayerAnEnemy(h.id)) continue;
            sendHuntersOf(h);
        }
    }

    /** Debug: this army marches on that chunk, now. */
    public void marchNow(Army a, int x, int y) {
        goTo(a, x, y);
    }

    /** A host whose follower has broken their oath comes for them where they stand, at once (A41). */
    public void hostTurnsOnThePlayer(int armyId) {
        Army a = roster.army(armyId);
        if (a == null) return;
        if (a.battle) {
            // No more an ally in its line.
            for (int i = 0; i < roster.battles.size(); i++) {
                ArmyBattle b = roster.battles.get(i);
                int line = ArmyBattle.holds(b.armiesA, a.id) ? 1 : ArmyBattle.holds(b.armiesB, a.id) ? 2 : 0;
                if (line == 0) continue;
                b.playerSide = 0;
                b.turnedOnPlayer = line;
            }
            return;
        }
        GridPoint2 at = watch == null ? null : watch.surfaceChunk();
        if (at != null) goTo(a, at.x, at.y);
    }

    private int[] sideHouses(int[] armies, int lead) {
        int[] houses = {lead};
        for (int id : armies) {
            Army a = roster.army(id);
            if (a == null) continue;
            if (!ArmyBattle.holds(houses, a.houseId)) houses = ArmyBattle.append(houses, a.houseId);
            if (!ArmyBattle.holds(houses, a.side())) houses = ArmyBattle.append(houses, a.side());
        }
        return houses;
    }

    /**
     * An oath broken: the betrayed house's nearest host sends a Hunting Party at once, from where it
     * stands, or one already out turns toward the player (A41). A house with no host and none to spare
     * from its seat sends nobody.
     */
    public void huntersNow(int houseId) {
        House h = world.house(houseId);
        GridPoint2 at = watch == null ? null : watch.surfaceChunk();
        if (at != null) roster.notePlayerSeen(houseId, at.x, at.y);
        int[] seen = roster.playerSeenBy(houseId);
        if (h == null || h.isExtinct() || !hasSeat(houseId) || seen == null) return;
        Army nearest = null;
        for (int i = 0; i < roster.armies.size(); i++) {
            Army a = roster.armies.get(i);
            if (a.houseId != houseId) continue;
            if (a.kind == Army.Kind.HUNTING_PARTY) {
                a.homeBySeason = season + ArmyTuning.HUNT_SEASONS;
                goTo(a, seen[1], seen[2]);
                return;
            }
            if (a.isHost() && !a.battle && a.soldiers >= ArmyTuning.MIN_ARMY + ArmyTuning.RAID_SIZE
                    && (nearest == null || cheb(a.x, a.y, seen[1], seen[2]) < cheb(nearest.x, nearest.y, seen[1], seen[2]))) {
                nearest = a;
            }
        }
        if (nearest == null && spare(h) < ArmyTuning.RAID_SIZE) return;
        if (nearest != null) nearest.soldiers -= ArmyTuning.RAID_SIZE;
        Army p = party(h, Army.Kind.HUNTING_PARTY, ArmyTuning.RAID_SIZE, nearest != null ? nearest.x : seatXs[h.id], nearest != null ? nearest.y : seatYs[h.id]);
        p.homeBySeason = season + ArmyTuning.HUNT_SEASONS;
        p.objective = Army.Objective.HUNT;
        goTo(p, seen[1], seen[2]);
    }

    /** A party from house {@code h}, toward where its armies last saw the player; none if they never have. */
    private void sendHuntersOf(House h) {
        int[] seen = roster.playerSeenBy(h.id);
        if (seen == null || spare(h) < ArmyTuning.RAID_SIZE) return;
        for (int i = 0; i < roster.armies.size(); i++) {
            Army o = roster.armies.get(i);
            if (o.houseId == h.id && o.kind == Army.Kind.HUNTING_PARTY) return;
        }
        Army p = party(h, Army.Kind.HUNTING_PARTY, ArmyTuning.RAID_SIZE, seatXs[h.id], seatYs[h.id]);
        p.homeBySeason = season + ArmyTuning.HUNT_SEASONS;
        p.objective = Army.Objective.HUNT;
        goTo(p, seen[1], seen[2]);
    }

    private void noteThePlayer() {
        if (watch == null) return;
        GridPoint2 at = watch.surfaceChunk();
        if (at == null) return;
        for (int i = 0; i < roster.armies.size(); i++) {
            Army a = roster.armies.get(i);
            if (cheb(a.x, a.y, at.x, at.y) <= ArmyTuning.SIGHT) roster.notePlayerSeen(a.side(), at.x, at.y);
        }
    }

    // ---------- supply and forage (A10) ----------

    private void logistics(long clock) {
        int count = roster.armies.size();
        for (int i = 0; i < count && i < roster.armies.size(); i++) {
            Army a = roster.armies.get(i);
            if (a.battle) continue;
            if (a.kind == Army.Kind.HOST || a.kind == Army.Kind.LEVY) {
                if ((clock + a.id) % ArmyTuning.SUPPLY_EVERY == 0) sendSupply(a);
                if ((clock + a.id * 7L) % ArmyTuning.FORAGE_EVERY == 0) sendForagers(a, clock);
            }
        }
        for (int i = roster.armies.size() - 1; i >= 0; i--) {
            Army c = roster.armies.get(i);
            if (c.battle) continue;
            if (c.kind == Army.Kind.SUPPLY) {
                Army t = roster.army(c.targetArmy);
                if (t == null) roster.armies.remove(i);
                else if (t.x == c.x && t.y == c.y) mergeInto(c, t, i);
                else if ((clock + c.id) % ArmyTuning.RETARGET_EVERY == 0 && (t.x != c.targetX || t.y != c.targetY)) goTo(c, t.x, t.y);
            } else if (c.kind == Army.Kind.FORAGERS && clock >= c.turnBackAt) {
                Army t = roster.army(c.backTo);
                if (t == null) roster.armies.remove(i);
                else if (t.x == c.x && t.y == c.y) mergeInto(c, t, i);
                else if ((clock + c.id) % ArmyTuning.RETARGET_EVERY == 0 && (t.x != c.targetX || t.y != c.targetY)) goTo(c, t.x, t.y);
            }
        }
    }

    private void mergeInto(Army party, Army host, int index) {
        host.soldiers += party.soldiers;
        host.startSoldiers = Math.max(host.startSoldiers, host.soldiers);
        roster.armies.remove(index);
    }

    private void sendSupply(Army a) {
        if (a.soldiers >= ArmyTuning.ARMY_SIZE || atSeat(a) || !hasSeat(a.houseId)) return;
        for (int i = 0; i < roster.armies.size(); i++) {
            Army o = roster.armies.get(i);
            if (o.kind == Army.Kind.SUPPLY && o.targetArmy == a.id) return;
        }
        House h = world.house(a.houseId);
        int n = Math.min(ArmyTuning.ARMY_SIZE - a.soldiers, spare(h));
        if (n < ArmyTuning.SUPPLY_MIN) return;
        Army c = party(h, Army.Kind.SUPPLY, n, seatXs[h.id], seatYs[h.id]);
        c.warId = a.warId;
        c.levyOf = a.levyOf;
        c.targetArmy = a.id;
        goTo(c, a.x, a.y);
    }

    private void sendForagers(Army a, long clock) {
        if (a.soldiers <= ArmyTuning.FORAGE_SIZE + ArmyTuning.MIN_ARMY) return;
        SplittableRandom rng = new SplittableRandom(world.seed ^ (clock * 0x9E3779B97F4A7C15L) ^ (a.id * 0xE7037ED1A0B428DBL));
        int dx = rng.nextInt(5) - 2, dy = rng.nextInt(5) - 2;
        if ((dx == 0 && dy == 0) || !terrain.passable(a.x + dx, a.y + dy)) return;
        a.soldiers -= ArmyTuning.FORAGE_SIZE;
        Army f = party(world.house(a.houseId), Army.Kind.FORAGERS, ArmyTuning.FORAGE_SIZE, a.x, a.y);
        f.warId = a.warId;
        f.levyOf = a.levyOf;
        f.backTo = a.id;
        f.turnBackAt = clock + ArmyTuning.FORAGE_TURNS;
        goTo(f, a.x + dx, a.y + dy);
    }

    // ---------- battles: one state, watched or not (A20) ----------

    /** Hostile armies in one chunk fight; a free army in a battle's chunk joins the side it is friend to. */
    private void contacts() {
        List<Army> armies = roster.armies;
        for (int i = 0; i < armies.size(); i++) {
            Army a = armies.get(i);
            if (a.battle || a.kind == Army.Kind.HERALD) continue;
            ArmyBattle here = battleAt(a.x, a.y);
            if (here != null) {
                join(here, a);
                continue;
            }
            for (int j = i + 1; j < armies.size(); j++) {
                Army b = armies.get(j);
                if (!b.battle && b.kind != Army.Kind.HERALD && b.x == a.x && b.y == a.y && hostile(a, b)) {
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
        battle.id = roster.nextArmyId++;
        battle.x = a.x;
        battle.y = a.y;
        battle.houseA = a.side();
        battle.houseB = b.side();
        roster.battles.add(battle);
        enlist(battle, a, true);
        enlist(battle, b, false);
        alliesMarchIn(battle);
    }

    // ---------- megabeasts (A28) ----------

    /** Each beast the Season sent at a house goes for that house's nearest army, or its seat. */
    private void beastsRaid() {
        List<com.bpm.minotaur.gamedata.history.Megabeast> beasts = world.megabeasts();
        for (int i = 0; i < beasts.size(); i++) {
            com.bpm.minotaur.gamedata.history.Megabeast b = beasts.get(i);
            if (!b.isAlive() || b.raidingHouse < 0 || roster.beastRaidDone(b.id, b.raidSeason)) continue;
            roster.markBeastRaid(b.id, b.raidSeason);
            // Bought off, it stays in its lair; and it can only be in one fight at a time.
            if (b.isPacified() || beastInBattle(b.id)) continue;
            GridPoint2 lair = com.bpm.minotaur.gamedata.history.beast.BeastTracks.lairChunk(world.seed, b);
            Army nearest = null;
            int best = Integer.MAX_VALUE;
            for (int j = 0; j < roster.armies.size(); j++) {
                Army a = roster.armies.get(j);
                if (a.houseId != b.raidingHouse || a.battle || a.soldiers <= 0 || a.kind == Army.Kind.HERALD || battleAt(a.x, a.y) != null) continue;
                int d = cheb(a.x, a.y, lair.x, lair.y);
                if (d < best) {
                    best = d;
                    nearest = a;
                }
            }
            if (nearest != null) beastFights(b.id, nearest, b.raidingHouse);
            else if (hasSeat(b.raidingHouse) && battleAt(seatXs[b.raidingHouse], seatYs[b.raidingHouse]) == null) {
                beastAtTheSeat(b.id, b.raidingHouse);
            }
        }
    }

    /** A beast that followed the player to the surface fights any army it meets. */
    private void beastsMeetArmies(long clock) {
        int[] hunting = watch == null ? null : watch.huntingBeast(clock);
        if (hunting == null) return;
        com.bpm.minotaur.gamedata.history.Megabeast b = world.megabeast(hunting[0]);
        if (b == null || !b.isAlive() || b.isPacified() || beastInBattle(b.id) || battleAt(hunting[1], hunting[2]) != null) return;
        for (int i = 0; i < roster.armies.size(); i++) {
            Army a = roster.armies.get(i);
            // A host it has just routed is going home, and is let go.
            boolean leaving = a.objective == Army.Objective.RECOVER || a.objective == Army.Objective.DISBAND;
            if (!a.battle && !leaving && a.soldiers > 0 && a.kind != Army.Kind.HERALD && a.x == hunting[1] && a.y == hunting[2]) {
                beastFights(b.id, a, a.side());
                return;
            }
        }
    }

    private boolean beastInBattle(int beastId) {
        for (int i = 0; i < roster.battles.size(); i++) {
            if (roster.battles.get(i).beastId == beastId) return true;
        }
        return false;
    }

    private ArmyBattle beastFights(int beastId, Army a, int house) {
        ArmyBattle there = battleAt(a.x, a.y);
        if (there != null) return null;
        ArmyBattle battle = beastBattle(beastId, a.x, a.y, house);
        enlist(battle, a, false);
        alliesMarchIn(battle);
        return battle;
    }

    private void beastAtTheSeat(int beastId, int house) {
        ArmyBattle battle = beastBattle(beastId, seatXs[house], seatYs[house], house);
        battle.garrisonOf = house;
        battle.wallEdge = 1f;
        battle.startB = roster.garrison(world, house);
        alliesMarchIn(battle);
    }

    private ArmyBattle beastBattle(int beastId, int x, int y, int house) {
        ArmyBattle battle = new ArmyBattle();
        battle.id = roster.nextArmyId++;
        battle.x = x;
        battle.y = y;
        battle.houseA = ArmyBattle.THE_BEAST;
        battle.houseB = house;
        battle.beastId = beastId;
        battle.beastHp = beastHpOf.applyAsInt(beastId);
        battle.startA = 1;
        roster.battles.add(battle);
        return battle;
    }

    private void stepBeastBattle(ArmyBattle b, long clock, SplittableRandom rng) {
        // Killed elsewhere meanwhile, by the player or by another fight's last blow.
        com.bpm.minotaur.gamedata.history.Megabeast beast = world.megabeast(b.beastId);
        if (beast == null || !beast.isAlive()) b.beastHp = 0;
        b.beastHp -= hits(alive(b, false), 1f, rng) * ArmyTuning.BEAST_BLOW;
        int lostB = rng.nextDouble() < ArmyTuning.BEAST_KILLS ? kill(b, false, 1) : 0;
        settle(b, 0, lostB, 0, captainsFall(b, b.armiesB, rng), clock, rng);
    }

    /** Debug: this beast falls on an army of this house here and now, or null if it can't. */
    public ArmyBattle beastFightsNow(int beastId, House house, int x, int y) {
        if (battleAt(x, y) != null || !hasSeat(house.id)) return null;
        Army a = raise(house, ArmyTuning.ARMY_SIZE, Army.Kind.HOST);
        a.x = x;
        a.y = y;
        a.targetX = x;
        a.targetY = y;
        return beastFights(beastId, a, house.id);
    }

    // ---------- the siege of Castle Tarmin (A35) ----------

    /** Where a house's host may camp before the castle: the ring side nearest it. Null without a castle. */
    private GridPoint2 ringBeforeTheCastle(Army a) {
        House tarmin = world.tarminHouse();
        GridPoint2 castle = tarmin == null || seats == null ? null : seats.seat(tarmin.id);
        if (castle == null) return null;
        int r = com.bpm.minotaur.gamedata.history.war.Siege.CAMP_RING;
        GridPoint2 best = null;
        int bestD = Integer.MAX_VALUE;
        int[][] sides = {{r, 0}, {-r, 0}, {0, r}, {0, -r}};
        for (int[] side : sides) {
            int x = castle.x + side[0], y = castle.y + side[1];
            int d = cheb(a.x, a.y, x, y);
            if (d < bestD && terrain.passable(x, y)) {
                bestD = d;
                best = new GridPoint2(x, y);
            }
        }
        return best;
    }

    /** Whether this war may send a host to the castle now: it is at war with its house, no siege holds, and none just ended. */
    private boolean castleOpenToSiege(War w) {
        return w != null && w.isActive() && !world.castleSiegesRoll() && world.activeSiege() == null && world.season() >= w.noSiegeBefore;
    }

    private boolean layCastleSiege(Army a, long clock) {
        House tarmin = world.tarminHouse();
        if (tarmin == null || world.activeWarBetween(a.side(), tarmin.id) == null) return false;
        a.beforeTheCastle = true;
        a.path = null;
        castleSiegesLaid++;
        SplittableRandom rng = new SplittableRandom(world.seed ^ (clock * 0x9E3779B97F4A7C15L) ^ a.id);
        PlayerDeed deed = new PlayerDeed(PlayerDeed.Kind.CASTLE_SIEGE_LAID, a.side(), world.liveSeasons());
        deed.other = tarmin.id;
        deed.figure = a.id;
        deed.seasons = com.bpm.minotaur.gamedata.history.HistorySimulator.SIEGE_MIN_SEASONS
                + rng.nextInt(com.bpm.minotaur.gamedata.history.HistorySimulator.SIEGE_MAX_SEASONS - com.bpm.minotaur.gamedata.history.HistorySimulator.SIEGE_MIN_SEASONS + 1);
        deeds.accept(deed);
        return true;
    }

    /** Broken if its host routs, lifted when its war ends or its term is up; while it holds, the Legion may come out at it. */
    private void castleSiege(long clock) {
        House tarmin = world.tarminHouse();
        if (tarmin == null) return;
        for (int i = 0; i < wars.size(); i++) {
            War w = wars.get(i);
            if (w.siegeHost < 0 || w.siegeSince < 0) continue;
            Army host = roster.army(w.siegeHost);
            // A war's end turns its hosts for home: that lifts a siege, it doesn't break one.
            boolean holds = host != null && host.beforeTheCastle
                    && (host.objective == Army.Objective.CASTLE || (host.objective == Army.Objective.DISBAND && !w.isActive()));
            if (!holds) {
                endCastleSiege(w, tarmin, PlayerDeed.Kind.CASTLE_SIEGE_BROKEN, host, clock);
            } else if (!host.battle && (!w.isActive() || world.season() >= w.siegeUntil)) {
                endCastleSiege(w, tarmin, PlayerDeed.Kind.CASTLE_SIEGE_LIFTED, host, clock);
            } else if (!host.battle && (clock + host.id) % SeatSiege.THINK_EVERY == 0 && battleAt(host.x, host.y) == null) {
                Figure lord = world.figure(tarmin.lordId);
                boolean spoiling = lord != null && !lord.has(Trait.PATIENT) && !lord.has(Trait.CRAVEN);
                if (SeatSiege.readyToSally(roster.garrison(world, tarmin.id), host.soldiers, spoiling)
                        && new SplittableRandom(world.seed ^ (clock * 0x9E3779B97F4A7C15L) ^ host.id).nextDouble() < SeatSiege.SALLY_CHANCE) {
                    legionComesOut(host, tarmin);
                }
            }
        }
    }

    private void endCastleSiege(War w, House tarmin, PlayerDeed.Kind kind, Army host, long clock) {
        if (kind == PlayerDeed.Kind.CASTLE_SIEGE_BROKEN) castleSiegesBroken++;
        else castleSiegesLifted++;
        PlayerDeed deed = new PlayerDeed(kind, w.enemyOf(tarmin.id), world.liveSeasons());
        deed.other = tarmin.id;
        deeds.accept(deed);
        if (host != null && host.beforeTheCastle) {
            host.beforeTheCastle = false;
            decide(host, clock);
        }
    }

    /** A sally from the castle at the host before it: a real battle, which can break the siege and never the castle (A35). */
    private ArmyBattle legionComesOut(Army host, House tarmin) {
        ArmyBattle battle = new ArmyBattle();
        battle.id = roster.nextArmyId++;
        battle.x = host.x;
        battle.y = host.y;
        battle.houseA = host.side();
        battle.houseB = tarmin.id;
        battle.garrisonOf = tarmin.id;
        battle.wallEdge = 1f;
        battle.startB = roster.garrison(world, tarmin.id);
        roster.battles.add(battle);
        enlist(battle, host, true);
        alliesMarchIn(battle);
        return battle;
    }

    /** Sieges of the castle laid, broken and lifted, for the batch. */
    public long[] castleSiegeCounts() {
        return new long[] {castleSiegesLaid, castleSiegesBroken, castleSiegesLifted};
    }

    /** Debug: a host of this house camps before the castle, now. Null if there is no war or no castle. */
    public Army besiegeTheCastleNow(House besieger, long clock) {
        House tarmin = world.tarminHouse();
        War w = tarmin == null ? null : world.activeWarBetween(besieger.id, tarmin.id);
        if (w == null || !hasSeat(besieger.id) || world.activeSiege() != null) return null;
        Army a = raise(besieger, ArmyTuning.ARMY_SIZE, Army.Kind.HOST);
        a.warId = w.id;
        GridPoint2 ring = ringBeforeTheCastle(a);
        if (ring == null) return null;
        a.x = ring.x;
        a.y = ring.y;
        a.objective = Army.Objective.CASTLE;
        a.targetX = ring.x;
        a.targetY = ring.y;
        return layCastleSiege(a, clock) ? a : null;
    }

    /** Debug: the Legion comes out now, whatever its lord's temper. */
    public ArmyBattle legionComesOutNow(Army host) {
        return legionComesOut(host, world.tarminHouse());
    }

    // ---------- sieges of seats (A30-A34) ----------

    /** An army reaches an enemy seat: it camps around it, and the seat's lord calls in its hosts. */
    private void invest(Army a, House holder, long clock) {
        SeatSiege s = roster.siegeAt(a.x, a.y);
        if (s != null) {
            a.sieging = s.id;
            if (!ArmyBattle.holds(s.armies, a.id)) s.armies = ArmyBattle.append(s.armies, a.id);
            return;
        }
        s = new SeatSiege();
        s.id = roster.nextArmyId++;
        s.x = a.x;
        s.y = a.y;
        s.holder = holder.id;
        s.besieger = a.side();
        s.since = clock;
        s.stone = holder.gashIndex >= 0;
        s.armies = new int[] {a.id};
        roster.sieges.add(s);
        siegesLaid++;
        a.sieging = s.id;
        a.path = null;
        siegeDeed(PlayerDeed.Kind.SEAT_INVESTED, s);
        for (int i = 0; i < roster.armies.size(); i++) {
            Army o = roster.armies.get(i);
            if (o.houseId == holder.id && !o.battle && o.isHost()) decide(o, clock);
        }
    }

    /** The first army still camped around it, or null. */
    private Army firstAt(SeatSiege s) {
        for (int id : s.armies) {
            Army a = roster.army(id);
            if (a != null && a.sieging == s.id) return a;
        }
        return null;
    }

    private void siegeDeed(PlayerDeed.Kind kind, SeatSiege s) {
        PlayerDeed deed = new PlayerDeed(kind, s.besieger, world.liveSeasons());
        deed.other = s.holder;
        deed.chunkX = s.x;
        deed.chunkY = s.y;
        deeds.accept(deed);
    }

    private void sieges(long clock, boolean newSeason) {
        for (int i = roster.sieges.size() - 1; i >= 0; i--) {
            SeatSiege s = roster.sieges.get(i);
            if (newSeason) {
                s.wear();
                // Shut in, a house takes in no one to replace the Season's dead.
                roster.addAttrition(s.holder, Math.round(com.bpm.minotaur.gamedata.history.HistorySimulator.ARMY_RECOVERY));
            }
            if (siegeIsOver(s)) {
                raiseSiege(s, clock);
                continue;
            }
            if (battleAt(s.x, s.y) != null) continue;
            s.works++;
            long day = clock - s.since;
            if (day > 0 && day % SeatSiege.STARVE_TURNS == 0 && roster.garrison(world, s.holder) > 0) roster.addAttrition(s.holder, 1);
            if ((clock + s.id) % SeatSiege.THINK_EVERY == 0) think(s, clock);
        }
    }

    /** The besiegers are gone or going, the war is done, or the seat is no longer the holder's to lose. */
    private boolean siegeIsOver(SeatSiege s) {
        int stayed = 0;
        for (int id : s.armies) {
            Army a = roster.army(id);
            if (a == null || a.sieging != s.id) continue;
            if (a.objective != Army.Objective.SEAT || a.x != s.x || a.y != s.y) a.sieging = -1;
            else stayed++;
        }
        if (stayed == 0) return true;
        House holder = world.house(s.holder);
        if (holder == null || holder.isExtinct() || world.activeWarBetween(s.besieger, s.holder) == null) return true;
        return !hasSeat(s.holder) || seatXs[s.holder] != s.x || seatYs[s.holder] != s.y;
    }

    private void raiseSiege(SeatSiege s, long clock) {
        siegesRaised++;
        siegeTurns += clock - s.since;
        roster.sieges.remove(s);
        release(s);
        siegeDeed(PlayerDeed.Kind.SIEGE_RAISED, s);
    }

    private void release(SeatSiege s) {
        for (int id : s.armies) {
            Army a = roster.army(id);
            if (a != null && a.sieging == s.id) a.sieging = -1;
        }
    }

    private void think(SeatSiege s, long clock) {
        Figure captain = besiegingCaptain(s);
        int soldiers = 0;
        for (int id : s.armies) {
            Army a = roster.army(id);
            if (a != null && a.sieging == s.id) soldiers += a.soldiers;
        }
        int garrison = roster.garrison(world, s.holder);
        boolean wrathful = captain != null && captain.has(Trait.WRATHFUL);
        boolean patient = captain != null && captain.has(Trait.PATIENT);
        if (SeatSiege.readyToAssault(soldiers, garrison, s.assaultEdge(), s.laddersBuilt(), wrathful, patient)) {
            assault(s);
            return;
        }
        House holder = world.house(s.holder);
        Figure lord = holder == null ? null : world.figure(holder.lordId);
        boolean spoiling = lord != null && !lord.has(Trait.PATIENT) && !lord.has(Trait.CRAVEN)
                && (lord.has(Trait.WRATHFUL) || lord.has(Trait.AMBITIOUS));
        if (SeatSiege.readyToSally(garrison, soldiers, spoiling)
                && new SplittableRandom(world.seed ^ (clock * 0x9E3779B97F4A7C15L) ^ s.id).nextDouble() < SeatSiege.SALLY_CHANCE) {
            sally(s);
        }
    }

    private Figure besiegingCaptain(SeatSiege s) {
        for (int id : s.armies) {
            Army a = roster.army(id);
            if (a != null && a.captainId >= 0 && a.sieging == s.id) return world.figure(a.captainId);
        }
        return null;
    }

    /** The captain takes the walls: only his temper and the numbers say when (A33). */
    private void assault(SeatSiege s) {
        ArmyBattle battle = siegeBattle(s);
        battle.assault = true;
        battle.wallEdge = s.assaultEdge();
    }

    /** The garrison strikes the camp: a battle there, fought the same way, with no wall to stand behind (A34). */
    private void sally(SeatSiege s) {
        ArmyBattle battle = siegeBattle(s);
        battle.sally = true;
        battle.wallEdge = 1f;
    }

    private ArmyBattle siegeBattle(SeatSiege s) {
        ArmyBattle battle = new ArmyBattle();
        battle.id = roster.nextArmyId++;
        battle.x = s.x;
        battle.y = s.y;
        battle.houseA = s.besieger;
        battle.houseB = s.holder;
        battle.garrisonOf = s.holder;
        battle.startB = roster.garrison(world, s.holder);
        roster.battles.add(battle);
        for (int id : s.armies) {
            Army a = roster.army(id);
            if (a != null && a.sieging == s.id) enlist(battle, a, true);
        }
        alliesMarchIn(battle);
        return battle;
    }

    /** Debug: this house's host is camped around that enemy seat, now, with its ladders up. Null if it can't be. */
    public SeatSiege investNow(House besieger, House holder, long clock) {
        GridPoint2 seat = seats == null ? null : seats.seat(holder.id);
        War w = world.activeWarBetween(besieger.id, holder.id);
        if (seat == null || w == null || !hasSeat(besieger.id) || holder.holdsCastle) return null;
        Army a = raise(besieger, ArmyTuning.ARMY_SIZE, Army.Kind.HOST);
        a.warId = w.id;
        a.x = seat.x;
        a.y = seat.y;
        a.objective = Army.Objective.SEAT;
        a.targetX = seat.x;
        a.targetY = seat.y;
        invest(a, holder, clock);
        SeatSiege s = roster.siegeAt(seat.x, seat.y);
        if (s != null) s.works = SeatSiege.RAM_TURNS;
        return s;
    }

    /** An army back at its walls takes up its place again. */
    void reinvest(Army a, long clock) {
        House holder = holderOfSeatAt(a);
        if (holder != null) invest(a, holder, clock);
    }

    /** Debug: the garrison comes out now, whatever its lord's temper. */
    public ArmyBattle sallyNow(SeatSiege s) {
        sally(s);
        return battleAt(s.x, s.y);
    }

    /** Debug: the captain goes in now, whatever his temper. */
    public ArmyBattle assaultNow(SeatSiege s) {
        ArmyBattle there = battleAt(s.x, s.y);
        if (there != null) return there.assault ? there : null;
        assault(s);
        return battleAt(s.x, s.y);
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
            if (!o.battle && o.kind != Army.Kind.HERALD && cheb(o.x, o.y, battle.x, battle.y) <= 1) join(battle, o);
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

    /** The battle under way in this chunk, or null. */
    public ArmyBattle battleIn(int x, int y) {
        return battleAt(x, y);
    }

    /** While the player stands in a battle the map is its truth, and the numbers wait on it (A22). Null to let go. */
    public void watchBattle(ArmyBattle b) {
        watched = b;
    }

    public ArmyBattle watchedBattle() {
        return watched;
    }

    /** Soldiers standing on one side of a battle, reserve and all. */
    public int aliveOn(ArmyBattle b, boolean sideA) {
        return alive(b, sideA);
    }

    /** Who stands on one side of a battle, as house and soldiers, for its line (A21). */
    public java.util.Map<Integer, Integer> housesOn(ArmyBattle b, boolean sideA) {
        java.util.Map<Integer, Integer> out = new java.util.LinkedHashMap<>();
        for (int id : sideA ? b.armiesA : b.armiesB) {
            Army a = roster.army(id);
            if (a != null && a.soldiers > 0) out.merge(a.houseId, a.soldiers, Integer::sum);
        }
        if (!sideA && b.garrisonOf >= 0) out.merge(b.garrisonOf, roster.garrison(world, b.garrisonOf), Integer::sum);
        return out;
    }

    /** One army's captain still fights here, as a figure id; -1 if it has none or they have fallen. */
    public int captainOf(ArmyBattle b, int armyId) {
        Army a = roster.army(armyId);
        return a == null || ArmyBattle.holds(b.captainFell, armyId) ? -1 : a.captainId;
    }

    /** A soldier of this house is dead on the map: a named number, not a share (A22). */
    public void soldierFell(ArmyBattle b, boolean sideA, int houseId) {
        if (sideA && b.beastId >= 0) {
            b.beastHp = 0;
            return;
        }
        for (int id : sideA ? b.armiesA : b.armiesB) {
            Army a = roster.army(id);
            if (a != null && a.houseId == houseId && a.soldiers > 0) {
                a.soldiers--;
                b.addDead(houseId, 1);
                return;
            }
        }
        kill(b, sideA, 1);
    }

    /** A captain is dead on the map. */
    public void captainFell(ArmyBattle b, int armyId) {
        if (!ArmyBattle.holds(b.captainFell, armyId)) b.captainFell = ArmyBattle.append(b.captainFell, armyId);
    }

    /** A turn of a watched battle: what the map says it cost each side, weighed as any other turn (A20). */
    public void watchedTurn(ArmyBattle b, int lostA, int lostB, int captainsA, int captainsB, long clock) {
        b.turn++;
        settle(b, lostA, lostB, captainsA * ArmyTuning.MORALE_CAPTAIN, captainsB * ArmyTuning.MORALE_CAPTAIN, clock, battleRng(b, clock));
    }

    /** Whether a sleeper here has soldiers on them: a battle, or any army not of a house {@code friend} says leaves them be. */
    public boolean threatIn(int x, int y, java.util.function.IntPredicate friend) {
        if (battleAt(x, y) != null) return true;
        for (int i = 0; i < roster.armies.size(); i++) {
            Army a = roster.armies.get(i);
            if (a.x == x && a.y == y && a.kind != Army.Kind.HERALD && !friend.test(a.houseId)) return true;
        }
        return false;
    }

    /** Debug: a host of each of two warring houses meets at this chunk, now. Null with no war to fight. */
    public ArmyBattle raiseBattleAt(int x, int y) {
        List<War> active = world.activeWars();
        if (seats == null || active.isEmpty() || battleAt(x, y) != null) return null;
        War w = active.get(0);
        House h = world.house(w.attackerId), o = world.house(w.defenderId);
        if (h == null || o == null || !hasSeat(h.id) || !hasSeat(o.id)) return null;
        Army a = raise(h, ArmyTuning.ARMY_SIZE, Army.Kind.HOST), b = raise(o, ArmyTuning.ARMY_SIZE, Army.Kind.HOST);
        a.warId = w.id;
        b.warId = w.id;
        for (Army e : java.util.Arrays.asList(a, b)) {
            e.x = x;
            e.y = y;
        }
        start(a, b);
        return battleAt(x, y);
    }

    private int alive(ArmyBattle b, boolean sideA) {
        if (sideA && b.beastId >= 0) return b.beastHp > 0 ? 1 : 0;
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

    private SplittableRandom battleRng(ArmyBattle b, long clock) {
        return new SplittableRandom(world.seed ^ (clock * 0x9E3779B97F4A7C15L) ^ ((b.x * 31L + b.y) * 0xC2B2AE3D27D4EB4FL));
    }

    private void stepBattle(ArmyBattle b, long clock) {
        b.turn++;
        SplittableRandom rng = battleRng(b, clock);
        if (b.beastId >= 0) {
            stepBeastBattle(b, clock, rng);
            return;
        }
        int aliveA = alive(b, true), aliveB = alive(b, false);
        float edgeB = b.wallEdge > 0f ? b.wallEdge : b.garrisonOf >= 0 ? ArmyTuning.WALL_EDGE : 1f;
        int lostB = kill(b, false, Math.min(aliveB, hits(aliveA, 1f, rng)));
        int lostA = kill(b, true, Math.min(aliveA, hits(aliveB, edgeB, rng)));
        settle(b, lostA, lostB, captainsFall(b, b.armiesA, rng), captainsFall(b, b.armiesB, rng), clock, rng);
    }

    /** What a turn cost each side, watched or not: morale, and whether either has broken. */
    private void settle(ArmyBattle b, int lostA, int lostB, int captainCostA, int captainCostB, long clock, SplittableRandom rng) {
        b.moraleA = moraleAfter(b.moraleA, lostA, b.startA) - captainCostA;
        b.moraleB = moraleAfter(b.moraleB, lostB, b.startB) - captainCostB;
        boolean aBroke = b.moraleA <= 0 || alive(b, true) <= 0;
        boolean bBroke = b.moraleB <= 0 || alive(b, false) <= 0;
        if (b.assault) bBroke = fallingBack(b, bBroke, aBroke);
        if (aBroke || bBroke) {
            endBattle(b, aBroke && (!bBroke || b.moraleA <= b.moraleB), clock, rng);
        } else if (b.beastId >= 0 && b.turn >= ArmyTuning.BEAST_TURN_CAP) {
            endBattle(b, true, clock, rng);
        } else if (b.turn >= ArmyTuning.TURN_CAP) {
            // A seat is taken only by clearing its yard: an assault out of time has failed.
            endBattle(b, b.assault || alive(b, true) * Math.max(1, b.moraleA) < alive(b, false) * Math.max(1, b.moraleB), clock, rng);
        }
    }

    /** A garrison broken on the walls falls back to the yard; there it stands until none are left (A33). */
    private boolean fallingBack(ArmyBattle b, boolean broke, boolean attackersBroke) {
        int left = alive(b, false);
        if (b.yard) return left <= 0;
        boolean wallsLost = broke || left <= b.startB * SeatSiege.WALLS_HELD_TO;
        if (wallsLost && left > 0 && !attackersBroke) {
            b.yard = true;
            b.moraleB = 100;
            b.wallEdge = SeatSiege.YARD_EDGE;
            b.turn = 0; // a fresh fight, with its own time
            return false;
        }
        return broke;
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
        b.witnessed = b == watched;
        if (b == watched) watched = null;
        int[] won = aLost ? b.armiesB : b.armiesA, lost = aLost ? b.armiesA : b.armiesB;
        int winner = aLost ? b.houseB : b.houseA, loser = aLost ? b.houseA : b.houseB;
        PlayerDeed deed = new PlayerDeed(PlayerDeed.Kind.ARMY_BATTLE, winner, world.liveSeasons());
        deed.other = loser;
        if (b.beastId >= 0) {
            deed = new PlayerDeed(PlayerDeed.Kind.ARMY_BEAST, b.beastId, world.liveSeasons());
            deed.other = b.houseB;
            deed.beastSlain = b.beastHp <= 0;
            deed.beastHp = Math.max(1, b.beastHp);
        }
        deed.chunkX = b.x;
        deed.chunkY = b.y;
        deed.deadHouses = b.deadHouses;
        deed.deadCounts = b.deadCounts;
        deed.skirmish = b.startA + b.startB < ArmyTuning.SKIRMISH_UNDER;
        deed.atSeat = b.garrisonOf >= 0;
        // A sally beaten back loses men, not the seat.
        deed.seatFell = b.assault && !aLost;
        deed.assault = b.assault;
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
        deed.foughtFor = b.foughtFor();
        deed.witnessed = b.witnessed;
        // Only one still on the field at the end stood to it: one who fled was no longer watching (A40).
        int stoodWith = b.stoodWith();
        if (b.witnessed && stoodWith != -1) {
            boolean withA = stoodWith == b.houseA;
            int enemyDead = (withA ? b.startB : b.startA) - alive(b, !withA);
            b.playerShare = enemyDead > 0 ? Math.min(1f, b.playerKills / (float) enemyDead) : 0f;
            b.stoodHouses = sideHouses(withA ? b.armiesA : b.armiesB, stoodWith);
            deed.stoodWith = b.stoodHouses;
            deed.stoodAndWon = stoodWith == winner;
            deed.fought = b.playerKills > 0 || (b.playerStruck & (withA ? 2 : 1)) != 0;
            b.playerTurned = b.beastId < 0 && com.bpm.minotaur.gamedata.history.favour.BattleFavour.turned(deed.stoodAndWon, b.playerShare);
            deed.turned = b.playerTurned;
        }
        deed.armiesRouted = lost;
        deed.armiesHeld = won;
        b.winner = winner;
        b.loser = loser;
        for (int id : slain) {
            Figure f = world.figure(id);
            House h = f == null ? null : world.house(f.houseId);
            b.lordFell |= h != null && h.lordId == id;
        }
        deeds.accept(deed);
        if (deed.seatFell) {
            SeatSiege fallen = roster.siegeAt(b.x, b.y);
            if (fallen != null) {
                seatsFell++;
                siegeTurns += clock - fallen.since;
                roster.sieges.remove(fallen);
                release(fallen);
            }
            refreshSeats();
        }

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
            boolean host = a.kind == Army.Kind.HOST || a.kind == Army.Kind.LEVY;
            if (!host || a.soldiers < ArmyTuning.MIN_ARMY) {
                roster.armies.remove(a);
            } else {
                a.raidSent = false;
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
        watched = null;
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
            // A host camped before the castle stays before it, wherever the world has put it.
            a.x = a.beforeTheCastle ? to.x : at.x;
            a.y = a.beforeTheCastle ? to.y : at.y;
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
        if (a.objective == Army.Objective.CASTLE) {
            GridPoint2 ring = ringBeforeTheCastle(a);
            if (ring != null) return ring;
        }
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
                a.trail = ArmyBattle.append(a.trail, WarRoster.packed(a.x, a.y));
                payTheLand(a, clock, next);
                if (a.soldiers <= 0) {
                    roster.armies.remove(a);
                    return;
                }
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
        if (a.kind == Army.Kind.HERALD) {
            roster.armies.remove(a);
            return;
        }
        if (a.kind == Army.Kind.HUNTING_PARTY) {
            int[] seen = roster.playerSeenBy(a.houseId);
            if (seen != null && (seen[1] != a.x || seen[2] != a.y)) goTo(a, seen[1], seen[2]);
            return;
        }
        if (a.kind != Army.Kind.HOST && a.kind != Army.Kind.LEVY) return;
        if (a.objective == Army.Objective.CASTLE && a.x == a.targetX && a.y == a.targetY && castleOpenToSiege(war(a)) && layCastleSiege(a, clock)) return;
        if (a.objective == Army.Objective.SEAT) {
            House holder = holderOfSeatAt(a);
            if (holder != null) {
                invest(a, holder, clock);
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
        if (hasSeat(h.id) && roster.siegeAt(seatXs[h.id], seatYs[h.id]) != null) return; // no host marches out of an invested seat
        int wanted = Math.max(1, Math.round(h.strength / ArmyTuning.STRENGTH_PER_ARMY));
        while (true) {
            int avail = spare(h);
            if (avail < ArmyTuning.MIN_ARMY || hostsOf(h.id) >= wanted) return;
            Army a = raise(h, Math.min(ArmyTuning.ARMY_SIZE, avail), Army.Kind.HOST);
            a.musteredAt = clock;
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
        a.musteredAt = clock;
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
        if (a.kind != Army.Kind.HOST && a.kind != Army.Kind.LEVY) return; // a party keeps its errand
        if (a.sieging >= 0 || a.beforeTheCastle) return; // it stays at the walls until the siege ends
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
            float score = 0.6f + (has(a, Trait.AMBITIOUS) ? 0.5f : 0f) + 0.4f * Math.min(ratio, 2f) - d / ArmyTuning.SEAT_DISTANCE
                    - (has(a, Trait.PATIENT) && ratio < 1.5f ? 0.8f : 0f) + (has(a, Trait.ZEALOT) ? 0.2f : 0f)
                    + (float) rng.nextDouble() * 0.15f;
            if (score > bestScore) {
                bestScore = score;
                best = Army.Objective.SEAT;
                tx = enemySeat.x;
                ty = enemySeat.y;
            }
        }
        // Against the castle's house, at its full strength, a host may go and hold the breach open (A35).
        if (enemy != null && enemy.holdsCastle && !enemy.isExtinct() && h.strength >= com.bpm.minotaur.gamedata.history.HistorySimulator.SIEGE_STRENGTH
                && a.kind == Army.Kind.HOST && castleOpenToSiege(war)) {
            GridPoint2 ring = ringBeforeTheCastle(a);
            float score = 0.6f + (has(a, Trait.AMBITIOUS) ? 0.5f : 0f) + (has(a, Trait.ZEALOT) ? 0.2f : 0f) - (has(a, Trait.PATIENT) ? 0.2f : 0f) + (float) rng.nextDouble() * 0.15f;
            if (ring != null && score > bestScore) {
                bestScore = score;
                best = Army.Objective.CASTLE;
                tx = ring.x;
                ty = ring.y;
            }
        }
        // A host of the usual size has none to spare, so its party comes up from the garrison.
        boolean canRaid = a.soldiers >= ArmyTuning.MIN_ARMY + ArmyTuning.RAID_SIZE || spare(h) >= ArmyTuning.RAID_SIZE;
        if (!has(a, Trait.HONOURABLE) && !a.raidSent && canRaid && enemy != null && !enemy.isExtinct()) {
            float raid = 0.3f + (has(a, Trait.CUNNING) ? 0.5f : 0f) + (has(a, Trait.CRUEL) ? 0.4f : 0f) + (float) rng.nextDouble() * 0.35f;
            if (raid > bestScore || has(a, Trait.CUNNING) || has(a, Trait.CRUEL)) sendRaid(a, enemy, war);
        }
        // Its own seat under siege outweighs anything else it might do (A30).
        for (int i = 0; i < roster.sieges.size(); i++) {
            SeatSiege besieged = roster.sieges.get(i);
            Army besieger = besieged.holder == a.side() ? firstAt(besieged) : null;
            if (besieger == null) continue;
            bestScore = Float.MAX_VALUE;
            best = Army.Objective.HUNT;
            tx = besieged.x;
            ty = besieged.y;
            target = besieger.id;
            break;
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
        if (a.peaceRaid || e.peaceRaid) return false;
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
