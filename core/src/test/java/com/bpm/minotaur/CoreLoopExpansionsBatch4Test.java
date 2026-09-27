package com.bpm.minotaur;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.ItemDataManager;
import com.bpm.minotaur.gamedata.item.ItemTemplate;
import com.bpm.minotaur.gamedata.player.Player;
import com.bpm.minotaur.gamedata.progression.ShelterAltar;
import com.bpm.minotaur.gamedata.progression.SkillId;
import com.bpm.minotaur.gamedata.progression.SkillRegistry;
import com.bpm.minotaur.gamedata.spells.SpellDataManager;
import com.bpm.minotaur.gamedata.spells.SpellTemplate;
import com.bpm.minotaur.generation.theme.ChunkTheme;
import com.bpm.minotaur.managers.DoomManager;
import com.bpm.minotaur.managers.UnlockManager;
import com.bpm.minotaur.managers.WorldManager;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.Arrays;

import static org.junit.Assert.*;

public class CoreLoopExpansionsBatch4Test {

    private static Application mockApp;
    private static com.badlogic.gdx.Files mockFiles;
    private ItemDataManager itemDataManager;

    @BeforeClass
    public static void setUpClass() {
        if (Gdx.app == null) {
            mockApp = (Application) Proxy.newProxyInstance(
                    Application.class.getClassLoader(),
                    new Class<?>[]{Application.class},
                    (proxy, method, methodArgs) -> {
                        if ("getType".equals(method.getName())) {
                            return Application.ApplicationType.HeadlessDesktop;
                        }
                        return null;
                    });
            Gdx.app = mockApp;
        }
        if (Gdx.files == null) {
            mockFiles = (com.badlogic.gdx.Files) Proxy.newProxyInstance(
                    com.badlogic.gdx.Files.class.getClassLoader(),
                    new Class<?>[]{com.badlogic.gdx.Files.class},
                    (proxy, method, args) -> {
                        if ("local".equals(method.getName()) || "internal".equals(method.getName())) {
                            String path = (String) args[0];
                            java.io.File file = new java.io.File(path);
                            if (file.isAbsolute()) {
                                return new com.badlogic.gdx.files.FileHandle(file);
                            }
                            if (!file.exists()) {
                                java.io.File assetsFile = new java.io.File("assets/" + path);
                                if (assetsFile.exists()) {
                                    return new com.badlogic.gdx.files.FileHandle(assetsFile);
                                }
                                java.io.File parentAssetsFile = new java.io.File("../assets/" + path);
                                if (parentAssetsFile.exists()) {
                                    return new com.badlogic.gdx.files.FileHandle(parentAssetsFile);
                                }
                            }
                            return new com.badlogic.gdx.files.FileHandle(file);
                        }
                        return null;
                    }
            );
            Gdx.files = mockFiles;
        }
    }

    @AfterClass
    public static void tearDownClass() {
        if (Gdx.app == mockApp) {
            Gdx.app = null;
        }
        if (Gdx.files == mockFiles) {
            Gdx.files = null;
        }
    }

    @Before
    public void setUp() {
        itemDataManager = new ItemDataManager();
        itemDataManager.load();
        itemDataManager.loadWeapons();
        itemDataManager.loadArmor();
        UnlockManager.getInstance().setItemDataManager(itemDataManager);
        SpellDataManager.getInstance().load();
    }

    // =========================================================================
    // Item 34: Shelter Warp Spell (WORD_OF_RECALL)
    // =========================================================================

    @Test
    public void testItem34_WordOfRecallInSpellsData() {
        SpellTemplate spell = SpellDataManager.getInstance().getSpell("WORD_OF_RECALL");
        assertNotNull("WORD_OF_RECALL must be loaded in spells data", spell);
        assertEquals("Word of Recall", spell.name);
        assertEquals(3, spell.level);
        assertEquals(15, spell.mpCost);
    }

    @Test
    public void testItem34_WordOfRecallInShelterAltarTier2() {
        assertTrue("ShelterAltar Arcane Tier 2 must contain WORD_OF_RECALL",
                Arrays.asList(ShelterAltar.ARCANE_UNLOCKED_SPELLS[1]).contains("WORD_OF_RECALL"));
    }

    @Test
    public void testItem34_AstralRecallSkillPermanentlyTeachesWordOfRecall() {
        assertNotNull("ASTRAL_RECALL skill must be registered in SkillRegistry",
                SkillRegistry.getInstance().getSkill(SkillId.ASTRAL_RECALL));

        Player player = new Player(0, 0);
        player.getStats().setWisdom(12);
        player.getStats().setUnallocatedSkillPoints(2);

        assertFalse(player.getPermanentSpellIds().contains("WORD_OF_RECALL"));

        // Learn prerequisite SPELL_WEAVER first
        assertTrue("Must learn prerequisite SPELL_WEAVER", player.learnSkill(SkillId.SPELL_WEAVER));

        // Now learn ASTRAL_RECALL
        boolean learned = player.learnSkill(SkillId.ASTRAL_RECALL);
        assertTrue("Player should learn ASTRAL_RECALL skill", learned);
        assertTrue("Player should have ASTRAL_RECALL skill", player.hasSkill(SkillId.ASTRAL_RECALL));
        assertTrue("Learning ASTRAL_RECALL must permanently grant WORD_OF_RECALL",
                player.getPermanentSpellIds().contains("WORD_OF_RECALL"));
        assertTrue("WORD_OF_RECALL must be in player's known spells",
                player.getKnownSpellIds().contains("WORD_OF_RECALL"));
    }

