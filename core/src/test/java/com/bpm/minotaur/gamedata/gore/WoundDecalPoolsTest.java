package com.bpm.minotaur.gamedata.gore;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * Which of the 40 wound decals each weapon type may leave. Classified by looking at the art: a
 * sword must not leave a burn or a bruise, and a fire weapon must have burns to leave.
 */
public class WoundDecalPoolsTest {

    private static Set<Integer> set(int[] a) {
        Set<Integer> s = new HashSet<>();
        for (int i : a) {
            s.add(i);
        }
        return s;
    }

    @Test
    public void scorchHasItsOwnPoolAndNoOtherTypeUsesIt() {
        Set<Integer> scorch = set(WoundDecalRegistry.SCORCH_INDICES);
        assertFalse(scorch.isEmpty());
        for (int[] other : new int[][] { WoundDecalRegistry.SLASH_INDICES, WoundDecalRegistry.SLICE_INDICES,
                WoundDecalRegistry.STAB_INDICES, WoundDecalRegistry.PUNCTURE_INDICES, WoundDecalRegistry.CRUSH_INDICES }) {
            for (int i : other) {
                assertFalse("decal " + i + " is a burn mark and a blade cannot leave it", scorch.contains(i));
            }
        }
    }

    @Test
    public void bladesLeaveNoBruisesAndBluntWeaponsLeaveNoCuts() {
        Set<Integer> bruises = set(new int[] { 9, 18, 29, 36, 38 });
        for (int i : WoundDecalRegistry.SLASH_INDICES) {
            assertFalse("decal " + i + " is a bruise in the slash pool", bruises.contains(i));
        }
        for (int i : WoundDecalRegistry.SLICE_INDICES) {
            assertFalse("decal " + i + " is a bruise in the slice pool", bruises.contains(i));
        }
        Set<Integer> cuts = set(new int[] { 1, 2, 5, 6, 10, 12, 19, 21, 22, 25, 26, 30, 39 });
        for (int i : WoundDecalRegistry.CRUSH_INDICES) {
            assertFalse("decal " + i + " is a cut in the crush pool", cuts.contains(i));
        }
    }

    @Test
    public void everyDecalBelongsToSomePool() {
        Set<Integer> all = new HashSet<>();
        for (int[] pool : new int[][] { WoundDecalRegistry.SLASH_INDICES, WoundDecalRegistry.SLICE_INDICES,
                WoundDecalRegistry.STAB_INDICES, WoundDecalRegistry.PUNCTURE_INDICES,
                WoundDecalRegistry.CRUSH_INDICES, WoundDecalRegistry.SCORCH_INDICES }) {
            all.addAll(set(pool));
        }
        for (int i = 1; i <= 40; i++) {
            assertTrue("decal " + i + " is unused", all.contains(i));
        }
    }
}
