package com.bpm.minotaur.gamedata.history.war.army;

import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.gamedata.history.war.EncounterLedger;
import com.bpm.minotaur.gamedata.history.war.WarSources;
import com.bpm.minotaur.gamedata.history.war.WarSources.Kind;
import com.bpm.minotaur.gamedata.history.war.WarSources.Plume;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

/** Living War A53-A57: what the armies really are doing is what is heard and seen. */
public class WarSourcesTest {

    private static final GridPoint2 ME = new GridPoint2(0, 0);

    private final List<Army> armies = new ArrayList<>();
    private final List<ArmyBattle> battles = new ArrayList<>();

    private Army army(int id, int house, int x, int y, int soldiers, boolean marching) {
        Army a = new Army();
        a.id = id;
        a.houseId = house;
        a.x = x;
        a.y = y;
        a.targetX = x;
        a.targetY = y;
        a.soldiers = soldiers;
        if (marching) a.path = new int[]{1, 2, 3};
        armies.add(a);
        return a;
    }

    private WarSources sources(EncounterLedger ledger) {
        return WarSources.of(armies, battles, null, Collections.emptyList(), ledger, null, 100);
    }

    private static Kind only(WarSources s) {
        assertEquals(1, s.all().size());
        return s.all().get(0).kind;
    }

    @Test
    public void armiesAreSortedByWhatTheyAreDoing() {
        army(1, 3, 1, 0, 15, true);
        assertEquals(Kind.MARCH, only(sources(null)));
        armies.clear();
        army(1, 3, 1, 0, 9, true);
        assertEquals(Kind.PARTY, only(sources(null)));
        armies.clear();
        army(1, 3, 1, 0, 15, false);
        assertEquals(Kind.CAMP, only(sources(null)));
        armies.clear();
        army(1, 3, 1, 0, 15, false).objective = Army.Objective.SEAT;
        assertEquals(Kind.INVESTED, only(sources(null)));
        armies.clear();
        army(1, 3, 1, 0, 5, false).kind = Army.Kind.RAID;
        assertEquals(Kind.BURNING, only(sources(null)));
    }

    @Test
    public void anArmyInABattleIsHeardAsTheBattle() {
        army(1, 3, 1, 0, 15, false).battle = true;
        ArmyBattle b = new ArmyBattle();
        b.x = 1;
        battles.add(b);
        assertEquals(Kind.BATTLE, only(sources(null)));
    }

    @Test
    public void theDeadMakeNoSound() {
        army(1, 3, 1, 0, 0, true);
        assertTrue(sources(null).all().isEmpty());
    }

    @Test
    public void aMarchingHostRaisesNoSmoke() {
        army(1, 3, 2, 0, 30, true);
        assertTrue(sources(null).smoke(ME).isEmpty());
    }

    @Test
    public void eachFireStandsAtItsOwnSize() {
        WarSources s = WarSources.none().add(Kind.BATTLE, 3, 0).add(Kind.INVESTED, 4, 0).add(Kind.CASTLE_SIEGE, 5, 0)
                .add(Kind.BURNING, 6, 0).add(Kind.AFTERMATH, 7, 0).add(Kind.CAMP, 8, 0);
        List<Plume> seen = new ArrayList<>();
        for (WarSources.Smoke m : s.smoke(ME)) seen.add(m.plume);
        assertEquals(java.util.Arrays.asList(Plume.BATTLE, Plume.SIEGE, Plume.SIEGE, Plume.MEDIUM, Plume.MEDIUM, Plume.WISP), seen);
    }

    @Test
    public void smokeIsSeenTwentyFourChunksAndAWispOnlyEight() {
        assertEquals(1, WarSources.none().add(Kind.BATTLE, 24, 0).smoke(ME).size());
        assertTrue(WarSources.none().add(Kind.BATTLE, 25, 0).smoke(ME).isEmpty());
        assertEquals(1, WarSources.none().add(Kind.CAMP, 8, 0).smoke(ME).size());
        assertTrue(WarSources.none().add(Kind.CAMP, 9, 0).smoke(ME).isEmpty());
    }

    @Test
    public void whatBurnsInThePlayersOwnChunkIsNotOnTheHorizon() {
        assertTrue(WarSources.none().add(Kind.BATTLE, 0, 0).smoke(ME).isEmpty());
    }

