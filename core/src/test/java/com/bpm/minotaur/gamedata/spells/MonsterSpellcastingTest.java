package com.bpm.minotaur.gamedata.spells;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.gamedata.DamageType;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.ItemColor;
import com.bpm.minotaur.gamedata.monster.Monster;
import com.bpm.minotaur.gamedata.monster.MonsterSpellbook;
import com.bpm.minotaur.gamedata.monster.MonsterTemplate;
import com.bpm.minotaur.gamedata.player.Player;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

public class MonsterSpellcastingTest {

    private SpellDataManager spellDataManager;

    @Before
    public void setUp() {
        spellDataManager = SpellDataManager.getInstance();
        if (!spellDataManager.isLoaded()) {
            spellDataManager.load();
        }
    }

    @Test
    public void testSpellCircleAndPreparedCalculation() {
        // Low tier: Level 3, INT 12
        MonsterSpellbook apprenticeBook = MonsterSpellbook.createForMonster(
                12, 3, Collections.singletonList("NECROMANCY"),
                Arrays.asList("CHILL_TOUCH", "RAY_OF_ENFEEBLEMENT"), spellDataManager);

        assertNotNull(apprenticeBook);
        assertEquals(2, apprenticeBook.getMaxSpellCircle());
        assertTrue(apprenticeBook.getPreparedSpells().contains("CHILL_TOUCH"));
        assertTrue(apprenticeBook.getPreparedSpells().contains("RAY_OF_ENFEEBLEMENT"));

        // High tier: Level 15, INT 25 (Lich)
        MonsterSpellbook archmageBook = MonsterSpellbook.createForMonster(
                25, 15, Arrays.asList("NECROMANCY", "EVOCATION"),
                Arrays.asList("FIREBALL", "MISTY_STEP", "CHILL_TOUCH"), spellDataManager);

        assertNotNull(archmageBook);
        assertTrue(archmageBook.getMaxSpellCircle() >= 3);
        assertTrue(archmageBook.getPreparedSpells().contains("FIREBALL"));
        assertTrue(archmageBook.getPreparedSpells().contains("MISTY_STEP"));
        assertTrue(archmageBook.getPreparedSpells().contains("CHILL_TOUCH"));
    }

    @Test
    public void testSpellResourceTrackingAndCaps() {
        MonsterSpellbook book = MonsterSpellbook.createForMonster(
                20, 10, Arrays.asList("EVOCATION"),
                Collections.singletonList("FIREBALL"), spellDataManager);

        // Fireball is circle 3, so it should have 2 uses per encounter
        assertEquals(2, book.getUsesRemaining("FIREBALL"));

        // With sufficient MP, canCast is true
        assertTrue(book.canCast("FIREBALL", 50, spellDataManager));

        // With insufficient MP, canCast is false
        assertFalse(book.canCast("FIREBALL", 2, spellDataManager));

        // Consume 2 uses
        book.recordCast("FIREBALL");
        assertEquals(1, book.getUsesRemaining("FIREBALL"));
        assertTrue(book.canCast("FIREBALL", 50, spellDataManager));

        book.recordCast("FIREBALL");
        assertEquals(0, book.getUsesRemaining("FIREBALL"));

        // Now uses are exhausted
        assertFalse(book.canCast("FIREBALL", 50, spellDataManager));
    }

    @Test
    public void testTacticalSpellSelection() {
        MonsterSpellbook book = new MonsterSpellbook(3,
                Arrays.asList("CHILL_TOUCH", "FIREBALL", "MISTY_STEP"), spellDataManager);

        // At distance 4 with plenty of MP, selects offensive ranged spell (Fireball)
        String offensive = book.selectOffensiveSpell(30, 4.0f, spellDataManager);
        assertNotNull(offensive);
        assertEquals("FIREBALL", offensive);

        // When out of MP (0 MP), falls back to cantrip (CHILL_TOUCH)
        String cantripFallback = book.selectOffensiveSpell(0, 3.0f, spellDataManager);
        assertNotNull(cantripFallback);
        assertEquals("CHILL_TOUCH", cantripFallback);

        // Defensive spell selection returns Misty Step
        String defensive = book.selectDefensiveOrHealSpell(20, spellDataManager);
        assertNotNull(defensive);
        assertEquals("MISTY_STEP", defensive);
    }

    @Test
    public void testMonsterSpellExecutionOnPlayer() {
        Monster caster = new Monster(Monster.MonsterType.LICH, 60, 16);
        caster.setSpellcaster(true);
        caster.setCurrentMP(50);
        caster.getPosition().set(5.5f, 5.5f);

        Player player = new Player(5, 2);
        int initialHP = player.getCurrentHP();

        MonsterSpellbook book = new MonsterSpellbook(3, Collections.singletonList("FIREBALL"), spellDataManager);
        caster.setSpellbook(book);

        // Cast Fireball at player
        boolean castSuccess = MonsterSpellExecutor.castMonsterSpell(caster, "FIREBALL", player, null, null, null);
        assertTrue(castSuccess);

        // Verify mana was deducted from monster
        assertTrue(caster.getCurrentMP() < 50);

        // Verify player took damage
        assertTrue(player.getCurrentHP() < initialHP);

        // Verify monster sprite flashed with archetype color
        assertNotNull(caster.getSpellFlashColor());
        assertTrue(caster.getSpellFlashProgress() < 1.0f);
    }

    @Test
    public void testMonsterHealingSpell() {
        Monster caster = new Monster(Monster.MonsterType.LICH, 60, 16);
        caster.setSpellcaster(true);
        caster.setCurrentMP(50);
        caster.setCurrentHP(20); // Injured
        assertEquals(20, caster.getCurrentHP());

        Player player = new Player(5, 2);

        // Cast Heal
        boolean castSuccess = MonsterSpellExecutor.castMonsterSpell(caster, "HEAL", player, null, null, null);
        assertTrue(castSuccess);

        // Verify caster was healed
        assertTrue(caster.getCurrentHP() > 20);
    }
}
