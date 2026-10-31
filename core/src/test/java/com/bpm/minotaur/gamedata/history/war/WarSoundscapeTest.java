package com.bpm.minotaur.gamedata.history.war;

import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.gamedata.Direction;
import com.bpm.minotaur.gamedata.history.war.WarSources.Kind;
import com.bpm.minotaur.gamedata.history.war.WarSoundscape.Bed;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/** Living War A53, A55, A56: only real things sound, by what they are and how far off. */
public class WarSoundscapeTest {

    private static final GridPoint2 ME = new GridPoint2(0, 0);

    private static WarSoundscape.Mix hear(Kind kind, int d) {
        return hear(kind, d, 1);
    }

    private static WarSoundscape.Mix hear(Kind kind, int d, int level) {
        return WarSoundscape.mix(WarSources.none().add(kind, d, 0), ME, level, Direction.NORTH, false);
    }

    private static boolean shot(WarSoundscape.Mix m, String key) {
        for (WarSoundscape.Shot s : m.shots) if (s.key.equals(key)) return true;
        return false;
    }

    @Test
    public void withNoSourceInRangeThereIsSilence() {
        assertTrue(WarSoundscape.mix(WarSources.none(), ME, 1, Direction.NORTH, false).silent());
        assertTrue(WarSoundscape.mix(WarSources.none(), ME, 3, Direction.NORTH, false).silent());
        for (Kind k : Kind.values()) {
            assertTrue(k + " beyond earshot", hear(k, WarSoundscape.EARSHOT + 1).silent());
        }
    }

    @Test
    public void aBattleHereOrNextDoorIsSteelScreamsAndVolleys() {
        for (int d = 0; d <= 1; d++) {
            WarSoundscape.Mix m = hear(Kind.BATTLE, d);
            assertTrue(m.volume(Bed.NEAR) > 0f);
        }
        WarSoundscape.Mix next = hear(Kind.BATTLE, 1);
        assertTrue(shot(next, "clash_sword"));
        assertTrue(shot(next, "volley_archers"));
        assertTrue(shot(next, "monster_grunt_light"));
    }

    @Test
    public void aBattleTwoOrThreeOffIsRoarDrumsAndHorn() {
        for (int d = 2; d <= 3; d++) {
            WarSoundscape.Mix m = hear(Kind.BATTLE, d);
            assertEquals(0f, m.volume(Bed.NEAR), 0f);
            assertTrue(m.volume(Bed.FAR) > 0f);
            assertTrue(m.volume(Bed.DRUMS) > 0f);
            assertTrue(shot(m, "horn_battle"));
            assertFalse(shot(m, "clash_sword"));
        }
    }

    @Test
    public void aBattleFourToSixOffIsAFaintRoarAndALoneHorn() {
        for (int d = 4; d <= 6; d++) {
            WarSoundscape.Mix m = hear(Kind.BATTLE, d);
            assertEquals(WarSoundscape.FAR_EDGE, m.volume(Bed.FAR), 0f);
            assertTrue(shot(m, "horn_battle"));
        }
    }

    @Test
    public void aHostMarchingIsDrumsAndFeetThenFaintDrumsThenNothing() {
        for (int d = 0; d <= 1; d++) {
            WarSoundscape.Mix m = hear(Kind.MARCH, d);
            assertTrue(m.volume(Bed.DRUMS) > 0f);
            assertTrue(m.volume(Bed.MARCH) > 0f);
        }
        for (int d = 2; d <= 3; d++) {
            WarSoundscape.Mix m = hear(Kind.MARCH, d);
            assertEquals(WarSoundscape.DRUMS_FAINT, m.volume(Bed.DRUMS), 0f);
            assertEquals("no feet at this range", 0f, m.volume(Bed.MARCH), 0f);
        }
        for (int d = 4; d <= 6; d++) assertTrue(hear(Kind.MARCH, d).silent());
    }

    @Test
    public void aSmallPartyIsOnlyFeetCloseBy() {
        for (int d = 0; d <= 1; d++) {
            WarSoundscape.Mix m = hear(Kind.PARTY, d);
            assertTrue(m.volume(Bed.MARCH) > 0f);
            assertEquals(0f, m.volume(Bed.DRUMS), 0f);
        }
        for (int d = 2; d <= 6; d++) assertTrue(hear(Kind.PARTY, d).silent());
    }

    @Test
    public void aCampIsCampNoiseCloseByAndNothingElse() {
        for (int d = 0; d <= 1; d++) assertTrue(hear(Kind.CAMP, d).volume(Bed.CAMP) > 0f);
        for (int d = 2; d <= 6; d++) assertTrue(hear(Kind.CAMP, d).silent());
    }

    @Test
    public void aSeatInvestedIsWorksThenThudsAndAHornThenNothing() {
        for (int d = 0; d <= 1; d++) {
            WarSoundscape.Mix m = hear(Kind.INVESTED, d);
            assertTrue(m.volume(Bed.CAMP) > 0f);
            assertTrue("the works going up", shot(m, "thud_distant_1"));
        }
        for (int d = 2; d <= 3; d++) {
            WarSoundscape.Mix m = hear(Kind.INVESTED, d);
            assertEquals(0f, m.volume(Bed.CAMP), 0f);
            assertTrue(shot(m, "thud_distant_2"));
            assertTrue(shot(m, "horn_battle"));
        }
        for (int d = 4; d <= 6; d++) assertTrue(hear(Kind.INVESTED, d).silent());
    }

