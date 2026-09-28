package com.bpm.minotaur.gamedata.spells;

import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Fog Cloud's data entry, which is what the audit of this spell actually found wrong.
 *
 * <p>It was generator output: 2d6 FORCE damage on a burst at range 24, wearing the poison-green
 * archetype, with a 5e duration string a turn-based game cannot use. None of that is a rendering
 * problem or an AI problem -- it is six wrong fields, and a test is the only thing that stops a
 * regeneration pass putting them back.
 */
public class FogCloudSpellTest {

    private static SpellTemplate fogCloud;

    @BeforeClass
    public static void load() {
        SpellDataManager.getInstance();
        fogCloud = SpellDataManager.getSpell("FOG_CLOUD");
    }

    @Test
    public void theSpellExists() {
        assertNotNull("FOG_CLOUD missing from spells.json", fogCloud);
        assertEquals("Fog Cloud", fogCloud.getName());
    }

    @Test
    public void itDoesNoDamage() {
        // The whole point. A utility spell that also chips for seven is a worse spell.
        String dice = fogCloud.getDamageDice();
        assertTrue("Fog Cloud must not deal damage, found: " + dice,
                dice == null || dice.trim().isEmpty() || dice.trim().equals("0"));
    }

    @Test
    public void itIsCastAtAReachableRange() {
        // 24 was a generation artefact; Fireball clamps itself to 12 for the same reason.
        assertTrue("range " + fogCloud.getRange() + " is further than any corridor",
                fogCloud.getRange() > 0 && fogCloud.getRange() <= 8);
    }

    @Test
    public void itRoutesThroughItsOwnResolver() {
        assertEquals("FOG_CLOUD", fogCloud.getBespokeEffect());
    }

    @Test
    public void itDoesNotWearThePoisonArchetype() {
        // The archetype drives the cast overlay and the post-process flash, not just particle
        // colour, so borrowing TOXIC_CLOUD flashed the screen green when casting fog.
        assertEquals("OBSCURING_MIST", fogCloud.getVisualArchetype());
        assertNotNull(VisualArchetype.valueOf(fogCloud.getVisualArchetype()));
    }

    @Test
    public void itStaysAnAffordableLevelOneEscape() {
        assertEquals(1, fogCloud.getLevel());
        assertTrue("fleeing should stay cheap", fogCloud.getMpCost() <= 4);
    }

    @Test
    public void itsDurationIsExpressedInTurns() {
        // "Up to 1 hour" means nothing on a turn grid.
        String duration = fogCloud.getDuration();
        assertNotNull(duration);
        assertTrue("duration should be in turns, found: " + duration,
                duration.toLowerCase().contains("turn"));
    }

    @Test
    public void itsDescriptionSurvivesTheGameFont() {
        // intellivision.ttf has no glyph for most punctuation beyond ASCII, and a spell
        // description is read on the spellbook page in that font.
        String text = fogCloud.getDescription();
        assertNotNull(text);
        assertFalse("description should not be the generated 5e text", text.contains("20-foot"));
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            assertTrue("unrenderable character in the description: " + c,
                    c == '\n' || (c >= ' ' && c <= '~'));
        }
    }
}
