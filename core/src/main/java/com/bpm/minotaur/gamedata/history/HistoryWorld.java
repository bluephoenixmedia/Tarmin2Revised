package com.bpm.minotaur.gamedata.history;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The whole generated history of one world: houses, figures, events, wars and grudges.
 * Built by {@link HistorySimulator}; never mutated by anything else.
 */
public class HistoryWorld {

    public static final int SEASONS_PER_YEAR = 4;
    public static final int GASH_COUNT = 3;

    /** How two houses stand toward each other right now. */
    public enum Stance { WAR, ALLIED, SWORN, NEUTRAL }

    public final long seed;
    final List<House> houses = new ArrayList<>();
    final List<Figure> figures = new ArrayList<>();
    final List<HistoryEvent> events = new ArrayList<>();
    final List<War> wars = new ArrayList<>();
    final List<Grudge> grudges = new ArrayList<>();
    /** Unordered pairs of allied houses, encoded by {@link #pairKey}. */
    final Set<Long> alliances = new HashSet<>();
    final Set<String> usedNames = new HashSet<>();
    final String[] gashNames = new String[GASH_COUNT];

    /** Seasons simulated so far, counted from the founding of the first houses. */
    int season;
    /** Seasons of live play after prehistory. */
    int liveSeasons;
    int tarminHouseId = -1;
    int tarminRisesSeason;
    int seekersFallen;

    HistoryWorld(long seed) {
        this.seed = seed;
    }

    public int season() {
        return season;
    }

    public int year() {
        return season / SEASONS_PER_YEAR;
    }

    public int liveSeasons() {
        return liveSeasons;
    }

    public List<House> houses() {
        return Collections.unmodifiableList(houses);
    }

    public List<Figure> figures() {
        return Collections.unmodifiableList(figures);
    }

    public List<HistoryEvent> events() {
        return Collections.unmodifiableList(events);
    }

    public List<War> wars() {
        return Collections.unmodifiableList(wars);
    }

    public House house(int id) {
        return id >= 0 && id < houses.size() ? houses.get(id) : null;
    }

    public Figure figure(int id) {
        return id >= 0 && id < figures.size() ? figures.get(id) : null;
    }

    public HistoryEvent event(int id) {
        return id >= 0 && id < events.size() ? events.get(id) : null;
    }

    public House tarminHouse() {
        return house(tarminHouseId);
    }

    public String gashName(int index) {
        return gashNames[index];
    }

    /** The house holding gash {@code index}. Exactly one house holds each gash at all times. */
    public House gashHolder(int index) {
        for (House h : houses) {
            if (h.gashIndex == index && !h.isExtinct()) return h;
        }
        return null;
    }

    public Figure lordOf(House h) {
        return h == null ? null : figure(h.lordId);
    }

    public List<House> livingHouses() {
        List<House> out = new ArrayList<>();
        for (House h : houses) {
            if (!h.isExtinct()) out.add(h);
        }
        return out;
    }

    public List<Figure> livingMembers(House h) {
        List<Figure> out = new ArrayList<>();
        for (Figure f : figures) {
            if (f.houseId == h.id && f.isAlive() && f.role == Figure.Role.KIN) out.add(f);
        }
        return out;
    }

    public List<Figure> swornSwords(House h) {
        List<Figure> out = new ArrayList<>();
        for (Figure f : figures) {
            if (f.houseId == h.id && f.isAlive() && f.role == Figure.Role.SWORN_SWORD) out.add(f);
        }
        return out;
    }

    public List<War> activeWars() {
        List<War> out = new ArrayList<>();
        for (War w : wars) {
            if (w.isActive()) out.add(w);
        }
        return out;
    }

    public War activeWarBetween(int a, int b) {
        for (War w : wars) {
            if (w.isActive() && w.involves(a) && w.involves(b)) return w;
        }
        return null;
    }

    public int activeWarCount(int houseId) {
        int n = 0;
        for (War w : wars) {
            if (w.isActive() && w.involves(houseId)) n++;
        }
        return n;
    }

    public Stance stance(int a, int b) {
        if (a == b) return Stance.ALLIED;
        if (activeWarBetween(a, b) != null) return Stance.WAR;
        House ha = house(a);
        House hb = house(b);
        if (ha != null && hb != null && (ha.liegeId == b || hb.liegeId == a)) return Stance.SWORN;
        if (alliances.contains(pairKey(a, b))) return Stance.ALLIED;
        return Stance.NEUTRAL;
    }

    /** Summed weight of what {@code holder} holds against {@code against}. */
    public float grudgeWeight(int holder, int against) {
        float sum = 0f;
        for (Grudge g : grudges) {
            if (g.holderId == holder && g.againstId == against) sum += g.weight;
        }
        return sum;
    }

    /** The heaviest grudge {@code holder} bears {@code against}, or null. */
    public Grudge strongestGrudge(int holder, int against) {
        Grudge best = null;
        for (Grudge g : grudges) {
            if (g.holderId == holder && g.againstId == against && (best == null || g.weight > best.weight)) best = g;
        }
        return best;
    }

    public int seekersFallen() {
        return seekersFallen;
    }

    /**
     * Every event in order, one per line. Two worlds with equal fingerprints have the same
     * history; the determinism and save round-trip tests compare these.
     */
    public String fingerprint() {
        StringBuilder sb = new StringBuilder();
        for (HistoryEvent e : events) sb.append(e).append('\n');
        for (House h : houses) {
            sb.append(h).append(" lord=").append(h.lordId).append(" gash=").append(h.gashIndex)
              .append(" liege=").append(h.liegeId).append(" str=").append(Math.round(h.strength)).append('\n');
        }
        return sb.toString();
    }

    static long pairKey(int a, int b) {
        int lo = Math.min(a, b);
        int hi = Math.max(a, b);
        return ((long) lo << 32) | (hi & 0xffffffffL);
    }
}
