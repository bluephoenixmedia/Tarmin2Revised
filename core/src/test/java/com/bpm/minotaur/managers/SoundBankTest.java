package com.bpm.minotaur.managers;

import com.badlogic.gdx.utils.JsonReader;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.io.FileReader;
import java.io.RandomAccessFile;
import java.util.Random;

import static org.junit.Assert.*;

public class SoundBankTest {

    private static SoundBank bank;

    private static File asset(String path) {
        File f = new File("../assets/" + path);
        return f.exists() ? f : new File("assets/" + path);
    }

    @BeforeClass
    public static void load() throws Exception {
        bank = SoundBank.parse(new JsonReader().parse(new FileReader(asset("data/soundbank.json"))));
    }

    @Test
    public void everyVariantIsAFileThatExists() {
        for (String name : bank.eventNames()) {
            for (String path : bank.get(name).files) {
                assertTrue(name + " names a missing file: " + path, asset(path).isFile());
            }
        }
    }

    @Test
    public void everyVariantIsSixteenBitPcmSoTheBackendCanDecodeIt() throws Exception {
        for (String name : bank.eventNames()) {
            for (String path : bank.get(name).files) {
                try (RandomAccessFile f = new RandomAccessFile(asset(path), "r")) {
                    byte[] h = new byte[36];
                    f.readFully(h);
                    int tag = (h[20] & 0xff) | (h[21] & 0xff) << 8;
                    int bits = (h[34] & 0xff) | (h[35] & 0xff) << 8;
                    assertEquals(path + " format", 1, tag);
                    assertEquals(path + " bit depth", 16, bits);
                }
            }
        }
    }

    @Test
    public void theEventsTheGameAsksForAllExist() {
        for (String e : new String[] { "swing", "hit_blade", "hit_blunt", "bow_shot", "monster_roar",
                "alert", "death_impact", "bandage", "bag", "drop" }) {
            assertTrue("no sound event " + e, bank.has(e));
        }
    }

    @Test
    public void everySpellArchetypeHasACastSound() {
        for (com.bpm.minotaur.gamedata.spells.VisualArchetype a : com.bpm.minotaur.gamedata.spells.VisualArchetype.values()) {
            assertTrue("no cast sound for " + a, bank.has("spell_" + a.name().toLowerCase()));
        }
        assertTrue(bank.has("spell_self"));
        assertTrue(bank.has("spell_warp"));
    }

    @Test
    public void neverRepeatsTheSameVariantBackToBack() {
        Random random = new Random(1);
        String previous = null;
        for (int i = 0; i < 500; i++) {
            String now = bank.pick("hit_blade", random);
            assertNotEquals(previous, now);
            previous = now;
        }
    }

    @Test
    public void anUnknownEventPicksNothing() {
        assertNull(bank.pick("no_such_event", new Random(1)));
        assertFalse(bank.has("no_such_event"));
    }
}
