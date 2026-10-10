package com.bpm.minotaur.managers;

import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.Scenery;
import com.bpm.minotaur.gamedata.history.Doctrine;
import com.bpm.minotaur.gamedata.history.DoctrineCatalog;
import com.bpm.minotaur.gamedata.history.Figure;
import com.bpm.minotaur.gamedata.history.House;
import com.bpm.minotaur.gamedata.history.HistoryWorld;
import com.bpm.minotaur.gamedata.history.War;
import com.bpm.minotaur.gamedata.history.war.Encounter;
import com.bpm.minotaur.gamedata.history.war.EncounterLedger;
import com.bpm.minotaur.gamedata.monster.Faction;
import com.bpm.minotaur.gamedata.monster.Monster;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Puts the surface war's encounters into the chunk the player stands in (Living War W2, W12):
 * a skirmish's two war-bands, a column marching through, raiders at their fires, a war camp.
 * The soldiers are war-bands, never saved with the chunk; the props the war leaves behind are
 * recorded in the ledger so they can be cleared again. Monsters and props come through
 * {@link BattleDirector.Recruiter} and {@link Props}, so it all runs headless in tests.
 */
public final class EncounterDirector {

    /** Makes a prop of {@code props.json} at a tile; null if it cannot. */
    public interface Props {
        Scenery make(String propId, int x, int y);
    }

    public enum Cue { CLASH, ROUT, DRUMS, FIRE }

    public static final class Turn {
        public final List<String> messages = new ArrayList<>();
        public final List<Cue> cues = new ArrayList<>();
    }

    static final int BAND_MIN = 6;
    static final int BAND_MAX = 10;
    static final int COLUMN_MIN = 8;
    static final int COLUMN_MAX = 15;
    static final int RAIDERS_MIN = 4;
    static final int RAIDERS_MAX = 6;
    static final int SENTRIES = 3;
    /** Things a raid sets alight, and how far from its centre it looks for them. */
    static final int RAID_BURNS = 3;
    static final int RAID_REACH = 8;
    /** Props never put to the torch: the way in and out, and the war's own. */
    private static final java.util.Set<String> UNBURNABLE = new java.util.HashSet<>(java.util.Arrays.asList(
            "campfire", "brazier", "camp_tent", "war_banner", "castle_gate", "castle_billboard"));
    /** A war-band breaks when this share of it is left standing. */
    static final float ROUT_AT = 0.4f;
    /** Turns of fighting before the weaker band gives up the ground anyway. */
    static final int FIGHT_CAP = 50;
    /** Turns the winners linger on the ground before marching on. */
    static final int LINGER = 3;
    /** Sleeps a battlefield stays scarred (W2). */
    public static final int AFTERMATH_SLEEPS = 3;
    static final String AFTERMATH = "AFTERMATH";
    static final String CAMP = "CAMP";
    private static final String[] FIELD = {"bone_pile", "skull_pile", "bone_pile", "war_banner", "head_spike"};

    private final DoctrineCatalog catalog;
    private final Map<String, Stage> stages = new LinkedHashMap<>();
    private GridPoint2 chunk;

    public EncounterDirector(DoctrineCatalog catalog) {
        this.catalog = catalog;
    }

    private enum Phase { FIGHT, LINGER, MARCH, HOLD, DONE }

    /** One encounter put into the live chunk. */
    static final class Stage {
        final Encounter e;
        final List<Monster> a = new ArrayList<>();
        final List<Monster> b = new ArrayList<>();
        Monster captainA;
        Monster captainB;
        int startA;
        int startB;
        int turns;
        int linger = LINGER;
        int winner = -1;
        Phase phase;

        Stage(Encounter e, Phase phase) {
            this.e = e;
            this.phase = phase;
        }
    }

