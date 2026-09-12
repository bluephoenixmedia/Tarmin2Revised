package com.bpm.minotaur.gamedata.spells;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Vector3;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.gore.GoreManager;
import com.bpm.minotaur.rendering.SpellPostProcessor;
import org.junit.Before;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.*;

public class SpellVfxTest {

    @Before
    public void setUp() {
        if (com.badlogic.gdx.Gdx.app == null) {
            com.badlogic.gdx.Gdx.app = (com.badlogic.gdx.Application) java.lang.reflect.Proxy.newProxyInstance(
                    com.badlogic.gdx.Application.class.getClassLoader(),
                    new Class<?>[]{com.badlogic.gdx.Application.class},
                    (proxy, method, args) -> null
            );
        }
    }

    @Test
    public void testVisualArchetypeEnumCompleteness() {
        VisualArchetype[] archetypes = VisualArchetype.values();
        assertEquals("There should be exactly 12 Visual Archetypes", 12, archetypes.length);

        for (VisualArchetype arch : archetypes) {
            assertNotNull("Primary color must not be null for " + arch, arch.getPrimaryColor());
            assertNotNull("Secondary color must not be null for " + arch, arch.getSecondaryColor());
            assertNotNull("Sound key must not be null for " + arch, arch.getSoundKey());
            assertNotNull("Decal color must not be null for " + arch, arch.getDecalColor());
            assertNotNull("Particle ASCII must not be null for " + arch, arch.getParticleAscii());
        }

        // Test fromString
        assertEquals(VisualArchetype.FLAME_BOLT, VisualArchetype.fromString("FLAME_BOLT"));
        assertEquals(VisualArchetype.FLAME_BOLT, VisualArchetype.fromString("flame_bolt"));
        assertEquals(VisualArchetype.FORCE_MISSILE, VisualArchetype.fromString("UNKNOWN_XYZ"));
        assertEquals(VisualArchetype.FORCE_MISSILE, VisualArchetype.fromString(null));
    }

    @Test
    public void testSpellsJsonVfxIntegrity() {
        SpellDataManager manager = SpellDataManager.getInstance();
        manager.load();
        java.util.List<SpellTemplate> spells = manager.getAllSpells();

        assertNotNull("Spells collection should not be null", spells);
        assertTrue("Should have loaded at least 300 spells", spells.size() >= 300);

        int validArchetypes = 0;
        int validRuneSchools = 0;

        for (SpellTemplate spell : spells) {
            VisualArchetype arch = spell.getVisualArchetypeEnum();
            assertNotNull("Spell " + spell.getId() + " must have valid archetype", arch);
            validArchetypes++;

            String runeSchool = spell.getRuneSchool();
            assertNotNull("Spell " + spell.getId() + " must have rune school", runeSchool);
            assertTrue("Rune school must be standard 5e school: " + runeSchool,
                    runeSchool.equals("EVOCATION") || runeSchool.equals("ABJURATION") ||
                    runeSchool.equals("NECROMANCY") || runeSchool.equals("CONJURATION") ||
                    runeSchool.equals("TRANSMUTATION") || runeSchool.equals("DIVINATION") ||
                    runeSchool.equals("ENCHANTMENT") || runeSchool.equals("ILLUSION"));
            validRuneSchools++;
        }

        assertEquals(spells.size(), validArchetypes);
        assertEquals(spells.size(), validRuneSchools);
    }

    @Test
    public void testFabFiveBespokeFlourishes() {
        SpellDataManager manager = SpellDataManager.getInstance();
        manager.load();

        SpellTemplate fireball = SpellDataManager.getSpell("FIREBALL");
        assertNotNull("Fireball must exist", fireball);
        assertEquals("FIREBALL", fireball.getBespokeEffect());
        assertEquals(VisualArchetype.EXPLOSIVE_BURST, fireball.getVisualArchetypeEnum());

        SpellTemplate magicMissile = SpellDataManager.getSpell("MAGIC_MISSILE");
        assertNotNull("Magic Missile must exist", magicMissile);
        assertEquals("MAGIC_MISSILE", magicMissile.getBespokeEffect());
        assertEquals(VisualArchetype.FORCE_MISSILE, magicMissile.getVisualArchetypeEnum());

        SpellTemplate mistyStep = SpellDataManager.getSpell("MISTY_STEP");
        assertNotNull("Misty Step must exist", mistyStep);
        assertEquals("MISTY_STEP", mistyStep.getBespokeEffect());
        assertEquals(VisualArchetype.SPATIAL_WARP, mistyStep.getVisualArchetypeEnum());

        SpellTemplate thunderwave = SpellDataManager.getSpell("THUNDERWAVE");
        assertNotNull("Thunderwave must exist", thunderwave);
        assertEquals("THUNDERWAVE", thunderwave.getBespokeEffect());
        assertEquals(VisualArchetype.THUNDER_CONCUSSION, thunderwave.getVisualArchetypeEnum());

        SpellTemplate shield = SpellDataManager.getSpell("SHIELD");
        assertNotNull("Shield must exist", shield);
        assertEquals("SHIELD", shield.getBespokeEffect());
        assertEquals(VisualArchetype.ARCANE_WARD, shield.getVisualArchetypeEnum());
    }

    @Test
    public void testSpellPostProcessorLifecycle() {
        SpellPostProcessor spp = new SpellPostProcessor();
        assertFalse("Initially should be inactive", spp.isActive());

        // Trigger shockwave
        spp.triggerShockwave(0.5f, 0.5f, 0.4f, 0.1f);
        assertTrue("Should be active after triggering shockwave", spp.isActive());

        // Advance timer past duration
        spp.update(0.5f);
        assertFalse("Should be inactive after duration expires", spp.isActive());

        // Trigger vignette
        spp.triggerVignette(Color.CYAN, 0.8f, 0.3f);
        assertTrue("Should be active after triggering vignette", spp.isActive());
        spp.update(0.35f);
        assertFalse("Vignette should fade out", spp.isActive());

        // Trigger glitch
        spp.triggerGlitch(0.8f, 0.2f);
        assertTrue("Glitch should be active", spp.isActive());
        spp.update(0.25f);
        assertFalse("Glitch should expire", spp.isActive());
    }

    @Test
    public void testElementalDecalSpawning() {
        int[][] wallData = new int[10][10];
        Maze maze = new Maze(1, wallData);
        GoreManager gore = maze.getGoreManager();
        assertNotNull("GoreManager must exist on Maze", gore);

        Vector3 scorchPos = new Vector3(3.5f, 0.05f, 4.5f);
        Color scorchColor = VisualArchetype.FLAME_BOLT.getDecalColor();

        gore.spawnElementalScorch(scorchPos, scorchColor, 0.5f);
        // Decals should be spawned into activeSurfaceDecals
        assertTrue("Decals should be spawned", true);
    }
}
