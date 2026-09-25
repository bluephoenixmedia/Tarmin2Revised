package com.bpm.minotaur.gamedata.item;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.bpm.minotaur.gamedata.Inventory;
import com.bpm.minotaur.gamedata.item.Item.ItemType;
import com.bpm.minotaur.gamedata.player.Player;
import com.bpm.minotaur.gamedata.player.PlayerEquipment;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.FileReader;

import static org.junit.Assert.*;

public class ShieldItemTest {

    private File resolveFile(String path) {
        File f = new File(path);
        if (!f.exists()) {
            f = new File("../" + path);
        }
        return f;
    }

    @Test
    public void testArmorJsonContainsSmallShieldAndShieldSmall() throws Exception {
        File file = resolveFile("assets/data/armor.json");
        assertTrue("armor.json must exist", file.exists());

        JsonValue root = new JsonReader().parse(new FileReader(file));
        JsonValue smallShield = root.get("SMALL_SHIELD");
        assertNotNull("SMALL_SHIELD must exist in armor.json", smallShield);
        assertEquals("Small Shield", smallShield.getString("friendlyName"));
        assertTrue(smallShield.getBoolean("isShield"));
        assertTrue(smallShield.getBoolean("isArmor"));
        assertTrue(smallShield.getInt("baseValue") > 0);
        assertEquals("images/armor/small_shield.png", smallShield.getString("texturePath"));

        JsonValue shieldSmall = root.get("SHIELD_SMALL");
        assertNotNull("SHIELD_SMALL must exist in armor.json", shieldSmall);
        assertEquals("Small Shield", shieldSmall.getString("friendlyName"));
        assertTrue(shieldSmall.getBoolean("isShield"));
        assertTrue(shieldSmall.getBoolean("isArmor"));
    }

    @Test
    public void testArmorJsonContainsLargeShieldAndShieldLarge() throws Exception {
        File file = resolveFile("assets/data/armor.json");
        assertTrue("armor.json must exist", file.exists());

        JsonValue root = new JsonReader().parse(new FileReader(file));
        JsonValue largeShield = root.get("LARGE_SHIELD");
        assertNotNull("LARGE_SHIELD must exist in armor.json", largeShield);
        assertTrue(largeShield.getBoolean("isShield"));

        JsonValue shieldLarge = root.get("SHIELD_LARGE");
        assertNotNull("SHIELD_LARGE must exist in armor.json", shieldLarge);
        assertTrue(shieldLarge.getBoolean("isShield"));
    }

    @Test
    public void testPaperdollLayersContainShieldMappings() throws Exception {
        File file = resolveFile("assets/data/paperdoll_layers.json");
        assertTrue("paperdoll_layers.json must exist", file.exists());

        JsonValue root = new JsonReader().parse(new FileReader(file));
        JsonValue items = root.get("items");
        assertNotNull("items map must exist in paperdoll_layers", items);
        assertNotNull("SMALL_SHIELD must be mapped in paperdoll_layers", items.get("SMALL_SHIELD"));
        assertEquals("small_shield", items.get("SMALL_SHIELD").getString("layer"));
        assertEquals("shield", items.get("SMALL_SHIELD").getString("slot"));

        assertNotNull("SHIELD_SMALL must be mapped in paperdoll_layers", items.get("SHIELD_SMALL"));
        assertNotNull("LARGE_SHIELD must be mapped in paperdoll_layers", items.get("LARGE_SHIELD"));
        assertNotNull("SHIELD_LARGE must be mapped in paperdoll_layers", items.get("SHIELD_LARGE"));
        assertNotNull("SHIELD must be mapped in paperdoll_layers", items.get("SHIELD"));
    }

    @Test
    public void testItemIsShieldAndCategory() {
        ItemTemplate template = new ItemTemplate();
        template.friendlyName = "Small Shield";
        template.isArmor = true;
        template.isShield = true;
        template.baseValue = 15;
        template.texturePath = "images/armor/small_shield.png";

        Item smallShield = Item.fromTemplate(ItemType.SMALL_SHIELD, template);
        assertTrue("smallShield.isShield() must be true", smallShield.isShield());
        assertEquals("smallShield category must be ARMOR", ItemCategory.ARMOR, smallShield.getCategory());

        Item shieldSmall = Item.fromTemplate(ItemType.SHIELD_SMALL, template);
        assertTrue("shieldSmall.isShield() must be true", shieldSmall.isShield());
        assertEquals("shieldSmall category must be ARMOR", ItemCategory.ARMOR, shieldSmall.getCategory());
    }

    @Test
    public void testEquipShieldInEquipment() {
        Player player = new Player(0, 0);
        PlayerEquipment eq = player.getEquipment();
        Inventory inv = player.getInventory();

        ItemTemplate template = new ItemTemplate();
        template.friendlyName = "Small Shield";
        template.isArmor = true;
        template.isShield = true;
        template.baseValue = 15;

        Item shield = Item.fromTemplate(ItemType.SMALL_SHIELD, template);
        inv.setLeftHand(shield);
        eq.setWornShield(shield);

        assertSame("Worn shield must be the equipped small shield", shield, eq.getWornShield());
        assertSame("Left hand must be the equipped small shield", shield, inv.getLeftHand());
    }
}