    /**
     * One player turn. {@code encounters} are the scheduler's for now, {@code camps} the war camps;
     * {@code sanctuary} is a shelter chunk, which the war passes by.
     */
    public Turn onTurn(HistoryManager history, GridPoint2 here, int level, boolean sanctuary, Maze maze,
            GridPoint2 playerTile, List<Encounter> encounters, List<Encounter> camps,
            BattleDirector.Recruiter recruiter, Props props, BattleDirector.Spoils spoils) {
        Turn t = new Turn();
        EncounterLedger ledger = history.encounterLedger();
        if (here == null || level != 1 || !here.equals(chunk)) {
            leave(ledger);
            chunk = level == 1 && here != null ? new GridPoint2(here) : null;
            if (chunk != null && maze != null) reconcile(history, maze, camps);
        }
        if (chunk == null || sanctuary || maze == null) return t;
        long clock = history.warClock();
        HistoryWorld world = history.world();
        for (Encounter e : encounters) {
            if (!e.visible() || !e.chunkAt(clock).equals(chunk) || ledger.isSpent(e)) continue;
            String key = e.key() + "@" + chunk.x + "," + chunk.y;
            if (stages.containsKey(key)) continue;
            Stage s = null;
            switch (e.kind) {
                case SKIRMISH: s = skirmish(e, world, maze, recruiter, t); break;
                case COLUMN: s = column(e, world, maze, recruiter, t); break;
                case RAID: s = raid(e, world, maze, recruiter, props, ledger, t); break;
                default: break;
            }
            if (s != null) stages.put(key, s);
        }
        for (Encounter c : camps) {
            if (!c.chunkAt(clock).equals(chunk) || ledger.isSpent(c)) continue;
            String key = c.key() + "@" + chunk.x + "," + chunk.y;
            if (!stages.containsKey(key)) stages.put(key, camp(c, world, maze, recruiter, props, ledger, t));
        }
        for (Iterator<Stage> it = stages.values().iterator(); it.hasNext(); ) {
            Stage s = it.next();
            tick(s, history, maze, playerTile, clock, props, spoils, t);
            if (s.phase == Phase.DONE) it.remove();
        }
        return t;
    }

    /** The player left the chunk: a fight under way goes on without them, and is not seen again. */
    private void leave(EncounterLedger ledger) {
        for (Stage s : stages.values()) {
            if (s.phase == Phase.FIGHT && s.e.kind == Encounter.Kind.SKIRMISH) ledger.spend(s.e);
        }
        stages.clear();
    }

    // ------------------------------------------------------------------ staging

    private Stage skirmish(Encounter e, HistoryWorld world, Maze maze, BattleDirector.Recruiter recruiter, Turn t) {
        Random r = rng(world, e, 1);
        Stage s = new Stage(e, Phase.FIGHT);
        int edge = r.nextInt(4);
        int na = BAND_MIN + r.nextInt(BAND_MAX - BAND_MIN + 1);
        int nb = BAND_MIN + r.nextInt(BAND_MAX - BAND_MIN + 1);
        for (int i = 0; i < na; i++) enlist(s.a, maze, recruiter, world, e.houseA, edgeTile(maze, edge, r), r);
        for (int i = 0; i < nb; i++) enlist(s.b, maze, recruiter, world, e.houseB, edgeTile(maze, edge ^ 1, r), r);
        if (s.a.isEmpty() || s.b.isEmpty()) {
            for (Monster m : s.a) maze.removeMonster(m);
            for (Monster m : s.b) maze.removeMonster(m);
            return null;
        }
        s.captainA = captain(s.a.get(0), world, e.houseA);
        s.captainB = captain(s.b.get(0), world, e.houseB);
        s.startA = s.a.size();
        s.startB = s.b.size();
        t.messages.add("Steel and screaming: a war-band of " + name(world, e.houseA) + " has met one of "
                + name(world, e.houseB) + " here.");
        t.cues.add(Cue.CLASH);
        return s;
    }

    private Stage column(Encounter e, HistoryWorld world, Maze maze, BattleDirector.Recruiter recruiter, Turn t) {
        Random r = rng(world, e, 2);
        Stage s = new Stage(e, Phase.MARCH);
        int dx = Integer.signum(e.to.x - e.from.x);
        int dy = Integer.signum(e.to.y - e.from.y);
        int w = maze.getWidth();
        int h = maze.getHeight();
        int n = COLUMN_MIN + r.nextInt(COLUMN_MAX - COLUMN_MIN + 1);
        for (int i = 0; i < n; i++) {
            int depth = 1 + i / 2;
            int lateral = (dx != 0 ? h : w) / 2 + (i % 2 == 0 ? 0 : 1);
            int x = dx > 0 ? depth : dx < 0 ? w - 1 - depth : lateral;
            int y = dy > 0 ? depth : dy < 0 ? h - 1 - depth : lateral;
            GridPoint2 at = WorldManager.findSafeArrivalTile(maze, x, y);
            Monster m = enlist(s.a, maze, recruiter, world, e.houseA, at, r);
            if (m == null) continue;
            int ex = dx > 0 ? w - 2 : dx < 0 ? 1 : lateral;
            int ey = dy > 0 ? h - 2 : dy < 0 ? 1 : lateral;
            m.setMarchTarget(new GridPoint2(ex, ey));
            m.setState(Monster.MonsterState.WANDERING);
        }
        if (s.a.isEmpty()) return null;
        s.a.get(0).setDisplayName("Standard-bearer of " + name(world, e.houseA));
        s.startA = s.a.size();
        t.messages.add("Drums. A column of " + name(world, e.houseA) + " marches through, banners high.");
        t.cues.add(Cue.DRUMS);
        return s;
    }

