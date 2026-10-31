package com.bpm.minotaur.gamedata.map;

import com.badlogic.gdx.utils.Json;
import com.bpm.minotaur.gamedata.history.war.army.Army;
import com.bpm.minotaur.gamedata.history.war.army.ArmyBattle;
import com.bpm.minotaur.gamedata.history.war.army.WarRoster;
import com.bpm.minotaur.managers.HistoryManager;
import org.junit.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

/** Living War A46-A49: what the player knows only ages, and none of it reaches the history. */
public class ArmyKnowledgeTest {

    private static final int HOUSE = 3;
    private static final int LEAGUE = 4;

    private final WarRoster roster = new WarRoster();
    private final ArmyKnowledge known = new ArmyKnowledge();

    private Army army(int id, int house, int x, int y, int soldiers) {
        Army a = new Army();
        a.id = id;
        a.houseId = house;
        a.x = x;
        a.y = y;
        a.targetX = x;
        a.targetY = y;
        a.soldiers = soldiers;
        a.captainId = 77;
        roster.armies.add(a);
        return a;
    }

    private List<ArmyKnowledge.Marker> markers(long clock) {
        return known.markers(roster, -1, Collections.emptyList(), clock);
    }

    private ArmyKnowledge.Marker only(long clock) {
        List<ArmyKnowledge.Marker> all = markers(clock);
        assertEquals(1, all.size());
        return all.get(0);
    }

    @Test
    public void aHostIsSeenInThisChunkOrTheNextAndNoFarther() {
        army(1, HOUSE, 11, 10, 15);
        army(2, HOUSE, 12, 10, 15);
        known.onTurn(roster, 10, 10, 100);
        assertEquals(1, only(100).armyId);
    }

    @Test
    public void aHostOutOfSightStaysWhereLastSeenWithTheHeadingItHad() {
        Army a = army(1, HOUSE, 11, 10, 15);
        a.targetX = 40;
        a.targetY = 40;
        known.onTurn(roster, 10, 10, 100);
        a.x = 30;
        a.y = 30;
        known.onTurn(roster, 10, 10, 200);
        ArmyKnowledge.Marker m = only(200);
        assertEquals(11, m.x);
        assertEquals(10, m.y);
        assertEquals(1, m.dx);
        assertEquals(1, m.dy);
        assertEquals("last seen when it was", 100L, m.observedAt);
    }

    @Test
    public void aMarkerDimsAndIsDroppedAfterASeason() {
        army(1, HOUSE, 10, 10, 15).x = 10;
        known.onTurn(roster, 10, 10, 1000);
        roster.armies.clear();
        float fresh = only(1000).strength(1000);
        long later = 1000 + HistoryManager.SLEEP_TURNS;
        assertTrue("it dims", only(later).strength(later) < fresh);
        assertEquals(1, only(later).sleepsAgo(later));
        assertEquals(1, markers(1000 + HistoryManager.SEASON_TURNS - 1).size());
        assertTrue("dropped", markers(1000 + HistoryManager.SEASON_TURNS).isEmpty());
    }

    /** A48: the card gives an estimate, never a count or an objective. */
    @Test
    public void aSightingToldNeverGivesACountOrObjective() {
        Army a = army(1, HOUSE, 10, 10, 37);
        a.objective = Army.Objective.SEAT;
        known.onTurn(roster, 10, 10, 100);
        List<String> card = ArmyKnowledge.card(only(100), "House Gullet", "Ser Vael", 100);
        String all = String.join(" | ", card);
        assertTrue(all, all.contains("A host of House Gullet"));
        assertTrue(all, all.contains("Ser Vael"));
        assertFalse("no count: " + all, all.contains("37"));
        assertFalse("no objective: " + all, all.toLowerCase().contains("seat"));
        assertEquals(-1, only(100).exactSoldiers);
        assertNull(only(100).objective);
    }

    @Test
    public void theSizeIsAnEstimateInFourWords() {
        assertEquals(ArmyKnowledge.Size.BAND, ArmyKnowledge.Size.of(9));
        assertEquals(ArmyKnowledge.Size.COMPANY, ArmyKnowledge.Size.of(10));
        assertEquals(ArmyKnowledge.Size.COMPANY, ArmyKnowledge.Size.of(20));
        assertEquals(ArmyKnowledge.Size.HOST, ArmyKnowledge.Size.of(21));
        assertEquals(ArmyKnowledge.Size.HOST, ArmyKnowledge.Size.of(40));
        assertEquals(ArmyKnowledge.Size.GREAT_HOST, ArmyKnowledge.Size.of(41));
    }

