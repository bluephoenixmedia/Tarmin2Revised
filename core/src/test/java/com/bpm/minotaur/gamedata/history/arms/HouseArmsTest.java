package com.bpm.minotaur.gamedata.history.arms;

import com.bpm.minotaur.gamedata.history.Doctrine;
import com.bpm.minotaur.gamedata.history.DoctrineCatalog;
import com.bpm.minotaur.gamedata.history.DoctrineCatalogTest;
import com.bpm.minotaur.gamedata.history.HistorySimulator;
import com.bpm.minotaur.gamedata.history.HistoryWorld;
import com.bpm.minotaur.gamedata.history.House;
import org.junit.BeforeClass;
import org.junit.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;

import static org.junit.Assert.*;

/** Living War A43-A44: every house has Arms of its own, and they never move the history. */
public class HouseArmsTest {

    private static final long[] SEEDS = {1000L, 8919L, 24757L, 40595L, 135623L, 151461L, 7L, 21L, 77L, 2024L, 5L, 6L};

    private static DoctrineCatalog catalog;

    @BeforeClass
    public static void load() throws IOException {
        catalog = DoctrineCatalogTest.loadCatalog();
    }

    private static File assets() {
        for (String p : new String[] {"../assets", "assets"}) {
            if (new File(p).isDirectory()) return new File(p);
        }
        throw new IllegalStateException("no assets folder");
    }

    private static ArmsRaster.Mask mask(String charge) throws IOException {
        BufferedImage im = ImageIO.read(new File(assets(), ChargeArt.pathOf(charge)));
        byte[] coverage = new byte[im.getWidth() * im.getWidth()];
        for (int y = 0; y < im.getWidth(); y++) {
            for (int x = 0; x < im.getWidth(); x++) coverage[y * im.getWidth() + x] = (byte) (im.getRGB(x, y) >>> 24);
        }
        return new ArmsRaster.Mask(im.getWidth(), coverage);
    }

    @Test
    public void noTwoHousesOfAWorldBearAlikeArms() {
        for (long seed : SEEDS) {
            List<HouseArms> all = HouseArms.all(HistorySimulator.prehistory(seed, catalog));
            for (int i = 0; i < all.size(); i++) {
                for (int j = i + 1; j < all.size(); j++) {
                    assertFalse("seed " + seed + ": " + all.get(i).written(), all.get(i).looksLike(all.get(j)));
                }
            }
        }
    }

    @Test
    public void aHousesArmsAreStable() {
        for (long seed : SEEDS) {
            HistoryWorld a = HistorySimulator.prehistory(seed, catalog), b = HistorySimulator.prehistory(seed, catalog);
            for (House h : a.houses()) {
                HouseArms x = HouseArms.of(a, h.id), y = HouseArms.of(b, h.id);
                assertEquals(x.written(), y.written());
                assertEquals(x.written(), HouseArms.all(a).get(a.houses().indexOf(h)).written());
            }
        }
    }

    @Test
    public void armsNeverMoveTheHistory() {
        HistoryWorld w = HistorySimulator.prehistory(8919L, catalog);
        String before = w.fingerprint();
        HouseArms.all(w);
        assertEquals(before, w.fingerprint());
    }

    @Test
    public void theWrittenSigilKeepsTheOldPhraseAsItsPrefix() {
        HistoryWorld w = HistorySimulator.prehistory(1000L, catalog);
        for (House h : w.houses()) {
            HouseArms a = HouseArms.of(w, h.id);
            assertTrue(a.written(), a.written().startsWith(h.sigil + " and "));
            assertTrue(a.written(), a.written().endsWith(a.division.words));
        }
    }

    @Test
    public void aHousesTwoColoursStandApart() {
        for (long seed : SEEDS) {
            for (HouseArms a : HouseArms.all(HistorySimulator.prehistory(seed, catalog))) {
                assertTrue(a.written(), HouseArms.contrast(HouseArms.rgb(a.field), HouseArms.rgb(a.secondField)) >= 2.0);
            }
        }
    }

    @Test
    public void everyChargeADoctrineCanBearHasArt() throws IOException {
        int charges = 0;
        for (Doctrine d : catalog.all()) {
            for (String charge : d.charges) {
                String path = ChargeArt.pathOf(charge);
                assertNotNull(charge + " has no art", path);
                assertTrue(path, new File(assets(), path).isFile());
                ArmsRaster.Mask m = mask(charge);
                assertEquals(64, m.size);
                charges++;
            }
        }
        assertEquals(23, charges);
    }

    @Test
    public void aShieldHasItsBorderAndFieldsAndItsCharge() throws IOException {
        HistoryWorld w = HistorySimulator.prehistory(1000L, catalog);
        HouseArms a = HouseArms.of(w, w.houses().get(0).id);
        int border = 0x123456;
        ArmsRaster.Image bare = ArmsRaster.shield(a, border, null, 64);
        assertEquals(64, bare.width);
        assertEquals(72, bare.height);
        assertEquals("the top-left corner lies on the border", border << 8 | 0xFF, bare.rgba[8 * 64 + 2]);
        int first = 0, second = 0;
        for (int p : bare.rgba) {
            if ((p >>> 8) == HouseArms.rgb(a.field)) first++;
            if ((p >>> 8) == HouseArms.rgb(a.secondField)) second++;
        }
        assertTrue("both field colours show: " + first + ", " + second, first > 200 && second > 200);
        assertEquals("the point's corners stay clear", 0, bare.rgba[71 * 64] & 0xFF);

        ArmsRaster.Image charged = ArmsRaster.shield(a, border, mask(a.charge), 64);
        int changed = 0;
        for (int i = 0; i < bare.rgba.length; i++) if (bare.rgba[i] != charged.rgba[i]) changed++;
        assertTrue("the charge shows, in " + changed + " pixels", changed > 150);
    }

    @Test
    public void armsDrawAtEverySizeTheFieldUses() throws IOException {
        HistoryWorld w = HistorySimulator.prehistory(8919L, catalog);
        for (HouseArms a : HouseArms.all(w)) {
            for (int size : new int[] {64, 32, 24, 18, 16, 14, 12}) {
                ArmsRaster.Image s = ArmsRaster.shield(a, 0x8E2A2A, mask(a.charge), size);
                ArmsRaster.Image b = ArmsRaster.banner(a, 0x8E2A2A, 0xD9C7A3, mask(a.charge), size);
                assertEquals(size, s.width);
                assertEquals(size, b.width);
            }
        }
    }

    @Test
    public void inkStandsOffItsFields() {
        assertEquals(0xF1E8D2, ArmsRaster.ink(HouseArms.rgb("black"), HouseArms.rgb("red")));
        assertEquals(0x151515, ArmsRaster.ink(HouseArms.rgb("bone white"), HouseArms.rgb("tarnished gold")));
    }
}
