package com.bpm.minotaur.gamedata.history.war;

import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.gamedata.Direction;
import org.junit.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

/** Living War W9: the war heard by distance, from the side it is on. */
public class WarSoundscapeTest {

    private static final GridPoint2 ME = new GridPoint2(0, 0);

    private static Encounter fightAt(int x, int y) {
        GridPoint2 at = new GridPoint2(x, y);
        return new Encounter(Encounter.Kind.SKIRMISH, 1, 0, 0, 1, 0, 100, at, at);
    }

    private static WarSoundscape.Mix hear(Encounter e, int level) {
        return WarSoundscape.mix(List.of(e), Collections.emptyList(), 10, ME, level, Direction.NORTH, false);
    }

    private static boolean shot(WarSoundscape.Mix m, String key) {
        for (WarSoundscape.Shot s : m.shots) if (s.key.equals(key)) return true;
        return false;
    }

    @Test
    public void nextDoorIsSteelAndScreams() {
        WarSoundscape.Mix m = hear(fightAt(1, 0), 1);
        assertTrue(m.volume(WarSoundscape.Bed.NEAR) > 0f);
        assertTrue(shot(m, "clash_sword"));
        assertTrue(shot(m, "volley_archers"));
    }

    @Test
    public void twoOrThreeChunksOffIsHornsAndDrumsAndAFarRoar() {
        for (int d = 2; d <= 3; d++) {
            WarSoundscape.Mix m = hear(fightAt(d, 0), 1);
            assertEquals(0f, m.volume(WarSoundscape.Bed.NEAR), 0f);
            assertTrue(m.volume(WarSoundscape.Bed.FAR) > 0f);
            assertTrue(m.volume(WarSoundscape.Bed.DRUMS) > 0f);
            assertTrue(shot(m, "horn_battle"));
            assertFalse(shot(m, "clash_sword"));
        }
    }

    @Test
    public void itFadesWithDistanceAndIsGoneBeyondEarshot() {
        float last = Float.MAX_VALUE;
        for (int d = 1; d <= EncounterScheduler.EARSHOT; d++) {
            WarSoundscape.Mix m = hear(fightAt(d, 0), 1);
            float loudest = Math.max(m.volume(WarSoundscape.Bed.NEAR), m.volume(WarSoundscape.Bed.FAR));
            assertTrue("heard at " + d, loudest > 0f);
            assertTrue("no louder at " + d, loudest <= last);
            last = loudest;
        }
        assertTrue(hear(fightAt(EncounterScheduler.EARSHOT + 1, 0), 1).silent());
    }

    @Test
    public void itComesFromTheSideItIsOn() {
        // Facing north, a fight to the east is on the right; to the west, on the left.
        assertTrue(hear(fightAt(2, 0), 1).pan(WarSoundscape.Bed.FAR) > 0.5f);
        assertTrue(hear(fightAt(-2, 0), 1).pan(WarSoundscape.Bed.FAR) < -0.5f);
        // Turned to face it, it is dead ahead.
        WarSoundscape.Mix ahead = WarSoundscape.mix(List.of(fightAt(2, 0)), Collections.emptyList(), 10, ME, 1,
                Direction.EAST, false);
        assertEquals(0f, ahead.pan(WarSoundscape.Bed.FAR), 0.01f);
    }

    @Test
    public void underTheGroundItIsARumbleAndNoSteel() {
        WarSoundscape.Mix m = hear(fightAt(1, 0), 2);
        assertTrue(m.volume(WarSoundscape.Bed.UNDER) > 0f);
        assertEquals(0f, m.volume(WarSoundscape.Bed.NEAR), 0f);
        assertFalse(shot(m, "clash_sword"));
        assertTrue(shot(m, "thud_distant_1"));
        assertTrue("too far overhead to feel", hear(fightAt(5, 0), 2).silent());
    }

    @Test
    public void aGashAtWarShakesWhateverTheDistance() {
        WarSoundscape.Mix m = WarSoundscape.mix(Collections.emptyList(), Collections.emptyList(), 10, ME, 3,
                Direction.NORTH, true);
        assertTrue(m.volume(WarSoundscape.Bed.UNDER) > 0f);
    }

    @Test
    public void aColumnIsHeardAsDrums() {
        Encounter col = new Encounter(Encounter.Kind.COLUMN, 1, 0, 0, -1, 0, 100, new GridPoint2(1, 0), new GridPoint2(1, 0));
        WarSoundscape.Mix m = hear(col, 1);
        assertTrue(m.volume(WarSoundscape.Bed.DRUMS) > 0f);
        assertEquals(0f, m.volume(WarSoundscape.Bed.NEAR), 0f);
    }

    @Test
    public void aFrontIsHeardFromItsNearestEdge() {
        Front f = new Front(0, 0, 1, new GridPoint2(3, 0));
        WarSoundscape.Mix m = WarSoundscape.mix(Collections.emptyList(), List.of(f), 10, ME, 1, Direction.NORTH, false);
        assertTrue("its edge is two chunks off", m.volume(WarSoundscape.Bed.FAR) >= WarSoundscape.FAR_2);
    }
}
