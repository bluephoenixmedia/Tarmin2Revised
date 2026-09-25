package com.bpm.minotaur.gamedata.monster;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * {@code Monster.scale} is a public field the renderers dereference directly.
 *
 * <p>The template-driven constructor takes it from the monster template, but the
 * plain constructor used for procedurally raised monsters left it null, and
 * `EntityRenderer.drawMonsterSprite` reads `monster.scale.y` with no guard. The
 * result was a hard crash the moment any such monster was drawn in retro mode.
 */
public class MonsterScaleTest {

    @Test
    public void everyConstructorLeavesAUsableScale() {
        Monster positioned = new Monster(Monster.MonsterType.SKELETON, 28, 12, 4, 7);
        assertNotNull("Positioned constructor must set a scale", positioned.scale);
        assertTrue("Scale X must be positive", positioned.scale.x > 0f);
        assertTrue("Scale Y must be positive", positioned.scale.y > 0f);

        Monster bare = new Monster(Monster.MonsterType.GOBLIN, 20, 10);
        assertNotNull("Bare constructor must set a scale", bare.scale);
        assertTrue("Scale X must be positive", bare.scale.x > 0f);
        assertTrue("Scale Y must be positive", bare.scale.y > 0f);
    }

    @Test
    public void aRaisedGraveGuardianIsRenderable() {
        // This is the exact shape ThemeObjectiveManager spawns when a grave is
        // reconsecrated, and the one that crashed the renderer.
        Monster risen = new Monster(Monster.MonsterType.SKELETON, 28, 12, 10, 10);
        risen.setFaction(Faction.UNDEAD);

        assertNotNull(risen.scale);
        // Mirrors EntityRenderer.drawMonsterSprite's arithmetic.
        int spriteHeight = (int) (64 * risen.scale.y);
        int spriteWidth = (int) (64 * risen.scale.x);
        assertTrue(spriteHeight > 0);
        assertTrue(spriteWidth > 0);
    }
}
