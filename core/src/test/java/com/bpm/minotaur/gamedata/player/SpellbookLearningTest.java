package com.bpm.minotaur.gamedata.player;

import com.bpm.minotaur.gamedata.GameEvent;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.ItemCategory;
import com.bpm.minotaur.gamedata.item.ItemTemplate;
import com.bpm.minotaur.gamedata.save.ItemSaveData;
import com.bpm.minotaur.gamedata.spells.SpellDataManager;
import com.bpm.minotaur.gamedata.spells.SpellTemplate;
import com.bpm.minotaur.managers.CombatManager;
import com.bpm.minotaur.managers.GameEventManager;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class SpellbookLearningTest {

    private Player player;
    private GameEventManager eventManager;

    @Before
    public void setUp() {
        if (com.badlogic.gdx.Gdx.app == null) {
            com.badlogic.gdx.Gdx.app = (com.badlogic.gdx.Application) java.lang.reflect.Proxy.newProxyInstance(
                    com.badlogic.gdx.Application.class.getClassLoader(),
                    new Class<?>[]{com.badlogic.gdx.Application.class},
                    (proxy, method, args) -> null
            );
        }

        SpellDataManager.getInstance().load();
        player = new Player(0, 0);
        player.setLevel(1);
        eventManager = new GameEventManager();
    }

    private Item createBook(String spellId) {
        ItemTemplate tmpl = new ItemTemplate();
        tmpl.friendlyName = "Spellbook";
        tmpl.isUsable = true;
        tmpl.isWeapon = false;
        tmpl.spellId = spellId;
        Item book = Item.fromTemplate(Item.ItemType.BOOK, tmpl);
        book.setSpellId(spellId);
        SpellTemplate spell = SpellDataManager.getSpell(spellId);
        String name = spell != null ? "Spellbook: " + spell.getName() : "Spellbook";
        book.setName(name);
        book.setFriendlyName(name);
        return book;
    }

    @Test
    public void testSpellbookClassification() {
        Item book = createBook("MAGIC_ARROW");
        assertTrue("BOOK must be classified as a spellbook", book.isSpellbook());
        assertFalse("BOOK must not be a weapon", book.isWeapon());
        assertEquals("BOOK category must be BOOK", ItemCategory.BOOK, book.getCategory());
        assertFalse("CombatManager must not treat BOOK as a book weapon", CombatManager.isBookWeapon(book));

        ItemTemplate warTmpl = new ItemTemplate();
        warTmpl.friendlyName = "War Book";
        warTmpl.isWeapon = true;
        Item warBook = Item.fromTemplate(Item.ItemType.WAR_BOOK, warTmpl);
        assertFalse("WAR_BOOK must not be classified as a readable spellbook", warBook.isSpellbook());
        assertTrue("CombatManager must treat WAR_BOOK as a book weapon", CombatManager.isBookWeapon(warBook));

        ItemTemplate spiritTmpl = new ItemTemplate();
        spiritTmpl.friendlyName = "Spiritual Book";
        spiritTmpl.isWeapon = true;
        Item spiritBook = Item.fromTemplate(Item.ItemType.SPIRITUAL_BOOK, spiritTmpl);
        assertFalse("SPIRITUAL_BOOK must not be classified as a readable spellbook", spiritBook.isSpellbook());
        assertTrue("CombatManager must treat SPIRITUAL_BOOK as a book weapon", CombatManager.isBookWeapon(spiritBook));
    }

    @Test
    public void testLevelRequirementProgression() {
        // Level 0 (cantrip) -> 1
        SpellTemplate cantrip = new SpellTemplate();
        cantrip.level = 0;
        assertEquals(1, Player.getRequiredPlayerLevelForSpell(cantrip));

        // Level 1 -> 1
        SpellTemplate lvl1 = new SpellTemplate();
        lvl1.level = 1;
        assertEquals(1, Player.getRequiredPlayerLevelForSpell(lvl1));

        // Level 2 -> 3
        SpellTemplate lvl2 = new SpellTemplate();
        lvl2.level = 2;
        assertEquals(3, Player.getRequiredPlayerLevelForSpell(lvl2));

        // Level 3 -> 5
        SpellTemplate lvl3 = new SpellTemplate();
        lvl3.level = 3;
        assertEquals(5, Player.getRequiredPlayerLevelForSpell(lvl3));

        // Level 4 -> 7
        SpellTemplate lvl4 = new SpellTemplate();
        lvl4.level = 4;
        assertEquals(7, Player.getRequiredPlayerLevelForSpell(lvl4));

        // Level 5 -> 9
        SpellTemplate lvl5 = new SpellTemplate();
        lvl5.level = 5;
        assertEquals(9, Player.getRequiredPlayerLevelForSpell(lvl5));
    }

    @Test
    public void testUnderLeveledPlayerCannotReadAndKeepsBook() {
        player.setLevel(1);

        // ACID_ARROW is a level 2 spell -> requires player level 3
        Item acidArrowBook = createBook("ACID_ARROW");
        player.getInventory().pickupToBackpack(acidArrowBook);

        assertTrue("Player should have the book in backpack", player.getInventory().contains(acidArrowBook));
        assertFalse("Player canLearnSpellbook should be false when under-leveled", player.canLearnSpellbook(acidArrowBook));

        // Attempt to read/use the book
        player.useItem(acidArrowBook, eventManager, null, null);

        // Spell must NOT be learned
        assertFalse("Player must not have learned ACID_ARROW", player.getKnownSpellIds().contains("ACID_ARROW"));

        // Book must NOT be consumed
        assertTrue("Player must keep the book until leveling up", player.getInventory().contains(acidArrowBook));

        // Event message must state that it's too complicated and requires level 3
        List<String> messages = eventManager.getMessageHistory();
        assertFalse("Expected event message", messages.isEmpty());
        String msg = messages.get(0);
        assertTrue("Message must mention too complicated: " + msg, msg.contains("too complicated to understand"));
        assertTrue("Message must mention Requires Level 3: " + msg, msg.contains("Requires Level 3"));
    }

    @Test
    public void testEligibleLevelPlayerLearnsSpellAndConsumesBook() {
        // Player levels up to level 3 -> eligible for level 2 spells
        player.setLevel(3);
        player.setUnlockedSpellSlots(2);

        Item acidArrowBook = createBook("ACID_ARROW");
        player.getInventory().pickupToBackpack(acidArrowBook);

        assertTrue("Player canLearnSpellbook should be true at level 3", player.canLearnSpellbook(acidArrowBook));

        // Read/use the book
        player.useItem(acidArrowBook, eventManager, null, null);

        // Spell must be learned
        assertTrue("Player must have learned ACID_ARROW", player.getKnownSpellIds().contains("ACID_ARROW"));

        // Spell must be prepared in free slot (slot index 1)
        assertEquals("ACID_ARROW should be prepared in free slot 2", "ACID_ARROW", player.getPreparedSpells()[1]);

        // Book must be consumed
        assertFalse("Book must be consumed upon successful study", player.getInventory().contains(acidArrowBook));

        // Event message should announce mastery
        List<String> messages = eventManager.getMessageHistory();
        assertFalse(messages.isEmpty());
        String msg = messages.get(0);
        assertTrue("Message should announce mastery: " + msg, msg.contains("master Acid Arrow"));
    }

    @Test
    public void testAlreadyKnownSpellBookIsNotConsumed() {
        player.setLevel(3);
        player.learnSpellId("ACID_ARROW");

        Item acidArrowBook = createBook("ACID_ARROW");
        player.getInventory().pickupToBackpack(acidArrowBook);

        // Attempt to read again
        player.useItem(acidArrowBook, eventManager, null, null);

        // Book must NOT be consumed
        assertTrue("Book of already known spell must remain in inventory", player.getInventory().contains(acidArrowBook));

        // Event message
        List<String> messages = eventManager.getMessageHistory();
        assertFalse(messages.isEmpty());
        String msg = messages.get(0);
        assertTrue("Message must note already mastered: " + msg, msg.contains("already mastered"));
    }

    @Test
    public void testLevel1SpellLearnedAtLevel1() {
        player.setLevel(1);

        Item magicArrowBook = createBook("MAGIC_ARROW");
        player.getInventory().pickupToBackpack(magicArrowBook);

        assertTrue(player.canLearnSpellbook(magicArrowBook));
        player.useItem(magicArrowBook, eventManager, null, null);

        assertTrue("Level 1 player learns level 1 spell", player.getKnownSpellIds().contains("MAGIC_ARROW"));
        assertFalse("Book consumed", player.getInventory().contains(magicArrowBook));
    }

    @Test
    public void testItemSaveDataRoundTripPreservesSpellId() {
        Item book = createBook("FIREBALL");
        book.setFriendlyName("Spellbook: Fireball");

        ItemSaveData save = new ItemSaveData(book);
        assertEquals("FIREBALL", save.spellId);
        assertEquals("Spellbook: Fireball", save.friendlyName);

        Item loaded = save.toItem(null, null);
        assertEquals("FIREBALL", loaded.getSpellId());
        assertTrue("DisplayName should reflect spellbook: " + loaded.getDisplayName(),
                loaded.getDisplayName().contains("Fireball"));
    }
}