    private Stage raid(Encounter e, HistoryWorld world, Maze maze, BattleDirector.Recruiter recruiter, Props props,
            EncounterLedger ledger, Turn t) {
        Random r = rng(world, e, 3);
        Stage s = new Stage(e, Phase.HOLD);
        GridPoint2 centre = WorldManager.findSafeArrivalTile(maze, maze.getWidth() / 2 + r.nextInt(9) - 4,
                maze.getHeight() / 2 + r.nextInt(9) - 4);
        if (centre == null) return null;
        EncounterLedger.Dressing fires = dressing(AFTERMATH, ledger);
        fires.clearAtSleep = ledger.sleeps + AFTERMATH_SLEEPS;
        // They burn what stands here -- trees, stores, shrines -- and light the rest of the ground.
        int burnt = 0;
        for (GridPoint2 at : burnable(maze, centre, RAID_REACH)) {
            if (burnt >= RAID_BURNS) break;
            maze.removeScenery(at.x, at.y);
            if (place(maze, props, "campfire", at, fires)) {
                maze.addBlood(at.x, at.y, 0.2f);
                burnt++;
            }
        }
        for (int i = burnt; i < RAID_BURNS; i++) place(maze, props, "campfire", around(maze, centre, 3, r), fires);
        keep(ledger, fires);
        int n = RAIDERS_MIN + r.nextInt(RAIDERS_MAX - RAIDERS_MIN + 1);
        for (int i = 0; i < n; i++) {
            Monster m = enlist(s.a, maze, recruiter, world, e.houseA, around(maze, centre, 4, r), r);
            if (m != null) m.setState(Monster.MonsterState.WANDERING);
        }
        if (s.a.isEmpty()) return null;
        s.startA = s.a.size();
        t.messages.add(e.houseB >= 0
                ? "Smoke and shouting: raiders of " + name(world, e.houseA) + " are burning the ground of " + name(world, e.houseB) + "."
                : "Smoke and shouting: raiders of " + name(world, e.houseA) + " are burning the ground here.");
        t.cues.add(Cue.FIRE);
        return s;
    }

    private Stage camp(Encounter c, HistoryWorld world, Maze maze, BattleDirector.Recruiter recruiter, Props props,
            EncounterLedger ledger, Turn t) {
        Random r = rng(world, c, 4);
        Stage s = new Stage(c, Phase.HOLD);
        EncounterLedger.Dressing camp = campDressing(ledger, c);
        GridPoint2 centre;
        if (camp == null) {
            centre = WorldManager.findSafeArrivalTile(maze, maze.getWidth() / 2, maze.getHeight() / 2);
            if (centre == null) return s;
            camp = dressing(CAMP, ledger);
            camp.warId = c.warId;
            camp.houseId = c.houseA;
            place(maze, props, "campfire", centre, camp);
            place(maze, props, "war_banner", around(maze, centre, 2, r), camp);
            for (int i = 0; i < 3; i++) place(maze, props, "camp_tent", around(maze, centre, 5, r), camp);
            for (int i = 0; i < 2; i++) place(maze, props, "brazier", around(maze, centre, 4, r), camp);
            keep(ledger, camp);
        } else {
            centre = new GridPoint2(camp.tiles.get(0), camp.tiles.get(1));
        }
        for (int i = 0; i < SENTRIES + 1; i++) {
            Monster m = enlist(s.a, maze, recruiter, world, c.houseA, around(maze, centre, 4, r), r);
            if (m != null) m.setState(Monster.MonsterState.IDLE);
        }
        if (!s.a.isEmpty()) captain(s.a.get(0), world, c.houseA);
        t.messages.add("A war camp of " + name(world, c.houseA) + ": tents, fires, and sentries watching the road.");
        return s;
    }

    // ------------------------------------------------------------------ turns

