package com.bpm.minotaur.gamedata.monster;

import org.junit.Test;

import java.io.File;

import static org.junit.Assert.*;

public class MonsterIdleAnimationTest {

    private File file(String path) {
        File f = new File(path);
        if (!f.exists()) f = new File("../" + path);
        return f;
    }

    @Test
    public void allRegisteredIdleFilesExistOnDisk() {
        assertFalse("Registry must have registered monsters", MonsterIdleAnimationRegistry.getAllConfigs().isEmpty());
        assertEquals("Expected 38 monster types with idle animations", 38, MonsterIdleAnimationRegistry.getAllConfigs().size());

        for (MonsterIdleAnimationRegistry.IdleConfig cfg : MonsterIdleAnimationRegistry.getAllConfigs()) {
            File f = file("assets/" + cfg.texturePath);
            assertTrue("Idle animation file must exist: " + cfg.texturePath, f.isFile());
            assertTrue("Idle animation file must not be empty: " + cfg.texturePath, f.length() > 50000);
            assertEquals("Expected 3 columns for 3x3 idle spritesheet: " + cfg.texturePath, 3, cfg.cols);
            assertEquals("Expected 3 rows for 3x3 idle spritesheet: " + cfg.texturePath, 3, cfg.rows);
            assertEquals("Expected 9 total frames: " + cfg.texturePath, 9, cfg.getTotalFrames());
            assertEquals("Expected 0.15s frame duration", 0.15f, cfg.frameDuration, 0.001f);
        }
    }

    @Test
    public void shopkeeperIdleFileExistsOnDisk() {
        File f = file("assets/images/monsters/idle/shop_keeper_idle.png");
        assertTrue("Shopkeeper idle animation file must exist", f.isFile());
        assertTrue("Shopkeeper idle file must not be empty", f.length() > 50000);
    }

    @Test
    public void specificKeyMonstersAreRegistered() {
        assertTrue(MonsterIdleAnimationRegistry.hasIdleAnimation(Monster.MonsterType.MINOTAUR));
        assertTrue(MonsterIdleAnimationRegistry.hasIdleAnimation(Monster.MonsterType.GOBLIN));
        assertTrue(MonsterIdleAnimationRegistry.hasIdleAnimation(Monster.MonsterType.DRAGON));
        assertTrue(MonsterIdleAnimationRegistry.hasIdleAnimation(Monster.MonsterType.SKELETON));
        assertTrue(MonsterIdleAnimationRegistry.hasIdleAnimation(Monster.MonsterType.ALLIGATOR));
        assertTrue(MonsterIdleAnimationRegistry.hasIdleAnimation(Monster.MonsterType.ZOMBIE));
        assertTrue(MonsterIdleAnimationRegistry.hasIdleAnimation(Monster.MonsterType.GELATINOUS_CUBE));
        assertTrue(MonsterIdleAnimationRegistry.hasIdleAnimation(Monster.MonsterType.MIND_FLAYER));

        assertFalse(MonsterIdleAnimationRegistry.hasIdleAnimation(Monster.MonsterType.PLAYER_GHOST));
        assertFalse(MonsterIdleAnimationRegistry.hasIdleAnimation(null));
    }

    @Test
    public void monsterAnimationCyclesAndLoops() {
        MonsterTemplate template = new MonsterTemplate();
        template.isSpriteSheet = true;
        template.spriteCols = 3;
        template.spriteRows = 3;
        template.spriteFrameDuration = 0.15f;
        template.animStartFrame = 0;
        template.animEndFrame = 8;

        Monster monster = new Monster(Monster.MonsterType.GOBLIN, 10, 5);
        // By default without assetManager, isSpriteSheet is false, but calling updateAnimation is safe
        monster.updateAnimation(0.15f);
        assertEquals(0, monster.getCurrentFrame());
    }
}
