package com.bpm.minotaur.generation;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.GridPoint2;
import com.badlogic.gdx.math.Vector2;
import com.bpm.minotaur.gamedata.DamageType;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.effects.StatusEffectType;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.ItemColor;
import com.bpm.minotaur.gamedata.item.ItemTemplate;
import com.bpm.minotaur.gamedata.monster.MonsterTemplate;
import com.bpm.minotaur.gamedata.player.Player;
import com.bpm.minotaur.gamedata.progression.ShelterAltar;
import com.bpm.minotaur.managers.DivinityManager;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;

import static org.junit.Assert.*;

/**
 * Automated tests covering the Shelter Progression, Desperate Survivor,
 * Flat Armor DR, and Strata 1 Threat Bounding balance reboot.
 */
public class ProgressionRebootTest {

    @Before
    public void setUp() {
        if (Gdx.app == null) {
            Gdx.app = (Application) Proxy.newProxyInstance(
                    Application.class.getClassLoader(),
                    new Class<?>[]{Application.class},
                    (proxy, method, args) -> null
            );
        }
        if (Gdx.files == null) {
            Gdx.files = (com.badlogic.gdx.Files) Proxy.newProxyInstance(
                    com.badlogic.gdx.Files.class.getClassLoader(),
                    new Class<?>[]{com.badlogic.gdx.Files.class},
                    (proxy, method, args) -> {
                        if ("local".equals(method.getName()) || "internal".equals(method.getName())) {
                            String path = (String) args[0];
                            java.io.File file = new java.io.File(path);
                            if (!file.exists()) {
                                file = new java.io.File("assets/" + path);
                            }
                            if (!file.exists()) {
                                file = new java.io.File("../assets/" + path);
                            }
                            return new com.badlogic.gdx.files.FileHandle(file);
                        }
                        return null;
                    }
            );
        }
        ShelterAltar.getInstance().reset();
    }

    @org.junit.After
    public void tearDown() {
        Gdx.app = null;
        Gdx.files = null;
        ShelterAltar.getInstance().reset();
    }

    @Test
    public void testEmptyShelterStartsWithoutStations() {
        ShelterAltar altar = ShelterAltar.getInstance();
        altar.reset();

        assertFalse("Shelter must start without Bed", altar.hasStation(ShelterAltar.Station.BED));
        assertFalse("Shelter must start without Stash Chest", altar.hasStation(ShelterAltar.Station.STASH_CHEST));
        assertFalse("Shelter must start without Campfire", altar.hasStation(ShelterAltar.Station.CAMPFIRE));
        assertFalse("Shelter must start without Crafting Bench", altar.hasStation(ShelterAltar.Station.CRAFTING_BENCH));
        assertFalse("Shelter must start without Lantern", altar.hasStation(ShelterAltar.Station.LANTERN));
        assertFalse("Shelter must start without Training Grounds", altar.hasStation(ShelterAltar.Station.TRAINING_DUMMY));
    }

    @Test
    public void testAltarCommuneRestoresHpMpAndRequiresDelve() {
        ShelterAltar altar = ShelterAltar.getInstance();
        altar.reset();
        assertTrue("Altar should be ready to commune initially", altar.canCommune());

        Player player = new Player(0, 0);
        player.getStats().setCurrentHP(5);
        player.getStats().setCurrentMP(2);

        boolean success = altar.commune(player, null);
        assertTrue("Commune should succeed", success);
        assertEquals("HP should be restored to max", player.getStats().getMaxHP(), player.getStats().getCurrentHP());
        assertEquals("MP should be restored to max", player.getStats().getMaxMP(), player.getStats().getCurrentMP());
        assertFalse("Altar should be dormant after commune", altar.canCommune());

        boolean secondTry = altar.commune(player, null);
        assertFalse("Dormant altar cannot commune without delve", secondTry);

        altar.rearmCommune();
        assertTrue("Rearm should restore commune ability", altar.canCommune());
    }

    @Test
    public void testPlayerStartingKitIsDesperateSurvivor() {
        Player player = new Player(0, 0);

        // Magic stripped down to ONLY Mote of Light
        assertTrue("Player must know MOTE_OF_LIGHT", player.getKnownSpellIds().contains("MOTE_OF_LIGHT"));
        assertEquals("Slot 0 must have MOTE_OF_LIGHT", "MOTE_OF_LIGHT", player.getPreparedSpell(0));
        assertNull("Slot 1 must be empty", player.getPreparedSpell(1));
        assertNull("Slot 2 must be empty", player.getPreparedSpell(2));
        assertNull("Slot 3 must be empty", player.getPreparedSpell(3));

        // Nukes stripped
        assertFalse("MAGIC_MISSILE must be stripped", player.getKnownSpellIds().contains("MAGIC_MISSILE"));
        assertFalse("FIRE_BOLT must be stripped", player.getKnownSpellIds().contains("FIRE_BOLT"));
        assertFalse("CURE_WOUNDS must be stripped", player.getKnownSpellIds().contains("CURE_WOUNDS"));
        assertFalse("SHIELD must be stripped", player.getKnownSpellIds().contains("SHIELD"));
    }