    @Test
    public void atTheCapTheTallestAndNearestStand() {
        WarSources s = WarSources.none();
        for (int i = 1; i <= 15; i++) s.add(Kind.AFTERMATH, i, 0);
        s.add(Kind.BATTLE, 20, 0);
        List<WarSources.Smoke> seen = s.smoke(ME);
        assertEquals(WarSources.SMOKE_MAX, seen.size());
        assertEquals("a battle before any burnt ground", Plume.BATTLE, seen.get(0).plume);
        assertEquals(1, seen.get(1).x);
        assertEquals(11, seen.get(11).x);
    }

    @Test
    public void burntGroundThinsOverThreeSleeps() {
        EncounterLedger ledger = new EncounterLedger();
        EncounterLedger.Field f = new EncounterLedger.Field();
        f.chunkX = 2;
        f.clearAtSleep = 3;
        ledger.fields.add(f);
        assertEquals(1f, sources(ledger).smoke(ME).get(0).strength, 0.001f);
        ledger.sleeps = 2;
        assertEquals(1f / 3f, sources(ledger).smoke(ME).get(0).strength, 0.001f);
        ledger.sleeps = 3;
        assertTrue("gone", sources(ledger).smoke(ME).isEmpty());
    }

    /** A56: a gash at war with nothing near its seat does not shake. */
    @Test
    public void aGashAtWarWithNothingNearItsSeatDoesNotShake() {
        GridPoint2 seat = new GridPoint2(20, 20), gash = new GridPoint2(5, 5);
        assertFalse(WarSources.none().gashShakes(seat, gash));
        assertFalse("a battle elsewhere", WarSources.none().add(Kind.BATTLE, 40, 40).gashShakes(seat, gash));
        assertFalse("a host marching by", WarSources.none().add(Kind.MARCH, 20, 20).gashShakes(seat, gash));
        assertFalse("a camp at its seat is no siege", WarSources.none().add(Kind.CAMP, 20, 20).gashShakes(seat, gash));
    }

    @Test
    public void aGashShakesWhileItsSeatIsInvestedOrAssaultedOrABattleIsOverIt() {
        GridPoint2 seat = new GridPoint2(20, 20), gash = new GridPoint2(5, 5);
        assertTrue(WarSources.none().add(Kind.INVESTED, 20, 20).gashShakes(seat, gash));
        assertTrue(WarSources.none().add(Kind.BATTLE, 20, 20).gashShakes(seat, gash));
        assertTrue(WarSources.none().add(Kind.BATTLE, 5, 5).gashShakes(seat, gash));
    }

    /** A57: a muster's beacons run toward the host's objective, catching one by one. */
    @Test
    public void aMusterLightsAChainTowardItsObjective() {
        GridPoint2 seat = new GridPoint2(10, 10);
        Army a = army(1, 3, 10, 10, 15, true);
        a.targetX = 30;
        a.targetY = 10;
        a.musteredAt = 95;
        WarSources s = WarSources.of(armies, battles, null, Collections.emptyList(), null, h -> seat, 100);
        assertEquals(1, s.beacons().size());
        assertEquals(seat, s.beacons().get(0).from);
        assertEquals(new GridPoint2(30, 10), s.beacons().get(0).to);
        assertEquals("catching one by one", 2, s.beacons().get(0).lit);
        a.musteredAt = 0;
        assertTrue("long since burnt out", WarSources.of(armies, battles, null, Collections.emptyList(), null, h -> seat, 100).beacons().isEmpty());
    }

    @Test
    public void anOldArmyLightsNothing() {
        army(1, 3, 10, 10, 15, true);
        assertTrue(WarSources.of(armies, battles, null, Collections.emptyList(), null, h -> new GridPoint2(10, 10), 100).beacons().isEmpty());
    }

    /** A57: invested, a seat calls for relief toward its lord's other hosts. */
    @Test
    public void anInvestedSeatCallsToItsLordsNearestHost() {
        GridPoint2 seat = new GridPoint2(10, 10);
        army(1, 3, 10, 10, 15, false).objective = Army.Objective.SEAT;
        army(2, 4, 30, 10, 15, true);
        army(3, 4, 15, 10, 15, true);
        WarSources s = WarSources.of(armies, battles, null, Collections.emptyList(), null, h -> h == 4 ? seat : new GridPoint2(90, 90), 100);
        assertEquals(1, s.beacons().size());
        assertEquals(seat, s.beacons().get(0).from);
        assertEquals("the nearest", new GridPoint2(15, 10), s.beacons().get(0).to);
    }
}