    private void tick(Stage s, HistoryManager history, Maze maze, GridPoint2 player, long clock, Props props,
            BattleDirector.Spoils spoils, Turn t) {
        HistoryWorld world = history.world();
        EncounterLedger ledger = history.encounterLedger();
        switch (s.phase) {
            case FIGHT: {
                s.turns++;
                int aliveA = standing(maze, s.a);
                int aliveB = standing(maze, s.b);
                boolean aBroke = aliveA <= Math.ceil(s.startA * ROUT_AT);
                boolean bBroke = aliveB <= Math.ceil(s.startB * ROUT_AT);
                if (!aBroke && !bBroke && s.turns < FIGHT_CAP && s.e.activeAt(clock)) return;
                float shareA = aliveA / (float) s.startA;
                float shareB = aliveB / (float) s.startB;
                boolean aWins = (aBroke != bBroke) ? bBroke : shareA >= shareB;
                s.winner = aWins ? s.e.houseA : s.e.houseB;
                int loser = aWins ? s.e.houseB : s.e.houseA;
                for (Monster m : aWins ? s.b : s.a) flee(maze, m, player);
                t.messages.add("The war-band of " + name(world, loser) + " breaks and runs. " + name(world, s.winner)
                        + " holds the ground.");
                t.cues.add(Cue.ROUT);
                if (joined(s)) {
                    history.recordSkirmish(s.winner, loser, fallenCaptain(s));
                    if (spoils != null) spoils.drop(maze, centroid(s, player), loser, false);
                }
                lay(maze, props, ledger, centroid(s, player), rng(world, s.e, 5));
                ledger.spend(s.e);
                s.phase = Phase.LINGER;
                return;
            }
            case LINGER:
                if (--s.linger > 0) return;
                for (Monster m : s.winner == s.e.houseA ? s.a : s.b) flee(maze, m, player);
                t.messages.add(name(world, s.winner) + " marches on. The field is yours.");
                s.phase = Phase.DONE;
                return;
            case MARCH: {
                int left = 0;
                for (Monster m : s.a) {
                    if (!m.isAlive() || !maze.getMonsters().containsValue(m)) continue;
                    GridPoint2 to = m.getMarchTarget();
                    if (to != null && Math.abs(m.getPosition().x - to.x) + Math.abs(m.getPosition().y - to.y) <= 1) {
                        maze.removeMonster(m);
                    } else {
                        left++;
                    }
                }
                if (left > 0) return;
                boolean destroyed = true;
                for (Monster m : s.a) if (m.isAlive()) destroyed = false;
                if (destroyed) {
                    ledger.spend(s.e);
                    t.messages.add("The column of " + name(world, s.e.houseA) + " is broken. Its banners lie in the mud.");
                }
                s.phase = Phase.DONE;
                return;
            }
            case HOLD:
                if (s.e.kind == Encounter.Kind.CAMP) {
                    // A camp whose sentries the player has cut down stays unguarded (W3).
                    if (!s.a.isEmpty() && standing(maze, s.a) == 0) {
                        ledger.spend(s.e);
                        t.messages.add("The camp of " + name(world, s.e.houseA) + " stands empty.");
                        s.phase = Phase.DONE;
                    }
                    return;
                }
                if (s.e.kind != Encounter.Kind.RAID) return;
                if (standing(maze, s.a) == 0) {
                    ledger.spend(s.e);
                    t.messages.add("The raiders of " + name(world, s.e.houseA) + " are dead. Their fires burn on.");
                    s.phase = Phase.DONE;
                } else if (!s.e.activeAt(clock)) {
                    for (Monster m : s.a) flee(maze, m, player);
                    ledger.spend(s.e);
                    t.messages.add("The raiders of " + name(world, s.e.houseA) + " move off, leaving the ground burning.");
                    s.phase = Phase.DONE;
                }
                return;
            default:
                return;
        }
    }

    /** Whether the player drew blood in the fight: then it is theirs, and the history hears of it (W12). */
    private static boolean joined(Stage s) {
        for (Monster m : s.a) if (m.seekerDrewBlood()) return true;
        for (Monster m : s.b) if (m.seekerDrewBlood()) return true;
        return false;
    }

    private static int fallenCaptain(Stage s) {
        for (Monster c : new Monster[]{s.captainA, s.captainB}) {
            if (c != null && !c.isAlive() && c.getFigureId() >= 0) return c.getFigureId();
        }
        return -1;
    }

    // ------------------------------------------------------------------ the ground the war leaves

