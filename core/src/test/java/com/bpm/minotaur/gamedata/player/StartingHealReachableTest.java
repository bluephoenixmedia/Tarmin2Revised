package com.bpm.minotaur.gamedata.player;

import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.ItemTemplate;
import com.bpm.minotaur.gamedata.spells.SpellDataManager;
import com.bpm.minotaur.gamedata.spells.SpellTemplate;
import com.bpm.minotaur.managers.GameEventManager;
import com.bpm.minotaur.managers.HintManager;
import com.bpm.minotaur.managers.SettingsManager;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/** The starting heal is reachable: H casts it, a hint names it, and it heals enough to matter (#263). */
public class StartingHealReachableTest {

    private Player player;
    private GameEventManager events;
    private boolean showHints;

    @Before
    public void setUp() {
        if (com.badlogic.gdx.Gdx.app == null) {
            com.badlogic.gdx.Gdx.app = (com.badlogic.gdx.Application) java.lang.reflect.Proxy.newProxyInstance(
                    com.badlogic.gdx.Application.class.getClassLoader(),
                    new Class<?>[]{com.badlogic.gdx.Application.class},
                    (proxy, method, args) -> null);
        }
        SpellDataManager.getInstance().load();
        player = new Player(1, 1); // Cure Wounds prepared in slot 1
        player.getStats().setMaxMP(11);
        player.getStats().setCurrentMP(11);
        events = new GameEventManager();
        HintManager.getInstance().resetForNewGame();
        showHints = SettingsManager.getInstance().isShowHints();
        SettingsManager.getInstance().setShowHints(true);
    }

    @After
    public void tearDown() {
        SettingsManager.getInstance().setShowHints(showHints);
        HintManager.getInstance().resetForNewGame();
    }

    private boolean said(String text) {
        return events.getMessageEvents().stream().anyMatch(e -> e.message.contains(text));
    }

    private void wound(int hp) {
        player.getStats().setCurrentHP(player.getStats().getMaxHP() - hp);
    }

    // --- spells.json ---

    @Test
    public void cureWoundsHealsTwoDiceOfSixForThreeMp() {
        SpellTemplate cure = SpellDataManager.getInstance().getSpell("CURE_WOUNDS");
        assertEquals("2d6", cure.getDamageDice());
        assertEquals(3, cure.getMpCost());
    }

    // --- H ---

    @Test
    public void hCastsThePreparedHealWhenItIsWorthIt() {
        wound(10);
        int hp = player.getCurrentHP();

        assertTrue(player.quickHeal(null, events, null, null));

        assertEquals(8, player.getStats().getCurrentMP());
        assertTrue(player.getCurrentHP() > hp);
        assertTrue(said("Cure Wounds"));
    }

    @Test
    public void hCastsBeforeSpendingACarriedPoultice() {
        Item poultice = Item.fromTemplate(Item.ItemType.POULTICE, new ItemTemplate());
        player.getInventory().pickupToBackpack(poultice);
        wound(10);

        assertTrue(player.quickHeal(null, events, null, null));

        assertEquals("the spell went first", 8, player.getStats().getCurrentMP());
        assertTrue("the poultice is kept", player.getInventory().getAllItems().contains(poultice));
    }

    @Test
    public void aScratchIsNotWorthTheMp() {
        wound(2);

        assertFalse(player.quickHeal(null, events, null, null));

        assertEquals(11, player.getStats().getCurrentMP());
        assertTrue(said("You are barely scratched"));
    }

    @Test
    public void withoutTheMpHFallsBackToItems() {
        player.getStats().setCurrentMP(2);
        wound(10);

        assertFalse(player.quickHeal(null, events, null, null));

        assertTrue(said("You carry nothing to heal with"));
    }

    @Test
    public void halfAnAverageHealIsTheLine() {
        // 2d6 averages 7; with no Wisdom bonus, half of that rounds to 4.
        assertEquals(4, player.minimumWoundToCastHeal());
    }

    @Test
    public void aFormWithNoHandsCannotCastSoHFallsBackToItems() {
        player.enterForm(new com.bpm.minotaur.gamedata.polymorph.PlayerForm("Wolf", 20, 6, 1f, "1d6", false, false, 100));
        wound(10);

        assertFalse(player.quickHeal(null, events, null, null));

        assertEquals("no MP spent", 11, player.getStats().getCurrentMP());
        assertTrue(said("You carry nothing to heal with"));
    }

    // --- the hint ---

    @Test
    public void theHintNamesTheSlotTheHealIsIn() {
        player.setUnlockedSpellSlots(3);
        player.prepareSpell(0, null);
        player.prepareSpell(2, Player.STARTING_HEAL_SPELL);
        wound(player.getStats().getMaxHP() / 2 + 1);

        assertTrue(HintManager.getInstance().checkHealReady(player, events));
        assertTrue(said("(or V)"));
    }


    @Test
    public void theHintComesOnceAtHalfHealthWithTheHealReady() {
        wound(player.getStats().getMaxHP() / 2 + 1);

        assertTrue(HintManager.getInstance().checkHealReady(player, events));
        assertTrue(said("Cure Wounds is ready"));
        assertFalse("once", HintManager.getInstance().checkHealReady(player, events));
    }

    @Test
    public void noHintAboveHalfHealthOrWithoutTheMp() {
        wound(2);
        assertFalse(HintManager.getInstance().checkHealReady(player, events));

        wound(player.getStats().getMaxHP() / 2 + 1);
        player.getStats().setCurrentMP(1);
        assertFalse(HintManager.getInstance().checkHealReady(player, events));
    }
}
