package com.bpm.minotaur.gamedata.history.war;

import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.gamedata.history.Grudge;
import com.bpm.minotaur.gamedata.history.House;
import com.bpm.minotaur.gamedata.history.HistoryWorld;
import com.bpm.minotaur.gamedata.history.War;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Where the surface war is right now (Living War W2-W8). Time is cut into slots of about
 * {@link #SLOT} war-clock turns; each slot brings one encounter the player can walk into --
 * a skirmish, a column, a raid, or once an expedition a pitched battle -- and one fight out of
 * sight, heard and seen as smoke. A slot is anchored on the player's chunk the turn it begins
 * (the bias of W5) and the anchor is saved; everything else follows from the seed, the slot and
 * the history, so a reload sees the same war.
 */
public final class EncounterScheduler {

    /**
     * Turns between encounters: each slot starts up to {@link #JITTER} either side of its mark, so
     * two in a row are 80 to 120 turns apart (W4).
     */
    public static final int SLOT = 100;
    static final int JITTER = 10;
    public static final int SKIRMISH_TURNS = 60;
    public static final int RAID_TURNS = 60;
    public static final int BATTLE_TURNS = 150;
    /** Turns a column takes to cross one chunk; it crosses {@code 2 * COLUMN_REACH + 1} of them. */
    public static final int COLUMN_CHUNK_TURNS = 20;
    static final int COLUMN_REACH = 2;
    /** A distant fight outlasts its slot, so one is always in earshot while a war is on (W4). */
    public static final int DISTANT_TURNS = SLOT + 2 * JITTER + 40;
    /** The first skirmish comes this many turns after the player first walks the surface (W8). */
    static final int FIRST_DELAY_MIN = 40;
    static final int FIRST_DELAY_MAX = 100;
    public static final int FIRST_TURNS = 80;
    /** Visible encounters an expedition sees before its pitched battle comes (W4). */
    static final int BATTLE_AFTER_SLOTS = 3;
    /** Chunks a fight is heard across (W9). */
    public static final int EARSHOT = 6;
    static final int DISTANT_MIN = 4;
    static final int DISTANT_MAX = 5;
    /** Chance a peacetime slot brings a border raid (W7), where some house bears a grudge. */
    static final float PEACE_RAID = 0.5f;

    private static final long SALT = 0x3A7C0FFEEL;

    private EncounterScheduler() {
    }

    /** The turn slot {@code k} begins. */
    public static long slotStart(long seed, long k) {
        return k * SLOT + rng(seed, k, 1).nextInt(2 * JITTER + 1) - JITTER;
    }

    /**
     * Every encounter under way at {@code clock}. Not a pure query: it anchors the slots that begin now on
     * {@code player} (null when the player's chunk is unknown) and records them in the ledger.
     * {@code surface}: the player walks the overland, where encounters can reach them.
     */
    public static List<Encounter> at(HistoryWorld world, long clock, EncounterLedger ledger, GridPoint2 player,
            boolean surface) {
        return at(world, clock, ledger, player, surface, null);
    }

    /** As {@link #at(HistoryWorld, long, EncounterLedger, GridPoint2, boolean)}; {@code seats}, when known, send columns toward their front. */
    public static List<Encounter> at(HistoryWorld world, long clock, EncounterLedger ledger, GridPoint2 player,
            boolean surface, SeatMap seats) {
        List<Encounter> out = new ArrayList<>();
        if (world == null || ledger == null) return out;
        first(world, clock, ledger, player, surface, out);
        long longest = Math.max(DISTANT_TURNS, (2L * COLUMN_REACH + 1) * COLUMN_CHUNK_TURNS);
        long firstSlot = Math.floorDiv(clock - longest - JITTER, SLOT);
        long lastSlot = Math.floorDiv(clock + JITTER, SLOT);
        for (long k = Math.max(0, firstSlot); k <= lastSlot; k++) {
            long start = slotStart(world.seed, k);
            if (start > clock) continue;
            EncounterLedger.Anchor a = ledger.anchor(k);
            if (a == null) {
                // Anchored only as it begins; a slot that ran its course unwatched is simply gone.
                if (clock >= start + DISTANT_TURNS || player == null) continue;
                Encounter.Kind kind = surface ? choose(world, ledger, k) : null;
                if (kind == Encounter.Kind.BATTLE) ledger.battleBrought = true;
                if (kind != null) ledger.expeditionSlots++;
                a = ledger.setAnchor(k, player.x, player.y, kind);
            }
            GridPoint2 origin = new GridPoint2(a.x, a.y);
            if (a.kind != null) {
                Encounter e = build(world, k, start, Encounter.Kind.valueOf(a.kind), origin, seats);
                if (e != null && e.activeAt(clock) && !ledger.isSpent(e)) out.add(e);
            }
            Encounter d = distant(world, k, start, player != null ? player : origin);
            if (d != null && d.activeAt(clock)) out.add(d);
        }
        return out;
    }

    /** The guaranteed first skirmish (W8): next to the player, within 150 turns of their first step outside. */
    private static void first(HistoryWorld world, long clock, EncounterLedger ledger, GridPoint2 player, boolean surface,
            List<Encounter> out) {
        if (ledger.firstDone) return;
        if (ledger.firstAt < 0) {
            if (!surface || player == null) return;
            ledger.firstAt = clock + FIRST_DELAY_MIN + rng(world.seed, -1, 2).nextInt(FIRST_DELAY_MAX - FIRST_DELAY_MIN + 1);
        }
        if (clock < ledger.firstAt) return;
        if (!ledger.firstAnchored) {
            // Underground, or no war to fight: it waits for the next slot's worth of turns.
            if (!surface || player == null || world.activeWars().isEmpty()) {
                ledger.firstAt = clock + SLOT;
                return;
            }
            ledger.firstX = player.x;
            ledger.firstY = player.y;
            ledger.firstAnchored = true;
        }
        long end = ledger.firstAt + FIRST_TURNS;
        if (clock >= end) {
            ledger.firstDone = true;
            return;
        }
        Random r = rng(world.seed, -1, 3);
        War w = pickWar(world, r);
        if (w == null) return;
        GridPoint2 at = ring(new GridPoint2(ledger.firstX, ledger.firstY), 1, 1, r);
        Encounter e = new Encounter(Encounter.Kind.SKIRMISH, -1, w.id, w.attackerId, w.defenderId, ledger.firstAt, end, at, at);
        if (!ledger.isSpent(e)) out.add(e);
    }

    /** What slot {@code k} brings the player, when it begins with them on the surface. */
    private static Encounter.Kind choose(HistoryWorld world, EncounterLedger ledger, long k) {
        Random r = rng(world.seed, k, 4);
        if (world.activeWars().isEmpty()) {
            return !grudges(world).isEmpty() && r.nextFloat() < PEACE_RAID ? Encounter.Kind.RAID : null;
        }
        if (!ledger.battleBrought && ledger.expeditionSlots >= BATTLE_AFTER_SLOTS) return Encounter.Kind.BATTLE;
        int roll = r.nextInt(100);
        if (roll < 40) return Encounter.Kind.SKIRMISH;
        if (roll < 75) return Encounter.Kind.COLUMN;
        return Encounter.Kind.RAID;
    }

    private static Encounter build(HistoryWorld world, long k, long start, Encounter.Kind kind, GridPoint2 origin,
            SeatMap seats) {
        Random r = rng(world.seed, k, 5);
        War w = pickWar(world, r);
        switch (kind) {
            case SKIRMISH: {
                if (w == null) return null;
                GridPoint2 at = ring(origin, 1, 2, r);
                return new Encounter(kind, k, w.id, w.attackerId, w.defenderId, start, start + SKIRMISH_TURNS, at, at);
            }
            case BATTLE: {
                if (w == null) return null;
                GridPoint2 at = ring(origin, 1, 1, r);
                return new Encounter(kind, k, w.id, w.attackerId, w.defenderId, start, start + BATTLE_TURNS, at, at);
            }
            case COLUMN: {
                if (w == null) return null;
                int house = r.nextBoolean() ? w.attackerId : w.defenderId;
                int[] dir = DIRS[r.nextInt(DIRS.length)];
                // Bound for its war's front, where the seats are known (W2).
                GridPoint2 front = frontOf(world, seats, w, start);
                if (front != null && !front.equals(origin)) {
                    int fx = front.x - origin.x, fy = front.y - origin.y;
                    dir = Math.abs(fx) >= Math.abs(fy) ? new int[]{Integer.signum(fx), 0} : new int[]{0, Integer.signum(fy)};
                }
                int side = r.nextInt(3) - 1;
                GridPoint2 from = new GridPoint2(origin.x - dir[0] * COLUMN_REACH + dir[1] * side,
                        origin.y - dir[1] * COLUMN_REACH + dir[0] * side);
                GridPoint2 to = new GridPoint2(origin.x + dir[0] * COLUMN_REACH + dir[1] * side,
                        origin.y + dir[1] * COLUMN_REACH + dir[0] * side);
                long turns = (2L * COLUMN_REACH + 1) * COLUMN_CHUNK_TURNS;
                return new Encounter(kind, k, w.id, house, -1, start, start + turns, from, to);
            }
            case RAID: {
                GridPoint2 at = ring(origin, 1, 2, r);
                if (w != null) {
                    boolean flip = r.nextBoolean();
                    return new Encounter(kind, k, w.id, flip ? w.defenderId : w.attackerId,
                            flip ? w.attackerId : w.defenderId, start, start + RAID_TURNS, at, at);
                }
                List<Grudge> grudges = grudges(world);
                if (grudges.isEmpty()) return null;
                Grudge g = grudges.get(r.nextInt(grudges.size()));
                return new Encounter(kind, k, -1, g.holderId, g.againstId, start, start + RAID_TURNS, at, at);
            }
            default:
                return null;
        }
    }

    /**
     * A fight out of sight, a few chunks off, while any war is on. It keeps its bearing for the
     * slot but not its place: it is always four or five chunks from the player, so walking never
     * leaves the war out of earshot (W4), and it is never walked into.
     */
    private static Encounter distant(HistoryWorld world, long k, long start, GridPoint2 origin) {
        Random r = rng(world.seed, k, 6);
        War w = pickWar(world, r);
        if (w == null) return null;
        GridPoint2 at = ring(origin, DISTANT_MIN, DISTANT_MAX, r);
        return new Encounter(Encounter.Kind.DISTANT, k, w.id, w.attackerId, w.defenderId, start, start + DISTANT_TURNS, at, at);
    }

    /**
     * Each side's camp near every front (W2): behind the farthest the front sways toward its own
     * seat, so the lines rarely overrun it. Stands as long as the war does.
     */
    public static List<Encounter> camps(HistoryWorld world, SeatMap seats) {
        List<Encounter> out = new ArrayList<>();
        if (world == null || seats == null) return out;
        for (War w : world.activeWars()) {
            GridPoint2 a = seats.seat(w.attackerId);
            GridPoint2 b = seats.seat(w.defenderId);
            if (a == null || b == null || a.equals(b)) continue;
            double len = Math.max(1.0, Math.hypot(b.x - a.x, b.y - a.y));
            double gap = (Front.RADIUS + 1) / len;
            double ta = Math.max(0.0, 0.5 - FrontPlanner.SWAY - gap);
            double tb = Math.min(1.0, 0.5 + FrontPlanner.SWAY + gap);
            GridPoint2 ca = lerp(a, b, ta);
            GridPoint2 cb = lerp(a, b, tb);
            out.add(new Encounter(Encounter.Kind.CAMP, -2, w.id, w.attackerId, -1, 0, Long.MAX_VALUE, ca, ca));
            out.add(new Encounter(Encounter.Kind.CAMP, -2, w.id, w.defenderId, -1, 0, Long.MAX_VALUE, cb, cb));
        }
        return out;
    }

    private static GridPoint2 frontOf(HistoryWorld world, SeatMap seats, War w, long clock) {
        if (seats == null) return null;
        for (Front f : FrontPlanner.fronts(world, seats, clock)) if (f.warId == w.id) return f.center;
        return null;
    }

    /** Chebyshev distance in chunks: how far off a fight is heard from (W9). */
    public static int distance(GridPoint2 a, GridPoint2 b) {
        return Math.max(Math.abs(a.x - b.x), Math.abs(a.y - b.y));
    }

    // ------------------------------------------------------------------ helpers

    private static final int[][] DIRS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private static GridPoint2 lerp(GridPoint2 a, GridPoint2 b, double t) {
        return new GridPoint2((int) Math.round(a.x + (b.x - a.x) * t), (int) Math.round(a.y + (b.y - a.y) * t));
    }

    private static War pickWar(HistoryWorld world, Random r) {
        List<War> wars = world.activeWars();
        return wars.isEmpty() ? null : wars.get(r.nextInt(wars.size()));
    }

    /** Living grudges between houses not bound to each other: the cause of a border raid. */
    private static List<Grudge> grudges(HistoryWorld world) {
        List<Grudge> out = new ArrayList<>();
        for (Grudge g : world.grudges()) {
            House a = world.house(g.holderId);
            House b = world.house(g.againstId);
            if (a == null || b == null || a.isExtinct() || b.isExtinct() || g.weight < 1f) continue;
            if (world.stance(a.id, b.id) == HistoryWorld.Stance.NEUTRAL) out.add(g);
        }
        return out;
    }

    /** A chunk on the square ring between {@code min} and {@code max} chunks from {@code c}. */
    static GridPoint2 ring(GridPoint2 c, int min, int max, Random r) {
        int d = min + r.nextInt(max - min + 1);
        int along = r.nextInt(2 * d + 1) - d;
        switch (r.nextInt(4)) {
            case 0: return new GridPoint2(c.x + d, c.y + along);
            case 1: return new GridPoint2(c.x - d, c.y + along);
            case 2: return new GridPoint2(c.x + along, c.y + d);
            default: return new GridPoint2(c.x + along, c.y - d);
        }
    }

    private static Random rng(long seed, long k, int stream) {
        return new Random(seed ^ SALT ^ (k * 0x9E3779B97F4A7C15L) ^ (stream * 0xC2B2AE3D27D4EB4FL));
    }
}