    /** A battlefield: the dead and the broken, and blood, for three sleeps (W2). */
    private void lay(Maze maze, Props props, EncounterLedger ledger, GridPoint2 centre, Random r) {
        EncounterLedger.Dressing field = dressing(AFTERMATH, ledger);
        field.clearAtSleep = ledger.sleeps + AFTERMATH_SLEEPS;
        int n = 3 + r.nextInt(4);
        for (int i = 0; i < n; i++) {
            GridPoint2 at = around(maze, centre, 4, r);
            if (place(maze, props, FIELD[r.nextInt(FIELD.length)], at, field)) maze.addBlood(at.x, at.y, 0.6f);
        }
        keep(ledger, field);
    }

    /**
     * On entering a chunk: a battlefield past its sleeps is cleared, and a camp whose war is over
     * (or which no longer stands here) is struck.
     */
    private void reconcile(HistoryManager history, Maze maze, List<Encounter> camps) {
        EncounterLedger ledger = history.encounterLedger();
        for (Iterator<EncounterLedger.Dressing> it = ledger.dressings.iterator(); it.hasNext(); ) {
            EncounterLedger.Dressing d = it.next();
            if (d.chunkX != chunk.x || d.chunkY != chunk.y) continue;
            boolean gone;
            if (AFTERMATH.equals(d.kind)) {
                gone = ledger.sleeps >= d.clearAtSleep;
            } else {
                gone = true;
                for (Encounter c : camps) {
                    if (c.warId == d.warId && c.houseA == d.houseId && c.from.equals(chunk)) gone = false;
                }
            }
            if (!gone) continue;
            for (int i = 0; i < d.props.size(); i++) {
                int x = d.tiles.get(2 * i);
                int y = d.tiles.get(2 * i + 1);
                Scenery s = maze.getScenery().get(new GridPoint2(x, y));
                if (s != null && d.props.get(i).equals(s.getPropId())) maze.removeScenery(x, y);
            }
            it.remove();
        }
    }

    /** What stands near {@code centre} that a raid can burn, nearest first. */
    private static List<GridPoint2> burnable(Maze maze, GridPoint2 centre, int reach) {
        List<GridPoint2> out = new ArrayList<>();
        for (Map.Entry<GridPoint2, Scenery> e : maze.getScenery().entrySet()) {
            Scenery s = e.getValue();
            GridPoint2 at = e.getKey();
            if (s == null || s.isTownsfolk() || s.isCorpse() || UNBURNABLE.contains(s.getPropId())) continue;
            if (Math.abs(at.x - centre.x) > reach || Math.abs(at.y - centre.y) > reach) continue;
            if (maze.getGates().containsKey(at) || maze.getMonsters().containsKey(at)) continue;
            out.add(at);
        }
        out.sort(java.util.Comparator.comparingInt(a -> Math.abs(a.x - centre.x) + Math.abs(a.y - centre.y)));
        return out;
    }

    private EncounterLedger.Dressing dressing(String kind, EncounterLedger ledger) {
        EncounterLedger.Dressing d = new EncounterLedger.Dressing();
        d.kind = kind;
        d.chunkX = chunk.x;
        d.chunkY = chunk.y;
        return d;
    }

    private static void keep(EncounterLedger ledger, EncounterLedger.Dressing d) {
        if (!d.props.isEmpty()) ledger.dressings.add(d);
    }

    private EncounterLedger.Dressing campDressing(EncounterLedger ledger, Encounter c) {
        for (EncounterLedger.Dressing d : ledger.dressings) {
            if (CAMP.equals(d.kind) && d.chunkX == chunk.x && d.chunkY == chunk.y && d.warId == c.warId && d.houseId == c.houseA) {
                return d;
            }
        }
        return null;
    }

    private static boolean place(Maze maze, Props props, String propId, GridPoint2 at, EncounterLedger.Dressing d) {
        if (at == null || props == null) return false;
        if (maze.getScenery().containsKey(at) || maze.getItems().containsKey(at) || maze.getMonsters().containsKey(at)) return false;
        Scenery s = props.make(propId, at.x, at.y);
        if (s == null) return false;
        maze.addScenery(s);
        d.tiles.add(at.x);
        d.tiles.add(at.y);
        d.props.add(propId);
        return true;
    }

    // ------------------------------------------------------------------ soldiers

