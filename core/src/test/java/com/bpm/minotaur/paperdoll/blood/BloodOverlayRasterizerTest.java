package com.bpm.minotaur.paperdoll.blood;

import com.bpm.minotaur.gamedata.gore.BloodCoat;
import com.bpm.minotaur.gamedata.gore.BloodStain;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class BloodOverlayRasterizerTest {

    private static final int D = BloodOverlayRasterizer.DOWNSAMPLE;
    private static final int RED = 0xC41E1E;

    /** A 1024x1536 layer that is solid everywhere, or only in the left half. */
    private static AlphaMask mask(boolean leftHalfOnly) {
        return AlphaMask.sample(1024, 1536, D, (x, y) -> (!leftHalfOnly || x < 512) ? 255 : 0);
    }

    private static int alpha(int[] px, AlphaMask m, float layerX, float layerY) {
        int x = (int) (layerX * m.scale);
        int y = (int) (layerY * m.scale);
        return px[y * m.width + x] & 0xFF;
    }

    private static int red(int[] px, AlphaMask m, float layerX, float layerY) {
        int x = (int) (layerX * m.scale);
        int y = (int) (layerY * m.scale);
        return (px[y * m.width + x] >>> 24) & 0xFF;
    }

    @Test
    public void aCleanCoatPaintsNothing() {
        AlphaMask m = mask(false);
        for (int p : BloodOverlayRasterizer.render(new BloodCoat(), m, 1)) {
            assertEquals(0, p);
        }
    }

    @Test
    public void aStainCoversWhereItLanded() {
        AlphaMask m = mask(false);
        BloodCoat coat = new BloodCoat();
        coat.add(new BloodStain(300f, 400f, 30f, 0f, 7, RED));
        int[] px = BloodOverlayRasterizer.render(coat, m, 1);
        assertTrue(alpha(px, m, 300f, 400f) > 200);
        assertEquals(0, alpha(px, m, 800f, 1200f));
    }

    @Test
    public void bloodNeverHangsInTheAirBesideThePiece() {
        // Same rule as the weapon's decals: clipped to the art's own pixels.
        AlphaMask m = mask(true);
        BloodCoat coat = new BloodCoat();
        coat.add(new BloodStain(512f, 700f, 60f, 0f, 3, RED));
        coat.addSoak(1f);
        int[] px = BloodOverlayRasterizer.render(coat, m, 1);
        for (int y = 0; y < m.height; y++) {
            for (int x = 0; x < m.width; x++) {
                if (!m.solidAt(x, y)) {
                    assertEquals(0, px[y * m.width + x]);
                }
            }
        }
        assertTrue(alpha(px, m, 490f, 700f) > 0);
    }

    @Test
    public void aDripRunsBelowTheStain() {
        AlphaMask m = mask(false);
        BloodCoat dry = new BloodCoat();
        dry.add(new BloodStain(500f, 400f, 30f, 0f, 11, RED));
        BloodCoat dripping = new BloodCoat();
        dripping.add(new BloodStain(500f, 400f, 30f, 8f, 11, RED));

        assertTrue(lowestBloodRow(BloodOverlayRasterizer.render(dripping, m, 1), m)
                > lowestBloodRow(BloodOverlayRasterizer.render(dry, m, 1), m) + 15);
    }

    @Test
    public void oldBloodIsDarkerThanFreshBlood() {
        AlphaMask m = mask(false);
        BloodCoat fresh = new BloodCoat();
        fresh.add(new BloodStain(500f, 600f, 40f, 0f, 5, RED));
        BloodCoat old = new BloodCoat();
        BloodStain s = new BloodStain(500f, 600f, 40f, 0f, 5, RED);
        s.age = 200;
        old.add(s);

        int freshRed = red(BloodOverlayRasterizer.render(fresh, m, 1), m, 500f, 600f);
        int oldRed = red(BloodOverlayRasterizer.render(old, m, 1), m, 500f, 600f);
        assertTrue("fresh " + freshRed + " old " + oldRed, oldRed < freshRed * 0.6f);
    }

    @Test
    public void soakFloodsThePieceAsItClimbs() {
        AlphaMask m = mask(false);
        float light = coveredShare(soaked(0.15f), m);
        float heavy = coveredShare(soaked(0.9f), m);
        assertTrue("light soak " + light, light < 0.2f);
        assertTrue("heavy soak " + heavy, heavy > 0.75f);
    }

    @Test
    public void aFewStainsDoNotSpeckleTheWholePiece() {
        // Each stain adds a little soak. Until it has built up, only the stains show.
        AlphaMask m = mask(false);
        BloodCoat coat = new BloodCoat();
        coat.add(new BloodStain(500f, 400f, 30f, 0f, 11, RED));
        coat.add(new BloodStain(520f, 450f, 40f, 0f, 12, RED));
        int[] px = BloodOverlayRasterizer.render(coat, m, 1);
        assertEquals(0, alpha(px, m, 500f, 1450f));
        assertTrue(lowestBloodRow(px, m) < 700f * m.scale);
    }

    @Test
    public void theSamePieceRedrawsIdentically() {
        // Seeded throughout: reopening the inventory must not reshuffle the blood.
        AlphaMask m = mask(false);
        BloodCoat coat = soaked(0.5f);
        coat.add(new BloodStain(200f, 300f, 25f, 3f, 9, RED));
        int[] a = BloodOverlayRasterizer.render(coat, m, 77);
        int[] b = BloodOverlayRasterizer.render(coat, m, 77);
        for (int i = 0; i < a.length; i++) {
            assertEquals(a[i], b[i]);
        }
    }

    private static BloodCoat soaked(float soak) {
        BloodCoat coat = new BloodCoat();
        coat.addSoak(soak);
        return coat;
    }

    private static float coveredShare(BloodCoat coat, AlphaMask m) {
        int[] px = BloodOverlayRasterizer.render(coat, m, 5);
        int covered = 0;
        for (int p : px) {
            if ((p & 0xFF) > 0) {
                covered++;
            }
        }
        return covered / (float) px.length;
    }

    private static int lowestBloodRow(int[] px, AlphaMask m) {
        for (int y = m.height - 1; y >= 0; y--) {
            for (int x = 0; x < m.width; x++) {
                if ((px[y * m.width + x] & 0xFF) > 0) {
                    return y;
                }
            }
        }
        return -1;
    }
}
