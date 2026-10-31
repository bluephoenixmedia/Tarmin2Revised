package com.bpm.minotaur.managers;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.history.Doctrine;
import com.bpm.minotaur.gamedata.history.DoctrineCatalog;
import com.bpm.minotaur.gamedata.history.Figure;
import com.bpm.minotaur.gamedata.history.HistoryWorld;
import com.bpm.minotaur.gamedata.history.House;
import com.bpm.minotaur.gamedata.history.war.army.ArmyBattle;
import com.bpm.minotaur.gamedata.history.war.army.ArmyLayer;
import com.bpm.minotaur.gamedata.monster.Faction;
import com.bpm.minotaur.gamedata.monster.Monster;
import com.bpm.minotaur.lighting.LightSource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * One battle of the armies, on the ground the player stands on (Living War A20-A24). There is one
 * state, the {@link ArmyBattle}: while the player is here the map is its truth, so every soldier
 * who dies on the field comes off its number that turn, and on leaving the soldiers simply go,
 * since what they stood for is already counted. Coming back raises the line again from the numbers.
 *
 * <p>A battle that begins with the player present starts as a warning: horns, drums, and a count of
 * turns in which to leave. One walked into under way gives none. Only the lines appear, up to
 * {@link #LINE} a side, and the rest march in from their edge; volleys fall on telegraphed ground;
 * when one side breaks it flees the field, the winners march on, and the field is left to the player.
 *
 * <p>Monsters and loot come through {@link Recruiter} and {@link Spoils}, so the whole arc runs
 * headless in tests.
 */
public final class BattleDirector {

    public enum Phase { WARNING, BATTLE, AFTERMATH, DONE }

    /** Makes a soldier of a monsters.json type at a tile; null if it cannot. */
    public interface Recruiter {
        Monster recruit(String monsterType, int x, int y);
    }

    /** Leaves what a broken army leaves behind. */
    public interface Spoils {
        void drop(Maze maze, GridPoint2 near, int loserHouseId, boolean lordFell);

        /** What is left for one who comes after the crows (A24). */
        default void pickedOver(Maze maze, GridPoint2 near) {
        }
    }

    /** Turns from the horns to the lines closing (plan T2.3; a tuning knob). */
    public static final int WARNING_TURNS = 12;
    /** Soldiers holding each gate once the lines close (plan D16, T2.5). */
    public static final int GATE_GUARD = 2;
    /** Soldiers of a side on the field at once, not counting those at the gates (A21). */
    public static final int LINE = ArmyBattle.LINE;
    /** Soldiers of the reserve who reach the field in a turn (A21). */
    public static final int MARCH_PER_TURN = 3;
    /** Share of the blows between soldiers of a battle that land: tuned against BattleEquivalenceTest (A22). */
    public static final float BLOWS_LAND = 0.33f;
    /** How deep from its edge a line is found: fresh from the march, or already at it. */
    private static final int DEPTH_FRESH = 4;
    private static final int DEPTH_UNDER_WAY = 9;
    /** A volley is loosed every this many battle turns, and lands the turn after. */
    static final int VOLLEY_EVERY = 7;
    static final int VOLLEY_RADIUS = 1;
    /** A charge comes through every this many battle turns, when no volley does. */
    static final int CHARGE_EVERY = 11;
    static final int CHARGE_HALF_WIDTH = 1;
    /** Turns the victors linger on the field before marching on. */
    static final int AFTERMATH_TURNS = 3;

    /** What a turn of the battle had to say. */
    public static final class Report {
        public final List<String> messages = new ArrayList<>();
        /** The house that held the field, once the battle breaks; -1 before. */
        public int winner = -1;
        public int loser = -1;
        /** Damage the player takes this turn from a landed volley. */
        public int volleyDamage;
    }

    public final ArmyBattle battle;
    public final GridPoint2 chunk;
    private final ArmyLayer layer;
    private final HistoryWorld world;
    private final DoctrineCatalog catalog;
    private final Random rng;
    private final int edgeA;
    private final int depth;

    private Phase phase;
    private boolean lined;
    private int warningLeft = WARNING_TURNS;
    private int aftermathLeft = AFTERMATH_TURNS;
    private Maze maze;
    private final List<Monster> sideA = new ArrayList<>();
    private final List<Monster> sideB = new ArrayList<>();
    /** The army each captain on the field leads. */
    private final Map<Monster, Integer> captains = new HashMap<>();
    private final List<GridPoint2> volley = new ArrayList<>();
    private int volleyCount;
    private int battleTurns;

    /**
     * @param edgeA   the edge side A marches in from: 0 west, 1 east, 2 south, 3 north. Side B takes
     *                the opposite edge.
     * @param underWay the player walked into it, so it starts at once, without the horns.
     */
    public BattleDirector(ArmyBattle battle, ArmyLayer layer, GridPoint2 chunk, int edgeA, HistoryWorld world,
            DoctrineCatalog catalog, long seed, boolean underWay) {
        this.battle = battle;
        this.layer = layer;
        this.chunk = new GridPoint2(chunk);
        this.edgeA = edgeA;
        this.world = world;
        this.catalog = catalog;
        this.rng = new Random(seed);
        this.phase = underWay ? Phase.BATTLE : Phase.WARNING;
        this.depth = underWay ? DEPTH_UNDER_WAY : DEPTH_FRESH;
        layer.watchBattle(battle);
    }

    /** The edge facing a seat, seen from a chunk: 0 west, 1 east, 2 south, 3 north. */
    public static int edgeToward(GridPoint2 from, GridPoint2 seat) {
        if (seat == null) return 0;
        int dx = seat.x - from.x;
        int dy = seat.y - from.y;
        if (Math.abs(dx) >= Math.abs(dy)) return dx < 0 ? 0 : 1;
        return dy < 0 ? 2 : 3;
    }

    public Phase phase() {
        return phase;
    }

    public ArmyLayer layer() {
        return layer;
    }

    /** The horns: what the player hears the turn a battle begins around them. */
    public String warning() {
        return "War-horns! " + name(battle.houseA) + " and " + name(battle.houseB)
                + " have met on this ground. The lines close here in " + WARNING_TURNS
                + " turns: leave by any edge, or go below.";
    }

    /** The player left it. */
    public String abandoned() {
        return phase == Phase.WARNING ? "You slip away before the lines close." : "You leave the battle behind you.";
    }

    public Report tick(Maze maze, GridPoint2 playerTile, Recruiter recruiter, Spoils spoils, long clock) {
        this.maze = maze;
        Report r = new Report();
        switch (phase) {
            case WARNING:
                warningLeft--;
                if (warningLeft == 6) r.messages.add("War-drums, close now. Scouts of " + name(battle.houseA) + " run past.");
                if (warningLeft == 2) r.messages.add("You can see the banners. The lines are almost on you.");
                if (warningLeft <= 0) begin(recruiter, r);
                break;
            case BATTLE:
                if (!lined) begin(recruiter, r);
                else fight(playerTile, recruiter, spoils, clock, r);
                break;
            case AFTERMATH:
                if (--aftermathLeft <= 0) {
                    marchOn(playerTile);
                    phase = Phase.DONE;
                    r.messages.add(name(battle.winner) + " marches on. The field is yours.");
                }
                break;
            default:
                break;
        }
        return r;
    }

    /**
     * The player has gone (A23). What has died so far is counted, the soldiers are gone with no
     * more said, wounds and all, and the number carries on by itself. Should that very count break
     * a side, it broke with the player gone: no spoils but what is left after, and no witness.
     */
    public void collapse(long clock) {
        boolean count = lined && layer.watchedBattle() == battle && layer.roster().battles.contains(battle);
        if (layer.watchedBattle() == battle) layer.watchBattle(null);
        if (count) {
            int[] lost = countTheFallen(null);
            if (lost[0] + lost[1] > 0) layer.watchedTurn(battle, lost[0], lost[1], lost[2], lost[3], clock);
        }
        if (maze != null) {
            maze.removeLight(volleyLight());
            for (Monster m : sideA) maze.removeMonster(m);
            for (Monster m : sideB) maze.removeMonster(m);
        }
        sideA.clear();
        sideB.clear();
        captains.clear();
        volley.clear();
    }

    // ------------------------------------------------------------------ the lines close

    private void begin(Recruiter recruiter, Report r) {
        boolean underWay = phase == Phase.BATTLE;
        phase = Phase.BATTLE;
        lined = true;
        // The gates are held first: leaving now means cutting through.
        boolean toA = true;
        for (GridPoint2 gate : new ArrayList<>(maze.getGates().keySet())) {
            for (int i = 0; i < GATE_GUARD; i++) {
                GridPoint2 at = WorldManager.findSafeArrivalTile(maze, gate.x, gate.y);
                if (at == null || at.equals(gate) || reserve(toA) <= 0) continue;
                Monster guard = enlist(recruiter, toA, at.x, at.y);
                if (guard != null) {
                    guard.setGateGuard(true);
                    guard.setMarchTarget(null);
                    guard.setState(Monster.MonsterState.HUNTING);
                }
                toA = !toA;
            }
        }
        captains(recruiter, true);
        captains(recruiter, false);
        march(recruiter, true, LINE);
        march(recruiter, false, LINE);
        r.messages.add(underWay
                ? "Steel and screaming: " + name(battle.houseA) + " and " + name(battle.houseB) + " are at each other's throats here."
                : "The lines close. " + name(battle.houseA) + " and " + name(battle.houseB)
                        + " meet on this ground, and you are standing on it.");
    }

    /** One captain for each army whose captain still stands (A21). */
    private void captains(Recruiter recruiter, boolean a) {
        for (int armyId : a ? battle.armiesA : battle.armiesB) {
            int figureId = layer.captainOf(battle, armyId);
            if (figureId < 0 || reserve(a) <= 0) continue;
            Figure leader = world.figure(figureId);
            House house = leader == null ? null : world.house(leader.houseId);
            GridPoint2 at = edgeTile(a ? edgeA : opposite(edgeA));
            Monster m = at == null || house == null ? null : enlist(recruiter, a, house.id, at.x, at.y);
            if (m == null) continue;
            m.setFigureId(figureId);
            m.setDisplayName(house.lordId == figureId ? leader.name + ", Lord of " + house.name : "Captain " + leader.name + " of " + house.name);
            m.setMaxHP(m.getMaxHP() * 2);
            m.setCurrentHP(m.getMaxHP());
            captains.put(m, armyId);
        }
    }

    // ------------------------------------------------------------------ the fighting

    private void fight(GridPoint2 playerTile, Recruiter recruiter, Spoils spoils, long clock, Report r) {
        battleTurns++;
        land(playerTile, r);
        int[] lost = countTheFallen(r);
        layer.watchedTurn(battle, lost[0], lost[1], lost[2], lost[3], clock);
        if (battle.winner >= 0) {
            rout(playerTile, spoils, r);
            return;
        }
        march(recruiter, true, MARCH_PER_TURN);
        march(recruiter, false, MARCH_PER_TURN);
        if (battleTurns % VOLLEY_EVERY == 0 && playerTile != null) loose(playerTile, r);
        else if (battleTurns % CHARGE_EVERY == 0 && playerTile != null) charge(playerTile, r);
    }

    /** Takes the dead off the field and off the numbers: lost on A, on B, captains of A, of B. */
    private int[] countTheFallen(Report r) {
        int[] lost = new int[4];
        for (int side = 0; side < 2; side++) {
            boolean a = side == 0;
            List<Monster> line = a ? sideA : sideB;
            for (int i = line.size() - 1; i >= 0; i--) {
                Monster m = line.get(i);
                if (m.seekerDrewBlood()) battle.playerStruck |= a ? 1 : 2;
                if (standing(m)) continue;
                line.remove(i);
                layer.soldierFell(battle, a, m.getHouseId());
                lost[side]++;
                Integer army = captains.remove(m);
                if (army == null) continue;
                layer.captainFell(battle, army);
                lost[side + 2]++;
                if (r != null) r.messages.add(m.getDisplayName() + " is down! The line of " + name(a ? battle.houseA : battle.houseB) + " wavers.");
            }
        }
        // Whoever strikes their own side has left it.
        if ((battle.playerSide == 1 && (battle.playerStruck & 1) != 0) || (battle.playerSide == 2 && (battle.playerStruck & 2) != 0)) {
            battle.playerSide = 0;
        }
        return lost;
    }

    /**
     * A charge: a band of ground across the field, along the line the armies face each other on,
     * marked a turn before the riders come through it.
     */
    private void charge(GridPoint2 playerTile, Report r) {
        boolean acrossX = edgeA < 2; // armies west and east charge along rows
        for (int i = 1; i < (acrossX ? maze.getWidth() : maze.getHeight()) - 1; i++) {
            for (int band = -CHARGE_HALF_WIDTH; band <= CHARGE_HALF_WIDTH; band++) {
                volley.add(acrossX ? new GridPoint2(i, playerTile.y + band) : new GridPoint2(playerTile.x + band, i));
            }
        }
        maze.addLight(new LightSource(volleyLight(), playerTile.x + 0.5f, playerTile.y + 0.5f, Color.ORANGE, 3.5f, 1.2f,
                LightSource.FlickerProfile.LANTERN_BREATH));
        r.messages.add("Hooves, and a horn. A charge is coming across the field toward you: get off its line!");
    }

    /** A volley is marked on the ground the turn before it lands, so the player can step out. */
    private void loose(GridPoint2 playerTile, Report r) {
        int cx = playerTile.x + rng.nextInt(3) - 1;
        int cy = playerTile.y + rng.nextInt(3) - 1;
        for (int dy = -VOLLEY_RADIUS; dy <= VOLLEY_RADIUS; dy++) {
            for (int dx = -VOLLEY_RADIUS; dx <= VOLLEY_RADIUS; dx++) volley.add(new GridPoint2(cx + dx, cy + dy));
        }
        maze.addLight(new LightSource(volleyLight(), cx + 0.5f, cy + 0.5f, Color.SCARLET, 2.5f, 1.4f,
                LightSource.FlickerProfile.LANTERN_BREATH));
        r.messages.add("Bowstrings, all at once. The sky darkens over you: move!");
    }

    private void land(GridPoint2 playerTile, Report r) {
        if (volley.isEmpty()) return;
        maze.removeLight(volleyLight());
        volleyCount++;
        if (playerTile != null && volley.contains(playerTile)) {
            r.volleyDamage = 2 + rng.nextInt(6) + rng.nextInt(6);
            r.messages.add("The volley finds you.");
        }
        for (Monster m : new ArrayList<>(maze.getMonsters().values())) {
            if (m == null || !m.isWarBand()) continue;
            GridPoint2 at = new GridPoint2((int) m.getPosition().x, (int) m.getPosition().y);
            if (!volley.contains(at)) continue;
            m.setCurrentHP(m.getCurrentHP() - (3 + rng.nextInt(4)));
            if (m.getCurrentHP() <= 0) maze.removeMonster(m);
        }
        volley.clear();
    }

    private String volleyLight() {
        return "volley_" + battle.id + "_" + volleyCount;
    }

    private void rout(GridPoint2 playerTile, Spoils spoils, Report r) {
        phase = Phase.AFTERMATH;
        r.winner = battle.winner;
        r.loser = battle.loser;
        maze.removeLight(volleyLight());
        volley.clear();
        boolean aLost = battle.loser == battle.houseA;
        for (Monster m : aLost ? sideA : sideB) maze.removeMonster(m);
        r.messages.add(name(r.loser) + " breaks and flees the field. " + name(r.winner) + " holds it.");
        GridPoint2 field = playerTile != null ? playerTile : new GridPoint2(maze.getWidth() / 2, maze.getHeight() / 2);
        spoils.drop(maze, field, r.loser, battle.lordFell);
    }

    /** The victors leave, all but any still locked in a fight with the player. */
    private void marchOn(GridPoint2 playerTile) {
        boolean aWon = battle.winner == battle.houseA;
        boolean friends = battle.playerSide == (aWon ? 1 : 2);
        for (Monster m : aWon ? sideA : sideB) {
            boolean locked = playerTile != null && Math.abs(m.getPosition().x - playerTile.x) + Math.abs(m.getPosition().y - playerTile.y) <= 1;
            if (locked && !friends) continue;
            maze.removeMonster(m);
        }
    }

    // ------------------------------------------------------------------ choosing a side (A26)

    /** The player stands with a side from here until the fight ends: 1 for A, 2 for B, 0 to keep away. */
    public void chooseSide(int side) {
        battle.playerSide = side;
    }

    /** Whether this soldier fights on the side the player has chosen. */
    public boolean alliedWith(Monster m) {
        if (phase == Phase.DONE || battle.playerSide == 0) return false;
        return (battle.playerSide == 1 ? sideA : sideB).contains(m);
    }

    /** Whether the player stands on either side or has drawn the blood of any soldier on this field (W15). */
    public boolean playerJoined() {
        if (battle.playerSide != 0 || battle.playerStruck != 0) return true;
        for (Monster m : sideA) if (m.seekerDrewBlood()) return true;
        for (Monster m : sideB) if (m.seekerDrewBlood()) return true;
        return false;
    }

    /** Which side a house fights on here: 1 for A, 2 for B, 0 if it is in neither. */
    public int sideOf(int houseId) {
        if (houseId == battle.houseA || layer.housesOn(battle, true).containsKey(houseId)) return 1;
        if (houseId == battle.houseB || layer.housesOn(battle, false).containsKey(houseId)) return 2;
        return 0;
    }

    /** Whether the player has drawn the blood of this house's side here, kills and all (A26). */
    public boolean playerStruckSideOf(int houseId) {
        int side = sideOf(houseId);
        return side != 0 && (battle.playerStruck & side) != 0;
    }

    /** The house the player fought for: the one whose enemies alone they struck; -1 if both or neither (W19). */
    public int playerFoughtFor() {
        return battle.foughtFor();
    }

    // ------------------------------------------------------------------ soldiers

    /** The soldiers of a side who have not reached the field. */
    private int reserve(boolean a) {
        return layer.aliveOn(battle, a) - (a ? sideA : sideB).size();
    }

    /** Fills a side's line, by no more than {@code count} this turn. */
    private void march(Recruiter recruiter, boolean a, int count) {
        for (int i = 0; i < count && reserve(a) > 0 && lineOf(a) < LINE; i++) {
            GridPoint2 at = edgeTile(a ? edgeA : opposite(edgeA));
            if (at == null || enlist(recruiter, a, at.x, at.y) == null) return;
        }
    }

    /** Soldiers of a side in the line, leaving out the gate guards. */
    private int lineOf(boolean a) {
        int n = 0;
        for (Monster m : a ? sideA : sideB) if (!m.isGateGuard()) n++;
        return n;
    }

    /** With several houses on a side, the line is in proportion to who has not yet reached it (A21). */
    private int nextHouse(boolean a) {
        Map<Integer, Integer> left = layer.housesOn(battle, a);
        for (Monster m : a ? sideA : sideB) left.computeIfPresent(m.getHouseId(), (h, n) -> n - 1);
        int total = 0;
        for (int n : left.values()) total += Math.max(0, n);
        if (total > 0) {
            int roll = rng.nextInt(total);
            for (Map.Entry<Integer, Integer> e : left.entrySet()) {
                roll -= Math.max(0, e.getValue());
                if (roll < 0) return e.getKey();
            }
        }
        return a ? battle.houseA : battle.houseB;
    }

    private Monster enlist(Recruiter recruiter, boolean a, int x, int y) {
        return enlist(recruiter, a, nextHouse(a), x, y);
    }

    private Monster enlist(Recruiter recruiter, boolean a, int houseId, int x, int y) {
        House house = world.house(houseId);
        Doctrine d = house == null ? null : catalog.get(house.doctrineId);
        if (d == null || d.roster.isEmpty()) return null;
        Monster m = recruiter.recruit(d.roster.get(rng.nextInt(d.roster.size())), x, y);
        if (m == null) return null;
        m.setFaction(Faction.MAZE_HOUSE);
        m.setHouseId(houseId);
        m.setWarBand(true);
        m.setBattleSide(a ? 1 : 2);
        // The line advances on the middle of the field until it sees someone to fight.
        m.setMarchTarget(new GridPoint2(maze.getWidth() / 2, maze.getHeight() / 2));
        m.setState(Monster.MonsterState.WANDERING);
        maze.addMonster(m);
        (a ? sideA : sideB).add(m);
        return m;
    }

    /** A free tile near the given edge, as deep as this battle has come. */
    private GridPoint2 edgeTile(int edge) {
        int w = maze.getWidth();
        int h = maze.getHeight();
        for (int attempt = 0; attempt < 12; attempt++) {
            int deep = 1 + rng.nextInt(battleTurns > 0 ? DEPTH_FRESH : depth);
            int along = 2 + rng.nextInt(Math.max(1, (edge < 2 ? h : w) - 4));
            int x, y;
            switch (edge) {
                case 0: x = deep; y = along; break;
                case 1: x = w - 1 - deep; y = along; break;
                case 2: x = along; y = deep; break;
                default: x = along; y = h - 1 - deep; break;
            }
            GridPoint2 at = WorldManager.findSafeArrivalTile(maze, x, y);
            if (at != null) return at;
        }
        return null;
    }

    private static int opposite(int edge) {
        return edge ^ 1;
    }

    private boolean standing(Monster m) {
        return m.isAlive() && maze.getMonsters().containsValue(m);
    }

    private String name(int houseId) {
        House h = world.house(houseId);
        return h == null ? "a house of the Maze" : h.name;
    }

    /** How the battle stands, line and reserve, for the HUD (W30); null before the lines close or after it breaks. */
    public com.bpm.minotaur.gamedata.history.war.WarTally tally() {
        if (phase != Phase.BATTLE) return null;
        return new com.bpm.minotaur.gamedata.history.war.WarTally(battle.houseA, battle.houseB,
                layer.aliveOn(battle, true), layer.aliveOn(battle, false));
    }

    // ------------------------------------------------------------------ for tests

    List<Monster> side(boolean a) {
        return a ? sideA : sideB;
    }

    Map<Monster, Integer> captainsOnField() {
        return captains;
    }
}
