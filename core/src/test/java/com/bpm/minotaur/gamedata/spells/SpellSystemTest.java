package com.bpm.minotaur.gamedata.spells;

import com.bpm.minotaur.gamedata.Direction;
import com.bpm.minotaur.gamedata.Inventory;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.ItemTemplate;
import com.bpm.minotaur.gamedata.player.Player;
import com.bpm.minotaur.managers.GameEventManager;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class SpellSystemTest {

    private Player player;
    private Maze maze;
    private GameEventManager eventManager;

    @Before
    public void setUp() {
        if (com.badlogic.gdx.Gdx.app == null) {
            com.badlogic.gdx.Gdx.app = (com.badlogic.gdx.Application) java.lang.reflect.Proxy.newProxyInstance(
                    com.badlogic.gdx.Application.class.getClassLoader(),
                    new Class<?>[]{com.badlogic.gdx.Application.class},
                    (proxy, method, args) -> null
            );
        }

        int[][] wallData = new int[10][10];
        maze = new Maze(1, wallData);
        player = new Player(2, 2);
        player.setFacing(Direction.NORTH);
        eventManager = new GameEventManager();
    }

    @Test
    public void testSpellDataManagerLoading() {
        SpellDataManager manager = SpellDataManager.getInstance();
        manager.load();

        SpellTemplate fireball = SpellDataManager.getSpell("FIREBALL");
        assertNotNull("Fireball spell should be loaded from spells.json", fireball);
        assertEquals("FIREBALL", fireball.id);
        assertEquals(3, fireball.level);
        assertEquals(8, fireball.mpCost);
        assertTrue("evocation".equalsIgnoreCase(fireball.school));

        SpellTemplate fireBolt = SpellDataManager.getSpell("FIRE_BOLT");
        assertNotNull("Fire Bolt cantrip should be loaded", fireBolt);
        assertEquals(0, fireBolt.level);
        assertEquals(0, fireBolt.mpCost);

        // Fallback for non-existent spell
        SpellTemplate unknown = SpellDataManager.getSpell("NON_EXISTENT_SPELL");
        assertNull("Unknown spell should return null", unknown);
    }

    @Test
    public void test5eArmorClassCalculations() {
        // Base AC without armor should be 10 + Dex modifier
        player.getStats().setDexterity(10); // DEX 10 -> +0
        assertEquals(10, player.getArmorClass());

        player.getStats().setDexterity(16); // DEX 16 -> +3
        assertEquals(13, player.getArmorClass());

        // Equip Light Armor (+2 AC, maxDex = 99)
        ItemTemplate lightTemplate = new ItemTemplate();
        lightTemplate.friendlyName = "Leather Armor";
        lightTemplate.armorClassBonus = 2;
        lightTemplate.isArmor = true;
        lightTemplate.isTorso = true;
        lightTemplate.armorCategory = "LIGHT";
        lightTemplate.maxDexBonus = 99;
        Item lightArmor = Item.fromTemplate(Item.ItemType.LEATHER_ARMOR, lightTemplate);
        player.getEquipment().setWornChest(lightArmor);

        // AC = 10 + 3 (DEX) + 2 (Armor) = 15
        assertEquals(15, player.getArmorClass());

        // Equip Medium Armor (+4 AC, maxDex = 2)
        ItemTemplate medTemplate = new ItemTemplate();
        medTemplate.friendlyName = "Scale Mail";
        medTemplate.armorClassBonus = 4;
        medTemplate.isArmor = true;
        medTemplate.isTorso = true;
        medTemplate.armorCategory = "MEDIUM";
        medTemplate.maxDexBonus = 2;
        Item medArmor = Item.fromTemplate(Item.ItemType.CHAIN_MAIL, medTemplate);
        player.getEquipment().setWornChest(medArmor);

        // AC = 10 + min(2, 3) (DEX capped at 2) + 4 (Armor) = 16
        assertEquals(16, player.getArmorClass());

        // Equip Heavy Armor (+8 AC, maxDex = 0)
        ItemTemplate heavyTemplate = new ItemTemplate();
        heavyTemplate.friendlyName = "Plate Armor";
        heavyTemplate.armorClassBonus = 8;
        heavyTemplate.isArmor = true;
        heavyTemplate.isTorso = true;
        heavyTemplate.armorCategory = "HEAVY";
        heavyTemplate.maxDexBonus = 0;
        heavyTemplate.stealthDisadvantage = true;
        Item heavyArmor = Item.fromTemplate(Item.ItemType.FULL_PLATE, heavyTemplate);
        player.getEquipment().setWornChest(heavyArmor);

        // AC = 10 + 0 (DEX capped at 0) + 8 (Armor) = 18
        assertEquals(18, player.getArmorClass());
        assertTrue(player.getEquipment().hasStealthDisadvantage());
    }

    @Test
    public void testWeaponVersatileAndTwoHandedMechanics() {
        Inventory inv = player.getInventory();

        // 1. Versatile Weapon (Longsword: 1d8 1H, 1d10 2H)
        ItemTemplate swordTmpl = new ItemTemplate();
        swordTmpl.friendlyName = "Longsword";
        swordTmpl.damageDice = "1d8";
        swordTmpl.isWeapon = true;
        swordTmpl.isVersatile = true;
        swordTmpl.versatileDamageDice = "1d10";
        Item sword = Item.fromTemplate(Item.ItemType.SWORD_LONG, swordTmpl);

        inv.setRightHand(sword);
        inv.setLeftHand(null);

        // Empty offhand -> versatile 2H damage die (1d10)
        assertEquals("1d10", inv.getActiveDamageDice(sword));

        // Equip Shield in offhand -> standard 1H damage die (1d8)
        ItemTemplate shieldTmpl = new ItemTemplate();
        shieldTmpl.friendlyName = "Round Shield";
        shieldTmpl.isShield = true;
        Item shield = Item.fromTemplate(Item.ItemType.BUCKLER, shieldTmpl);
        inv.setLeftHand(shield);

        assertEquals("1d8", inv.getActiveDamageDice(sword));

        // 2. Two-Handed Weapon (Greatsword)
        ItemTemplate greatTmpl = new ItemTemplate();
        greatTmpl.friendlyName = "Greatsword";
        greatTmpl.damageDice = "2d6";
        greatTmpl.isWeapon = true;
        greatTmpl.isTwoHanded = true;
        Item greatsword = Item.fromTemplate(Item.ItemType.SWORD_TWO_HANDED, greatTmpl);

        // Equipping two-handed weapon in right hand should unequip left hand to backpack
        inv.setRightHand(greatsword);
        assertNull("Left hand should be cleared when equipping 2H weapon", inv.getLeftHand());
        assertEquals(greatsword, inv.getRightHand());
        assertTrue("Offhand shield should be placed in backpack", inv.getMainInventory().contains(shield));

        // Equipping into left hand while wielding 2H should unequip the 2H weapon to backpack
        inv.setLeftHand(shield);
        assertEquals(shield, inv.getLeftHand());
        assertNull("2H weapon should be unequipped when equipping left hand", inv.getRightHand());
        assertTrue("Greatsword should be placed in backpack", inv.getMainInventory().contains(greatsword));
    }

    @Test
    public void testFinesseScaling() {
        // High DEX, Low STR: mod +4 plus base bonus 2 = 6 to-hit; mod +4 to damage
        player.getStats().setStrength(10);  // STR 10 -> mod 0
        player.getStats().setDexterity(18); // DEX 18 -> mod +4

        assertEquals(6, player.getFinesseToHitBonus());
        assertEquals(4, player.getFinesseDamageBonus());

        // High STR, Low DEX: mod +3 plus base bonus 2 = 5 to-hit; mod +3 to damage
        player.getStats().setStrength(16);  // STR 16 -> mod +3
        player.getStats().setDexterity(12); // DEX 12 -> mod +1

        assertEquals(5, player.getFinesseToHitBonus());
        assertEquals(3, player.getFinesseDamageBonus());
    }

    @Test
    public void testSpellPreparationAndScribing() {
        // Verify default prepared spells
        assertEquals("MOTE_OF_LIGHT", player.getPreparedSpell(0));
        assertNull(player.getPreparedSpell(1));
        assertNull(player.getPreparedSpell(2));
        assertNull(player.getPreparedSpell(3));

        // Prepare a new spell in slot 4 (requires all 5 spell slots unlocked)
        player.setUnlockedSpellSlots(5);
        player.learnSpellId("FIREBALL");
        player.prepareSpell(4, "FIREBALL");
        assertEquals("FIREBALL", player.getPreparedSpell(4));

        // Test Scroll Inscription (permanently learns the spell, costs 100% of its MP,
        // and requires an open prepared spell slot -- slot 1 is free at this point)
        ItemTemplate scrollTmpl = new ItemTemplate();
        scrollTmpl.friendlyName = "Scroll of Misty Step";
        Item scroll = Item.fromTemplate(Item.ItemType.SCROLL_MISTY_STEP, scrollTmpl);
        player.getInventory().pickup(scroll);
        player.getStats().setCurrentMP(player.getStats().getMaxMP());

        assertFalse(player.getKnownSpellIds().contains("MISTY_STEP"));
        assertTrue("Player should be able to inscribe this scroll", player.canInscribeScroll(scroll));

        boolean inscribed = player.inscribeScroll(scroll, eventManager, null);
        assertTrue("Scroll should be inscribed successfully", inscribed);
        assertTrue(player.getKnownSpellIds().contains("MISTY_STEP"));
        assertEquals("MISTY_STEP", player.getPreparedSpell(1));
        assertFalse("Scroll should be consumed from inventory", player.getInventory().getMainInventory().contains(scroll));
    }
}
