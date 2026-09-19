package com.bpm.minotaur.rendering.weaponview;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class WeaponViewCycleTest {

    private final WeaponViewCycle cycle = new WeaponViewCycle(
            Arrays.asList("DAGGER", "SWORD", "LONGSWORD", "AXE"));

    @Test
    public void startsOnWhatIsEquipped() {
        assertFalse(cycle.isPreviewing());
        assertNull(cycle.current());
        assertEquals(0, cycle.position());
    }

    @Test
    public void firstStepLandsNextToTheEquippedWeapon() {
        // Not at the top of a list of hundreds: ] from a sword shows the one after it.
        cycle.next("SWORD");
        assertEquals("LONGSWORD", cycle.current());

        cycle.reset();
        cycle.previous("SWORD");
        assertEquals("DAGGER", cycle.current());
    }

    @Test
    public void laterStepsIgnoreTheEquippedWeapon() {
        cycle.next("SWORD");
        cycle.next("SWORD");
        assertEquals("AXE", cycle.current());
        assertEquals(4, cycle.position());
    }

    @Test
    public void anUnlistedOrEmptyHandStartsAtTheTop() {
        cycle.next(null);
        assertEquals("DAGGER", cycle.current());

        cycle.reset();
        cycle.previous("WAND");
        assertEquals("DAGGER", cycle.current());
    }

    @Test
    public void stopsAtTheEndsRatherThanWrapping() {
        // As the paperdoll editor does, so the end of a pass is visible.
        cycle.next("AXE");
        assertEquals("AXE", cycle.current());
        cycle.next(null);
        assertEquals("AXE", cycle.current());

        cycle.reset();
        cycle.previous("DAGGER");
        cycle.previous(null);
        assertEquals("DAGGER", cycle.current());
    }

    @Test
    public void resetGoesBackToWhatIsEquipped() {
        cycle.next("SWORD");
        cycle.reset();
        assertFalse(cycle.isPreviewing());
        assertNull(cycle.current());
    }

    @Test
    public void anEmptyListNeverPreviews() {
        WeaponViewCycle empty = new WeaponViewCycle(Collections.<String>emptyList());
        empty.next("SWORD");
        empty.previous("SWORD");
        assertFalse(empty.isPreviewing());
        assertTrue(empty.size() == 0);
    }
}