    private Monster enlist(List<Monster> side, Maze maze, BattleDirector.Recruiter recruiter, HistoryWorld world,
            int houseId, GridPoint2 at, Random r) {
        House house = world.house(houseId);
        if (at == null || house == null || recruiter == null) return null;
        Doctrine d = catalog.get(house.doctrineId);
        if (d == null || d.roster.isEmpty()) return null;
        Monster m = recruiter.recruit(d.roster.get(r.nextInt(d.roster.size())), at.x, at.y);
        if (m == null) return null;
        m.setFaction(Faction.MAZE_HOUSE);
        m.setHouseId(houseId);
        m.setWarBand(true);
        m.setState(Monster.MonsterState.HUNTING);
        maze.addMonster(m);
        side.add(m);
        return m;
    }

    /** A band's captain: one of the house's sworn swords if it has one, twice as hard to kill. */
    private static Monster captain(Monster m, HistoryWorld world, int houseId) {
        House house = world.house(houseId);
        Figure leader = null;
        if (house != null) {
            for (Figure f : world.swornSwords(house)) {
                if (f.hostageOf < 0) {
                    leader = f;
                    break;
                }
            }
        }
        if (leader != null) {
            m.setFigureId(leader.id);
            m.setDisplayName("Captain " + leader.name + " of " + house.name);
        } else {
            m.setDisplayName("A captain of " + name(world, houseId));
        }
        m.setMaxHP(m.getMaxHP() * 2);
        m.setCurrentHP(m.getMaxHP());
        return m;
    }

    /** A soldier leaves the field, unless it is at the player's throat. */
    private static void flee(Maze maze, Monster m, GridPoint2 player) {
        if (!m.isAlive()) return;
        if (player != null && Math.abs(m.getPosition().x - player.x) + Math.abs(m.getPosition().y - player.y) <= 1) return;
        maze.removeMonster(m);
    }

    private static int standing(Maze maze, List<Monster> side) {
        int n = 0;
        for (Monster m : side) if (m.isAlive() && maze.getMonsters().containsValue(m)) n++;
        return n;
    }

    private static GridPoint2 centroid(Stage s, GridPoint2 fallback) {
        float x = 0f, y = 0f;
        int n = 0;
        for (List<Monster> side : java.util.Arrays.asList(s.a, s.b)) {
            for (Monster m : side) {
                x += m.getPosition().x;
                y += m.getPosition().y;
                n++;
            }
        }
        return n == 0 ? fallback : new GridPoint2(Math.round(x / n), Math.round(y / n));
    }

    private static GridPoint2 around(Maze maze, GridPoint2 c, int radius, Random r) {
        if (c == null) return null;
        for (int attempt = 0; attempt < 10; attempt++) {
            int x = Math.max(1, Math.min(maze.getWidth() - 2, c.x + r.nextInt(2 * radius + 1) - radius));
            int y = Math.max(1, Math.min(maze.getHeight() - 2, c.y + r.nextInt(2 * radius + 1) - radius));
            GridPoint2 at = WorldManager.findSafeArrivalTile(maze, x, y);
            if (at != null && !maze.getMonsters().containsKey(at)) return at;
        }
        return null;
    }

    /** A free tile near the given edge, a few tiles deep: 0 west, 1 east, 2 south, 3 north. */
    private static GridPoint2 edgeTile(Maze maze, int edge, Random r) {
        int w = maze.getWidth();
        int h = maze.getHeight();
        for (int attempt = 0; attempt < 12; attempt++) {
            int depth = 1 + r.nextInt(4);
            int along = 2 + r.nextInt(Math.max(1, (edge < 2 ? h : w) - 4));
            int x, y;
            switch (edge) {
                case 0: x = depth; y = along; break;
                case 1: x = w - 1 - depth; y = along; break;
                case 2: x = along; y = depth; break;
                default: x = along; y = h - 1 - depth; break;
            }
            GridPoint2 at = WorldManager.findSafeArrivalTile(maze, x, y);
            if (at != null && !maze.getMonsters().containsKey(at)) return at;
        }
        return null;
    }

    private static String name(HistoryWorld world, int houseId) {
        House h = world.house(houseId);
        return h == null ? "a house of the Maze" : h.name;
    }

    private static Random rng(HistoryWorld world, Encounter e, int stream) {
        return new Random(world.seed ^ (e.slot * 0x9E3779B97F4A7C15L) ^ (e.houseA * 31L + e.warId) ^ (stream * 0x632BE59BD9B4E019L));
    }

    // ------------------------------------------------------------------ for tests

    int stagedCount() {
        return stages.size();
    }

    /** The war in {@code warId}, if it is still being fought. */
    static boolean warOn(HistoryWorld world, int warId) {
        for (War w : world.activeWars()) if (w.id == warId) return true;
        return false;
    }
}
