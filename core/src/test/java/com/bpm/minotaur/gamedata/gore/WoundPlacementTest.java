package com.bpm.minotaur.gamedata.gore;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.*;

public class WoundPlacementTest {

    private static WoundDecal at(float[] p) {
        return new WoundDecal(WoundDecal.WoundType.SLASH, p[0], p[1], 0f, 0.2f, 0.05f, null);
    }

    @Test
    public void woundsSpreadAcrossTheSpriteInsteadOfPilingInTheMiddle() {
        Random random = new Random(7);
        int outsideCentre = 0;
        int total = 600;
        for (int i = 0; i < total; i++) {
            float[] p = WoundPlacement.pick(SilhouetteMask.full(), new ArrayList<>(), random);
            if (p[0] < 0.35f || p[0] > 0.65f || p[1] < 0.35f || p[1] > 0.65f) {
                outsideCentre++;
            }
        }
        // The old placement put 100% inside the central 30% box; a uniform spread puts ~91% outside.
        assertTrue("only " + outsideCentre + "/" + total + " landed off-centre", outsideCentre > total * 0.75);
    }

    @Test
    public void woundsStayOnTheCreatureNotTheEmptyHalf() {
        boolean[] leftHalf = new boolean[SilhouetteMask.GRID * SilhouetteMask.GRID];
        for (int y = 0; y < SilhouetteMask.GRID; y++) {
            for (int x = 0; x < SilhouetteMask.GRID / 2; x++) {
                leftHalf[y * SilhouetteMask.GRID + x] = true;
            }
        }
        SilhouetteMask mask = new SilhouetteMask(SilhouetteMask.GRID, SilhouetteMask.GRID, leftHalf);
        Random random = new Random(3);
        for (int i = 0; i < 300; i++) {
            float[] p = WoundPlacement.pick(mask, new ArrayList<>(), random);
            assertTrue("wound at u=" + p[0] + " is off the body", mask.isSolidAt(p[0], p[1]));
        }
    }

    @Test
    public void successiveWoundsDoNotStackOnEachOther() {
        Random random = new Random(11);
        List<WoundDecal> wounds = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            wounds.add(at(WoundPlacement.pick(SilhouetteMask.full(), wounds, random)));
        }
        float minGap = Float.MAX_VALUE;
        for (int i = 0; i < wounds.size(); i++) {
            for (int j = i + 1; j < wounds.size(); j++) {
                float dx = wounds.get(i).u - wounds.get(j).u;
                float dy = wounds.get(i).v - wounds.get(j).v;
                minGap = Math.min(minGap, (float) Math.sqrt(dx * dx + dy * dy));
            }
        }
        assertTrue("two wounds only " + minGap + " apart", minGap > 0.08f);
    }

    @Test
    public void anUnreadableSpriteStillGetsAWound() {
        float[] p = WoundPlacement.pick(null, null, new Random(1));
        assertTrue(p[0] >= 0f && p[0] <= 1f && p[1] >= 0f && p[1] <= 1f);
    }
}