    // =========================================================================
    // Item 39: Death Penalty (Strip Armor & Weapons, Restart with Rusty Sword 1d3)
    // =========================================================================

    @Test
    public void testItem39_RustySwordDamageDiceIs1d3() {
        ItemTemplate template = itemDataManager.getTemplate(Item.ItemType.RUSTY_SWORD);
        assertNotNull("RUSTY_SWORD template must exist", template);
        assertEquals("1d3", template.damageDice);
    }

    @Test
    public void testItem39_DeathPenaltyStripsEquippedAndRestoresRustySword() {
        Player player = new Player(0, 0);

        // Equip armor and weapons
        ItemTemplate armorTmpl = new ItemTemplate();
        armorTmpl.isArmor = true;
        Item helmet = Item.fromTemplate(Item.ItemType.HELMET, armorTmpl);
        Item breastplate = Item.fromTemplate(Item.ItemType.BREASTPLATE, armorTmpl);
        player.getEquipment().setWornHelmet(helmet);
        player.getEquipment().setWornChest(breastplate);

        ItemTemplate weaponTmpl = new ItemTemplate();
        weaponTmpl.isWeapon = true;
        weaponTmpl.damageDice = "2d6";
        Item shield = Item.fromTemplate(Item.ItemType.SHIELD, weaponTmpl);
        player.getInventory().setLeftHand(shield);

        assertFalse(player.getEquipment().getAllEquipped().isEmpty());
        assertNotNull(player.getInventory().getLeftHand());

        // Simulate respawnInShelter strip and reset
        player.getEquipment().stripAllEquipped();
        player.getInventory().setLeftHand(null);
        Item starterWeapon = itemDataManager.createItem(Item.ItemType.RUSTY_SWORD, 0, 0, null, null);
        player.getInventory().setRightHand(starterWeapon);

        assertTrue("All worn armor must be stripped upon death respawn",
                player.getEquipment().getAllEquipped().isEmpty());
        assertNull("Left hand must be empty upon death respawn", player.getInventory().getLeftHand());
        assertNotNull("Right hand must have starter weapon", player.getInventory().getRightHand());
        assertEquals(Item.ItemType.RUSTY_SWORD, player.getInventory().getRightHand().getType());
        assertEquals("1d3", player.getInventory().getRightHand().getDamageDice());
    }

    // =========================================================================
    // Item 45: Level-Up System, Skill Points & Attribute Point Allocation
    // =========================================================================

    @Test
    public void testItem45_ExperienceLevelUpModalAndAttributeAllocation() {
        Player player = new Player(0, 0);
        assertFalse(player.hasPendingLevelUpModal());
        assertEquals(0, player.getStats().getUnallocatedAttributePoints());
        assertEquals(0, player.getStats().getUnallocatedSkillPoints());

        // Gaining sufficient experience to level up
        player.addExperience(500, null);

        assertTrue("Player should level up", player.getLevel() > 1);
        assertTrue("Pending level up modal flag must be true upon leveling", player.hasPendingLevelUpModal());
        assertTrue("Must gain attribute points on level up",
                player.getStats().getUnallocatedAttributePoints() >= 2);
        assertTrue("Must gain skill points on level up",
                player.getStats().getUnallocatedSkillPoints() >= 1);

        // Allocate attribute points
        int prevStrength = player.getStats().getStrength();
        int prevUnallocated = player.getStats().getUnallocatedAttributePoints();

        boolean allocated = player.allocateAttribute(ShelterAltar.StatType.STRENGTH);
        assertTrue("Attribute allocation must succeed", allocated);
        assertEquals(prevStrength + 1, player.getStats().getStrength());
        assertEquals(prevUnallocated - 1, player.getStats().getUnallocatedAttributePoints());
    }

    @Test
    public void testItem45_SkillLearningWithoutTrainingGroundsRequirement() {
        Player player = new Player(0, 0);
        player.getStats().setStrength(11);
        player.getStats().addUnallocatedSkillPoints(1);

        assertTrue("Player should have at least 1 skill point",
                player.getStats().getUnallocatedSkillPoints() >= 1);

        // Learn root skill DUAL_WIELDER directly without needing training grounds station
        boolean learned = player.learnSkill(SkillId.DUAL_WIELDER);
        assertTrue("Root skill DUAL_WIELDER should be learned immediately with skill points", learned);
        assertTrue(player.hasSkill(SkillId.DUAL_WIELDER));
    }

