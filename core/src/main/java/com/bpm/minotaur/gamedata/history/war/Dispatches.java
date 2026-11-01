package com.bpm.minotaur.gamedata.history.war;

import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.gamedata.history.favour.Oath;
import com.bpm.minotaur.gamedata.history.war.army.Army;
import com.bpm.minotaur.gamedata.map.ArmyKnowledge;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.function.IntFunction;

/**
 * The War Table's news column (Living War A50): what aims at the player, their own house, the sieges,
 * then every other host the player knows of, nearest the Shelter first. It reports only what is in
 * {@link ArmyKnowledge}, so a host nobody has word of is not listed. Pure, so the screen only lays it out.
 */
public final class Dispatches {

    public enum Section {
        AIMED_AT_YOU("AIMED AT YOU"), YOUR_HOUSE("YOUR HOUSE"), SIEGES("SIEGES"),
        NEAR_THE_SHELTER("NEAR THE SHELTER"), FARTHER_OFF("FARTHER OFF");

        public final String heading;

        Section(String heading) {
            this.heading = heading;
        }
    }

    /** One item of news. */
    public static final class Row {
        public final Section section;
        /** Whose Arms it carries, or -1. */
        public final int houseId;
        public final String title;
        public final String detail;
        /** How far off it is, or null if the news has no place. */
        public final String distanceWords;
        /** Where the W key sets the waypoint; null if the news has no place. */
        public final GridPoint2 at;
        final int distance;
        /** For a siege: the house besieged, or -1. */
        final int besieged;

        Row(Section section, int houseId, String title, String detail, String distanceWords, GridPoint2 at, int distance, int besieged) {
            this.section = section;
            this.houseId = houseId;
            this.title = title;
            this.detail = detail;
            this.distanceWords = distanceWords;
            this.at = at;
            this.distance = distance;
            this.besieged = besieged;
        }
    }

    /** About three days' march from the Shelter, at the {@link #CHUNKS_PER_DAY} a host keeps. */
    public static final int NEAR_DAYS = 3;
    /** How far a host off the road marches between one sleep and the next. */
    public static final int CHUNKS_PER_DAY = com.bpm.minotaur.managers.HistoryManager.SLEEP_TURNS / com.bpm.minotaur.gamedata.history.war.army.Terrain.OFF_ROAD_TURNS;

    private Dispatches() {
    }

    /**
     * The news, in reading order: each section's rows nearest first, and no section without a row.
     *
     * @param known what {@link ArmyKnowledge#markers} says the player knows
     * @param hunted the houses with hunters out after the player
     * @param task what the player's sworn house asks of them, or null
     */
    public static List<Row> news(List<ArmyKnowledge.Marker> known, GridPoint2 shelter, Iterable<Integer> hunted, String task, long clock,
            IntFunction<String> houseName, IntFunction<String> figureName) {
        List<Row> rows = new ArrayList<>();
        for (int house : hunted) {
            rows.add(new Row(Section.AIMED_AT_YOU, house, opening(houseName.apply(house)) + " has put a price on you",
                    "Hunters are out. Concealment wards and going below are the answers.", null, null, 0, -1));
        }
        if (task != null) rows.add(new Row(Section.AIMED_AT_YOU, -1, "Your task", task, null, null, 0, -1));
        for (ArmyKnowledge.Marker m : known) rows.add(rowOf(m, shelter, clock, houseName, figureName));
        // Stable, so what has no distance keeps its order.
        rows.sort(Comparator.<Row>comparingInt(r -> r.section.ordinal()).thenComparingInt(r -> r.distance));
        return rows;
    }

    private static Row rowOf(ArmyKnowledge.Marker m, GridPoint2 shelter, long clock, IntFunction<String> houseName, IntFunction<String> figureName) {
        GridPoint2 at = new GridPoint2(m.x, m.y);
        int distance = Encounter.distance(shelter, at);
        String far = daysFrom(distance);
        String house = houseName.apply(m.houseId);
        if (m.source == ArmyKnowledge.Source.SIEGE) {
            return new Row(Section.SIEGES, m.houseId, opening(house) + " lays siege to " + houseName.apply(m.besiegedHouseId), "This much is certain.", far, at, distance, m.besiegedHouseId);
        }
        List<String> card = ArmyKnowledge.card(m, house, m.captainId >= 0 ? figureName.apply(m.captainId) : null, clock);
        String title = card.get(0);
        String detail = String.join(". ", card.subList(1, card.size())) + ".";
        Section section = m.exact() ? Section.YOUR_HOUSE : distance <= NEAR_DAYS * CHUNKS_PER_DAY ? Section.NEAR_THE_SHELTER : Section.FARTHER_OFF;
        return new Row(section, m.houseId, title, detail, far, at, distance, -1);
    }

    /** A name beginning a line: "the Antlered Host" takes a capital there. */
    private static String opening(String name) {
        return name.isEmpty() ? name : Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    private static String daysFrom(int chunks) {
        if (chunks == 0) return "At the Shelter";
        int days = Math.max(1, Math.round(chunks / (float) CHUNKS_PER_DAY));
        return "About " + days + (days == 1 ? " day" : " days") + " from the Shelter";
    }

    /** The news under its headings, in order; a section with no rows is not there at all. */
    public static Map<Section, List<Row>> bySection(List<Row> rows) {
        Map<Section, List<Row>> out = new LinkedHashMap<>();
        for (Row r : rows) out.computeIfAbsent(r.section, s -> new ArrayList<>()).add(r);
        return out;
    }

    /** The news of one war: the hosts and sieges of its two houses. */
    public static List<Row> ofWar(List<Row> rows, int attackerId, int defenderId) {
        List<Row> out = new ArrayList<>();
        for (Row r : rows) {
            if (r.section != Section.AIMED_AT_YOU && (r.houseId == attackerId || r.houseId == defenderId)) out.add(r);
            // A house under siege by a third party is that house's war's news too.
            else if (r.section == Section.SIEGES && (r.besieged == attackerId || r.besieged == defenderId)) out.add(r);
        }
        return out;
    }

    /**
     * The houses with a Hunting Party out after the player, each once: the Table warns of them, but
     * not of where they are.
     *
     * @param countsThePlayerAnEnemy a party out from a house that no longer does is not aimed at them
     */
    public static Iterable<Integer> hunted(Iterable<Army> armies, java.util.function.IntPredicate countsThePlayerAnEnemy) {
        TreeSet<Integer> out = new TreeSet<>();
        for (Army a : armies) {
            if (a.kind == Army.Kind.HUNTING_PARTY && a.soldiers > 0 && countsThePlayerAnEnemy.test(a.houseId)) out.add(a.houseId);
        }
        return out;
    }

    /** What a sworn player's house asks of them, or null if they are not sworn. */
    public static String task(Oath oath, com.bpm.minotaur.gamedata.history.HistoryWorld world) {
        if (!oath.sworn()) return null;
        if (oath.contract == null) return "Your house has no task for you. Go to its war banner.";
        return oath.contract.done ? "Your task is done. Go to its war banner to be paid." : oath.contract.describe(world);
    }
}
