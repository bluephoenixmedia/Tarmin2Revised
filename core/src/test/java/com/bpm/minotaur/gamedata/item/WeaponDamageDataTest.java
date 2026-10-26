package com.bpm.minotaur.gamedata.item;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * Every weapon's damage type and dice were chosen for it (#230). 101 entries once carried the
 * import default, Slashing 1d4+1, whatever the weapon was: a Knobkerrie that cut, a bow that
 * could only be swung. The damage type picks the wound a hit leaves and feeds the Combat Arts.
 */
public class WeaponDamageDataTest {

    /**
     * Light slashing weapons whose fitting stats happen to be the old default: small blades, and
     * whips and the Quabone held up to the 3.5 weapon floor (#195).
     */
    private static final Set<String> SLASHING_1D4_PLUS_1_BY_CHOICE = new HashSet<>(Arrays.asList(
            "CHAKRAM", "JAMBIYA", "SICKLE", "RAZOR", "QUABONE", "SCOURGE", "WHIP", "WHIP_MASTERS"));

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

    @Test
    public void noWeaponStillCarriesTheImportDefault() {
        List<String> defaulted = new ArrayList<>();
        for (JsonValue w = weapons.child; w != null; w = w.next) {
            if (!w.getBoolean("isWeapon", false)) continue;
            if ("SLASHING".equals(w.getString("damageType", null)) && "1d4+1".equals(w.getString("damageDice", null))
                    && !SLASHING_1D4_PLUS_1_BY_CHOICE.contains(w.name)) {
                defaulted.add(w.name);
            }
        }
        assertTrue("still on the import default: " + defaulted, defaulted.isEmpty());
    }

    @Test
    public void bluntWeaponsBludgeonUnlessTheyReallyAreEdged() {
        Set<String> edged = new HashSet<>(Arrays.asList(
                "ALHULAK", "STAFF_HORNHEAD", "QUABONE", // hooks, horn blades, jawbone teeth
                "BUCKLER_SPIKED", "MORNING_STAR", "MORNING_STAR_DOUBLE_BALL", "SHIELD_SPIKE")); // spikes pierce
        List<String> wrong = new ArrayList<>();
        for (JsonValue w = weapons.child; w != null; w = w.next) {
            if (!"BLUNT".equals(w.getString("weaponFamily", null)) || edged.contains(w.name)) continue;
            if (!"BLUDGEONING".equals(w.getString("damageType", null))) wrong.add(w.name);
        }
        assertTrue("Blunt but not Bludgeoning: " + wrong, wrong.isEmpty());
    }

    @Test
    public void theDaikyuIsABowLikeItsPeers() {
        JsonValue daikyu = weapons.get("DAIKYU");
        assertTrue(daikyu.getBoolean("isRanged"));
        assertTrue(daikyu.getBoolean("isTwoHanded"));
        assertEquals("PIERCING", daikyu.getString("damageType"));
        assertEquals(weapons.get("BOW_LONG").getInt("range"), daikyu.getInt("range"));
    }

    @Test
    public void theSpiritualWeaponsDealSpiritualDamage() {
        for (String id : new String[]{"WOODEN_CROSS", "WAND", "SMALL_FIREBALL", "LARGE_FIREBALL",
                "SMALL_LIGHTNING", "LARGE_LIGHTNING"}) {
            assertEquals(id, "SPIRITUAL", weapons.get(id).getString("damageType"));
        }
    }

    @Test
    public void theWeaponsTheBalanceWasTunedOnAreUnchanged() {
        assertEquals("1d3", weapons.get("RUSTY_SWORD").getString("damageDice"));
        assertEquals("1d8", weapons.get("RUSTY_SWORD").getString("versatileDamageDice"));
        assertEquals("1d6+2", weapons.get("DAGGER").getString("damageDice"));
        assertEquals("1d8+1", weapons.get("SPEAR").getString("damageDice"));
    }
}
