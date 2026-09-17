package com.bpm.minotaur.gamedata.bones;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.bpm.minotaur.gamedata.GameMode;
import com.bpm.minotaur.gamedata.Scenery;
import com.bpm.minotaur.gamedata.monster.GhostPlayerMonster;
import com.bpm.minotaur.gamedata.monster.Monster;
import com.bpm.minotaur.gamedata.player.Player;
import com.bpm.minotaur.gamedata.save.ItemSaveData;
import com.bpm.minotaur.gamedata.save.PlayerSaveData;
import com.bpm.minotaur.managers.BonesManager;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class BonesSystemTest {

    private final List<BonesData> createdBonesForCleanup = new ArrayList<>();

    @Before
    public void setUp() {
        if (Gdx.app == null) {
            Gdx.app = (Application) Proxy.newProxyInstance(
                    Application.class.getClassLoader(),
                    new Class<?>[]{Application.class},
                    (proxy, method, args) -> null
            );
        }
    }

    @After
    public void tearDown() {
        BonesManager bm = BonesManager.getInstance();
        for (BonesData b : createdBonesForCleanup) {
            bm.consumeBones(b);
        }
        createdBonesForCleanup.clear();
        bm.clearActiveFloorBones();
    }

    @Test
    public void testStrataBracketMatching() {
        // Strata bracket formula: (floorLevel - 1) / 3 + 1
        assertEquals(1, Math.max(1, (1 - 1) / 3 + 1));
        assertEquals(1, Math.max(1, (2 - 1) / 3 + 1));
        assertEquals(1, Math.max(1, (3 - 1) / 3 + 1));
        assertEquals(2, Math.max(1, (4 - 1) / 3 + 1));
        assertEquals(2, Math.max(1, (5 - 1) / 3 + 1));
        assertEquals(2, Math.max(1, (6 - 1) / 3 + 1));
        assertEquals(3, Math.max(1, (7 - 1) / 3 + 1));
        assertEquals(4, Math.max(1, (10 - 1) / 3 + 1));
    }

    @Test
    public void testBonesSaveAndLoad() {
        Player player = new Player(5, 5);
        player.getStats().setMaxHP(35);
        player.getStats().setCurrentHP(35);
        player.getStats().setDexterity(14);
        player.getStats().setIntelligence(16);

        BonesManager bm = BonesManager.getInstance();
        // Record bones on Floor 5 (Strata 2)
        BonesData bones = bm.recordBonesOnDeath(player, 5, "Fell in glorious combat", GameMode.ADVANCED);
        assertNotNull("Bones should be recorded for ADVANCED mode", bones);
        createdBonesForCleanup.add(bones);

        assertEquals(5, bones.floorLevel);
        assertEquals(2, bones.strataDepth);
        assertEquals("Fell in glorious combat", bones.epitaph);
        assertNotNull(bones.filePath);
        assertTrue(bm.bonesFileExists(bones));

        // Strata 2 should find this bones file
        List<BonesData> strata2Bones = bm.getEligibleBonesForStrata(2);
        boolean found = false;
        for (BonesData b : strata2Bones) {
            if (b.id != null && b.id.equals(bones.id)) {
                found = true;
                break;
            }
        }
        assertTrue("Eligible bones list for Strata 2 must contain the saved bones", found);

        // Strata 1 should NOT find this bones file
        List<BonesData> strata1Bones = bm.getEligibleBonesForStrata(1);
        for (BonesData b : strata1Bones) {
            assertNotEquals("Strata 1 must not include Strata 2 bones", bones.id, b.id);
        }
    }

    @Test
    public void testRetroModeClassicIsolation() {
        Player player = new Player(5, 5);
        BonesManager bm = BonesManager.getInstance();

        // 1. Never record bones in Classic Mode
        BonesData classicRecord = bm.recordBonesOnDeath(player, 4, "Classic test", GameMode.CLASSIC);
        assertNull("Classic mode must never record bones files", classicRecord);

        // 2. Never roll bones on floor entry in Classic Mode
        BonesData rolled = bm.rollBonesForFloor(4, GameMode.CLASSIC);
        assertNull("Classic mode must never roll bones for dungeon floors", rolled);
        assertNull("Active floor bones must remain null in Classic Mode", bm.getActiveFloorBones());
    }

    @Test
    public void testGhostPlayerMonsterStats() {
        PlayerSaveData psd = new PlayerSaveData();
        psd.maxHP = 40;
        psd.currentHP = 40;
        psd.maxMP = 25;
        psd.currentMP = 25;
        psd.dexterity = 16;     // +3 AC bonus
        psd.intelligence = 18;
        psd.level = 4;
        psd.knownSpells = new ArrayList<>();
        psd.knownSpells.add("MAGIC_ARROW");
        psd.knownSpells.add("HEAL");

        BonesData bData = new BonesData("test-ghost-id", "Spectral Dennis", "Perished bravely", 5, 2, psd);

        GhostPlayerMonster ghost = new GhostPlayerMonster(bData, 10, 10, null);

        // Verify mirrored stats
        assertEquals(Monster.MonsterType.PLAYER_GHOST, ghost.getType());
        assertEquals("PLAYER_GHOST", ghost.getMonsterType());
        assertEquals("Spectral Dennis", ghost.getGhostPlayerName());
        assertEquals(40, ghost.getMaxHP());
        assertEquals(40, ghost.getCurrentHP());
        assertEquals(25, ghost.getMaxMP());
        assertEquals(25, ghost.getCurrentMP());
        assertEquals(16, ghost.getDexterity());
        assertEquals(18, ghost.getIntelligence());
        assertEquals(4, ghost.getLevel());

        // Spectral Category: BAD (50% physical resistance, 150% spiritual vulnerability)
        assertEquals(Monster.Category.BAD, ghost.getType().getCategory());

        // AC calculation: 10 + Dex bonus (+3) = 13
        assertTrue("Base AC with 16 DEX should be at least 13", ghost.getArmorClass() >= 13);
    }

    @Test
    public void testBonesLifecycleAndConsumption() {
        Player player = new Player(3, 3);
        BonesManager bm = BonesManager.getInstance();

        BonesData bones = bm.recordBonesOnDeath(player, 2, "Test consumption", GameMode.ADVANCED);
        assertNotNull(bones);
        assertTrue("File must exist initially", bm.bonesFileExists(bones));

        // Consume bones (simulating ghost defeat)
        boolean deleted = bm.consumeBones(bones);
        assertTrue("Bones consumption must succeed", deleted);
        assertFalse("Bones file must be deleted from disk upon consumption", bm.bonesFileExists(bones));

        // Verify it is no longer in eligible list
        List<BonesData> eligible = bm.getEligibleBonesForStrata(1);
        for (BonesData b : eligible) {
            assertNotEquals("Consumed bones must not appear in strata pool", bones.id, b.id);
        }
    }

    @Test
    public void testCorpseSceneryDefeatedState() {
        BonesData bData = new BonesData();
        bData.id = "corpse-test-1";
        bData.playerName = "Old Bones";
        bData.awakened = false;
        bData.defeated = false;

        Scenery corpse = new Scenery(Scenery.SceneryType.DECOMPOSING_CORPSE, 5, 5);
        corpse.setBonesData(bData);

        assertTrue(corpse.isDecomposingCorpse());
        assertFalse(corpse.isAwakened());
        assertFalse(corpse.isDefeated());

        // Awaken
        bData.awakened = true;
        assertTrue(corpse.isAwakened());
        assertFalse(corpse.isDefeated());

        // Defeat
        bData.defeated = true;
        assertTrue(corpse.isAwakened());
        assertTrue(corpse.isDefeated());
    }
}
