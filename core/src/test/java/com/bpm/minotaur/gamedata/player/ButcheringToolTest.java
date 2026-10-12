package com.bpm.minotaur.gamedata.player;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.ItemTemplate;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Proxy;

import static org.junit.Assert.*;

public class ButcheringToolTest {

    private Player player;

    @Before
    public void setUp() {
        if (Gdx.app == null) {
            Gdx.app = (Application) Proxy.newProxyInstance(
                    Application.class.getClassLoader(),
                    new Class<?>[]{Application.class},
                    (proxy, method, args) -> null);
        }
        player = new Player(0, 0);
    }

    private static Item createItem(Item.ItemType type, String friendlyName, boolean isWeapon, boolean isRanged, String damageType) {
        ItemTemplate t = new ItemTemplate();
        t.friendlyName = friendlyName;
        t.isWeapon = isWeapon;
        t.isRanged = isRanged;
        t.damageType = damageType;
        return Item.fromTemplate(type, t);
    }

    @Test
    public void testSpearsQualifyAsBladedButcheringTools() {
        Item spear1H = createItem(Item.ItemType.SPEAR_ONE_HANDED, "Spear, One-Handed", true, false, "PIERCING");
        assertTrue("One-handed spear must be recognized as bladed", spear1H.isBladed());

        player.getInventory().setRightHand(spear1H);
        assertTrue("Player holding a spear must have butchering tool", player.hasButcheringTool());

        // Classic Intellivision SPEAR
        Item classicSpear = createItem(Item.ItemType.SPEAR, "Spear", true, true, "PIERCING");
        assertTrue("Spear must be bladed even if flagged with ranged throw property", classicSpear.isBladed());

        // Polearms and pikes
        Item halberd = createItem(Item.ItemType.POLEARM_HALBERD, "Polearm, Halberd", true, false, "SLASHING");
        assertTrue("Halberd must be recognized as bladed", halberd.isBladed());

        Item pike = createItem(Item.ItemType.PIKE_WEIGHTED, "Pike, Weighted", true, false, "PIERCING");
        assertTrue("Pike must be recognized as bladed", pike.isBladed());
    }

    @Test
    public void testSwordsDaggersAxesQualifyAsBladedTools() {
        Item sword = createItem(Item.ItemType.SWORD_LONG, "Longsword", true, false, "SLASHING");
        Item dagger = createItem(Item.ItemType.DAGGER, "Dagger", true, false, "PIERCING");
        Item axe = createItem(Item.ItemType.AXE, "Hand Axe", true, false, "SLASHING");
        Item scythe = createItem(Item.ItemType.SCYTHE, "Scythe", true, false, "SLASHING");

        assertTrue(sword.isBladed());
        assertTrue(dagger.isBladed());
        assertTrue(axe.isBladed());
        assertTrue(scythe.isBladed());

        player.getInventory().getRightHand();
        player.getInventory().getMainInventory().add(dagger);
        assertTrue("Player with dagger in backpack must qualify for butchering", player.hasButcheringTool());
    }

    @Test
    public void testBluntWeaponsDoNotQualifyAsBladedTools() {
        Item mace = createItem(Item.ItemType.MACE_FOOTMANS, "Mace, Footman's", true, false, "BLUDGEONING");
        Item club = createItem(Item.ItemType.CLUB, "Club", true, false, "BLUDGEONING");
        Item warhammer = createItem(Item.ItemType.WARHAMMER, "Warhammer", true, false, "BLUDGEONING");
        Item staff = createItem(Item.ItemType.QUARTERSTAFF, "Quarterstaff", true, false, "BLUDGEONING");

        assertFalse("Mace is blunt and not bladed", mace.isBladed());
        assertFalse("Club is blunt and not bladed", club.isBladed());
        assertFalse("Warhammer is blunt and not bladed", warhammer.isBladed());
        assertFalse("Staff is blunt and not bladed", staff.isBladed());

        player.getInventory().setRightHand(mace);
        assertFalse("Player with only a mace cannot butcher a corpse", player.hasButcheringTool());
    }

    @Test
    public void testRangedBowsAndGunsDoNotQualify() {
        Item bow = createItem(Item.ItemType.BOW, "Bow", true, true, "PIERCING");
        Item musket = createItem(Item.ItemType.MUSKET, "Musket", true, true, "PIERCING");

        assertFalse("Bow is not a bladed butchering tool", bow.isBladed());
        assertFalse("Musket is not a bladed butchering tool", musket.isBladed());

        player.getInventory().setRightHand(musket);
        assertFalse(player.hasButcheringTool());
    }
}
