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
}
