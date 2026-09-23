package com.bpm.minotaur.rendering;

import com.badlogic.gdx.graphics.Color;
import com.bpm.minotaur.gamedata.gore.WoundDecal;
import com.bpm.minotaur.gamedata.monster.Monster;
import org.junit.Test;

import static org.junit.Assert.*;

public class MonsterDecalCompositorTest {

    @Test
    public void testAddWoundDelegatesToMonster() {
        MonsterDecalCompositor compositor = MonsterDecalCompositor.getInstance();
        Monster monster = new Monster(Monster.MonsterType.SKELETON, 0, 0);

        assertTrue(monster.getWoundDecals().isEmpty());

        WoundDecal decal = new WoundDecal(WoundDecal.WoundType.SLASH, 0.4f, 0.6f, 0.5f, 0.2f, 0.05f, Color.RED);
        compositor.addWound(monster, decal);

        assertEquals(1, monster.getWoundDecals().size());
        assertEquals(decal, monster.getWoundDecals().get(0));
    }

    @Test
    public void testHeadlessFallbackDoesNotThrow() {
        MonsterDecalCompositor compositor = MonsterDecalCompositor.getInstance();
        Monster monster = new Monster(Monster.MonsterType.SKELETON, 0, 0);
        WoundDecal decal = new WoundDecal(WoundDecal.WoundType.CRUSH, 0.5f, 0.5f, 0f, 0.3f, 0.08f, Color.WHITE);
        compositor.addWound(monster, decal);

        // In test environments where Gdx.gl is null, getCompositeRegion returns null or base region safely
        assertNull(compositor.getCompositeRegion(null));
        compositor.releaseMonster(monster, false);
        compositor.clearAll();
    }
}
