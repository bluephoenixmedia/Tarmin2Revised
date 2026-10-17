package com.bpm.minotaur.gamedata.player;

import com.bpm.minotaur.gamedata.effects.StatusEffectType;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.ItemTemplate;
import com.bpm.minotaur.gamedata.spells.SpellDataManager;
import com.bpm.minotaur.gamedata.spells.SpellTemplate;
import com.bpm.minotaur.managers.GameEventManager;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Random;

import static org.junit.Assert.*;

/**
 * Studying a book above your level is a warned gamble, not a refusal: the first try warns and
 * keeps the book, the second takes the risk. ACID_ARROW is a level 2 spell, so it needs level 3.
 */
public class SpellbookStudyRiskTest {

    private Player player;
    private GameEventManager events;

    /** A Random whose rolls are fixed, so a test decides what the gamble does. */
    private static Random rolls(final float nextFloat, final boolean nextBoolean, final int nextInt) {
        return new Random() {
            @Override
            public float nextFloat() {
                return nextFloat;
            }

            @Override
            public boolean nextBoolean() {
                return nextBoolean;
            }

            @Override
            public int nextInt(int bound) {
                return Math.min(nextInt, bound - 1);
            }
        };
    }

    @Before
    public void setUp() {
        if (com.badlogic.gdx.Gdx.app == null) {
            com.badlogic.gdx.Gdx.app = (com.badlogic.gdx.Application) java.lang.reflect.Proxy.newProxyInstance(
                    com.badlogic.gdx.Application.class.getClassLoader(),
                    new Class<?>[] { com.badlogic.gdx.Application.class },
                    (proxy, method, args) -> null);
        }
        SpellDataManager.getInstance().load();
        player = new Player(0, 0);
        player.setLevel(1);
        player.getStats().setIntelligence(18);
        events = new GameEventManager();
    }

    private Item book(String spellId) {
        ItemTemplate tmpl = new ItemTemplate();
        tmpl.friendlyName = "Spellbook";
        tmpl.isUsable = true;
        tmpl.spellId = spellId;
        Item book = Item.fromTemplate(Item.ItemType.BOOK, tmpl);
        book.setSpellId(spellId);
        SpellTemplate spell = SpellDataManager.getSpell(spellId);
        book.setName("Spellbook: " + spell.getName());
        book.setFriendlyName("Spellbook: " + spell.getName());
        player.getInventory().pickupToBackpack(book);
        return book;
    }

    private String lastMessage() {
        List<String> messages = events.getMessageHistory();
        assertFalse(messages.isEmpty());
        return messages.get(0);
    }

    @Test
    public void theFirstAttemptWarnsOfTheRiskAndTakesNoneOfIt() {
        Item acid = book("ACID_ARROW");
        int hp = player.getStats().getCurrentHP();

        assertFalse(player.readSpellbook(acid, events, rolls(0f, true, 1)));

        assertTrue("the book is kept", player.getInventory().contains(acid));
        assertFalse(player.getKnownSpellIds().contains("ACID_ARROW"));
        assertEquals("nothing is risked yet", hp, player.getStats().getCurrentHP());
        assertTrue(lastMessage(), lastMessage().contains("too complicated"));
        assertTrue(lastMessage(), lastMessage().contains("% risk"));
    }

    @Test
    public void aSecondAttemptThatBeatsTheOddsLearnsTheSpell() {
        Item acid = book("ACID_ARROW");
        player.setUnlockedSpellSlots(2);
        player.readSpellbook(acid, events, rolls(0.999f, true, 1));

        assertTrue(player.readSpellbook(acid, events, rolls(0.999f, true, 1)));

        assertTrue(player.getKnownSpellIds().contains("ACID_ARROW"));
        assertFalse("the book is spent on success", player.getInventory().contains(acid));
        assertTrue(lastMessage(), lastMessage().contains("Against the odds"));
    }

    @Test
    public void aFailedAttemptStingsPutsThePlayerToSleepAndMayKeepTheBook() {
        Item acid = book("ACID_ARROW");
        int hp = player.getStats().getCurrentHP();
        player.readSpellbook(acid, events, rolls(0f, true, 1));

        // nextFloat 0 always fails; nextBoolean true is a sting at this level; nextInt 1 keeps the book.
        assertFalse(player.readSpellbook(acid, events, rolls(0f, true, 1)));

        assertEquals("a sting costs 2 HP", hp - 2, player.getStats().getCurrentHP());
        assertTrue("a failed reading is a long one",
                player.getStatusManager().hasEffect(StatusEffectType.SLEEP));
        assertTrue("this time the book survives", player.getInventory().contains(acid));
        assertFalse(player.getKnownSpellIds().contains("ACID_ARROW"));
    }

    @Test
    public void aMishapCanDestroyTheBook() {
        Item acid = book("ACID_ARROW");
        player.readSpellbook(acid, events, rolls(0f, true, 0));

        player.readSpellbook(acid, events, rolls(0f, true, 0));

        assertFalse(player.getInventory().contains(acid));
        assertTrue(lastMessage(), lastMessage().contains("crumbles to dust"));
    }

    @Test
    public void aBookAtTheReadersLevelIsNeverRisky() {
        player.setLevel(3);
        player.setUnlockedSpellSlots(2);
        Item acid = book("ACID_ARROW");
        int hp = player.getStats().getCurrentHP();

        // A roll that would fail any gamble: a safe book ignores it.
        assertTrue(player.readSpellbook(acid, events, rolls(0f, true, 0)));

        assertTrue(player.getKnownSpellIds().contains("ACID_ARROW"));
        assertEquals(hp, player.getStats().getCurrentHP());
        assertFalse(player.getStatusManager().hasEffect(StatusEffectType.SLEEP));
    }

    @Test
    public void theWarningIsPerBook() {
        Item acid = book("ACID_ARROW");
        Item other = book("FIREBALL");
        player.readSpellbook(acid, events, rolls(0f, true, 1));

        // Warned about the acid book; a different book gets its own warning, not a gamble.
        assertFalse(player.readSpellbook(other, events, rolls(0f, true, 1)));

        assertTrue(lastMessage(), lastMessage().contains("% risk"));
        assertTrue(player.getInventory().contains(other));
    }
}
