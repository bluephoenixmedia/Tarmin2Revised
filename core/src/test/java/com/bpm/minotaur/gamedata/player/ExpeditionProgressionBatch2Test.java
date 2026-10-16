package com.bpm.minotaur.gamedata.player;

import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.ItemTemplate;
import com.bpm.minotaur.gamedata.save.PlayerSaveData;
import com.bpm.minotaur.gamedata.spells.SpellDataManager;
import com.bpm.minotaur.gamedata.spells.SpellTemplate;
import com.bpm.minotaur.gamedata.progression.ShelterAltar;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class ExpeditionProgressionBatch2Test {

    private Player player;

    @Before
    public void setUp() {
        if (com.badlogic.gdx.Gdx.app == null) {
            com.badlogic.gdx.Gdx.app = (com.badlogic.gdx.Application) java.lang.reflect.Proxy.newProxyInstance(
                    com.badlogic.gdx.Application.class.getClassLoader(),
                    new Class<?>[]{com.badlogic.gdx.Application.class},
                    (proxy, method, args) -> null
            );
        }
        SpellDataManager.getInstance().load();
        player = new Player(0, 0);
    }

    @Test
    public void testStripAllEquipped() {
        PlayerEquipment eq = new PlayerEquipment();
        ItemTemplate tmpl = new ItemTemplate();
        tmpl.isArmor = true;
        Item helmet = Item.fromTemplate(Item.ItemType.HELMET, tmpl);
        Item chest = Item.fromTemplate(Item.ItemType.BREASTPLATE, tmpl);
        Item boots = Item.fromTemplate(Item.ItemType.BOOTS, tmpl);

        eq.setWornHelmet(helmet);
        eq.setWornChest(chest);
        eq.setWornBoots(boots);

        assertEquals(3, eq.getAllEquipped().size());

        eq.stripAllEquipped();

        assertEquals(0, eq.getAllEquipped().size());
        assertNull(eq.getWornHelmet());
        assertNull(eq.getWornChest());
        assertNull(eq.getWornBoots());
    }

    @Test
    public void testSpellPersistenceSplit() {
        // Player starts with MOTE_OF_LIGHT as permanent spell
        assertTrue(player.getPermanentSpellIds().contains("MOTE_OF_LIGHT"));
        assertTrue(player.getKnownSpellIds().contains("MOTE_OF_LIGHT"));
        assertTrue(player.getRunSpellIds().isEmpty());

        // Learn a run spell (from field scroll or spellbook)
        player.learnRunSpellId("FIREBALL");
        assertTrue(player.getRunSpellIds().contains("FIREBALL"));
        assertTrue(player.getKnownSpellIds().contains("FIREBALL"));
        assertFalse(player.getPermanentSpellIds().contains("FIREBALL"));

        // Prepare both spells
        player.prepareSpell(0, "MOTE_OF_LIGHT");
        player.setUnlockedSpellSlots(2);
        player.prepareSpell(1, "FIREBALL");

        assertEquals("MOTE_OF_LIGHT", player.getPreparedSpell(0));
        assertEquals("FIREBALL", player.getPreparedSpell(1));

        // Player dies -> run spells must be cleared and their prepared slots wiped
        player.clearRunSpellsOnDeath();

        assertTrue("Permanent spell should be kept on death", player.getPermanentSpellIds().contains("MOTE_OF_LIGHT"));
        assertTrue("Permanent spell should remain known", player.getKnownSpellIds().contains("MOTE_OF_LIGHT"));
        assertFalse("Run spell should be removed from known on death", player.getKnownSpellIds().contains("FIREBALL"));
        assertTrue("Run spells list should be empty", player.getRunSpellIds().isEmpty());

        assertEquals("Slot 0 holding permanent spell should stay prepared", "MOTE_OF_LIGHT", player.getPreparedSpell(0));
        assertNull("Slot 1 holding run spell should be cleared to null", player.getPreparedSpell(1));
    }

    @Test
    public void testPlayerSaveDataSpellSplitSerialization() {
        player.learnPermanentSpellId("MOTE_OF_LIGHT");
        player.learnRunSpellId("MAGIC_MISSILE");

        PlayerSaveData saveData = new PlayerSaveData(player);

        assertNotNull(saveData.permanentSpellIds);
        assertNotNull(saveData.runSpellIds);
        assertTrue(saveData.permanentSpellIds.contains("MOTE_OF_LIGHT"));
        assertTrue(saveData.runSpellIds.contains("MAGIC_MISSILE"));

        Player restoredPlayer = new Player(0, 0);
        saveData.applyToPlayer(restoredPlayer, null, null);

        assertTrue(restoredPlayer.getPermanentSpellIds().contains("MOTE_OF_LIGHT"));
        assertTrue(restoredPlayer.getRunSpellIds().contains("MAGIC_MISSILE"));
        assertTrue(restoredPlayer.getKnownSpellIds().contains("MOTE_OF_LIGHT"));
        assertTrue(restoredPlayer.getKnownSpellIds().contains("MAGIC_MISSILE"));
    }

    @Test
    public void testXpCurveScalingAndAttributePoints() {
        PlayerStats stats = player.getStats();
        assertEquals(1, stats.getLevel());
        assertEquals(0, stats.getExperience());

        // Level 2 threshold with BASE 90, LOG_BASE 1.2: 90 * 1.2^1 = 108
        assertEquals(108, stats.getExperienceToNextLevel());

        // Add 108 XP -> should level up to 2
        boolean leveled = stats.addExperience(108);
        assertTrue("Player should level up at 108 XP", leveled);
        assertEquals(2, stats.getLevel());

        // Level 3 threshold: 90 * 1.2^2 = 130
        assertEquals(130, stats.getExperienceToNextLevel());

        // Check attribute points: 2 points awarded per level
        assertEquals(2, stats.getUnallocatedAttributePoints());

        // Allocate into STRENGTH
        int oldStr = stats.getStrength();
        boolean allocated = stats.allocateAttribute(ShelterAltar.StatType.STRENGTH);
        assertTrue("Allocation should succeed", allocated);
        assertEquals(oldStr + 1, stats.getStrength());
        assertEquals(1, stats.getUnallocatedAttributePoints());
    }

    @Test
    public void testWisdomMpIntervalFormula() {
        // 1 MP every 12 turns at WIS 10, a turn faster per WIS modifier, never below 6 (#190).
        assertEquals(12, PlayerStats.mpRegenIntervalTurns(8));
        assertEquals(12, PlayerStats.mpRegenIntervalTurns(10));
        assertEquals(10, PlayerStats.mpRegenIntervalTurns(14));
        assertEquals(7, PlayerStats.mpRegenIntervalTurns(20));
        assertEquals(6, PlayerStats.mpRegenIntervalTurns(22));
        assertEquals(6, PlayerStats.mpRegenIntervalTurns(40));
    }

    @Test
    public void testWordOfRecallSpellProperties() {
        SpellTemplate recall = SpellDataManager.getSpell("WORD_OF_RECALL");
        assertNotNull("WORD_OF_RECALL must be loaded from spells.json", recall);
        assertEquals("WORD_OF_RECALL", recall.id);
        assertEquals(15, recall.getMpCost());
        assertEquals(0, recall.getRange());
        assertEquals("SELF", recall.getTargetType());
        assertEquals("WORD_OF_RECALL", recall.getBespokeEffect());
        assertEquals("SPATIAL_WARP", recall.getVisualArchetype());
    }
}
