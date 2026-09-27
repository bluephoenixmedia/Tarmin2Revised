package com.bpm.minotaur.ui;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** The stack that decides whether the world still hears a key, and whether it may draw a prompt. */
public class UiContextsTest {

    @Before
    public void reset() {
        UiContexts.resetToGameplay();
    }

    @Test
    public void gameplayIsTheBottomAndIsNeverPushed() {
        assertEquals("GAMEPLAY", UiContexts.top());
        assertFalse(UiContexts.isAnyOpen());
        assertFalse(UiContexts.isModalOpen());
        assertEquals(0, UiContexts.depth());
    }

    @Test
    public void aPanelTakesTheTopWithoutBecomingModal() {
        UiContexts.push("INVENTORY", UiContexts.Kind.PANEL);
        assertTrue(UiContexts.isAnyOpen());
        assertFalse(UiContexts.isModalOpen());
        assertTrue(UiContexts.isTop("INVENTORY"));
    }

    @Test
    public void aModalBlocksEverythingBelowIt() {
        UiContexts.push("ALTAR", UiContexts.Kind.PANEL);
        UiContexts.push("CONFIRM_SACRIFICE", UiContexts.Kind.MODAL);
        assertTrue(UiContexts.isModalOpen());
        assertTrue(UiContexts.isTop("CONFIRM_SACRIFICE"));

        UiContexts.pop("CONFIRM_SACRIFICE");
        assertFalse(UiContexts.isModalOpen());
        assertTrue(UiContexts.isTop("ALTAR"));
    }

    @Test
    public void pushingTwiceLeavesOneEntryToPop() {
        // A screen whose show() runs twice must not outlive its single hide().
        UiContexts.push("LEVELUP", UiContexts.Kind.MODAL);
        UiContexts.push("LEVELUP", UiContexts.Kind.MODAL);
        assertEquals(1, UiContexts.depth());
        UiContexts.pop("LEVELUP");
        assertFalse(UiContexts.isAnyOpen());
    }

    @Test
    public void poppingSomethingBuriedLeavesTheRestInOrder() {
        UiContexts.push("A", UiContexts.Kind.PANEL);
        UiContexts.push("B", UiContexts.Kind.PANEL);
        UiContexts.push("C", UiContexts.Kind.PANEL);
        UiContexts.pop("B");
        assertEquals(2, UiContexts.depth());
        assertTrue(UiContexts.isTop("C"));
    }

    @Test
    public void returningToTheWorldClearsWhateverAPanelForgot() {
        UiContexts.push("STASH", UiContexts.Kind.PANEL);
        UiContexts.push("HEARTH", UiContexts.Kind.PANEL);
        UiContexts.resetToGameplay();
        assertEquals("GAMEPLAY", UiContexts.top());
        assertFalse(UiContexts.isAnyOpen());
    }

    @Test
    public void nullsAreIgnoredRatherThanStacked() {
        UiContexts.push(null, UiContexts.Kind.PANEL);
        UiContexts.pop(null);
        assertEquals(0, UiContexts.depth());
    }
}