    /** A48: the captain only if their banner was seen. */
    @Test
    public void aCaptainIsKnownOnlyFromTheSameChunkAndStaysKnown() {
        Army a = army(1, HOUSE, 11, 10, 15);
        known.onTurn(roster, 10, 10, 100);
        assertEquals("across an edge, no banner", -1, only(100).captainId);
        known.onTurn(roster, 11, 10, 101);
        assertEquals(77, only(101).captainId);
        a.captainId = 99;
        known.onTurn(roster, 10, 10, 102);
        assertEquals("what was seen is remembered", 77, only(102).captainId);
    }

    @Test
    public void rumoursAreRoughAndAgeLikeTheRest() {
        army(1, HOUSE, 21, 30, 15);
        known.hearWarTable(roster, 500);
        ArmyKnowledge.Marker m = only(500);
        assertTrue(m.rumour());
        assertEquals("the region, not the chunk", 22, m.x);
        assertEquals(30, m.y);
        assertTrue("fainter than a sighting", m.strength(500) < 1f);
        long later = 500 + HistoryManager.SLEEP_TURNS;
        assertTrue(only(later).strength(later) < m.strength(500));
        assertTrue(markers(500 + HistoryManager.SEASON_TURNS).isEmpty());
    }

    @Test
    public void theWarTableSpeaksOfHostsNotOfHuntersOrForagers() {
        army(1, HOUSE, 100, 100, 5).kind = Army.Kind.HUNTING_PARTY;
        army(2, HOUSE, 100, 100, 5).kind = Army.Kind.FORAGERS;
        known.hearWarTable(roster, 500);
        assertTrue(markers(500).isEmpty());
    }

    /** A47: a sighting only ages. A sleep beside the Table does not turn it back into a rumour. */
    @Test
    public void wordOfAHostDoesNotUnseatASighting() {
        Army a = army(1, HOUSE, 21, 30, 15);
        known.onTurn(roster, 21, 30, 500);
        a.x = 60;
        long later = 500 + HistoryManager.SLEEP_TURNS;
        known.hearWarTable(roster, later);
        known.hearInTown(roster, later, 0);
        ArmyKnowledge.Marker m = only(later);
        assertEquals(ArmyKnowledge.Source.SIGHTED, m.source);
        assertEquals(21, m.x);
        assertEquals(1, m.sleepsAgo(later));
        long dropped = 500 + HistoryManager.SEASON_TURNS;
        known.hearWarTable(roster, dropped);
        assertEquals("once it is dropped, word may stand", ArmyKnowledge.Source.WAR_TABLE, only(dropped).source);
    }

    @Test
    public void aTownsNamedPlaceBettersTheTablesRegionEvenIfOlder() {
        Army a = army(1, HOUSE, 21, 30, 15);
        a.trail = new int[]{WarRoster.packedChunk(19, 30)};
        long clock = HistoryManager.SEASON_TURNS * 4 + 300;
        known.hearWarTable(roster, clock);
        known.hearInTown(roster, clock, 0);
        assertEquals(ArmyKnowledge.Source.TOWN, only(clock).source);
        assertEquals(19, only(clock).x);
    }

    /** #328: a Hunting Party shows on the map only when seen. */
    @Test
    public void huntersLeaveNoWordInTheirTracks() {
        Army a = army(1, HOUSE, 8, 8, 5);
        a.kind = Army.Kind.HUNTING_PARTY;
        a.trail = new int[]{WarRoster.packedChunk(5, 5)};
        assertNull(known.readTracks(roster, 5, 5, 700));
        assertTrue(markers(700).isEmpty());
    }

    @Test
    public void aFresherSightingReplacesARumourAndARumourOfTheSameMomentDoesNot() {
        army(1, HOUSE, 21, 30, 15);
        known.hearWarTable(roster, 500);
        known.onTurn(roster, 21, 30, 500);
        assertEquals(ArmyKnowledge.Source.SIGHTED, only(500).source);
        known.hearWarTable(roster, 500);
        assertEquals("what was seen stands", ArmyKnowledge.Source.SIGHTED, only(500).source);
        assertEquals(21, only(500).x);
    }

