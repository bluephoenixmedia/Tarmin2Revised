package com.bpm.minotaur.gamedata.map;

import com.bpm.minotaur.gamedata.history.war.WarSoundscape;
import com.bpm.minotaur.gamedata.history.war.army.Army;
import com.bpm.minotaur.gamedata.history.war.army.ArmyBattle;
import com.bpm.minotaur.gamedata.history.war.army.WarRoster;
import com.bpm.minotaur.managers.HistoryManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What the player knows of where the armies are (Living War A46-A49): last-known sightings, rumours
 * and tracks, each of which only ages. It reads the war and writes nothing back; it is saved with
 * {@link MapKnowledge}, not with the history.
 */
public final class ArmyKnowledge {

    public enum Source {
        /** Seen, from the player's chunk or the next. */
        SIGHTED,
        /** Read from a trail. */
        TRACKS,
        /** A rough region from the War Table. */
        WAR_TABLE,
        /** A townsperson's word, of a place the host has been. */
        TOWN,
        /** The sworn player's own house, known as it is. */
        EXACT,
        /** A siege: public, and exact. */
        SIEGE
    }

    /** All a sighting can tell of numbers (A48). */
    public enum Size {
        BAND("A band"), COMPANY("A company"), HOST("A host"), GREAT_HOST("A great host");

        public final String words;

        Size(String words) {
            this.words = words;
        }

        public static Size of(int soldiers) {
            if (soldiers < 10) return BAND;
            if (soldiers <= 20) return COMPANY;
            if (soldiers <= 40) return HOST;
            return GREAT_HOST;
        }
    }

    /** What is known of one army. */
    public static final class Marker {
        public final int armyId;
        public final int houseId;
        public final int x;
        public final int y;
        /** The heading it had, each of -1, 0 or 1; both 0 if it was standing. */
        public final int dx;
        public final int dy;
        public final Size size;
        /** The captain, if their banner was seen; -1 otherwise. */
        public final int captainId;
        public final long observedAt;
        public final Source source;
        /** Only for what is known exactly: the count, or -1. */
        public final int exactSoldiers;
        /** Only for what is known exactly: the objective, or null. */
        public final Army.Objective objective;
        /** Where else it has been known to be, oldest first, as x, y pairs. */
        public final int[] past;
        /** For a siege: the house besieged, or -1. */
        public int besiegedHouseId = -1;

        Marker(int armyId, int houseId, int x, int y, int dx, int dy, Size size, int captainId, long observedAt, Source source,
                int exactSoldiers, Army.Objective objective, int[] past) {
            this.armyId = armyId;
            this.houseId = houseId;
            this.x = x;
            this.y = y;
            this.dx = dx;
            this.dy = dy;
            this.size = size;
            this.captainId = captainId;
            this.observedAt = observedAt;
            this.source = source;
            this.exactSoldiers = exactSoldiers;
            this.objective = objective;
            this.past = past;
        }

        /** Whether this is only what someone said. */
        public boolean rumour() {
            return source == Source.WAR_TABLE || source == Source.TOWN;
        }

        /** Whether this can never be out of date. */
        public boolean exact() {
            return source == Source.EXACT || source == Source.SIEGE;
        }

        public int sleepsAgo(long now) {
            return (int) (Math.max(0L, now - observedAt) / HistoryManager.SLEEP_TURNS);
        }

        /** How strongly it is drawn: dimming with age, and fainter still if only said. */
        public float strength(long now) {
            if (exact()) return 1f;
            float s = aged(now - observedAt);
            return rumour() ? s * RUMOUR_STRENGTH : s;
        }
    }

    /** A battle heard, marked only in its rough direction (A47). */
    public static final class Sound {
        final int battleId;
        public final int x;
        public final int y;
        public final long heardAt;

        Sound(int battleId, int x, int y, long heardAt) {
            this.battleId = battleId;
            this.x = x;
            this.y = y;
            this.heardAt = heardAt;
        }

        public float strength(long now) {
            return aged(now - heardAt);
        }
    }

    /** What a trail says, and the host it was left by. */
    public static final class Tracks {
        public final Marker marker;
        public final long ageTurns;

        Tracks(Marker marker, long ageTurns) {
            this.marker = marker;
            this.ageTurns = ageTurns;
        }
    }

    private static float aged(long turns) {
        float share = Math.min(1f, Math.max(0L, turns) / (float) HistoryManager.SEASON_TURNS);
        return 1f - share * (1f - MIN_STRENGTH);
    }

