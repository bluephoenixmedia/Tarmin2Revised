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
    public void healingSpellsHealTheCasterAndNeverHurtTheMonsterInFront() {
        SpellDataManager.getInstance().load();
        com.bpm.minotaur.gamedata.monster.Monster goblin =
                new com.bpm.minotaur.gamedata.monster.Monster(com.bpm.minotaur.gamedata.monster.Monster.MonsterType.GOBLIN, 30, 10, 2, 3);
        maze.addMonster(goblin);
        player.getStats().setMaxMP(100);

        for (String id : new String[] { "CURE_WOUNDS", "HEALING_WORD", "HEAL", "PRAYER_OF_HEALING", "MASS_CURE_WOUNDS" }) {
            player.getStats().setCurrentHP(1);
            player.getStats().setCurrentMP(player.getStats().getMaxMP());

            assertTrue(id, SpellExecutionEngine.castSpell(id, player, maze, eventManager, null));

            assertEquals(id + " must not hurt the goblin", 30, goblin.getCurrentHP());
            assertTrue(id + " heals the caster", player.getStats().getCurrentHP() > 1);
        }
    }

    @Test
    public void thunderwaveDealsThunderDamage() {
        SpellDataManager.getInstance().load();
        assertEquals("THUNDER", SpellDataManager.getSpell("THUNDERWAVE").getDamageType());
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
    public void testLightSpellCostsMP() {
        SpellDataManager manager = SpellDataManager.getInstance();
        manager.load();

        SpellTemplate moteOfLight = SpellDataManager.getSpell("MOTE_OF_LIGHT");
        assertNotNull("Mote of Light spell should be loaded", moteOfLight);
        assertEquals(2, moteOfLight.mpCost);

        SpellTemplate light = SpellDataManager.getSpell("LIGHT");
        assertNotNull("Light spell should be loaded", light);
        assertEquals(2, light.mpCost);

        // Player starts with Cure Wounds in prepared slot 0
        assertEquals(com.bpm.minotaur.gamedata.player.Player.STARTING_HEAL_SPELL, player.getPreparedSpell(0));

        // When player has enough MP, casting MOTE_OF_LIGHT deducts 2 MP
        player.getStats().setMaxMP(10);
        player.getStats().setCurrentMP(10);
        player.prepareSpell(0, "MOTE_OF_LIGHT");

        boolean castSuccess = player.castPreparedSpell(0, maze, eventManager, null);
        assertTrue("Player should successfully cast Mote of Light with enough MP", castSuccess);
        assertEquals("Player MP should be deducted by 2", 8, player.getStats().getCurrentMP());

        // When player does not have enough MP (e.g. 1 MP), casting must fail and preserve MP
        player.getStats().setCurrentMP(1);
        boolean castFail = player.castPreparedSpell(0, maze, eventManager, null);
        assertFalse("Player should not be able to cast Mote of Light with 1 MP", castFail);
        assertEquals("Player MP should remain unchanged at 1", 1, player.getStats().getCurrentMP());
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
        assertEquals(com.bpm.minotaur.gamedata.player.Player.STARTING_HEAL_SPELL, player.getPreparedSpell(0));
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

    @Test
    public void testHealingSpellsFlagAndArchetypes() {
        SpellDataManager.getInstance().load();

        String[] radiantHealingSpells = {
                "AID", "CURE_WOUNDS", "GOODBERRY", "GREATER_RESTORATION", "HEAL",
                "HEALING_WORD", "LESSER_RESTORATION", "MASS_CURE_WOUNDS", "MASS_HEAL",
                "MASS_HEALING_WORD", "PRAYER_OF_HEALING", "REGENERATE", "REVIVIFY",
                "TRUE_RESURRECTION"
        };

        for (String id : radiantHealingSpells) {
            SpellTemplate spell = SpellDataManager.getSpell(id);
            assertNotNull("Spell should exist: " + id, spell);
            assertTrue("Spell " + id + " must have isHealing() == true", spell.isHealing());
            assertEquals("Spell " + id + " must have HOLY_RADIANCE archetype",
                    VisualArchetype.HOLY_RADIANCE, spell.getVisualArchetypeEnum());
        }

        SpellTemplate beacon = SpellDataManager.getSpell("BEACON_OF_HOPE");
        assertNotNull(beacon);
        assertTrue("Beacon of Hope must have isHealing() == true", beacon.isHealing());
        assertEquals(VisualArchetype.ARCANE_WARD, beacon.getVisualArchetypeEnum());

        SpellTemplate fireball = SpellDataManager.getSpell("FIREBALL");
        assertNotNull(fireball);
        assertFalse("Fireball must not be healing", fireball.isHealing());

        SpellTemplate magicMissile = SpellDataManager.getSpell("MAGIC_MISSILE");
        assertNotNull(magicMissile);
        assertFalse("Magic Missile must not be healing", magicMissile.isHealing());
    }

    @Test
    public void testReauthoredVisualArchetypes() {
        SpellDataManager.getInstance().load();

        // Enchantment / Mind spells re-authored to PSYCHIC_SHOCK
        for (String id : new String[] { "CHARM_PERSON", "COMMAND", "CONFUSION", "SUGGESTION", "HIDEOUS_LAUGHTER" }) {
            SpellTemplate spell = SpellDataManager.getSpell(id);
            assertNotNull(spell);
            assertEquals(id + " should be PSYCHIC_SHOCK", VisualArchetype.PSYCHIC_SHOCK, spell.getVisualArchetypeEnum());
        }

        // Illusion / Warp spells re-authored to SPATIAL_WARP
        for (String id : new String[] { "INVISIBILITY", "SEEMING", "SIMULACRUM", "COLOR_SPRAY", "CREATION" }) {
            SpellTemplate spell = SpellDataManager.getSpell(id);
            assertNotNull(spell);
            assertEquals(id + " should be SPATIAL_WARP", VisualArchetype.SPATIAL_WARP, spell.getVisualArchetypeEnum());
        }

        // Evocation / Element spells re-authored appropriately
        assertEquals(VisualArchetype.FLAME_BOLT, SpellDataManager.getSpell("CONTINUAL_FLAME").getVisualArchetypeEnum());
        assertEquals(VisualArchetype.FROST_RAY, SpellDataManager.getSpell("SLEET_STORM").getVisualArchetypeEnum());
        assertEquals(VisualArchetype.THUNDER_CONCUSSION, SpellDataManager.getSpell("EARTHQUAKE").getVisualArchetypeEnum());
        assertEquals(VisualArchetype.THUNDER_CONCUSSION, SpellDataManager.getSpell("GUST_OF_WIND").getVisualArchetypeEnum());
        assertEquals(VisualArchetype.ARCANE_WARD, SpellDataManager.getSpell("FORCECAGE").getVisualArchetypeEnum());
        assertEquals(VisualArchetype.ARCANE_WARD, SpellDataManager.getSpell("WALL_OF_FORCE").getVisualArchetypeEnum());

        // Canonical kinetic/force spells retaining FORCE_MISSILE
        for (String id : new String[] { "MAGIC_MISSILE", "ELDRITCH_BLAST", "ARCANE_HAND", "SPIRITUAL_WEAPON", "TELEKINESIS" }) {
            SpellTemplate spell = SpellDataManager.getSpell(id);
            assertNotNull(spell);
            assertEquals(id + " should retain FORCE_MISSILE", VisualArchetype.FORCE_MISSILE, spell.getVisualArchetypeEnum());
        }
    }

    @Test
    public void testExplosionForArchetypes() {
        assertEquals(com.bpm.minotaur.rendering.vfx.SpellExplosionRegistry.ExplosionType.FIRE,
                SpellExecutionEngine.getExplosionForArchetype(VisualArchetype.FLAME_BOLT));
        assertEquals(com.bpm.minotaur.rendering.vfx.SpellExplosionRegistry.ExplosionType.ICE,
                SpellExecutionEngine.getExplosionForArchetype(VisualArchetype.FROST_RAY));
        assertEquals(com.bpm.minotaur.rendering.vfx.SpellExplosionRegistry.ExplosionType.ELECTRIC,
                SpellExecutionEngine.getExplosionForArchetype(VisualArchetype.LIGHTNING_ARC));
        assertEquals(com.bpm.minotaur.rendering.vfx.SpellExplosionRegistry.ExplosionType.CONCUSSIVE,
                SpellExecutionEngine.getExplosionForArchetype(VisualArchetype.FORCE_MISSILE));
        assertEquals(com.bpm.minotaur.rendering.vfx.SpellExplosionRegistry.ExplosionType.HOLY_CROSS,
                SpellExecutionEngine.getExplosionForArchetype(VisualArchetype.HOLY_RADIANCE));
        assertEquals(com.bpm.minotaur.rendering.vfx.SpellExplosionRegistry.ExplosionType.TOXIC,
                SpellExecutionEngine.getExplosionForArchetype(VisualArchetype.NECROTIC_DRAIN));
        assertEquals(com.bpm.minotaur.rendering.vfx.SpellExplosionRegistry.ExplosionType.TOXIC,
                SpellExecutionEngine.getExplosionForArchetype(VisualArchetype.TOXIC_CLOUD));
        assertEquals(com.bpm.minotaur.rendering.vfx.SpellExplosionRegistry.ExplosionType.VOID,
                SpellExecutionEngine.getExplosionForArchetype(VisualArchetype.SPATIAL_WARP));
        assertEquals(com.bpm.minotaur.rendering.vfx.SpellExplosionRegistry.ExplosionType.STANDARD,
                SpellExecutionEngine.getExplosionForArchetype(VisualArchetype.ARCANE_WARD));
    }
}
