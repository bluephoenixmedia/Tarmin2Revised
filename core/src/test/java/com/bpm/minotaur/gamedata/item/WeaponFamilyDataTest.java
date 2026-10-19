package com.bpm.minotaur.gamedata.item;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.*;

/** Every weapon names its Weapon Family, as settled on the Weapon Family prototype (#224). */
public class WeaponFamilyDataTest {

    private static JsonValue weapons;

    @BeforeClass
    public static void loadWeaponData() throws IOException {
        File file = new File("../assets/data/weapons.json");
        if (!file.exists()) {
            file = new File("assets/data/weapons.json");
        }
        assertTrue("weapons.json not found from " + new File(".").getAbsolutePath(), file.exists());
        weapons = new JsonReader().parse(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
    }

    private static WeaponFamily familyOf(String id) {
        JsonValue entry = weapons.get(id);
        assertNotNull(id + " missing from weapons.json", entry);
        return WeaponFamily.require(id, entry.getString("weaponFamily", null));
    }

    @Test
    public void everyWeaponNamesARealFamily() {
        for (JsonValue entry = weapons.child; entry != null; entry = entry.next) {
            WeaponFamily.require(entry.name, entry.getString("weaponFamily", null));
        }
    }

    @Test
    public void aMissingOrUnknownFamilyFailsTheLoad() {
        assertThrows(IllegalStateException.class, () -> WeaponFamily.require("CLUB", null));
        assertThrows(IllegalStateException.class, () -> WeaponFamily.require("CLUB", "BLUDGEON"));
    }

    @Test
    public void theHardCallsLandWhereTheUserPutThem() {
        assertEquals("staves are Blunt", WeaponFamily.BLUNT, familyOf("QUARTERSTAFF"));
        assertEquals("the Tetsubo is a club", WeaponFamily.BLUNT, familyOf("POLEARM_TETSUBO"));
        assertEquals("whips are Blade", WeaponFamily.BLADE, familyOf("WHIP"));
        assertEquals("picks are Axe", WeaponFamily.AXE, familyOf("PICK_FOOTMANS"));
        assertEquals("slings are Thrown", WeaponFamily.THROWN, familyOf("SLING"));
        assertEquals("the blowgun is Bow", WeaponFamily.BOW, familyOf("BLOWGUN"));
        assertEquals("armour spikes are Unarmed", WeaponFamily.UNARMED, familyOf("SPIKE_ELBOW"));
        assertEquals("spiked shields are Blunt", WeaponFamily.BLUNT, familyOf("BUCKLER_SPIKED"));
        assertEquals("nets are Thrown", WeaponFamily.THROWN, familyOf("NET"));
        assertEquals("the scythe is Polearm", WeaponFamily.POLEARM, familyOf("SCYTHE"));
        assertEquals("the Father's Sword is an ordinary Blade", WeaponFamily.BLADE, familyOf("RUSTY_SWORD"));
        assertEquals("the Wooden Cross has none", WeaponFamily.NONE, familyOf("WOODEN_CROSS"));
        assertEquals("a javelin's home is Polearm; throwing it trains Thrown", WeaponFamily.POLEARM, familyOf("JAVELIN_ONE_HANDED"));
        assertEquals("arrows have none", WeaponFamily.NONE, familyOf("ARROW_WAR"));
        assertEquals("guns are Firearm", WeaponFamily.FIREARM, familyOf("MUSKET"));
    }
}