    /** How far off an army can be and still be seen: this chunk, or the next. */
    static final int SIGHT_CHUNKS = 1;
    /** The War Table speaks of regions this many chunks across. */
    static final int REGION_CHUNKS = 4;
    private static final float MIN_STRENGTH = 0.35f;
    private static final float RUMOUR_STRENGTH = 0.6f;
    /** A marker remembers this many places before it. */
    private static final int PAST_KEPT = 8;

    private final Map<Integer, Marker> markers = new LinkedHashMap<>();
    private final Map<Integer, Sound> sounds = new LinkedHashMap<>();

    // ---- Learning ----

    /**
     * The player has stood a turn on the surface at this chunk: every army in sight is marked, and
     * every battle in earshot.
     *
     * @return whether anything changed that is worth saving: not merely that a host is still there
     */
    public boolean onTurn(WarRoster roster, int px, int py, long clock) {
        boolean changed = forget(clock);
        for (Army a : roster.armies) {
            if (a.soldiers <= 0) continue;
            int off = Math.max(Math.abs(a.x - px), Math.abs(a.y - py));
            if (off > SIGHT_CHUNKS) continue;
            Marker before = markers.get(a.id);
            // A banner is seen across the field, not across a chunk's edge.
            int captain = off == 0 ? a.captainId : before != null ? before.captainId : -1;
            int[] heading = headingOf(a);
            learn(new Marker(a.id, a.houseId, a.x, a.y, heading[0], heading[1], Size.of(a.soldiers), captain, clock, Source.SIGHTED, -1, null, null));
            if (before == null || before.x != a.x || before.y != a.y || before.dx != heading[0] || before.dy != heading[1]
                    || before.captainId != captain || before.size != Size.of(a.soldiers) || before.source != Source.SIGHTED) {
                changed = true;
            }
        }
        for (ArmyBattle b : roster.battles) {
            int off = Math.max(Math.abs(b.x - px), Math.abs(b.y - py));
            if (off > WarSoundscape.EARSHOT || off == 0) continue;
            int rx = region(b.x);
            int ry = region(b.y);
            Sound was = sounds.put(b.id, new Sound(b.id, rx, ry, clock));
            if (was == null || was.x != rx || was.y != ry) changed = true;
        }
        return changed;
    }

    /** The mark of a host in sight of this chunk, if one is, for the first sighting's hint; null if none. */
    public Marker hostInSight(WarRoster roster, int px, int py) {
        for (Army a : roster.armies) {
            if (a.soldiers <= 0 || !a.isHost() || Math.max(Math.abs(a.x - px), Math.abs(a.y - py)) > SIGHT_CHUNKS) continue;
            Marker m = markers.get(a.id);
            if (m != null) return m;
        }
        return null;
    }

    /** A battle the player can hear from this chunk, for the first horns' hint; null if none. The ear is true where the map's mark is rough. */
    public static ArmyBattle battleInEarshot(WarRoster roster, int px, int py) {
        for (ArmyBattle b : roster.battles) {
            int off = Math.max(Math.abs(b.x - px), Math.abs(b.y - py));
            if (off > 0 && off <= WarSoundscape.EARSHOT) return b;
        }
        return null;
    }

    /** A sleep beside the War Table: each host's rough region and heading (A49). */
    public void hearWarTable(WarRoster roster, long clock) {
        forget(clock);
        for (Army a : roster.armies) {
            if (a.soldiers <= 0 || (a.kind != Army.Kind.HOST && a.kind != Army.Kind.LEVY)) continue;
            int[] heading = headingOf(a);
            learn(new Marker(a.id, a.houseId, region(a.x), region(a.y), heading[0], heading[1], Size.of(a.soldiers), -1, clock,
                    Source.WAR_TABLE, -1, null, null));
        }
    }