    @Test
    public void testArmorDamageReductionTiers() {
        // Cloth / Padded: 0 DR
        Item padded = createItem(Item.ItemType.PADDED_ARMOR, 1, true);
        assertEquals(0, padded.getDamageReduction());

        // Leather / Boots: 1 DR
        Item leather = createItem(Item.ItemType.LEATHER_ARMOR, 2, true);
        assertEquals(1, leather.getDamageReduction());

        Item boots = createItem(Item.ItemType.BOOTS, 1, true);
        assertEquals(1, boots.getDamageReduction());

        // Chain / Hauberk: 2 DR
        Item chain = createItem(Item.ItemType.HAUBERK, 4, true);
        assertEquals(2, chain.getDamageReduction());

        // Plate: 3 DR
        Item plate = createItem(Item.ItemType.FULL_PLATE, 8, true);
        assertEquals(3, plate.getDamageReduction());

        // Shield: 1 DR
        Item shield = createItem(Item.ItemType.SHIELD, 2, false);
        assertEquals(1, shield.getDamageReduction());
    }

    @Test
    public void testArmorMitigationReducesDamageWithChipFloor() {
        Player player = new Player(0, 0);

        // Equip Full Plate on chest (3 DR)
        Item plate = createItem(Item.ItemType.FULL_PLATE, 8, true);
        player.getEquipment().setWornChest(plate);
        assertEquals(3, player.getTotalDamageReduction());

        int initialHP = player.getCurrentHP();
        // Raw 10 physical damage with 3 DR -> (10 - 3) * vulnerabilityMultiplier
        int expected = (int) (7 * player.getStats().getVulnerabilityMultiplier());
        int taken = player.takeDamage(10, DamageType.PHYSICAL);
        assertEquals(expected, taken);
        assertEquals(initialHP - expected, player.getCurrentHP());

        // Raw 2 physical damage with 3 DR -> should deal minimum chip floor of 1 damage
        player.getStats().setCurrentHP(initialHP);
        int lowDamageTaken = player.takeDamage(2, DamageType.PHYSICAL);
        assertEquals("Damage must respect 1 chip damage floor", 1, lowDamageTaken);
    }

    @Test
    public void testSacrificeValuesByRarity() {
        Item tanItem = createItem(Item.ItemType.DAGGER, 0, false);
        tanItem.setItemColor(ItemColor.TAN);
        assertEquals(1, ShelterAltar.getSacrificeValue(tanItem));

        Item grayItem = createItem(Item.ItemType.SWORD, 0, false);
        grayItem.setItemColor(ItemColor.GRAY);
        assertEquals(2, ShelterAltar.getSacrificeValue(grayItem));

        Item greenItem = createItem(Item.ItemType.AXE_BATTLE, 0, false);
        greenItem.setItemColor(ItemColor.GREEN);
        assertEquals(4, ShelterAltar.getSacrificeValue(greenItem));

        Item blueItem = createItem(Item.ItemType.WARHAMMER, 0, false);
        blueItem.setItemColor(ItemColor.BLUE);
        assertEquals(8, ShelterAltar.getSacrificeValue(blueItem));

        Item goldItem = createItem(Item.ItemType.CROWN, 0, false);
        goldItem.setItemColor(ItemColor.GOLD);
        assertEquals(15, ShelterAltar.getSacrificeValue(goldItem));
    }

    @Test
    public void testStrata1ThreatBoundingMCRCalculation() {
        // Tier 1 foe: Goblin / Kobold (low HP, low AC)
        MonsterTemplate goblin = new MonsterTemplate();
        goblin.maxHP = 12;
        goblin.maxMP = 0;
        goblin.armorClass = 11;
        goblin.dexterity = 8;
        goblin.hasRangedAttack = false;
        // MCR = 12 + 0 + 33 + 8 + 0 = 53 (<= 55, Tier 1)
        int goblinMCR = MonsterSpawner.calculateMCR(goblin);
        assertTrue("Goblin MCR should be <= 55", goblinMCR <= 55);

        // Tier 2 foe: Skeleton / Hobgoblin (higher HP, armor)
        MonsterTemplate hobgoblin = new MonsterTemplate();
        hobgoblin.maxHP = 28;
        hobgoblin.maxMP = 0;
        hobgoblin.armorClass = 15;
        hobgoblin.dexterity = 12;
        hobgoblin.hasRangedAttack = false;
        // MCR = 28 + 0 + 45 + 12 + 0 = 85 (> 55, Tier 2)
        int hobgoblinMCR = MonsterSpawner.calculateMCR(hobgoblin);
        assertTrue("Hobgoblin MCR should be > 55", hobgoblinMCR > 55);
    }

    private Item createItem(Item.ItemType type, int acBonus, boolean isArmor) {
        Item item = new Item();
        try {
            Field typeField = Item.class.getDeclaredField("type");
            typeField.setAccessible(true);
            typeField.set(item, type);

            Field acField = Item.class.getDeclaredField("armorClassBonus");
            acField.setAccessible(true);
            acField.set(item, acBonus);

            Field armorField = Item.class.getDeclaredField("isArmor");
            armorField.setAccessible(true);
            armorField.set(item, isArmor);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return item;
    }
}
