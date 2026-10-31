package com.bpm.minotaur.gamedata.history.war;

import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.gamedata.history.war.army.Army;
import com.bpm.minotaur.gamedata.history.war.army.ArmyBattle;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.IntFunction;

/**
 * Every real thing in the war that makes a sound or raises smoke (Living War A53-A57). A front makes
 * neither, so with nothing here the player hears and sees no war at all. Pure: {@link WarSoundscape},
 * the sky and the beacons all read this one list.
 */
public final class WarSources {

    public enum Kind {
        /** A battle, assault or sally. */
        BATTLE,
        /** A host of ten or more on the march. */
        MARCH,
        /** Fewer than that on the march. */
        PARTY,
        /** A camp or halted host. */
        CAMP,
        /** An army sitting around an enemy seat. */
        INVESTED,
        /** The siege of Castle Tarmin. */
        CASTLE_SIEGE,
        /** A raid burning the land it stands on. */
        BURNING,
        /** Burnt ground a battle or raid left behind. */
        AFTERMATH
    }

    /** How large a plume stands (A54). */
    public enum Plume {
        BATTLE, SIEGE, MEDIUM, WISP
    }

    public static final class Source {
        public final Kind kind;
        public final int id;
        public final int x;
        public final int y;
        /** Who is fighting, for a spell's colour in the sky; -1 if nobody. */
        public final int houseA;
        public final int houseB;
        /** How much is left of it, 0 to 1: burnt ground thins with the sleeps. */
        public final float strength;

        Source(Kind kind, int id, int x, int y, int houseA, int houseB, float strength) {
            this.kind = kind;
            this.id = id;
            this.x = x;
            this.y = y;
            this.houseA = houseA;
            this.houseB = houseB;
            this.strength = strength;
        }

        public int distance(GridPoint2 from) {
            return Encounter.distance(from, new GridPoint2(x, y));
        }
    }

    /** A plume on the player's sky, at a chunk's bearing. */
    public static final class Smoke {
        public final int x;
        public final int y;
        public final Plume plume;
        public final float strength;

        Smoke(int x, int y, Plume plume, float strength) {
            this.x = x;
            this.y = y;
            this.plume = plume;
            this.strength = strength;
        }
    }

    /** A chain of beacon fires from a seat (A57), of which the first {@code lit} burn. */
    public static final class Beacons {
        public final GridPoint2 from;
        public final GridPoint2 to;
        public final int lit;

        Beacons(GridPoint2 from, GridPoint2 to, int lit) {
            this.from = from;
            this.to = to;
            this.lit = lit;
        }
    }

    public static final int SMOKE_RANGE = 24;
    public static final int SMOKE_MAX = 12;
    /** A cookfire's wisp is seen no farther than this. */
    static final int WISP_RANGE = 8;
    /** A host this large is heard coming as a host. */
    static final int MARCH_SIZE = 10;
    /** Burnt ground thins over this many sleeps. */
    static final float THINS_OVER_SLEEPS = 3f;
    public static final int BEACON_CHAIN = 4;
    /** Turns between one beacon catching and the next. */
    static final int BEACON_TURNS = 3;
    /** Turns a muster's beacons burn. */
    static final int MUSTER_BEACON_TURNS = 60;

    private final List<Source> sources = new ArrayList<>();
    private final List<Beacons> beacons = new ArrayList<>();
    private GridPoint2 smokeFrom;
    private List<Smoke> smokeSeen;

    /** Nothing at all: no war heard, no smoke seen. */
    public static WarSources none() {
        return new WarSources();
    }

    /**
     * What the armies are doing now.
     *
     * @param castleSiege where the castle is besieged, or null if it is not
     * @param castleCamps the besieger's camps around it; may be empty
     * @param ledger for what battles and raids have left behind
     * @param seatOf where a house sits, or null
     */
    public static WarSources of(List<Army> armies, List<ArmyBattle> battles, GridPoint2 castleSiege, List<GridPoint2> castleCamps,
            EncounterLedger ledger, IntFunction<GridPoint2> seatOf, long clock) {
        WarSources out = new WarSources();
        for (int i = 0; i < battles.size(); i++) {
            ArmyBattle b = battles.get(i);
            out.sources.add(new Source(Kind.BATTLE, b.id, b.x, b.y, b.houseA, b.houseB, 1f));
        }
        for (int i = 0; i < armies.size(); i++) {
            Army a = armies.get(i);
            if (a.soldiers <= 0 || a.battle) continue;
            int house = a.houseId;
            if (!a.halted()) {
                out.sources.add(new Source(a.soldiers >= MARCH_SIZE ? Kind.MARCH : Kind.PARTY, a.id, a.x, a.y, house, -1, 1f));
            } else if (a.kind == Army.Kind.RAID) {
                out.sources.add(new Source(Kind.BURNING, a.id, a.x, a.y, house, -1, 1f));
            } else if (a.sieging >= 0 || a.beforeTheCastle) {
                out.sources.add(new Source(Kind.INVESTED, a.id, a.x, a.y, house, -1, 1f));
            } else if (a.isHost()) {
                out.sources.add(new Source(Kind.CAMP, a.id, a.x, a.y, house, -1, 1f));
            }
        }
        if (castleSiege != null) {
            out.sources.add(new Source(Kind.CASTLE_SIEGE, -1, castleSiege.x, castleSiege.y, -1, -1, 1f));
            for (GridPoint2 camp : castleCamps) out.sources.add(new Source(Kind.CAMP, -1, camp.x, camp.y, -1, -1, 1f));
        }
        if (ledger != null) {
            for (EncounterLedger.Field f : ledger.fields) {
                out.sources.add(new Source(Kind.AFTERMATH, -1, f.chunkX, f.chunkY, -1, -1, left(f.clearAtSleep, ledger.sleeps)));
            }
            for (EncounterLedger.Dressing d : ledger.dressings) {
                if ("AFTERMATH".equals(d.kind) && d.level == 1 && !d.trail) {
                    out.sources.add(new Source(Kind.AFTERMATH, -1, d.chunkX, d.chunkY, -1, -1, left(d.clearAtSleep, ledger.sleeps)));
                }
            }
        }
        if (seatOf != null) out.lightBeacons(armies, seatOf, clock);
        return out;
    }

