package com.bpm.minotaur.gamedata.gore;

import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonWriter;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.ItemColor;
import com.bpm.minotaur.gamedata.save.ItemSaveData;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class BloodCoatTest {

    private static BloodStain stain(float radius) {
        return new BloodStain(500f, 600f, radius, 0f, 1, 0xC41E1E);
    }

    @Test
    public void everyStainSoaksInByItsArea() {
        BloodCoat coat = new BloodCoat();
        coat.add(stain(5f));
        float afterDroplet = coat.soak;
        coat.add(stain(40f));
        float afterSplash = coat.soak - afterDroplet;

        assertTrue(afterDroplet > 0f);
        assertTrue("a splash soaks in far more than a droplet", afterSplash > afterDroplet * 20f);
    }

    @Test
    public void aFullCoatKeepsDarkeningInsteadOfStopping() {
        // The horror-film look comes from what happens after the stain list is full:
        // old stains sink into the soak rather than the coat simply capping out.
        BloodCoat coat = new BloodCoat();
        for (int i = 0; i < BloodCoat.MAX_STAINS; i++) {
            coat.add(stain(1f));
        }
        float full = coat.soak;
        for (int i = 0; i < 50; i++) {
            coat.add(stain(1f));
        }
        assertEquals(BloodCoat.MAX_STAINS, coat.stains.size());
        assertTrue(coat.soak > full + 49 * BloodCoat.OVERFLOW_SOAK);
    }

    @Test
    public void soakNeverPassesFullySoaked() {
        BloodCoat coat = new BloodCoat();
        for (int i = 0; i < 2000; i++) {
            coat.add(stain(60f));
        }
        assertEquals(1f, coat.soak, 0f);
    }

    @Test
    public void agingDriesEveryStainAndInvalidatesTheOverlay() {
        BloodCoat coat = new BloodCoat();
        coat.add(stain(10f));
        int version = coat.version();
        coat.age(3);
        assertEquals(3, coat.stains.get(0).age);
        assertNotEquals(version, coat.version());
    }

    @Test
    public void clearingWashesItAll() {
        BloodCoat coat = new BloodCoat();
        coat.add(stain(30f));
        coat.clear();
        assertTrue(coat.isEmpty());
        assertEquals(0f, coat.soak, 0f);
    }

    @Test
    public void pendingSplashesWaitForTheDollAndOverflowIntoTheSkin() {
        PlayerBlood blood = new PlayerBlood();
        List<BloodStain> many = new ArrayList<BloodStain>();
        for (int i = 0; i < PlayerBlood.MAX_PENDING + 10; i++) {
            many.add(stain(5f));
        }
        blood.splatter(many);

        assertEquals(PlayerBlood.MAX_PENDING, blood.pending.size());
        assertTrue("overflow still darkens him", blood.body.soak > 0f);

        List<BloodStain> drained = blood.drainPending();
        assertEquals(PlayerBlood.MAX_PENDING, drained.size());
        assertTrue(blood.pending.isEmpty());
    }

    @Test
    public void pendingSplashesDryWhileTheyWait() {
        PlayerBlood blood = new PlayerBlood();
        blood.splatter(Arrays.asList(stain(5f)));
        blood.age(7);
        assertEquals(7, blood.pending.get(0).age);
    }

    @Test
    public void bloodSurvivesASaveAndLoad() {
        // Weeks of fighting must not wash off because the game was saved.
        PlayerBlood blood = new PlayerBlood();
        blood.body.add(new BloodStain(410f, 520f, 22f, 3f, 99, 0x38E047));
        blood.body.age(12);
        blood.splatter(Arrays.asList(stain(6f)));

        Json json = new Json();
        json.setOutputType(JsonWriter.OutputType.json);
        PlayerBlood back = json.fromJson(PlayerBlood.class, json.toJson(blood));

        assertEquals(1, back.body.stains.size());
        BloodStain s = back.body.stains.get(0);
        assertEquals(410f, s.x, 0f);
        assertEquals(22f, s.radius, 0f);
        assertEquals(3f, s.drip, 0f);
        assertEquals(99, s.seed);
        assertEquals(0x38E047, s.rgb);
        assertEquals(12, s.age);
        assertEquals(blood.body.soak, back.body.soak, 0f);
        assertEquals(1, back.pending.size());
    }

    @Test
    public void itemBloodTravelsWithTheItemThroughASave() {
        ItemSaveData data = new ItemSaveData(Item.ItemType.HELMET, ItemColor.GRAY);
        HashMap<String, BloodCoat> coats = new HashMap<String, BloodCoat>();
        BloodCoat left = new BloodCoat();
        left.add(stain(15f));
        coats.put("left", left);
        data.blood = coats;

        Json json = new Json();
        json.setOutputType(JsonWriter.OutputType.json);
        ItemSaveData back = json.fromJson(ItemSaveData.class, json.toJson(data));

        assertNotNull(back.blood);
        assertEquals(1, back.blood.get("left").stains.size());
        assertEquals(15f, back.blood.get("left").stains.get(0).radius, 0f);
    }
}
