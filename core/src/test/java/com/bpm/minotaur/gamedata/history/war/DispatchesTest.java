package com.bpm.minotaur.gamedata.history.war;

import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.gamedata.history.war.army.Army;
import com.bpm.minotaur.gamedata.history.war.army.WarRoster;
import com.bpm.minotaur.gamedata.map.ArmyKnowledge;
import com.bpm.minotaur.ui.UiGlyphs;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/** Living War A50: the War Table's news, nearest the Shelter first, with no empty sections. */
public class DispatchesTest {

    private static final GridPoint2 SHELTER = new GridPoint2(0, 0);
    private static final int GULLET = 3;
    private static final int SEEP = 4;

    private final WarRoster roster = new WarRoster();

    private Army army(int id, int house, int x, int y, int soldiers) {
        Army a = new Army();
        a.id = id;
        a.houseId = house;
        a.x = x;
        a.y = y;
        a.targetX = x;
        a.targetY = y;
        a.soldiers = soldiers;
        a.captainId = -1;
        roster.armies.add(a);
        return a;
    }

    private List<ArmyKnowledge.Marker> seen(int sworn, List<ArmyKnowledge.Marker> sieges) {
        ArmyKnowledge known = new ArmyKnowledge();
        for (Army a : roster.armies) if (a.houseId != sworn) known.onTurn(roster, a.x, a.y, 100);
        return known.markers(roster, sworn, sieges, 100);
    }

    private static List<Dispatches.Row> news(List<ArmyKnowledge.Marker> known, Iterable<Integer> hunted, String task) {
        return Dispatches.news(known, SHELTER, hunted, task, 100, h -> h == GULLET ? "House Gullet" : "House Seep", f -> "Ser Vael");
    }

    @Test
    public void rowsSortNearestTheShelterFirst() {
        army(1, GULLET, 40, 0, 15);
        army(2, SEEP, 2, 0, 15);
        army(3, GULLET, 90, 0, 15);
        army(4, SEEP, 9, 0, 15);
        List<Dispatches.Row> rows = news(seen(-1, Collections.emptyList()), Collections.emptyList(), null);
        List<Integer> far = new ArrayList<>();
        for (Dispatches.Row r : rows) far.add(r.at.x);
        assertEquals(Arrays.asList(2, 9, 40, 90), far);
        assertEquals(Dispatches.Section.NEAR_THE_SHELTER, rows.get(1).section);
        assertEquals(Dispatches.Section.FARTHER_OFF, rows.get(2).section);
    }

    @Test
    public void sectionsAppearOnlyWithContent() {
        assertTrue(Dispatches.bySection(news(Collections.emptyList(), Collections.emptyList(), null)).isEmpty());
        army(1, SEEP, 2, 0, 15);
        Map<Dispatches.Section, List<Dispatches.Row>> near = Dispatches.bySection(news(seen(-1, Collections.emptyList()), Collections.emptyList(), null));
        assertEquals(Collections.singleton(Dispatches.Section.NEAR_THE_SHELTER), near.keySet());
    }

    @Test
    public void sectionsReadInTheOrderOfTheDecision() {
        army(1, GULLET, 60, 0, 15);
        army(2, SEEP, 2, 0, 15);
        army(3, SEEP, 90, 0, 15);
        List<ArmyKnowledge.Marker> sieges = Collections.singletonList(ArmyKnowledge.siege(SEEP, 7, 7, 100));
        List<Dispatches.Row> rows = news(seen(GULLET, sieges), Collections.singletonList(SEEP), "Hold the ground");
        assertEquals(Arrays.asList(Dispatches.Section.AIMED_AT_YOU, Dispatches.Section.YOUR_HOUSE, Dispatches.Section.SIEGES,
                Dispatches.Section.NEAR_THE_SHELTER, Dispatches.Section.FARTHER_OFF), new ArrayList<>(Dispatches.bySection(rows).keySet()));
    }