    private static float left(int clearAtSleep, int sleeps) {
        return Math.max(0f, Math.min(1f, (clearAtSleep - sleeps) / THINS_OVER_SLEEPS));
    }

    /** A seat that musters a host, toward its objective; a seat invested, toward its lord's nearest other host (A57). */
    private void lightBeacons(List<Army> armies, IntFunction<GridPoint2> seatOf, long clock) {
        for (int i = 0; i < armies.size(); i++) {
            Army a = armies.get(i);
            if (a.soldiers <= 0) continue;
            if (a.musteredAt >= 0 && clock - a.musteredAt <= MUSTER_BEACON_TURNS && a.isHost()) {
                GridPoint2 seat = seatOf.apply(a.houseId);
                // A host raised to guard its own seat has nowhere to send word.
                if (seat != null && (a.targetX != seat.x || a.targetY != seat.y)) {
                    int lit = (int) Math.min(BEACON_CHAIN, (clock - a.musteredAt) / BEACON_TURNS + 1);
                    beacons.add(new Beacons(seat, new GridPoint2(a.targetX, a.targetY), lit));
                }
            }
            if (a.sieging >= 0) callForRelief(armies, seatOf, a);
        }
    }

    private void callForRelief(List<Army> armies, IntFunction<GridPoint2> seatOf, Army besieger) {
        GridPoint2 seat = new GridPoint2(besieger.x, besieger.y);
        Army nearest = null;
        int best = Integer.MAX_VALUE;
        for (int i = 0; i < armies.size(); i++) {
            Army o = armies.get(i);
            if (o.soldiers <= 0 || !o.isHost() || o.houseId == besieger.houseId || !seat.equals(seatOf.apply(o.houseId))) continue;
            int d = Encounter.distance(seat, new GridPoint2(o.x, o.y));
            if (d > 0 && d < best) {
                best = d;
                nearest = o;
            }
        }
        if (nearest != null) beacons.add(new Beacons(seat, new GridPoint2(nearest.x, nearest.y), BEACON_CHAIN));
    }

    public List<Source> all() {
        return sources;
    }

    public List<Beacons> beacons() {
        return beacons;
    }

    /** For a staged reading, and for tests. */
    public WarSources add(Kind kind, int x, int y) {
        smokeFrom = null;
        sources.add(new Source(kind, sources.size(), x, y, -1, -1, 1f));
        return this;
    }

    /**
     * The plumes the player can see from {@code player} (A54): nothing on the march raises smoke, and
     * nothing in their own chunk stands on the horizon. If more than {@link #SMOKE_MAX} could be seen,
     * the tallest and nearest are kept.
     */
    public List<Smoke> smoke(GridPoint2 player) {
        // The sky asks every frame; the answer holds until the player or the turn moves on.
        if (player.equals(smokeFrom)) return smokeSeen;
        List<Smoke> out = new ArrayList<>();
        for (int i = 0; i < sources.size(); i++) {
            Source s = sources.get(i);
            Plume plume = plumeOf(s.kind);
            if (plume == null || s.strength <= 0f) continue;
            int d = s.distance(player);
            if (d == 0 || d > (plume == Plume.WISP ? WISP_RANGE : SMOKE_RANGE)) continue;
            out.add(new Smoke(s.x, s.y, plume, s.strength));
        }
        out.sort(Comparator.<Smoke>comparingInt(m -> m.plume.ordinal() >= Plume.MEDIUM.ordinal() ? m.plume.ordinal() : 0)
                .thenComparingInt(m -> Encounter.distance(player, new GridPoint2(m.x, m.y))));
        smokeFrom = new GridPoint2(player);
        smokeSeen = out.size() > SMOKE_MAX ? new ArrayList<>(out.subList(0, SMOKE_MAX)) : out;
        return smokeSeen;
    }

    private static Plume plumeOf(Kind kind) {
        switch (kind) {
            case BATTLE: return Plume.BATTLE;
            case INVESTED:
            case CASTLE_SIEGE: return Plume.SIEGE;
            case BURNING:
            case AFTERMATH: return Plume.MEDIUM;
            case CAMP: return Plume.WISP;
            default: return null;
        }
    }

    /** A gash shakes only while its house's seat is invested or assaulted, or a battle is fought over it (A56). */
    public boolean gashShakes(GridPoint2 seat, GridPoint2 gash) {
        for (int i = 0; i < sources.size(); i++) {
            Source s = sources.get(i);
            if (s.kind == Kind.BATTLE && (at(s, seat) || at(s, gash))) return true;
            if (s.kind == Kind.INVESTED && at(s, seat)) return true;
        }
        return false;
    }

    private static boolean at(Source s, GridPoint2 chunk) {
        return chunk != null && s.x == chunk.x && s.y == chunk.y;
    }
}
