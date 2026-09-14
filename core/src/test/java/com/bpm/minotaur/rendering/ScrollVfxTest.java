package com.bpm.minotaur.rendering;

import com.badlogic.gdx.math.Vector3;
import com.bpm.minotaur.gamedata.spells.SpellExecutionEngine;
import com.bpm.minotaur.gamedata.spells.VisualArchetype;
import com.bpm.minotaur.rendering.Animation.AnimationType;
import com.bpm.minotaur.rendering.vfx.SpellExplosionRegistry;
import com.bpm.minotaur.rendering.vfx.SpellExplosionRegistry.ExplosionType;
import org.junit.Test;

import java.io.File;

import static org.junit.Assert.*;

public class ScrollVfxTest {

    @Test
    public void testAllExplosionTypesMetadata() {
        ExplosionType[] types = ExplosionType.values();
        assertEquals("There must be exactly 11 BearFX explosion types", 11, types.length);

        for (ExplosionType type : types) {
            assertNotNull("Asset path cannot be null for " + type, type.getAssetPath());
            assertTrue("Asset path must point to images/vfx/explosions", type.getAssetPath().startsWith("images/vfx/explosions/"));
            assertTrue("Asset path must end with .png", type.getAssetPath().endsWith(".png"));
            assertTrue("Cols must be positive", type.getCols() > 0);
            assertTrue("Rows must be positive", type.getRows() > 0);
            assertTrue("Frame count must be positive", type.getFrameCount() > 0);
            assertTrue("Frame count cannot exceed cols * rows", type.getFrameCount() <= type.getCols() * type.getRows());
            assertTrue("Default duration must be positive", type.getDefaultDuration() > 0.1f);
        }
    }

    @Test
    public void testAllExplosionFilesExistOnDisk() {
        for (ExplosionType type : ExplosionType.values()) {
            File directFile = new File("assets/" + type.getAssetPath());
            File parentDirFile = new File("../assets/" + type.getAssetPath());
            boolean exists = directFile.exists() || parentDirFile.exists();
            assertTrue("Asset file for " + type + " must exist on disk: " + type.getAssetPath(), exists);
        }
    }

    @Test
    public void testArchetypeToExplosionMapping() {
        assertEquals(ExplosionType.FIRE, SpellExecutionEngine.getExplosionForArchetype(VisualArchetype.EXPLOSIVE_BURST));
        assertEquals(ExplosionType.FIRE, SpellExecutionEngine.getExplosionForArchetype(VisualArchetype.FLAME_BOLT));
        assertEquals(ExplosionType.ELECTRIC, SpellExecutionEngine.getExplosionForArchetype(VisualArchetype.LIGHTNING_ARC));
        assertEquals(ExplosionType.ICE, SpellExecutionEngine.getExplosionForArchetype(VisualArchetype.FROST_RAY));
        assertEquals(ExplosionType.TOXIC, SpellExecutionEngine.getExplosionForArchetype(VisualArchetype.TOXIC_CLOUD));
        assertEquals(ExplosionType.TOXIC, SpellExecutionEngine.getExplosionForArchetype(VisualArchetype.NECROTIC_DRAIN));
        assertEquals(ExplosionType.VOID, SpellExecutionEngine.getExplosionForArchetype(VisualArchetype.SPATIAL_WARP));
        assertEquals(ExplosionType.VOID, SpellExecutionEngine.getExplosionForArchetype(VisualArchetype.PSYCHIC_SHOCK));
        assertEquals(ExplosionType.CONCUSSIVE, SpellExecutionEngine.getExplosionForArchetype(VisualArchetype.FORCE_MISSILE));
        assertEquals(ExplosionType.CONCUSSIVE, SpellExecutionEngine.getExplosionForArchetype(VisualArchetype.THUNDER_CONCUSSION));
        assertEquals(ExplosionType.HOLY_CROSS, SpellExecutionEngine.getExplosionForArchetype(VisualArchetype.HOLY_RADIANCE));
        assertEquals(ExplosionType.STANDARD, SpellExecutionEngine.getExplosionForArchetype(VisualArchetype.ARCANE_WARD));
        assertEquals(ExplosionType.STANDARD, SpellExecutionEngine.getExplosionForArchetype(null));
    }

    @Test
    public void testSpriteExplosion3DAnimationCreation() {
        Vector3 pos = new Vector3(5.5f, 0.5f, 10.5f);
        Animation anim = new Animation(ExplosionType.FIRE, pos, 1.8f, 0.65f);

        assertEquals(AnimationType.SPRITE_EXPLOSION_3D, anim.getType());
        assertEquals(ExplosionType.FIRE, anim.getExplosionType());
        assertEquals(pos, anim.getPosition3D());
        assertEquals(1.8f, anim.getScale3D(), 0.001f);
        assertTrue(anim.isAdditiveBlend());
        assertEquals(0.65f, anim.getDuration(), 0.001f);
    }

    @Test
    public void testAnimationManagerSpawnExplosion() {
        AnimationManager animMgr = new AnimationManager();
        Vector3 pos = new Vector3(2.5f, 0.5f, 4.5f);
        animMgr.spawnExplosion(ExplosionType.ELECTRIC, pos, 1.6f, 0.55f);

        assertEquals(1, animMgr.getAnimations().size());
        Animation anim = animMgr.getAnimations().get(0);
        assertEquals(AnimationType.SPRITE_EXPLOSION_3D, anim.getType());
        assertEquals(ExplosionType.ELECTRIC, anim.getExplosionType());
        assertEquals(pos, anim.getPosition3D());
        assertEquals(1.6f, anim.getScale3D(), 0.001f);
    }
}
