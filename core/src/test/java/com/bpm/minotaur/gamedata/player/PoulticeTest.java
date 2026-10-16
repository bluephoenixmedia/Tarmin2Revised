package com.bpm.minotaur.gamedata.player;

import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.ItemColor;
import com.bpm.minotaur.gamedata.item.PotionEffectType;
import com.bpm.minotaur.managers.GameEventManager;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/** The Poultice and the quick heal (H) that reaches for it first (#190). */
public class PoulticeTest {

    private Player player;
    private GameEventManager events;

    @Before
    public void setUp() {
        player = new Player(0, 0);
        events = new GameEventManager();
    }

    @Test
    public void healsThirtyPercentOfMaxOverFiveTurns() {
        Poultice p = Poultice.apply(20, 0);
        int total = 0;
        for (int turn = 0; turn < Poultice.TURNS; turn++) {
            assertFalse(p.isDone());
            int heal = p.afterTurn(0);
            assertTrue(heal >= 1);
            total += heal;
        }
        assertEquals(6, total);
        assertTrue(p.isDone());
        assertEquals(0, p.afterTurn(0));
    }

    @Test
    public void aWoundCancelsTheRest() {
        Poultice p = Poultice.apply(20, 3);
        assertTrue(p.afterTurn(3) > 0);
        assertEquals(-1, p.afterTurn(4));
        assertTrue(p.isDone());
    }

    @Test
    public void playerPoulticeHealsEachTurnUntilHurt() {
        int max = player.getStats().getMaxHP();
        player.takeTrueDamage(max - 1);
        player.getInventory().addItem(poultice());

        assertTrue(player.quickHeal(null, events));
        assertFalse("the poultice was used up", player.getInventory().hasItemOfType(Item.ItemType.POULTICE));
        assertNotNull(player.getActivePoultice());

        player.tickPoultice(events);
        int afterOne = player.getCurrentHP();
        assertTrue(afterOne > 1);

        player.takeTrueDamage(1);
        player.tickPoultice(events);
        assertNull("damage tore the poultice away", player.getActivePoultice());
        assertEquals(afterOne - 1, player.getCurrentHP());
    }

    @Test
    public void quickHealRefusesAtFullHpAndWithNothingCarried() {
        assertFalse(player.quickHeal(null, events));
        player.takeTrueDamage(3);
        assertFalse(player.quickHeal(null, events));
    }

    @Test
    public void quickHealPrefersAPoulticeToAPotion() {
        Item potion = potion(PotionEffectType.HEALING);
        Item poultice = poultice();
        assertSame(poultice, QuickHeal.choose(List.of(potion, poultice), 5, 20, e -> true));
    }

    @Test
    public void quickHealDrinksTheSmallestKnownPotionThatCoversTheWound() {
        Item small = potion(PotionEffectType.HEALING);
        Item greater = potion(PotionEffectType.GREATER_HEALING);
        Item supreme = potion(PotionEffectType.SUPREME_HEALING);
        List<Item> carried = List.of(supreme, greater, small);

        assertSame(small, QuickHeal.choose(carried, 5, 30, e -> true));
        assertSame(greater, QuickHeal.choose(carried, 18, 30, e -> true));
        assertSame("nothing covers it: the strongest", supreme, QuickHeal.choose(carried, 290, 300, e -> true));
    }

    @Test
    public void quickHealNeverDrinksAnUnknownPotion() {
        Item unknown = potion(PotionEffectType.HEALING);
        unknown.setIdentified(false);
        assertNull(QuickHeal.choose(List.of(unknown), 5, 20, e -> false));
        assertSame(unknown, QuickHeal.choose(List.of(unknown), 5, 20, e -> e == PotionEffectType.HEALING));
    }

    private static Item poultice() {
        return new Item(Item.ItemType.POULTICE, 0, 0, ItemColor.TAN, null, null);
    }

    private static Item potion(PotionEffectType effect) {
        Item item = new Item(Item.ItemType.POTION_OF_HEALING, 0, 0, ItemColor.TAN, null, null);
        item.setTrueEffect(effect);
        item.setIdentified(true);
        return item;
    }
}