    @Test
    public void theCastlesSiegeIsEnginesAndAGreatHornFainterWithDistance() {
        float last = Float.MAX_VALUE;
        for (int d = 0; d <= 6; d++) {
            WarSoundscape.Mix m = hear(Kind.CASTLE_SIEGE, d);
            assertTrue(shot(m, "siege_barrage"));
            assertTrue(shot(m, "horn_great"));
            float loud = 0f;
            for (WarSoundscape.Shot s : m.shots) if (s.key.equals("siege_barrage")) loud = s.volume;
            assertTrue("fainter at " + d, loud < last);
            last = loud;
        }
    }

    @Test
    public void crowsCircleAnAftermathFromNextDoor() {
        for (int d = 0; d <= 1; d++) assertTrue(hear(Kind.AFTERMATH, d).volume(Bed.CROWS) > 0f);
        for (int d = 2; d <= 6; d++) assertTrue(hear(Kind.AFTERMATH, d).silent());
    }

    @Test
    public void itComesFromTheSideItIsOn() {
        assertTrue(hear(Kind.BATTLE, 2).pan(Bed.FAR) > 0.5f);
        assertTrue(hear(Kind.BATTLE, -2).pan(Bed.FAR) < -0.5f);
        WarSoundscape.Mix ahead = WarSoundscape.mix(WarSources.none().add(Kind.BATTLE, 2, 0), ME, 1, Direction.EAST, false);
        assertEquals(0f, ahead.pan(Bed.FAR), 0.01f);
    }

    @Test
    public void underTheGroundOnlyABattleOverheadIsHeard() {
        WarSoundscape.Mix m = hear(Kind.BATTLE, 1, 2);
        assertTrue(m.volume(Bed.UNDER) > 0f);
        assertEquals(0f, m.volume(Bed.NEAR), 0f);
        assertFalse(shot(m, "clash_sword"));
        assertTrue(shot(m, "thud_distant_1"));
        assertTrue("too far overhead to feel", hear(Kind.BATTLE, 5, 2).silent());
        for (Kind k : Kind.values()) {
            if (k != Kind.BATTLE) assertTrue(k + " is not heard below", hear(k, 0, 2).silent());
        }
    }

    @Test
    public void aFightJustOverheadShakesTheStone() {
        boolean close = false, far = false;
        for (WarSoundscape.Shot s : hear(Kind.BATTLE, 1, 2).shots) close |= s.tremor;
        for (WarSoundscape.Shot s : hear(Kind.BATTLE, 3, 2).shots) far |= s.tremor;
        assertTrue("next door overhead, the lights gutter", close);
        assertFalse("three chunks off, only a rumble", far);
    }

    @Test
    public void aGashShakesWhenItIsToldItDoes() {
        WarSoundscape.Mix m = WarSoundscape.mix(WarSources.none(), ME, 3, Direction.NORTH, true);
        assertTrue(m.volume(Bed.UNDER) > 0f);
    }

    // ---- One-shots at a battle's start and rout ----

    private static List<WarSoundscape.Shot> news(WarSoundscape.BattleWatch w, WarSources s, long clock) {
        return w.onTurn(s, clock, ME, 1, Direction.NORTH);
    }

    @Test
    public void aHornAtABattlesStartAndAWarHornAtItsRoutOutToSixChunks() {
        WarSoundscape.BattleWatch watch = new WarSoundscape.BattleWatch();
        assertTrue("the first look only notes what is under way", news(watch, WarSources.none().add(Kind.BATTLE, 9, 0), 10).isEmpty());
        WarSources one = WarSources.none().add(Kind.BATTLE, 9, 0);
        WarSources two = WarSources.none().add(Kind.BATTLE, 9, 0).add(Kind.BATTLE, 3, 0);
        List<WarSoundscape.Shot> started = news(watch, two, 11);
        assertEquals(1, started.size());
        assertEquals("horn_battle", started.get(0).key);
        assertEquals("certain, not a chance", 1f, started.get(0).chance, 0f);
        assertTrue("it goes on", news(watch, two, 12).isEmpty());
        List<WarSoundscape.Shot> routed = news(watch, one, 13);
        assertEquals(1, routed.size());
        assertEquals("war_horn", routed.get(0).key);
    }

    @Test
    public void aBattleBeyondEarshotBlowsNoHorn() {
        WarSoundscape.BattleWatch watch = new WarSoundscape.BattleWatch();
        news(watch, WarSources.none(), 10);
        assertTrue(news(watch, WarSources.none().add(Kind.BATTLE, 7, 0), 11).isEmpty());
        assertTrue("nor at its end", news(watch, WarSources.none(), 12).isEmpty());
    }

    @Test
    public void whatChangedWhileThePlayerSleptSoundsNoHorn() {
        WarSoundscape.BattleWatch watch = new WarSoundscape.BattleWatch();
        news(watch, WarSources.none(), 10);
        assertTrue(news(watch, WarSources.none().add(Kind.BATTLE, 3, 0), 260).isEmpty());
    }

    @Test
    public void belowGroundNoHornIsHeard() {
        WarSoundscape.BattleWatch watch = new WarSoundscape.BattleWatch();
        watch.onTurn(WarSources.none(), 10, ME, 2, Direction.NORTH);
        assertTrue(watch.onTurn(WarSources.none().add(Kind.BATTLE, 3, 0), 11, ME, 2, Direction.NORTH).isEmpty());
    }
}
