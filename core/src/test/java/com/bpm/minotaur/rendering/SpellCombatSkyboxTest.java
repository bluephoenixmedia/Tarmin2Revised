package com.bpm.minotaur.rendering;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class SpellCombatSkyboxTest {

    @Test
    public void testSpellThemeConstants() {
        assertEquals(0, Skybox3DRenderer.SPELL_THEME_FIREBALL);
        assertEquals(1, Skybox3DRenderer.SPELL_THEME_ARCANE);
        assertEquals(2, Skybox3DRenderer.SPELL_THEME_FROST);
        assertEquals(3, Skybox3DRenderer.SPELL_THEME_NECROTIC);
    }

    @Test
    public void testSkyStateSpellCombatFields() {
        Skybox3DRenderer.SkyState state = new Skybox3DRenderer.SkyState();
        assertEquals(0, state.spellVolleyCount);

        state.spellVolleys = new float[] { 1.5f, 2.0f, -0.5f, 1.2f };
        state.spellThemes = new int[] { Skybox3DRenderer.SPELL_THEME_FIREBALL, Skybox3DRenderer.SPELL_THEME_ARCANE };
        state.spellVolleyCount = 2;

        assertNotNull(state.spellVolleys);
        assertNotNull(state.spellThemes);
        assertEquals(2, state.spellVolleyCount);
        assertEquals(1.5f, state.spellVolleys[0], 0.001f);
        assertEquals(2.0f, state.spellVolleys[1], 0.001f);
        assertEquals(Skybox3DRenderer.SPELL_THEME_FIREBALL, state.spellThemes[0]);
        assertEquals(Skybox3DRenderer.SPELL_THEME_ARCANE, state.spellThemes[1]);
    }
}
