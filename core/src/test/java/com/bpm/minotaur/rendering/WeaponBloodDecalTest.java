package com.bpm.minotaur.rendering;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.ItemTemplate;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * The equipped weapon should acquire real blood decals (reusing
 * com.bpm.minotaur.gamedata.gore.SurfaceDecal, the same class the world gore
 * system uses) in step with how much blood a hit dispersed, respecting the
 * same pooled-budget and lifetime/fade behavior as world decals.
 */
public class WeaponBloodDecalTest {

    private Item sword() {
        ItemTemplate t = new ItemTemplate();
        t.friendlyName = "Broad Sword";
        t.isWeapon = true;
        return Item.fromTemplate(Item.ItemType.SWORD, t);
    }

    @Test
    public void addBloodDecalsAddsExactlyTheGivenCount() {
        FirstPersonWeaponOverlay overlay = new FirstPersonWeaponOverlay(null, null);
        overlay.setEquipment(sword(), null);

        overlay.addBloodDecals(5, Color.RED, (TextureRegion) null);

        assertEquals(5, overlay.getBloodDecalCount());
    }

    @Test
    public void bloodDecalsAccumulateAcrossMultipleHits() {
        FirstPersonWeaponOverlay overlay = new FirstPersonWeaponOverlay(null, null);
        overlay.setEquipment(sword(), null);

        overlay.addBloodDecals(2, Color.RED, (TextureRegion) null);
        overlay.addBloodDecals(3, Color.RED, (TextureRegion) null);

        assertEquals("Decals from successive hits in the same fight should stack",
                5, overlay.getBloodDecalCount());
    }

    @Test
    public void bloodDecalCountIsCappedLikeWorldDecalPools() {
        FirstPersonWeaponOverlay overlay = new FirstPersonWeaponOverlay(null, null);
        overlay.setEquipment(sword(), null);

        overlay.addBloodDecals(FirstPersonWeaponOverlay.MAX_WEAPON_BLOOD_DECALS + 10, Color.RED, (TextureRegion) null);

        assertEquals(FirstPersonWeaponOverlay.MAX_WEAPON_BLOOD_DECALS, overlay.getBloodDecalCount());
    }

    @Test
    public void addBloodDecalsKeepsDecalsEvenWithoutAnAtlasTexture() {
        // World decals fall back to a plain colored quad (World3DRenderer's
        // blankTexture) rather than disappearing when no atlas texture is
        // available yet; a null texture here must still leave a real decal in
        // the list for the renderer to draw with its own blank-texture
        // fallback, not silently vanish via a `textureRegion == null` skip.
        FirstPersonWeaponOverlay overlay = new FirstPersonWeaponOverlay(null, null);
        overlay.setEquipment(sword(), null);

        overlay.addBloodDecals(3, Color.RED, (TextureRegion) null);

        assertEquals(3, overlay.getBloodDecalCount());
    }

    @Test
    public void bloodDecalsFadeAndExpireOverTimeLikeWorldDecals() {
        FirstPersonWeaponOverlay overlay = new FirstPersonWeaponOverlay(null, null);
        overlay.setEquipment(sword(), null);

        overlay.addBloodDecals(4, Color.RED, (TextureRegion) null);
        assertEquals(4, overlay.getBloodDecalCount());

        // SurfaceDecal.MAX_DECAL_LIFE is 45s; a single large update tick should
        // age every decal past its lifetime, exactly like world gore decals.
        overlay.update(46f);

        assertEquals("Decals should expire using SurfaceDecal's own lifetime, same as world decals",
                0, overlay.getBloodDecalCount());
    }

    @Test
    public void decalSizeIsReducedBy80Percent() {
        FirstPersonWeaponOverlay overlay = new FirstPersonWeaponOverlay(null, null);
        overlay.setEquipment(sword(), null);

        overlay.addBloodDecals(10, Color.RED, (TextureRegion) null);

        for (com.bpm.minotaur.gamedata.gore.SurfaceDecal decal : overlay.getWeaponBloodDecals()) {
            // Previous decal target size was clamped to >= 0.16f.
            // 80% reduction brings target size down to 0.016f - 0.032f.
            assertTrue("Decal target size should be <= 0.035f (80% smaller than previous 0.16f), but was: " + decal.targetSize,
                    decal.targetSize <= 0.035f);
            assertTrue("Decal target size should be >= 0.015f, but was: " + decal.targetSize,
                    decal.targetSize >= 0.015f);
            // Initial size should also be finely scaled
            assertTrue("Decal initial size should be smaller than target size",
                    decal.initialSize < decal.targetSize);
        }
    }