    @Test
    public void aTownSpeaksOfSomewhereTheHostHasBeenWithinTheSeason() {
        Army a = army(1, HOUSE, 8, 0, 15);
        a.trail = new int[]{WarRoster.packedChunk(2, 0), WarRoster.packedChunk(4, 0), WarRoster.packedChunk(6, 0)};
        long clock = HistoryManager.SEASON_TURNS * 4 + 300;
        for (int pick = 0; pick < 30; pick++) {
            ArmyKnowledge.Marker said = new ArmyKnowledge().hearInTown(roster, clock, pick);
            assertTrue("on its road: " + said.x, said.x == 2 || said.x == 4 || said.x == 6);
            assertEquals("it marched on from there", 1, said.dx);
            assertTrue("within the Season", clock - said.observedAt < HistoryManager.SEASON_TURNS);
            assertTrue(said.rumour());
        }
    }

    @Test
    public void aTownHasNothingToSayWithNoHostsInTheField() {
        assertNull(known.hearInTown(roster, 100, 1));
    }

    /** A49: a sworn player's own hosts are exact, and nobody else's are. */
    @Test
    public void aSwornPlayersOwnHostsAreExactAndNobodyElsesAre() {
        Army own = army(1, HOUSE, 50, 50, 23);
        own.objective = Army.Objective.HUNT;
        own.targetX = 60;
        own.targetY = 50;
        army(2, LEAGUE, 11, 10, 23).objective = Army.Objective.SEAT;
        known.onTurn(roster, 10, 10, 100);
        List<ArmyKnowledge.Marker> all = known.markers(roster, HOUSE, Collections.emptyList(), 100);
        assertEquals(2, all.size());
        for (ArmyKnowledge.Marker m : all) {
            if (m.houseId == HOUSE) {
                assertTrue(m.exact());
                assertEquals(50, m.x);
                assertEquals(23, m.exactSoldiers);
                assertEquals(Army.Objective.HUNT, m.objective);
                String card = String.join(" ", ArmyKnowledge.card(m, "House Gullet", null, 100));
                assertTrue(card, card.contains("23 soldiers") && card.contains("Hunting"));
            } else {
                assertFalse(m.exact());
                assertEquals(-1, m.exactSoldiers);
                assertNull(m.objective);
            }
        }
        assertEquals("unsworn, the far host is unknown", 1, markers(100).size());
    }

    /** An oath's end takes the exact view with it, and nothing of it stayed in what is remembered. */
    @Test
    public void whatASwornPlayerKnewExactlyIsNotRemembered() {
        army(1, HOUSE, 50, 50, 23);
        known.markers(roster, HOUSE, Collections.emptyList(), 100);
        assertTrue(markers(100).isEmpty());
    }

    @Test
    public void aSiegeIsPublicAndNeverAges() {
        ArmyKnowledge.Marker siege = ArmyKnowledge.siege(HOUSE, 5, 6, 100);
        assertTrue(siege.exact());
        assertEquals(1f, siege.strength(100000), 0f);
        List<ArmyKnowledge.Marker> all = known.markers(roster, -1, Collections.singletonList(siege), 100);
        assertEquals(1, all.size());
        assertEquals(5, all.get(0).x);
    }

    @Test
    public void aBattleInEarshotIsMarkedInItsRoughDirection() {
        ArmyBattle b = new ArmyBattle();
        b.id = 9;
        b.x = 14;
        b.y = 11;
        roster.battles.add(b);
        assertTrue(known.onTurn(roster, 10, 10, 100));
        List<ArmyKnowledge.Sound> heard = known.sounds(100);
        assertEquals(1, heard.size());
        assertEquals("its region, not its chunk", 14, heard.get(0).x);
        assertEquals(10, heard.get(0).y);
        assertFalse("still the same news", known.onTurn(roster, 10, 10, 101));
        assertTrue(known.sounds(100 + HistoryManager.SEASON_TURNS + 1).isEmpty());
    }

    @Test
    public void aBattleOutOfEarshotIsNotHeard() {
        ArmyBattle b = new ArmyBattle();
        b.id = 9;
        b.x = 50;
        b.y = 10;
        roster.battles.add(b);
        known.onTurn(roster, 10, 10, 100);
        assertTrue(known.sounds(100).isEmpty());
    }

