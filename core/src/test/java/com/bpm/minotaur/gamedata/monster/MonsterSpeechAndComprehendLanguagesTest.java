package com.bpm.minotaur.gamedata.monster;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.bpm.minotaur.gamedata.effects.StatusEffectType;
import com.bpm.minotaur.gamedata.monster.MonsterColor;
import org.junit.Test;

import java.io.File;

import static org.junit.Assert.*;

/**
 * Validates monster native language mappings, untranslated barks,
 * and Comprehend Languages translations.
 */
public class MonsterSpeechAndComprehendLanguagesTest {

    private static File asset(String relativePath) {
        File f = new File("assets/" + relativePath);
        if (!f.exists()) {
            f = new File("../assets/" + relativePath);
        }
        return f;
    }

    @Test
    public void testMonsterLanguageMappings() {
        assertEquals(MonsterSpeechManager.Language.GOBLIN, MonsterSpeechManager.getLanguage(Monster.MonsterType.GOBLIN));
        assertEquals(MonsterSpeechManager.Language.ORCISH, MonsterSpeechManager.getLanguage(Monster.MonsterType.ORC));
        assertEquals(MonsterSpeechManager.Language.DRACONIC, MonsterSpeechManager.getLanguage(Monster.MonsterType.DRAGON));
        assertEquals(MonsterSpeechManager.Language.DEEP_SPEECH, MonsterSpeechManager.getLanguage(Monster.MonsterType.MIND_FLAYER));
        assertEquals(MonsterSpeechManager.Language.NECRIL, MonsterSpeechManager.getLanguage(Monster.MonsterType.SKELETON));
        assertEquals(MonsterSpeechManager.Language.ABYSSAL, MonsterSpeechManager.getLanguage(Monster.MonsterType.MINOTAUR));
        assertEquals(MonsterSpeechManager.Language.GIANT, MonsterSpeechManager.getLanguage(Monster.MonsterType.OGRE));
        assertEquals(MonsterSpeechManager.Language.DWARVISH, MonsterSpeechManager.getLanguage(Monster.MonsterType.DWARF));
        assertEquals(MonsterSpeechManager.Language.BEAST_SPEECH, MonsterSpeechManager.getLanguage(Monster.MonsterType.SPIDER));
    }

    @Test
    public void testSpeechPhraseFormattingWithoutComprehendLanguages() {
        Monster goblin = new Monster(Monster.MonsterType.GOBLIN, 20, 10);
        String untranslated = MonsterSpeechManager.getSpeechPhrase(goblin, false);

        assertNotNull("Untranslated phrase must not be null", untranslated);
        assertTrue("Phrase must start with [Goblin]", untranslated.startsWith("[Goblin]"));
        assertTrue("Phrase must be enclosed in quotes", untranslated.contains("\""));
    }

    @Test
    public void testSpeechPhraseFormattingWithComprehendLanguages() {
        Monster goblin = new Monster(Monster.MonsterType.GOBLIN, 20, 10);
        String translated = MonsterSpeechManager.getSpeechPhrase(goblin, true);

        assertNotNull("Translated phrase must not be null", translated);
        assertTrue("Translated phrase must start with [Goblin]", translated.startsWith("[Goblin]"));
        assertTrue("Translated phrase must be enclosed in quotes", translated.contains("\""));
        // Translated phrases have understandable words like 'flesh', 'shiv', 'boots', or 'meat'
        assertTrue("Translated phrase must contain readable English words",
                translated.toLowerCase().contains("flesh")
                        || translated.toLowerCase().contains("shiv")
                        || translated.toLowerCase().contains("boots")
                        || translated.toLowerCase().contains("meat"));
    }

    @Test
    public void testMonsterSpeechLifecycle() {
        Monster orc = new Monster(Monster.MonsterType.ORC, 30, 12);
        assertFalse(orc.hasSpeech());
        assertNull(orc.getCurrentSpeech());

        orc.speak("[Orcish] \"Crush his skull!\"", 3.0f);
        assertTrue(orc.hasSpeech());
        assertEquals("[Orcish] \"Crush his skull!\"", orc.getCurrentSpeech());

        // Update partially
        orc.updateSpeech(1.5f);
        assertTrue(orc.hasSpeech());

        // Update past expiration
        orc.updateSpeech(2.0f);
        assertFalse(orc.hasSpeech());
        assertNull(orc.getCurrentSpeech());
    }

    @Test
    public void testComprehendLanguagesSpellData() {
        File spellsFile = asset("data/spells.json");
        assertTrue("spells.json must exist", spellsFile.exists());

        JsonValue root = new JsonReader().parse(new com.badlogic.gdx.files.FileHandle(spellsFile));
        JsonValue spell = root.get("COMPREHEND_LANGUAGES");
        assertNotNull("COMPREHEND_LANGUAGES must exist in spells.json", spell);

        assertEquals("COMPREHEND_LANGUAGES", spell.getString("id"));
        assertEquals("SELF", spell.getString("targetType"));
        assertEquals("COMPREHEND_LANGUAGES", spell.getString("statusEffect"));
        assertEquals("COMPREHEND_LANGUAGES", spell.getString("bespokeEffect"));

        // Verify StatusEffectType enum value exists
        StatusEffectType effect = StatusEffectType.valueOf("COMPREHEND_LANGUAGES");
        assertNotNull(effect);
    }
}