    @Test
    public void bloodDecalsHaveRealisticTranslucentOpacity() {
        FirstPersonWeaponOverlay overlay = new FirstPersonWeaponOverlay(null, null);
        overlay.setEquipment(sword(), null);

        overlay.addBloodDecals(5, new Color(1f, 0f, 0f, 1f), (TextureRegion) null);

        for (com.bpm.minotaur.gamedata.gore.SurfaceDecal decal : overlay.getWeaponBloodDecals()) {
            // Blood decals should have opacity applied (alpha between 0.65 and 0.90),
            // allowing underlying metallic blade steel to show through subtly.
            assertTrue("Decal alpha should be < 1.0f for realistic translucency, but was: " + decal.color.a,
                    decal.color.a < 1.0f);
            assertTrue("Decal alpha should be >= 0.65f, but was: " + decal.color.a,
                    decal.color.a >= 0.65f);
        }
    }

    @Test
    public void extractsSolidPixelsFromSpriteData() {
        ItemTemplate t = new ItemTemplate();
        t.friendlyName = "ASCII Cross";
        t.isWeapon = true;
        t.spriteData = new String[] {
            "....",
            ".##.",
            ".##.",
            "...."
        };
        Item cross = Item.fromTemplate(Item.ItemType.SWORD, t);

        FirstPersonWeaponOverlay overlay = new FirstPersonWeaponOverlay(null, null);
        overlay.setEquipment(cross, null);

        assertTrue("Should have extracted solid non-alpha pixels from sprite data",
                overlay.getSolidPixelCount() > 0);
        // Specifically, there are 4 '#' characters
        assertEquals(4, overlay.getSolidPixelCount());

        overlay.addBloodDecals(10, Color.RED, null);
        for (com.bpm.minotaur.gamedata.gore.SurfaceDecal decal : overlay.getWeaponBloodDecals()) {
            // Normalized X for col 1: (1.5 / 4) - 0.5 = -0.125
            // Normalized X for col 2: (2.5 / 4) - 0.5 = +0.125
            assertTrue("Decal X must be placed on non-alpha pixels (-0.125 or +0.125)",
                    Math.abs(decal.position.x) <= 0.15f);
        }
    }

    @Test
    public void equippingDifferentWeaponClearsPreviousBloodDecals() {
        FirstPersonWeaponOverlay overlay = new FirstPersonWeaponOverlay(null, null);
        overlay.setEquipment(sword(), null);
        overlay.addBloodDecals(6, Color.RED, null);
        assertEquals(6, overlay.getBloodDecalCount());

        ItemTemplate bowTemplate = new ItemTemplate();
        bowTemplate.friendlyName = "Short Bow";
        bowTemplate.isWeapon = true;
        Item bow = Item.fromTemplate(Item.ItemType.BOW, bowTemplate);

        overlay.setEquipment(bow, null);
        assertEquals("Switching weapon should clear blood decals from previous blade",
                0, overlay.getBloodDecalCount());
    }

    @Test
    public void extractsSolidPixelsFromTextureFile() {
        ItemTemplate t = new ItemTemplate();
        t.friendlyName = "Broad Sword";
        t.isWeapon = true;
        t.texturePath = "images/weapons/sword_broad.png";
        Item sword = Item.fromTemplate(Item.ItemType.SWORD, t);

        FirstPersonWeaponOverlay overlay = new FirstPersonWeaponOverlay(null, null);
        overlay.setEquipment(sword, null);

        assertTrue("Should have extracted solid pixels from images/weapons/sword_broad.png, but was: " + overlay.getSolidPixelCount(),
                overlay.getSolidPixelCount() > 50);

        overlay.addBloodDecals(15, Color.RED, null);
        assertEquals(15, overlay.getBloodDecalCount());
        for (com.bpm.minotaur.gamedata.gore.SurfaceDecal decal : overlay.getWeaponBloodDecals()) {
            assertTrue(decal.position.x >= -0.5f && decal.position.x <= 0.5f);
            assertTrue(decal.position.z >= -0.5f && decal.position.z <= 0.5f);
        }
    }
}