    /**
     * A townsperson's word of one host, of somewhere it has been this Season (A49).
     *
     * @param pick chooses the host, and how far back along its road
     * @return what they said, or null with no host to speak of
     */
    public Marker hearInTown(WarRoster roster, long clock, int pick) {
        List<Army> hosts = new ArrayList<>();
        for (Army a : roster.armies) {
            if (a.soldiers > 0 && (a.kind == Army.Kind.HOST || a.kind == Army.Kind.LEVY)) hosts.add(a);
        }
        if (hosts.isEmpty()) return null;
        Army a = hosts.get(Math.floorMod(pick, hosts.size()));
        int n = a.trail.length;
        int i = n == 0 ? -1 : Math.floorMod(pick / 7, n);
        int[] at = i < 0 ? new int[]{a.x, a.y} : new int[]{WarRoster.unpackedX(a.trail[i]), WarRoster.unpackedY(a.trail[i])};
        int[] next = i < 0 || i == n - 1 ? new int[]{a.x, a.y}
                : new int[]{WarRoster.unpackedX(a.trail[i + 1]), WarRoster.unpackedY(a.trail[i + 1])};
        long age = i < 0 ? 0L : trailAge(clock, n, i, false);
        Marker said = new Marker(a.id, a.houseId, at[0], at[1], Integer.signum(next[0] - at[0]), Integer.signum(next[1] - at[1]),
                Size.of(a.soldiers), -1, clock - age, Source.TOWN, -1, null, null);
        learn(said);
        return said;
    }

    /** The player stepped into this chunk: the newest trail through it says where it went, and refreshes that host's marker (A49). */
    public Tracks readTracks(WarRoster roster, int x, int y, long clock) {
        Tracks best = null;
        int packed = WarRoster.packedChunk(x, y);
        for (Army a : roster.armies) {
            // Hunters show only when seen.
            if (a.soldiers <= 0 || a.kind == Army.Kind.HUNTING_PARTY) continue;
            Tracks t = readTrail(a, a.trail, packed, x, y, clock, false);
            if (t == null) t = readTrail(a, a.trailLast, packed, x, y, clock, true);
            if (t != null && (best == null || t.ageTurns < best.ageTurns)) best = t;
        }
        if (best != null) learn(best.marker);
        return best;
    }

    private static Tracks readTrail(Army a, int[] trail, int packed, int x, int y, long clock, boolean lastSeason) {
        for (int i = trail.length - 1; i >= 0; i--) {
            if (trail[i] != packed) continue;
            int nx, ny;
            if (i < trail.length - 1) {
                nx = WarRoster.unpackedX(trail[i + 1]);
                ny = WarRoster.unpackedY(trail[i + 1]);
            } else if (lastSeason && a.trail.length > 0) {
                nx = WarRoster.unpackedX(a.trail[0]);
                ny = WarRoster.unpackedY(a.trail[0]);
            } else {
                nx = a.x;
                ny = a.y;
            }
            if (nx == x && ny == y) return null;
            long age = trailAge(clock, trail.length, i, lastSeason);
            return new Tracks(new Marker(a.id, a.houseId, x, y, Integer.signum(nx - x), Integer.signum(ny - y), Size.of(a.soldiers), -1,
                    clock - age, Source.TRACKS, -1, null, null), age);
        }
        return null;
    }

    /** A trail keeps no dates, so how long since its {@code i}th chunk is reckoned from its place along the Season's road. */
    private static long trailAge(long clock, int length, int i, boolean lastSeason) {
        long intoSeason = clock % HistoryManager.SEASON_TURNS;
        if (lastSeason) return intoSeason + HistoryManager.SEASON_TURNS * (length - i) / length;
        return intoSeason * (length - i) / length;
    }

    /**
     * Newer word replaces older, with two exceptions. Nothing said unseats what was seen or read from
     * a trail: that stays, only ageing, until it is dropped (A47). A town's named place betters the
     * War Table's region, however old. A captain once seen, and the road it has been on, stay known.
     */
    private void learn(Marker m) {
        Marker was = markers.get(m.armyId);
        if (was != null) {
            if (m.rumour() && !was.rumour()) return;
            boolean townOverTable = m.source == Source.TOWN && was.source == Source.WAR_TABLE;
            if (was.observedAt > m.observedAt && !townOverTable) return;
        }
        int captain = m.captainId >= 0 ? m.captainId : was != null ? was.captainId : -1;
        int[] past = was == null ? new int[0] : was.past;
        if (was != null && !was.rumour() && !m.rumour() && (was.x != m.x || was.y != m.y)) past = appended(past, was.x, was.y);
        markers.put(m.armyId, new Marker(m.armyId, m.houseId, m.x, m.y, m.dx, m.dy, m.size, captain, m.observedAt, m.source, -1, null, past));
    }

    private static int[] appended(int[] past, int x, int y) {
        int keep = Math.min(past.length, (PAST_KEPT - 1) * 2);
        int[] out = Arrays.copyOfRange(past, past.length - keep, past.length + 2);
        out[keep] = x;
        out[keep + 1] = y;
        return out;
    }

