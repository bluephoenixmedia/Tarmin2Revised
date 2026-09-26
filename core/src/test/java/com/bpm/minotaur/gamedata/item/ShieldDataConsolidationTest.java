package com.bpm.minotaur.gamedata.item;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Shields were defined twice under two spellings.
 *
 * <p>{@code SMALL_SHIELD}/{@code SHIELD_SMALL} were byte-identical in
 * armor.json and both sat in the drop table, so the same shield rolled under
 * two names. {@code LARGE_SHIELD}/{@code SHIELD_LARGE} had drifted apart --
 * different colour ladders -- which is how an armour colour with no enum value
 * reached the game and killed it at startup.
 *
 * <p>The duplicate <em>data</em> is gone. The duplicate <em>enum names</em> must
 * stay: saves store ItemType by name, and an unknown name does not lose one
 * item, it loses the whole player file.
 */
public class ShieldDataConsolidationTest {

    private JsonValue load(String name) {
        File f = new File("assets/data/" + name);
        if (!f.exists()) f = new File("../assets/data/" + name);
        assertTrue("Cannot find " + name, f.exists());
        try {
            return new JsonReader().parse(new java.io.FileInputStream(f));
        } catch (java.io.IOException e) {
            throw new AssertionError("Cannot read " + name, e);
        }
    }

    @Test
    public void theDuplicateSpellingsNoLongerCarryTheirOwnData() {
        JsonValue armor = load("armor.json");
        for (String dupe : new String[]{"SHIELD_SMALL", "SHIELD_LARGE"}) {
            assertNull(dupe + " still has its own armor.json entry; it should resolve"
                    + " through the alias to the canonical spelling", armor.get(dupe));
        }
        assertNotNull("SMALL_SHIELD is canonical and must remain", armor.get("SMALL_SHIELD"));
        assertNotNull("LARGE_SHIELD is canonical and must remain", armor.get("LARGE_SHIELD"));
    }

    @Test
    public void theCanonicalLargeShieldKeepsTheFullerColourLadder() {
        // The two spellings disagreed: LARGE_SHIELD carried a BLUE tier that
        // SHIELD_LARGE lacked. Collapsing onto the poorer ladder would have
        // silently deleted a colour tier from the game.
        JsonValue variants = load("armor.json").get("LARGE_SHIELD").get("variants");
        assertNotNull("LARGE_SHIELD must have a variant ladder", variants);
        List<String> colours = new ArrayList<>();
        for (JsonValue v = variants.child; v != null; v = v.next) {
            colours.add(v.getString("color", ""));
        }
        assertTrue("The fuller ladder included BLUE: " + colours, colours.contains("BLUE"));
        assertTrue("Expected the 6-entry ladder, got " + colours.size(), colours.size() >= 6);
    }

    @Test
    public void theDropTableRollsEachShieldUnderOneNameOnly() {
        JsonValue spawn = load("spawntables.json");
        List<String> shieldRows = new ArrayList<>();
        collectTypes(spawn, shieldRows);
        for (String dupe : new String[]{"SHIELD_SMALL", "SHIELD_LARGE", "SHIELD_MEDIUM"}) {
            assertFalse("The drop table still spawns " + dupe + ", duplicating its canonical"
                    + " spelling: " + shieldRows, shieldRows.contains(dupe));
        }
        assertTrue("SMALL_SHIELD must still spawn", shieldRows.contains("SMALL_SHIELD"));
        assertTrue("LARGE_SHIELD must still spawn", shieldRows.contains("LARGE_SHIELD"));
    }

    @Test
    public void theOrphanedWoodenShieldIsReachableNow() {
        // It had art, an armor.json entry and a paperdoll layer, but no enum
        // value -- so the loader silently dropped it and it could never appear.
        boolean inEnum = false;
        for (Item.ItemType t : Item.ItemType.values()) {
            if (t.name().equals("SHIELD_ROUND_WOODEN")) inEnum = true;
        }
        assertTrue("SHIELD_ROUND_WOODEN must exist as an enum value", inEnum);

        List<String> rows = new ArrayList<>();
        collectTypes(load("spawntables.json"), rows);
        assertTrue("SHIELD_ROUND_WOODEN must be able to spawn", rows.contains("SHIELD_ROUND_WOODEN"));
    }

    @Test
    public void everyShieldTypeThatCanSpawnHasAPaperdollLayer() {
        // SHIELD_MEDIUM could spawn with no layer, so it equipped invisibly.
        JsonValue layers = load("paperdoll_layers.json");
        JsonValue items = layers.get("items") != null ? layers.get("items") : layers;

        List<String> rows = new ArrayList<>();
        collectTypes(load("spawntables.json"), rows);

        List<String> missing = new ArrayList<>();
        for (String type : rows) {
            if (!type.contains("SHIELD") && !type.contains("BUCKLER")) continue;
            if (items.get(type) == null) missing.add(type);
        }
        assertEquals("Spawnable shields with no paperdoll layer render invisibly: " + missing,
                0, missing.size());
    }

    @Test
    public void theAliasNamesSurviveForOldSaves() {
        // Deleting these would not lose one shield. SaveManager returns null for
        // an unparseable player.json, WorldManager regenerates an unparseable
        // chunk, and ShelterChest would drop the stash.
        for (String alias : new String[]{"SHIELD_SMALL", "SHIELD_MEDIUM", "SHIELD_LARGE", "SHIELD"}) {
            boolean found = false;
            for (Item.ItemType t : Item.ItemType.values()) {
                if (t.name().equals(alias)) found = true;
            }
            assertTrue("Enum value " + alias + " was removed; any save containing it is now"
                    + " unreadable", found);
        }
    }

    private void collectTypes(JsonValue node, List<String> out) {
        for (JsonValue v = node.child; v != null; v = v.next) {
            if (v.isObject() || v.isArray()) {
                String t = v.getString("type", null);
                if (t != null) out.add(t);
                collectTypes(v, out);
            }
        }
    }
}
