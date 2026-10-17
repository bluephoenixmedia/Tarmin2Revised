package com.bpm.minotaur.ui;

import com.bpm.minotaur.gamedata.DamageType;
import com.bpm.minotaur.gamedata.monster.Monster;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** #172: the War vs Spiritual line reads the same on every damage path. */
public class AffinityTextTest {

    @Test
    public void physicalIsWarAndSpiritualIsSpiritual() {
        assertEquals("War", AffinityText.schoolOf(DamageType.PHYSICAL));
        assertEquals("Spiritual", AffinityText.schoolOf(DamageType.SPIRITUAL));
    }

    @Test
    public void aResistedHitSaysSo() {
        assertEquals("Goblin resists War attacks!",
                AffinityText.hitLine("Goblin", Monster.Affinity.RESISTANT, DamageType.PHYSICAL, false));
    }

    @Test
    public void aWeakHitSaysSoEvenOnACrit() {
        assertEquals("Goblin is weak to Spiritual!",
                AffinityText.hitLine("Goblin", Monster.Affinity.WEAK, DamageType.SPIRITUAL, false));
        assertEquals("Goblin is weak to Spiritual!",
                AffinityText.hitLine("Goblin", Monster.Affinity.WEAK, DamageType.SPIRITUAL, true));
    }

    @Test
    public void aCritOnAResistantMonsterSaysItPierced() {
        String line = AffinityText.hitLine("Ghoul", Monster.Affinity.RESISTANT, DamageType.SPIRITUAL, true);
        assertTrue(line, line.contains("pierces"));
        assertTrue(line, line.contains("Spiritual"));
    }

    @Test
    public void aNeutralHitHasNoLine() {
        assertNull(AffinityText.hitLine("Wraith", Monster.Affinity.NEUTRAL, DamageType.PHYSICAL, false));
    }

    @Test
    public void theMonsterOverloadUsesTheMonstersOwnAffinity() {
        // Goblins are Bad: resistant to War, weak to Spiritual.
        assertEquals(Monster.Affinity.RESISTANT, Monster.getAffinity(Monster.MonsterType.GOBLIN, DamageType.PHYSICAL));
        assertEquals("Goblin resists War attacks!", AffinityText.hitLine(
                "Goblin", Monster.getAffinity(Monster.MonsterType.GOBLIN, DamageType.PHYSICAL), DamageType.PHYSICAL, false));
    }

    @Test
    public void badgesUseOneWording() {
        assertEquals("Resists War / Weak to Spiritual", AffinityText.badge(Monster.Category.BAD));
        assertEquals("Resists Spiritual / Weak to War", AffinityText.badge(Monster.Category.NASTY));
        assertEquals("No resistance", AffinityText.badge(Monster.Category.HORRIBLE));
    }

    @Test
    public void everyLineSurvivesTheFont() {
        for (Monster.Affinity a : Monster.Affinity.values()) {
            for (DamageType t : DamageType.values()) {
                for (boolean crit : new boolean[] { false, true }) {
                    String line = AffinityText.hitLine("Giant Ant", a, t, crit);
                    if (line != null) assertEquals(line, UiGlyphs.sanitize(line));
                }
            }
        }
        for (Monster.Category c : Monster.Category.values()) {
            String b = AffinityText.badge(c);
            assertEquals(b, UiGlyphs.sanitize(b));
        }
    }
}