    /** Whatever has outlasted a Season is dropped. */
    private boolean forget(long clock) {
        boolean a = markers.values().removeIf(m -> clock - m.observedAt >= HistoryManager.SEASON_TURNS);
        boolean b = sounds.values().removeIf(s -> clock - s.heardAt >= HistoryManager.SEASON_TURNS);
        return a || b;
    }

    /** Where it is bound, as a walker would judge it; standing if it is fighting or has arrived. */
    private static int[] headingOf(Army a) {
        if (a.battle || (a.targetX == a.x && a.targetY == a.y)) return new int[]{0, 0};
        return new int[]{Integer.signum(a.targetX - a.x), Integer.signum(a.targetY - a.y)};
    }

    private static int region(int chunk) {
        return Math.floorDiv(chunk, REGION_CHUNKS) * REGION_CHUNKS + REGION_CHUNKS / 2;
    }

    // ---- Knowing ----

    /**
     * Everything on the map now.
     *
     * @param swornHouse the house the player is sworn to, whose armies are known exactly; -1 if none
     * @param sieges what is public and exact, which outranks any word of the same army
     */
    public List<Marker> markers(WarRoster roster, int swornHouse, List<Marker> sieges, long clock) {
        forget(clock);
        Map<Integer, Marker> out = new LinkedHashMap<>(markers);
        if (swornHouse >= 0) {
            for (Army a : roster.armies) {
                if (a.soldiers <= 0 || a.houseId != swornHouse || !aHost(a)) continue;
                int[] heading = headingOf(a);
                int[] road = new int[a.trail.length * 2];
                for (int i = 0; i < a.trail.length; i++) {
                    road[i * 2] = WarRoster.unpackedX(a.trail[i]);
                    road[i * 2 + 1] = WarRoster.unpackedY(a.trail[i]);
                }
                out.put(a.id, new Marker(a.id, a.houseId, a.x, a.y, heading[0], heading[1], Size.of(a.soldiers), a.captainId, clock,
                        Source.EXACT, a.soldiers, a.objective, road));
            }
        }
        List<Marker> all = new ArrayList<>(out.values());
        all.addAll(sieges);
        return all;
    }

    /** A siege, marked exactly: {@code at} is where it is, and everyone knows it (A49). */
    public static Marker siege(int houseId, int besiegedHouseId, int x, int y, long clock) {
        Marker m = new Marker(-1 - houseId, houseId, x, y, 0, 0, Size.HOST, -1, clock, Source.SIEGE, -1, null, new int[0]);
        m.besiegedHouseId = besiegedHouseId;
        return m;
    }

    /** What is public whatever the player has heard: Castle Tarmin's siege, and any seat under assault (A49). */
    public static List<Marker> sieges(com.bpm.minotaur.gamedata.history.HistoryWorld world, WarRoster roster, com.bpm.minotaur.managers.BiomeManager biomes, long clock) {
        List<Marker> out = new ArrayList<>();
        int besieger = com.bpm.minotaur.gamedata.history.war.Siege.besieger(world);
        if (besieger >= 0 && biomes != null && biomes.getCastleSite() != null) {
            out.add(siege(besieger, world.tarminHouse().id, Math.round(biomes.getCastleSite().x), Math.round(biomes.getCastleSite().y), clock));
        }
        for (com.bpm.minotaur.gamedata.history.war.army.SeatSiege s : roster.sieges) out.add(siege(s.besieger, s.holder, s.x, s.y, clock));
        return out;
    }

    public List<Sound> sounds(long clock) {
        forget(clock);
        return new ArrayList<>(sounds.values());
    }

    // ---- Telling ----

    /** What is reported as a house's hosts: not its foragers, heralds or hunters. */
    private static boolean aHost(Army a) {
        return a.kind != Army.Kind.FORAGERS && a.kind != Army.Kind.HERALD && a.kind != Army.Kind.HUNTING_PARTY;
    }

    /** Words for a heading: "north-east"; null for none. */
    public static String headingWords(int dx, int dy) {
        if (dx == 0 && dy == 0) return null;
        String ns = dy > 0 ? "north" : dy < 0 ? "south" : "";
        String ew = dx > 0 ? "east" : dx < 0 ? "west" : "";
        return ns.isEmpty() || ew.isEmpty() ? ns + ew : ns + "-" + ew;
    }

    /** "tracks lead north-east, a few days old" (A49). */
    public static String tracksLine(Tracks t, String houseName) {
        return "Tracks of " + houseName + " lead " + headingWords(t.marker.dx, t.marker.dy) + ", " + agedWords(t.ageTurns) + ".";
    }

