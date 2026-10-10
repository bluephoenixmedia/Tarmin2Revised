package com.bpm.minotaur.gamedata.history.war;

import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.gamedata.history.CasusBelli;
import com.bpm.minotaur.gamedata.history.EventType;
import com.bpm.minotaur.gamedata.history.HistoryEvent;
import com.bpm.minotaur.gamedata.history.HistoryWorld;
import com.bpm.minotaur.gamedata.history.House;
import com.bpm.minotaur.gamedata.history.War;

import java.util.ArrayList;
import java.util.List;

/**
 * The War Table's reading of the history (Living War W27): every war under way, why, since when,
 * how its battles have gone and where its front stands from the player; and the gashes whose
 * holders are bleeding out. Pure, so the screen only lays it out.
 */
public final class WarReport {

    /** A great house this weak, at war, may lose its gash. */
    public static final float AT_RISK = 30f;

    public static final class WarLine {
        public final int warId;
        public final int attackerId;
        public final int defenderId;
        public final String attacker;
        public final String defender;
        /** Why it was declared, in a few words. */
        public final String cause;
        public final int sinceYear;
        /** Battles each side has won in it, chronicled. */
        public final int winsA;
        public final int winsB;
        /** Where its front is from the player: "3 chunks north-east", "here", or null if unknown. */
        public final String front;

        WarLine(War w, HistoryWorld world, int winsA, int winsB, String front) {
            this.warId = w.id;
            this.attackerId = w.attackerId;
            this.defenderId = w.defenderId;
            this.attacker = name(world, w.attackerId);
            this.defender = name(world, w.defenderId);
            this.cause = cause(w.casusBelli);
            this.sinceYear = w.startSeason / HistoryWorld.SEASONS_PER_YEAR + 1;
            this.winsA = winsA;
            this.winsB = winsB;
            this.front = front;
        }

        /** Who the battles favour, in a word. */
        public String standing() {
            if (winsA == winsB) return "Even";
            return (winsA > winsB ? attacker : defender) + " ahead";
        }
    }

    public static final class SeatAtRisk {
        public final String house;
        public final String gash;
        public final int strength;

        SeatAtRisk(String house, String gash, int strength) {
            this.house = house;
            this.gash = gash;
            this.strength = strength;
        }
    }

    private WarReport() {
    }

    /** Every war under way, oldest first. {@code fronts} and {@code player} may be null. */
    public static List<WarLine> wars(HistoryWorld world, List<Front> fronts, GridPoint2 player) {
        List<WarLine> out = new ArrayList<>();
        if (world == null) return out;
        for (War w : world.activeWars()) {
            int a = 0, b = 0;
            for (HistoryEvent e : world.events()) {
                if (e.type != EventType.BATTLE || e.causeEventId != w.declaredEventId) continue;
                if (e.houseA == w.attackerId) a++;
                else if (e.houseA == w.defenderId) b++;
            }
            String where = null;
            if (fronts != null && player != null) {
                for (Front f : fronts) {
                    if (f.warId == w.id) {
                        where = bearing(player, f.center);
                        break;
                    }
                }
            }
            out.add(new WarLine(w, world, a, b, where));
        }
        return out;
    }

    /** Great houses at war and weak enough to lose their gash. */
    public static List<SeatAtRisk> seatsAtRisk(HistoryWorld world) {
        List<SeatAtRisk> out = new ArrayList<>();
        if (world == null) return out;
        for (int g = 0; g < HistoryWorld.GASH_COUNT; g++) {
            House h = world.gashHolder(g);
            if (h == null || world.activeWarCount(h.id) == 0 || h.strength >= AT_RISK) continue;
            out.add(new SeatAtRisk(h.name, world.gashName(g), Math.round(h.strength)));
        }
        return out;
    }

    /** Why a war was declared, as the War Table words it. */
    public static String cause(CasusBelli cb) {
        if (cb == null) return "No reason given";
        switch (cb) {
            case SLAIN_KIN: return "Blood for blood";
            case BROKEN_PACT: return "A broken pact";
            case HOSTAGE_HELD: return "Kin held hostage";
            case USURPED_SEAT: return "A seat taken";
            case CLAIM_TO_GASH: return "A claim to a gash";
            case CLAIM_TO_CASTLE: return "A claim to the castle";
            case INDEPENDENCE: return "Throwing off a liege";
            case TREACHERY: return "Treachery";
            default: return "Appetite";
        }
    }

    /** "here", or "3 chunks north-east" (x east, y north). */
    public static String bearing(GridPoint2 from, GridPoint2 to) {
        int dx = to.x - from.x, dy = to.y - from.y;
        int d = EncounterScheduler.distance(from, to);
        if (d == 0) return "here";
        String ns = dy * 2 > Math.abs(dx) ? "north" : -dy * 2 > Math.abs(dx) ? "south" : "";
        String ew = dx * 2 > Math.abs(dy) ? "east" : -dx * 2 > Math.abs(dy) ? "west" : "";
        String dir = ns.isEmpty() ? ew : ew.isEmpty() ? ns : ns + "-" + ew;
        return d + (d == 1 ? " chunk " : " chunks ") + dir;
    }

    private static String name(HistoryWorld world, int houseId) {
        House h = world.house(houseId);
        return h == null ? "a house of the Maze" : h.name;
    }
}