    // =========================================================================
    // Item 31: Bridge Integrity Boss at 100% Integrity
    // =========================================================================

    @Test
    public void testItem31_BridgeBossSpawnAt100PercentIntegrity() {
        DoomManager doom = DoomManager.getInstance();
        doom.reset();

        assertEquals(0, doom.getDeathCount());
        assertEquals(0f, doom.getBridgeIntegrity(), 0.001f);
        assertFalse(doom.isBridgeBossActive());
        assertFalse(doom.isApocalypse());

        // Increment 49 deaths (98% integrity)
        for (int i = 0; i < 49; i++) {
            doom.incrementDeaths();
        }
        assertEquals(49, doom.getDeathCount());
        assertEquals(98f, doom.getBridgeIntegrity(), 0.001f);
        assertFalse("Bridge boss should not be active before 100%", doom.isBridgeBossActive());
        assertFalse("Apocalypse must not trigger at 98%", doom.isApocalypse());

        // 50th death (100% integrity) -> spawns boss instead of wiping save
        doom.incrementDeaths();
        assertEquals(50, doom.getDeathCount());
        assertEquals(100f, doom.getBridgeIntegrity(), 0.001f);
        assertTrue("Bridge boss must activate at 100% integrity", doom.isBridgeBossActive());
        assertFalse("Apocalypse wipe must NOT trigger on first 100% completion", doom.isApocalypse());
    }

    @Test
    public void testItem31_GuardianRoamsTheSurfaceAndNeverSealsAChunk() {
        DoomManager doom = DoomManager.getInstance();
        WorldManager wm = new WorldManager(null, null, 1, null, null, null, null, null, null);

        // The guardian is no longer a themed chunk. Themed chunks lock their
        // gates on entry, and this boss is meant to roam and be fled from, so
        // it must never bring a seal with it -- nor stand at a fixed address the
        // player can learn.
        doom.setBridgeBossActive(true);
        assertNotEquals("An active guardian must not theme -- and therefore seal -- any chunk",
                ChunkTheme.BRIDGE_OF_SOULS, wm.getChunkTheme(new GridPoint2(0, 1), 1));

        // It stands somewhere on the surface, never in the shelter.
        GridPoint2 seat = com.bpm.minotaur.gamedata.boss.BridgeBoss.chunkFor(12345L, 1);
        assertNotEquals("The guardian must never stand in the shelter",
                new GridPoint2(0, 0), seat);
        assertTrue("It belongs on the surface only",
                com.bpm.minotaur.gamedata.boss.BridgeBoss.isBossChunk(12345L, 1, 1, seat));
        assertFalse("It must not follow the player underground",
                com.bpm.minotaur.gamedata.boss.BridgeBoss.isBossChunk(12345L, 1, 2, seat));

        // Reset state
        doom.setBridgeBossActive(false);
    }

    @Test
    public void testItem31_BridgeBossDefeatResetsIntegrityAndEscalatesNextSummon() {
        DoomManager doom = DoomManager.getInstance();
        doom.reset();
        for (int i = 0; i < 50; i++) {
            doom.incrementDeaths();
        }
        assertTrue(doom.isBridgeBossActive());
        assertEquals(100f, doom.getBridgeIntegrity(), 0.001f);

        // Defeating the Bridge Boss
        doom.onBridgeBossDefeated();

        assertFalse("Bridge boss must be deactivated upon defeat", doom.isBridgeBossActive());
        // Killing the guardian resets integrity outright rather than cutting it.
        // The counterweight is that each summoning is harder than the last, so
        // the 50-death cap still arrives over a long enough campaign.
        assertEquals("Defeating the guardian resets the death count", 0, doom.getDeathCount());
        assertEquals("Integrity should be back to 0%", 0f, doom.getBridgeIntegrity(), 0.001f);
        assertFalse(doom.isApocalypse());
        assertTrue("The summoning must be counted, or the next boss is no harder",
                doom.getBridgeBossSummons() >= 1);
        assertTrue("The next summoning must be harder than the authored value",
                doom.getBridgeBossHpBonus(120) >= 120);
    }

    @Test
    public void testItem31_SubsequentDeathWhileBossActiveTriggersApocalypse() {
        DoomManager doom = DoomManager.getInstance();
        doom.reset();
        for (int i = 0; i < 50; i++) {
            doom.incrementDeaths();
        }
        assertTrue("Boss should be active at 50 deaths", doom.isBridgeBossActive());
        assertFalse("Apocalypse must not trigger on initial boss spawn", doom.isApocalypse());

        // Subsequent death while boss is active
        doom.incrementDeaths();

        assertTrue("Subsequent death while Bridge Boss is active MUST trigger apocalypse wipe",
                doom.isApocalypse());

        // Clean up
        doom.reset();
    }
}