    private static String agedWords(long turns) {
        if (turns < HistoryManager.SLEEP_TURNS / 5) return "still fresh";
        if (turns < HistoryManager.SEASON_TURNS) return "a few days old";
        return "weeks old";
    }

    /** What a townsperson says of a host, in words that give a bearing from where they stand. */
    public static String townLine(Marker m, String houseName, int fromX, int fromY) {
        String where = headingWords(Integer.signum(m.x - fromX), Integer.signum(m.y - fromY));
        String heading = headingWords(m.dx, m.dy);
        return m.size.words + " of " + houseName + " was seen " + (where == null ? "hereabouts" : "away to the " + where)
                + (heading == null ? ", encamped." : ", marching " + heading + ".");
    }

    /**
     * The card for a marker (A48): the house, the captain if known, the heading and how long ago, with
     * a count and an objective only for what the player knows exactly.
     *
     * @param captainName the captain's name, or null
     */
    public static List<String> card(Marker m, String houseName, String captainName, long now) {
        List<String> lines = new ArrayList<>();
        if (m.source == Source.SIEGE) {
            lines.add(houseName + " lays siege here.");
            return lines;
        }
        lines.add(m.size.words + " of " + houseName);
        if (captainName != null) lines.add("Under " + captainName);
        String heading = headingWords(m.dx, m.dy);
        lines.add(heading == null ? "Standing" : "Marching " + heading);
        if (m.exact()) {
            lines.add(m.exactSoldiers + " soldiers");
            if (m.objective != null) lines.add(objectiveWords(m.objective));
            return lines;
        }
        lines.add(sourceLine(m, now));
        return lines;
    }

    private static String sourceLine(Marker m, long now) {
        int sleeps = m.sleepsAgo(now);
        String ago = sleeps == 0 ? "today" : sleeps == 1 ? "1 sleep ago" : sleeps + " sleeps ago";
        switch (m.source) {
            case WAR_TABLE:
                return "The War Table's word " + ago;
            case TOWN:
                return "Rumoured in a town, " + ago;
            case TRACKS:
                return "Tracks read, left " + ago;
            default:
                return sleeps == 0 ? "Last seen today" : "Last seen " + ago;
        }
    }

    private static String objectiveWords(Army.Objective o) {
        switch (o) {
            case SEAT: return "Marching on an enemy seat";
            case HUNT: return "Hunting an enemy host";
            case RAID: return "Raiding";
            case GUARD: return "Guarding its seat";
            case RECOVER: return "Going home to recover";
            case FOLLOW: return "Marching with its liege";
            case CASTLE: return "Besieging Castle Tarmin";
            default: return "Going home, its war done";
        }
    }

    // ---- Saving: as plain numbers, like the rest of the map's knowledge ----

    private static final int FIXED_FIELDS = 10;

    List<int[]> encodedMarkers() {
        List<int[]> out = new ArrayList<>();
        for (Marker m : markers.values()) {
            int[] e = Arrays.copyOf(new int[]{m.armyId, m.houseId, m.x, m.y, m.dx, m.dy, m.size.ordinal(), m.captainId, (int) m.observedAt,
                    m.source.ordinal()}, FIXED_FIELDS + m.past.length);
            System.arraycopy(m.past, 0, e, FIXED_FIELDS, m.past.length);
            out.add(e);
        }
        return out;
    }

    List<int[]> encodedSounds() {
        List<int[]> out = new ArrayList<>();
        for (Sound s : sounds.values()) out.add(new int[]{s.battleId, s.x, s.y, (int) s.heardAt});
        return out;
    }

    void decode(List<int[]> encodedMarkers, List<int[]> encodedSounds) {
        clear();
        Size[] sizes = Size.values();
        Source[] sources = Source.values();
        if (encodedMarkers != null) {
            for (int[] e : encodedMarkers) {
                if (e == null || e.length < FIXED_FIELDS || e[6] < 0 || e[6] >= sizes.length || e[9] < 0 || e[9] >= sources.length) continue;
                markers.put(e[0], new Marker(e[0], e[1], e[2], e[3], e[4], e[5], sizes[e[6]], e[7], e[8], sources[e[9]], -1, null,
                        Arrays.copyOfRange(e, FIXED_FIELDS, e.length)));
            }
        }
        if (encodedSounds != null) {
            for (int[] e : encodedSounds) {
                if (e != null && e.length == 4) sounds.put(e[0], new Sound(e[0], e[1], e[2], e[3]));
            }
        }
    }

    void clear() {
        markers.clear();
        sounds.clear();
    }
}