    @Test
    public void aTrailSaysWhereItLedAndRefreshesTheMarker() {
        Army a = army(1, HOUSE, 8, 8, 15);
        a.trail = new int[]{WarRoster.packedChunk(4, 4), WarRoster.packedChunk(5, 5), WarRoster.packedChunk(6, 6)};
        long clock = HistoryManager.SEASON_TURNS * 4 + 300;
        known.onTurn(roster, 8, 8, HistoryManager.SEASON_TURNS * 3L + 400);
        long seen = HistoryManager.SEASON_TURNS * 3L + 400;
        assertEquals(seen, only(seen).observedAt);
        ArmyKnowledge.Tracks t = known.readTracks(roster, 5, 5, clock);
        assertNotNull(t);
        assertEquals(1, t.marker.dx);
        assertEquals(1, t.marker.dy);
        String line = ArmyKnowledge.tracksLine(t, "House Gullet");
        assertTrue(line, line.contains("lead north-east"));
        assertTrue(line, line.contains("a few days old"));
        ArmyKnowledge.Marker refreshed = only(clock);
        assertEquals(ArmyKnowledge.Source.TRACKS, refreshed.source);
        assertEquals(5, refreshed.x);
    }

    @Test
    public void noTrailNoTracks() {
        army(1, HOUSE, 8, 8, 15);
        assertNull(known.readTracks(roster, 5, 5, 100));
        assertTrue(markers(100).isEmpty());
    }

    @Test
    public void tracksDoNotUnseatSomethingFresherSeen() {
        Army a = army(1, HOUSE, 8, 8, 15);
        a.trail = new int[]{WarRoster.packedChunk(5, 5)};
        long clock = HistoryManager.SEASON_TURNS * 4 + 300;
        known.onTurn(roster, 8, 8, clock);
        known.readTracks(roster, 5, 5, clock + 1);
        assertEquals(ArmyKnowledge.Source.SIGHTED, only(clock + 1).source);
        assertEquals(8, only(clock + 1).x);
    }

    @Test
    public void knowledgeSurvivesSavingAsPlainNumbers() {
        Army a = army(1, HOUSE, 11, 10, 15);
        known.onTurn(roster, 11, 10, 100);
        a.x = 12;
        known.onTurn(roster, 11, 10, 101);
        ArmyBattle b = new ArmyBattle();
        b.id = 9;
        b.x = 14;
        b.y = 10;
        roster.battles.add(b);
        known.onTurn(roster, 11, 10, 102);

        ArmyKnowledge back = new ArmyKnowledge();
        back.decode(known.encodedMarkers(), known.encodedSounds());
        ArmyKnowledge.Marker was = only(102), is = back.markers(roster, -1, Collections.emptyList(), 102).get(0);
        assertEquals(was.x, is.x);
        assertEquals(was.captainId, is.captainId);
        assertEquals(was.observedAt, is.observedAt);
        assertEquals(was.size, is.size);
        assertEquals(was.source, is.source);
        assertArrayEquals("the road it has been known on", was.past, is.past);
        assertEquals(1, back.sounds(102).size());
    }

    /** A49: nothing the player knows reaches the history. */
    @Test
    public void nothingThePlayerKnowsReachesTheWar() {
        Army a = army(1, HOUSE, 11, 10, 15);
        a.objective = Army.Objective.SEAT;
        a.targetX = 30;
        a.trail = new int[]{WarRoster.packedChunk(9, 10), WarRoster.packedChunk(10, 10)};
        ArmyBattle b = new ArmyBattle();
        b.id = 9;
        b.x = 14;
        b.y = 11;
        roster.battles.add(b);
        Json json = new Json();
        String before = json.toJson(roster);

        known.onTurn(roster, 10, 10, 100);
        known.hearWarTable(roster, 101);
        known.hearInTown(roster, 102, 5);
        known.readTracks(roster, 10, 10, 103);
        known.markers(roster, HOUSE, Collections.emptyList(), 104);
        known.sounds(105);
        known.encodedMarkers();

        assertEquals(before, json.toJson(roster));
    }

    /** The other half: the type of the model takes nothing of the history but what it reads. */
    @Test
    public void knowledgeIsNotPartOfTheWarsSave() {
        Json json = new Json();
        assertFalse(json.toJson(new WarRoster()).contains("observedAt"));
        for (java.lang.reflect.Field f : WarRoster.class.getFields()) {
            assertNotEquals(f.getName(), ArmyKnowledge.class, f.getType());
        }
        for (java.lang.reflect.Field f : com.bpm.minotaur.gamedata.history.HistorySaveData.class.getFields()) {
            assertNotEquals(f.getName(), ArmyKnowledge.class, f.getType());
        }
    }
}
