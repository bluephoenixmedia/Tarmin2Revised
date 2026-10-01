package com.bpm.minotaur.gamedata.spells;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.bpm.minotaur.rendering.vfx.FxClips;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.io.FileReader;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

public class SpellFxTest {

    private static SpellFx fx;
    private static FxClips clips;

    private static File data(String name) {
        File f = new File("../assets/data/" + name);
        return f.exists() ? f : new File("assets/data/" + name);
    }

    @BeforeClass
    public static void load() throws Exception {
        fx = SpellFx.parse(new JsonReader().parse(new FileReader(data("spellfx.json"))));
        clips = FxClips.parse(new JsonReader().parse(new FileReader(data("fx.json"))));
    }

    @Test
    public void everyArchetypeHasAnImpact() {
        for (VisualArchetype a : VisualArchetype.values()) {
            assertNotNull(a + " has no impact effect", fx.impactFor(a));
            assertTrue(a + " impact scale", fx.impactFor(a).scale > 0f);
        }
    }

    @Test
    public void everyClipTheMappingNamesIsReallyInTheAtlas() {
        for (VisualArchetype a : VisualArchetype.values()) {
            String id = fx.impactFor(a).clip;
            assertTrue(a + " names a clip that is not in fx.json: " + id, clips.has(id));
        }
        for (String id : fx.allSelfClips()) {
            assertTrue("self clip missing from fx.json: " + id, clips.has(id));
        }
        assertTrue(clips.has(fx.castClip()));
        assertTrue(clips.has(fx.warpDepartClip()));
        assertTrue(clips.has(fx.warpArriveClip()));
    }

    @Test
    public void healingSpellsGetTheHealingGlow() {
        List<String> clipsForCure = fx.selfClipsFor("Cure Wounds", VisualArchetype.HOLY_RADIANCE);
        assertEquals("self_heal", clipsForCure.get(0));
        assertTrue(fx.selfClipsFor("Healing Word", VisualArchetype.HOLY_RADIANCE).contains("self_heal"));
    }

    @Test
    public void buffsGetTheirOwnLook() {
        assertEquals(Arrays.asList("self_haste"), fx.selfClipsFor("Haste", VisualArchetype.FORCE_MISSILE));
        assertEquals(Arrays.asList("self_attack_up"), fx.selfClipsFor("Heroism", VisualArchetype.FORCE_MISSILE));
        assertEquals(Arrays.asList("self_defense_up"), fx.selfClipsFor("Mage Armor", VisualArchetype.ARCANE_WARD));
        assertEquals(Arrays.asList("self_defense_up"), fx.selfClipsFor("Shield", VisualArchetype.ARCANE_WARD));
    }

    @Test
    public void aDivinationIsNotMistakenForAHealing() {
        // Many self spells share the holy archetype but only restore nothing: no heal glow for them.
        assertTrue(fx.selfClipsFor("Detect Magic", VisualArchetype.HOLY_RADIANCE).isEmpty());
        assertTrue(fx.selfClipsFor("Augury", VisualArchetype.HOLY_RADIANCE).isEmpty());
    }

    @Test
    public void anUnnamedWardFallsBackToItsArchetype() {
        assertEquals(Arrays.asList("self_defense_up"), fx.selfClipsFor("Mirror Image", VisualArchetype.ARCANE_WARD));
        assertEquals(Arrays.asList("warp_arrive"), fx.selfClipsFor("Misty Step", VisualArchetype.SPATIAL_WARP));
    }

    @Test
    public void thePlayerFacingSizesAreSane() {
        assertTrue(fx.selfSize() > 100f);
        assertTrue(fx.castSize() > 50f);
        assertTrue(fx.warpSize() > 100f);
    }

    @Test
    public void anEmptyMappingIsHarmless() {
        SpellFx empty = SpellFx.parse(new JsonReader().parse("{}"));
        assertNull(empty.impactFor(VisualArchetype.FLAME_BOLT));
        assertTrue(empty.selfClipsFor("Cure Wounds", VisualArchetype.HOLY_RADIANCE).isEmpty());
        assertNull(empty.castClip());
    }
}