    @Test
    public void onlyTheSwornPlayersOwnHostsGiveNumbersAndObjectives() {
        army(1, GULLET, 60, 0, 23).objective = Army.Objective.HUNT;
        army(2, SEEP, 2, 0, 37).objective = Army.Objective.SEAT;
        for (Dispatches.Row r : news(seen(GULLET, Collections.emptyList()), Collections.emptyList(), null)) {
            if (r.houseId == GULLET) {
                assertEquals(Dispatches.Section.YOUR_HOUSE, r.section);
                assertTrue(r.detail, r.detail.contains("23 soldiers") && r.detail.contains("Hunting"));
            } else {
                assertFalse(r.detail, r.detail.contains("37") || r.detail.toLowerCase().contains("enemy seat"));
            }
        }
    }

    @Test
    public void aHostNobodyHasWordOfIsNotListed() {
        army(1, SEEP, 2, 0, 15);
        assertTrue(news(new ArmyKnowledge().markers(roster, -1, Collections.emptyList(), 100), Collections.emptyList(), null).isEmpty());
    }

    @Test
    public void huntersAreWarnedOfOnceAHouseAndNeverPlaced() {
        army(1, SEEP, 2, 0, 5).kind = Army.Kind.HUNTING_PARTY;
        army(2, SEEP, 9, 0, 5).kind = Army.Kind.HUNTING_PARTY;
        army(3, GULLET, 9, 0, 15);
        List<Dispatches.Row> rows = news(Collections.emptyList(), Dispatches.hunted(roster.armies), null);
        assertEquals(1, rows.size());
        assertEquals(Dispatches.Section.AIMED_AT_YOU, rows.get(0).section);
        assertTrue(rows.get(0).title, rows.get(0).title.contains("price on you"));
        assertNull("no waypoint to a hunter", rows.get(0).at);
    }

    @Test
    public void hostAndSiegeRowsCarryAWaypoint() {
        army(1, SEEP, 2, 0, 15);
        for (Dispatches.Row r : news(seen(-1, Collections.singletonList(ArmyKnowledge.siege(GULLET, 7, 7, 100))), Collections.emptyList(), "A task")) {
            if (r.section == Dispatches.Section.AIMED_AT_YOU) assertNull(r.at);
            else assertNotNull(r.title, r.at);
        }
    }

    @Test
    public void aWarsPageHoldsItsTwoHousesAlone() {
        army(1, GULLET, 2, 0, 15);
        army(2, SEEP, 3, 0, 15);
        army(3, 9, 4, 0, 15);
        List<Dispatches.Row> rows = Dispatches.news(seen(-1, Collections.emptyList()), SHELTER, Collections.singletonList(GULLET), null, 100, h -> "House", f -> "Ser");
        List<Dispatches.Row> page = Dispatches.ofWar(rows, GULLET, SEEP);
        assertEquals(2, page.size());
        for (Dispatches.Row r : page) assertNotEquals(Dispatches.Section.AIMED_AT_YOU, r.section);
    }

    @Test
    public void everyStringSurvivesTheFont() {
        for (Army.Objective o : Army.Objective.values()) {
            Army a = army(100 + o.ordinal(), GULLET, 60 + o.ordinal(), 0, 23);
            a.objective = o;
            a.targetX = a.x + 1;
            a.targetY = 1;
        }
        army(1, SEEP, 2, 0, 15);
        List<ArmyKnowledge.Marker> sieges = Collections.singletonList(ArmyKnowledge.siege(SEEP, 7, 7, 100));
        for (Dispatches.Row r : news(seen(GULLET, sieges), Collections.singletonList(SEEP), "A task")) {
            assertEquals(r.title, -1, UiGlyphs.firstUnsupportedIndex(r.title));
            assertEquals(r.detail, -1, UiGlyphs.firstUnsupportedIndex(r.detail));
        }
    }
}
