package com.bpm.minotaur.gamedata.gore;

import com.badlogic.gdx.math.Vector3;
import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.*;

public class WoundDecalRegistryTest {

    @Test
    public void testCategoryIndicesValidRange() {
        int[][] allCategories = {
                WoundDecalRegistry.SLASH_INDICES,
                WoundDecalRegistry.SLICE_INDICES,
                WoundDecalRegistry.STAB_INDICES,
                WoundDecalRegistry.PUNCTURE_INDICES,
                WoundDecalRegistry.CRUSH_INDICES
        };

        for (int[] category : allCategories) {
            assertTrue("Category must not be empty", category.length > 0);
            for (int idx : category) {
                assertTrue("Index must be between 1 and 40 (got " + idx + ")", idx >= 1 && idx <= 40);
            }
        }
    }

    @Test
    public void testMultiCategorizationOverlap() {
        // Confirm that some decals are intentionally shared across weapon types
        Set<Integer> slashSet = toSet(WoundDecalRegistry.SLASH_INDICES);
        Set<Integer> sliceSet = toSet(WoundDecalRegistry.SLICE_INDICES);
        Set<Integer> stabSet = toSet(WoundDecalRegistry.STAB_INDICES);
        Set<Integer> punctureSet = toSet(WoundDecalRegistry.PUNCTURE_INDICES);
        Set<Integer> crushSet = toSet(WoundDecalRegistry.CRUSH_INDICES);

        // Razor slash #6 and #10 should be in both SLASH and SLICE
        assertTrue("Decal #6 should be in both SLASH and SLICE", slashSet.contains(6) && sliceSet.contains(6));
        assertTrue("Decal #10 should be in both SLASH and SLICE", slashSet.contains(10) && sliceSet.contains(10));

        // Entry cavity #1, #4, #5 should be in both STAB and PUNCTURE
        assertTrue("Decal #1 should be in both STAB and PUNCTURE", stabSet.contains(1) && punctureSet.contains(1));
        assertTrue("Decal #4 should be in both STAB and PUNCTURE", stabSet.contains(4) && punctureSet.contains(4));

        // Decal #14 and #24 should overlap with CRUSH
        assertTrue("Decal #14 should be in SLASH and CRUSH", slashSet.contains(14) && crushSet.contains(14));
        assertTrue("Decal #24 should be in PUNCTURE and CRUSH", punctureSet.contains(24) && crushSet.contains(24));
    }

    @Test
    public void testAspectRatioDefaults() {
        assertEquals(4.0f, WoundDecalRegistry.getAspectRatio(null, WoundDecal.WoundType.SLASH), 0.01f);
        assertEquals(4.5f, WoundDecalRegistry.getAspectRatio(null, WoundDecal.WoundType.SLICE), 0.01f);
        assertEquals(1.5f, WoundDecalRegistry.getAspectRatio(null, WoundDecal.WoundType.STAB), 0.01f);
        assertEquals(1.0f, WoundDecalRegistry.getAspectRatio(null, WoundDecal.WoundType.PUNCTURE), 0.01f);
        assertEquals(1.0f, WoundDecalRegistry.getAspectRatio(null, WoundDecal.WoundType.CRUSH), 0.01f);
        assertEquals(1.0f, WoundDecalRegistry.getAspectRatio(null, WoundDecal.WoundType.SCORCH), 0.01f);
    }

    @Test
    public void testSpawnWoundBloodBurst() {
        GoreManager gore = new GoreManager();
        assertEquals(0, gore.getActiveParticles().size);

        Vector3 woundSite = new Vector3(10.5f, 0.6f, 12.3f);
        Vector3 splashDir = new Vector3(0f, 0.25f, 1f);

        gore.spawnWoundBloodBurst(woundSite, splashDir, 10, GoreProfile.FLESH);
        assertTrue("Particles should be spawned at wound site", gore.getActiveParticles().size >= 4);

        // Check that particle initial origin matches woundSite
        BloodParticle first = gore.getActiveParticles().get(0);
        assertEquals(10.5f, first.position.x, 0.01f);
        assertEquals(0.6f, first.position.y, 0.01f);
        assertEquals(12.3f, first.position.z, 0.01f);
    }

    @Test
    public void testIncorporealEmitsNoWoundBlood() {
        GoreManager gore = new GoreManager();
        Vector3 woundSite = new Vector3(5f, 0.5f, 5f);
        Vector3 splashDir = new Vector3(0f, 0f, 1f);

        gore.spawnWoundBloodBurst(woundSite, splashDir, 25, GoreProfile.INCORPOREAL);
        assertEquals("Incorporeal creatures must not emit blood particles", 0, gore.getActiveParticles().size);
    }

    private Set<Integer> toSet(int[] array) {
        Set<Integer> set = new HashSet<>();
        for (int i : array) {
            set.add(i);
        }
        return set;
    }
}
