package com.bpm.minotaur.managers;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Vector3;
import com.bpm.minotaur.gamedata.gore.Gib;
import com.bpm.minotaur.gamedata.gore.GoreManager;
import com.bpm.minotaur.gamedata.gore.GoreProfile;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.ItemTemplate;
import com.bpm.minotaur.gamedata.monster.Monster;
import com.bpm.minotaur.rendering.animation.AnimationArchetype;
import com.bpm.minotaur.rendering.mesh.BillboardSlicer;
import org.junit.Test;

import static org.junit.Assert.*;

public class VisceralCombatLethalDispatchTest {

    @Test
    public void testWeaponArchetypeClassification() {
        // Slashing weapons
        Item sword = createMockWeapon(Item.ItemType.SWORD, "Broadsword", "SLASHING_1H");
        assertEquals(AnimationArchetype.SLASHING_1H, AnimationArchetype.fromItem(sword));

        Item twoHanded = createMockWeapon(Item.ItemType.TWO_HANDED_SWORD, "Claymore", "SLASHING_2H");
        assertEquals(AnimationArchetype.SLASHING_2H, AnimationArchetype.fromItem(twoHanded));

        Item axe = createMockWeapon(Item.ItemType.AXE, "Battle Axe", "AXE_CHOPPING");
        assertEquals(AnimationArchetype.AXE_CHOPPING, AnimationArchetype.fromItem(axe));

        // Blunt / Crushing weapons
        Item mace = createMockWeapon(Item.ItemType.GAUNTLETS, "Heavy Mace", "BLUNT_CRUSHING");
        assertEquals(AnimationArchetype.BLUNT_CRUSHING, AnimationArchetype.fromItem(mace));

        // Bare hands
        assertEquals(AnimationArchetype.BRAWLING, AnimationArchetype.fromItem(null));

        // Ranged / Piercing
        Item bow = createMockWeapon(Item.ItemType.BOW, "Longbow", "RANGED_BOW");
        assertEquals(AnimationArchetype.RANGED_BOW, AnimationArchetype.fromItem(bow));
    }

    @Test
    public void testGoreProfilePhysiologySeamAndWoundColors() {
        assertNotNull(GoreProfile.FLESH.seamColor);
        assertNotNull(GoreProfile.FLESH.woundColor);
        assertTrue(GoreProfile.FLESH.hasBlood);
        assertTrue(GoreProfile.FLESH.hasGibs);

        assertNotNull(GoreProfile.SKELETAL.seamColor);
        assertNotNull(GoreProfile.SKELETAL.woundColor);
        assertFalse(GoreProfile.SKELETAL.hasBlood);
        assertTrue(GoreProfile.SKELETAL.hasGibs);

        assertNotNull(GoreProfile.SLIME.seamColor);
        assertNotNull(GoreProfile.SLIME.woundColor);
        assertTrue(GoreProfile.SLIME.hasBlood);
        assertFalse(GoreProfile.SLIME.hasGibs);

        assertNotNull(GoreProfile.INCORPOREAL.seamColor);
        assertNotNull(GoreProfile.INCORPOREAL.woundColor);
        assertFalse(GoreProfile.INCORPOREAL.hasBlood);
        assertFalse(GoreProfile.INCORPOREAL.hasGibs);
    }

    @Test
    public void testSeveredLimbGibSpawning() {
        GoreManager gore = new GoreManager();
        float[] polyVerts = new float[] { -0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 1f, -0.5f, 1f };
        float[] polyUVs = new float[] { 0f, 0.5f, 1f, 0.5f, 1f, 1f, 0f, 1f };
        float[] seamVerts = new float[] { -0.5f, 0.5f, 0.5f, 0.5f };

        Gib gib = gore.spawnSeveredLimbGib(
                new Vector3(10f, 0.5f, 10f),
                new Vector3(0f, 5f, 1f),
                null,
                polyVerts, polyUVs, seamVerts,
                GoreProfile.FLESH
        );

        assertNotNull(gib);
        assertArrayEquals(polyVerts, gib.polygonVertices, 0.001f);
        assertArrayEquals(polyUVs, gib.polygonUVs, 0.001f);
        assertArrayEquals(seamVerts, gib.seamVertices, 0.001f);
        assertTrue(gib.lifeTimer > 0f);
        assertFalse(gib.onGround);
    }

    @Test
    public void testArterialFountainSpawning() {
        GoreManager gore = new GoreManager();
        assertEquals(0, gore.getActiveParticles().size);

        gore.spawnArterialFountain(new Vector3(5f, 0.5f, 5f), Vector3.Y, 2.0f, GoreProfile.FLESH);
        assertTrue("Arterial fountain should emit high-velocity blood particles", gore.getActiveParticles().size > 0);
    }

    @Test
    public void testCrushShatterSpawning() {
        GoreManager gore = new GoreManager();
        gore.spawnCrushShatter(new Vector3(5f, 0.5f, 5f), GoreProfile.FLESH);
        assertTrue("Crush shatter should emit burst particles and gibs",
                gore.getActiveParticles().size > 0 || gore.getActiveGibs().size > 0);
    }

    @Test
    public void testOverkillFreeformBisectionIntegration() {
        BillboardSlicer.SlicedResult slice = BillboardSlicer.sliceFromSwing(
                0.82f, 1.2f, null,
                0.85f, 0.15f, 0.15f, 0.85f
        );

        assertTrue("Overkill swing should slice monster quad", slice.isSliced);
        assertNotNull(slice.severedVertices);
        assertNotNull(slice.severedUVs);
        assertEquals(4, slice.seamVertices.length);
        assertTrue(slice.severedVertices.length >= 6);
    }

    private Item createMockWeapon(Item.ItemType type, String name, String archetype) {
        ItemTemplate template = new ItemTemplate();
        template.isWeapon = true;
        template.friendlyName = name;
        template.animationArchetype = archetype;
        return Item.fromTemplate(type, template);
    }
}
