package com.bpm.minotaur.managers;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.history.Doctrine;
import com.bpm.minotaur.gamedata.history.DoctrineCatalog;
import com.bpm.minotaur.gamedata.history.Figure;
import com.bpm.minotaur.gamedata.history.HistoryWorld;
import com.bpm.minotaur.gamedata.history.House;
import com.bpm.minotaur.gamedata.history.war.BattleModel;
import com.bpm.minotaur.gamedata.history.war.Front;
import com.bpm.minotaur.gamedata.monster.Faction;
import com.bpm.minotaur.gamedata.monster.Monster;
import com.bpm.minotaur.lighting.LightSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * One battle, fought in the chunk the player chose to stay in (plan D14-D18, D31, T2.3-T2.7).
 *
 * <p>It starts as a warning: horns, drums, and a count of turns in which the player can leave.
 * If they stay, the lines close: the chunk's gates are held by soldiers of both houses, and each
 * house marches its line in from its own edge. Every turn the {@link BattleModel} is told who
 * still stands and answers with reinforcements; volleys fall on telegraphed ground; when one
 * side routs it flees the field, the winners march on, and the field is left to the player.
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
    }

    /** Turns from the horns to the lines closing (plan T2.3; a tuning knob). */
    public static final int WARNING_TURNS = 12;
    /** Soldiers holding each gate once the lines close (plan D16, T2.5). */
    public static final int GATE_GUARD = 2;
    /** A volley is loosed every this many battle turns, and lands the turn after. */
    static final int VOLLEY_EVERY = 7;
    static final int VOLLEY_RADIUS = 1;
    /** A charge comes through every this many battle turns, when no volley does. */
    static final int CHARGE_EVERY = 11;
    static final int CHARGE_HALF_WIDTH = 1;
    /** Turns the victors linger on the field before marching on. */
    static final int AFTERMATH_TURNS = 3;
    /** How often a war-captain is the house's own lord rather than a sworn sword. */
    static final float LORD_LEADS = 0.15f;

    /** What a turn of the battle had to say. */
    public static final class Report {
        public final List<String> messages = new ArrayList<>();
        /** The house that held the field, once the battle breaks; -1 before. */
        public int winner = -1;
        public int loser = -1;
        /** Damage the player takes this turn from a landed volley. */
        public int volleyDamage;
    }

    public final Front front;
    public final GridPoint2 chunk;
    private final HistoryWorld world;
    private final DoctrineCatalog catalog;
    private final Random rng;
    private final int edgeA;

    private Phase phase = Phase.WARNING;
    private int warningLeft = WARNING_TURNS;
    private int aftermathLeft = AFTERMATH_TURNS;
    private BattleModel model;
    private final List<Monster> sideA = new ArrayList<>();
    private final List<Monster> sideB = new ArrayList<>();
    private Monster captainA;
    private Monster captainB;
    private boolean captainAFell;
    private boolean captainBFell;
    private boolean lordFell;
    private final List<GridPoint2> volley = new ArrayList<>();
    private int volleyCount;

    /**
     * @param edgeA the edge the attacker marches in from: 0 west, 1 east, 2 south, 3 north.
     *              The defender takes the opposite edge.
     */
    public BattleDirector(Front front, GridPoint2 chunk, int edgeA, HistoryWorld world, DoctrineCatalog catalog, long seed) {
        this.front = front;
        this.chunk = new GridPoint2(chunk);
        this.edgeA = edgeA;
        this.world = world;
        this.catalog = catalog;
        this.rng = new Random(seed);
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

    /** The horns: what the player hears the turn a front reaches them. */
    public String warning() {
        return "War-horns! " + name(front.attackerId) + " marches on " + name(front.defenderId)
                + ". The lines close here in " + WARNING_TURNS + " turns: leave by any edge, or go below.";
    }

    /** The player left before the battle broke. */
    public String abandoned() {
        return phase == Phase.WARNING ? "You slip away before the lines close." : "You leave the battle behind you.";
    }

    public Report tick(Maze maze, GridPoint2 playerTile, Recruiter recruiter, Spoils spoils) {
        Report r = new Report();
        switch (phase) {
            case WARNING:
                warningLeft--;
                if (warningLeft == 6) r.messages.add("War-drums, close now. Scouts of " + name(front.attackerId) + " run past.");
                if (warningLeft == 2) r.messages.add("You can see the banners. The lines are almost on you.");
                if (warningLeft <= 0) begin(maze, recruiter, r);
                break;
            case BATTLE:
                fight(maze, playerTile, recruiter, spoils, r);
                break;
            case AFTERMATH:
                if (--aftermathLeft <= 0) {
                    marchOn(maze, playerTile);
                    phase = Phase.DONE;
                    r.messages.add(name(model.winner()) + " marches on. The field is yours.");
                }
                break;
            default:
                break;
        }
        return r;
    }

    // ------------------------------------------------------------------ the lines close

    private void begin(Maze maze, Recruiter recruiter, Report r) {
        phase = Phase.BATTLE;
        model = new BattleModel(front.attackerId, BattleModel.reserveFor(strength(front.attackerId)),
                front.defenderId, BattleModel.reserveFor(strength(front.defenderId)));
        // The gates are held first: leaving now means cutting through.
        boolean toA = true;
        for (GridPoint2 gate : new ArrayList<>(maze.getGates().keySet())) {
            for (int i = 0; i < GATE_GUARD; i++) {
                GridPoint2 at = WorldManager.findSafeArrivalTile(maze, gate.x, gate.y);
                if (at == null || at.equals(gate)) continue;
                enlist(maze, recruiter, toA, at.x, at.y);
                toA = !toA;
            }
        }
        captainA = captain(maze, recruiter, true);
        captainB = captain(maze, recruiter, false);
        BattleModel.Orders o = model.muster(alive(sideA), alive(sideB));
        march(maze, recruiter, true, o.sendA);
        march(maze, recruiter, false, o.sendB);
        r.messages.add("The lines close. " + name(front.attackerId) + " and " + name(front.defenderId)
                + " meet on this ground, and you are standing on it.");
    }

    private Monster captain(Maze maze, Recruiter recruiter, boolean a) {
        int houseId = a ? front.attackerId : front.defenderId;
        House house = world.house(houseId);
        Figure leader = null;
        Figure lord = world.lordOf(house);
        if (lord != null && lord.isAlive() && !lord.ageless && rng.nextFloat() < LORD_LEADS) {
            leader = lord;
        } else {
            for (Figure f : world.swornSwords(house)) {
                if (f.hostageOf < 0) {
                    leader = f;
                    break;
                }
            }
        }
        GridPoint2 at = edgeTile(maze, a ? edgeA : opposite(edgeA));
        Monster m = at == null ? null : enlist(maze, recruiter, a, at.x, at.y);
        if (m == null) return null;
        if (leader != null) {
            m.setFigureId(leader.id);
            m.setDisplayName((leader == lord ? leader.name + ", Lord of " : "Captain " + leader.name + " of ") + house.name);
        } else {
            m.setDisplayName("A captain of " + house.name);
        }
        m.setMaxHP(m.getMaxHP() * 2);
        m.setCurrentHP(m.getMaxHP());
        return m;
    }

    // ------------------------------------------------------------------ the fighting

    private void fight(Maze maze, GridPoint2 playerTile, Recruiter recruiter, Spoils spoils, Report r) {
        land(maze, playerTile, r);
        boolean aFell = captainA != null && !captainAFell && !standing(maze, captainA);
        boolean bFell = captainB != null && !captainBFell && !standing(maze, captainB);
        if (aFell) {
            captainAFell = true;
            lordFell |= captainA.getFigureId() >= 0 && isLord(captainA.getFigureId());
            r.messages.add(captainA.getDisplayName() + " is down! The line of " + name(front.attackerId) + " wavers.");
        }
        if (bFell) {
            captainBFell = true;
            lordFell |= captainB.getFigureId() >= 0 && isLord(captainB.getFigureId());
            r.messages.add(captainB.getDisplayName() + " is down! The line of " + name(front.defenderId) + " wavers.");
        }
        BattleModel.Orders o = model.step(alive(sideA), alive(sideB), aFell, bFell);
        if (o.outcome != BattleModel.Outcome.ONGOING) {
            rout(maze, playerTile, spoils, r);
            return;
        }
        march(maze, recruiter, true, o.sendA);
        march(maze, recruiter, false, o.sendB);
        if (model.turn() % VOLLEY_EVERY == 0 && playerTile != null) loose(maze, playerTile, r);
        else if (model.turn() % CHARGE_EVERY == 0 && playerTile != null) charge(maze, playerTile, r);
    }

    /**
     * A charge: a band of ground across the field, along the line the armies face each other on,
     * marked a turn before the riders come through it.
     */
    private void charge(Maze maze, GridPoint2 playerTile, Report r) {
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
    private void loose(Maze maze, GridPoint2 playerTile, Report r) {
        int cx = playerTile.x + rng.nextInt(3) - 1;
        int cy = playerTile.y + rng.nextInt(3) - 1;
        for (int dy = -VOLLEY_RADIUS; dy <= VOLLEY_RADIUS; dy++) {
            for (int dx = -VOLLEY_RADIUS; dx <= VOLLEY_RADIUS; dx++) volley.add(new GridPoint2(cx + dx, cy + dy));
        }
        maze.addLight(new LightSource(volleyLight(), cx + 0.5f, cy + 0.5f, Color.SCARLET, 2.5f, 1.4f,
                LightSource.FlickerProfile.LANTERN_BREATH));
        r.messages.add("Bowstrings, all at once. The sky darkens over you: move!");
    }

    private void land(Maze maze, GridPoint2 playerTile, Report r) {
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
        return "volley_" + front.warId + "_" + volleyCount;
    }

    private void rout(Maze maze, GridPoint2 playerTile, Spoils spoils, Report r) {
        phase = Phase.AFTERMATH;
        r.winner = model.winner();
        r.loser = model.loser();
        maze.removeLight(volleyLight());
        volley.clear();
        List<Monster> broken = r.loser == front.attackerId ? sideA : sideB;
        for (Monster m : broken) maze.removeMonster(m);
        r.messages.add(name(r.loser) + " breaks and flees the field. " + name(r.winner) + " holds it.");
        GridPoint2 field = playerTile != null ? playerTile : new GridPoint2(maze.getWidth() / 2, maze.getHeight() / 2);
        spoils.drop(maze, field, r.loser, lordFell);
    }

    /** The victors leave, all but any still locked in a fight with the player. */
    private void marchOn(Maze maze, GridPoint2 playerTile) {
        List<Monster> victors = model.winner() == front.attackerId ? sideA : sideB;
        for (Monster m : victors) {
            if (playerTile != null && Math.abs(m.getPosition().x - playerTile.x) + Math.abs(m.getPosition().y - playerTile.y) <= 1) continue;
            maze.removeMonster(m);
        }
    }

    // ------------------------------------------------------------------ soldiers

    private void march(Maze maze, Recruiter recruiter, boolean a, int count) {
        for (int i = 0; i < count; i++) {
            GridPoint2 at = edgeTile(maze, a ? edgeA : opposite(edgeA));
            if (at == null) return;
            enlist(maze, recruiter, a, at.x, at.y);
        }
    }

    private Monster enlist(Maze maze, Recruiter recruiter, boolean a, int x, int y) {
        int houseId = a ? front.attackerId : front.defenderId;
        Doctrine d = catalog.get(world.house(houseId).doctrineId);
        Monster m = recruiter.recruit(d.roster.get(rng.nextInt(d.roster.size())), x, y);
        if (m == null) return null;
        m.setFaction(Faction.MAZE_HOUSE);
        m.setHouseId(houseId);
        m.setWarBand(true);
        m.setState(Monster.MonsterState.HUNTING);
        maze.addMonster(m);
        (a ? sideA : sideB).add(m);
        return m;
    }

    /** A free tile near the given edge, four tiles deep. */
    private GridPoint2 edgeTile(Maze maze, int edge) {
        int w = maze.getWidth();
        int h = maze.getHeight();
        for (int attempt = 0; attempt < 12; attempt++) {
            int depth = 1 + rng.nextInt(4);
            int along = 2 + rng.nextInt(Math.max(1, (edge < 2 ? h : w) - 4));
            int x, y;
            switch (edge) {
                case 0: x = depth; y = along; break;
                case 1: x = w - 1 - depth; y = along; break;
                case 2: x = along; y = depth; break;
                default: x = along; y = h - 1 - depth; break;
            }
            GridPoint2 at = WorldManager.findSafeArrivalTile(maze, x, y);
            if (at != null) return at;
        }
        return null;
    }

    private static int opposite(int edge) {
        return edge ^ 1;
    }

    private int alive(List<Monster> side) {
        int n = 0;
        for (Monster m : side) if (m.isAlive()) n++;
        return n;
    }

    private static boolean standing(Maze maze, Monster m) {
        return m.isAlive() && maze.getMonsters().containsValue(m);
    }

    private boolean isLord(int figureId) {
        Figure f = world.figure(figureId);
        House h = f == null ? null : world.house(f.houseId);
        return h != null && h.lordId == figureId;
    }

    private float strength(int houseId) {
        House h = world.house(houseId);
        return h == null ? 10f : h.strength;
    }

    private String name(int houseId) {
        House h = world.house(houseId);
        return h == null ? "a house of the Maze" : h.name;
    }

    // ------------------------------------------------------------------ for tests

    List<Monster> side(boolean a) {
        return a ? sideA : sideB;
    }

    Monster captainOf(boolean a) {
        return a ? captainA : captainB;
    }

    BattleModel model() {
        return model;
    }
}
