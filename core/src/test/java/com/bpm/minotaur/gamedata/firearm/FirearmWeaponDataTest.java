package com.bpm.minotaur.gamedata.firearm;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.bpm.minotaur.gamedata.item.Item;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.*;

/**
 * The firearms are configured as firearms.
 *
 * <p>Every one of the five shipped as {@code isRanged: false}, {@code range: 1},
 * {@code damageType: "SLASHING"} -- one-handed melee slashing weapons that happened to
 * be named after guns, and which spawned freely because {@code probability: 0} is
 * rewritten to 10 at load. {@code isRanged} is the load-bearing flag: it gates firing,
 * the ammunition check, the block on bump-melee, and the animation archetype. A silent
 * revert here would turn every gun back into a sword.
 */
public class FirearmWeaponDataTest {

    private static JsonValue weapons;

    @BeforeClass
    public static void loadWeaponData() throws IOException {
        File file = new File("../assets/data/weapons.json");
        if (!file.exists()) {
            file = new File("assets/data/weapons.json");
        }
        assertTrue("weapons.json not found from " + new File(".").getAbsolutePath(), file.exists());
        String json = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        weapons = new JsonReader().parse(json);
    }

    private static JsonValue firearm(Item.ItemType type) {
        JsonValue entry = weapons.get(type.name());
        assertNotNull(type + " missing from weapons.json", entry);
        return entry;
    }

    @Test
    public void testEveryFirearmIsARangedWeapon() {
        for (Item.ItemType type : FirearmProfile.all()) {
            assertTrue(type + " must be ranged -- this flag gates firing, ammo and animation",
                    firearm(type).getBoolean("isRanged"));
        }
    }

    @Test
    public void testNoFirearmIsStillAMeleeStub() {
        for (Item.ItemType type : FirearmProfile.all()) {
            JsonValue w = firearm(type);
            assertNotEquals(type + " still has melee reach", 1, w.getInt("range"));
            assertNotEquals(type + " still deals SLASHING damage", "SLASHING", w.getString("damageType"));
        }
    }

    @Test
    public void testFirearmsShootAtBowRange() {
        // Q4(c): the reload carries the balance, not a range penalty.
        int bowRange = weapons.get("BOW").getInt("range");
        for (Item.ItemType type : FirearmProfile.all()) {
            assertEquals(type + " should reach as far as a bow", bowRange, firearm(type).getInt("range"));
        }
    }

    @Test
    public void testFirearmsPierce() {
        for (Item.ItemType type : FirearmProfile.all()) {
            assertEquals("PIERCING", firearm(type).getString("damageType"));
        }
    }

    @Test
    public void testOnlyTheSidearmIsOneHanded() {
        assertFalse("a starwheel pistol is a sidearm",
                firearm(Item.ItemType.PISTOL_STARWHEEL).getBoolean("isTwoHanded"));

        for (Item.ItemType type : FirearmProfile.all()) {
            if (type != Item.ItemType.PISTOL_STARWHEEL) {
                assertTrue(type + " is shouldered", firearm(type).getBoolean("isTwoHanded"));
            }
        }
    }

    @Test
    public void testTufenkIsNoLongerWeakerThanADagger() {
        assertEquals("1d10", firearm(Item.ItemType.TUFENK).getString("damageDice"));
    }

    @Test
    public void testFirearmsHitHarderThanTheBowTheyCompeteWith() {
        // The whole justification for the reload, the noise and the ammunition scarcity
        // is that a firearm hits materially harder. If that stops being true, the costs
        // are unearned.
        assertEquals("1d4", weapons.get("BOW").getString("damageDice"));
        assertEquals("2d8", firearm(Item.ItemType.ARQUEBUS).getString("damageDice"));
        assertEquals("2d8", firearm(Item.ItemType.BLUNDERBUS).getString("damageDice"));
        assertEquals("1d12", firearm(Item.ItemType.MUSKET).getString("damageDice"));
    }
}
