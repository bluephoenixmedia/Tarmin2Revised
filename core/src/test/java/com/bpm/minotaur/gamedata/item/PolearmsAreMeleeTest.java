package com.bpm.minotaur.gamedata.item;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.*;

/**
 * A polearm is a melee weapon (#301): one marked ranged cannot bump-attack at all, as the Spear
 * could not, and its Family's Arts never fire.
 */
public class PolearmsAreMeleeTest {

    private static JsonValue weapons() throws IOException {
        for (String candidate : new String[]{"assets/data/weapons.json", "../assets/data/weapons.json"}) {
            Path path = Paths.get(candidate);
            if (Files.exists(path)) {
                return new JsonReader().parse(new String(Files.readAllBytes(path), StandardCharsets.UTF_8));
            }
        }
        throw new IOException("weapons.json not found from " + Paths.get("").toAbsolutePath());
    }

    @Test
    public void noPolearmIsMarkedRanged() throws IOException {
        int polearms = 0;
        for (JsonValue weapon = weapons().child; weapon != null; weapon = weapon.next) {
            if (!"POLEARM".equals(weapon.getString("weaponFamily", null))) continue;
            polearms++;
            assertFalse(weapon.name + " is a polearm, so a melee weapon", weapon.getBoolean("isRanged", false));
        }
        assertTrue("the Spear and its kin are there", polearms > 1);
    }

    @Test
    public void theSpearIsAMeleePolearm() throws IOException {
        JsonValue spear = weapons().get("SPEAR");
        assertNotNull(spear);
        assertEquals("POLEARM", spear.getString("weaponFamily"));
        assertFalse(spear.getBoolean("isRanged"));
    }
}
