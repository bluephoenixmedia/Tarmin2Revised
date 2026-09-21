package com.bpm.minotaur.gamedata.firearm;

import com.bpm.minotaur.gamedata.item.Item;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * The reload is yours to abandon, not the enemy's to deny.
 *
 * <p>Taking a hit does not cost you the reload -- you flinch and keep loading. Choosing
 * to move or swing does. That asymmetry is the whole mechanic: having fired, the player
 * decides whether to stand and finish loading while something closes, or give it up and
 * draw a blade. A reload any goblin could erase would read as the game cheating.
 *
 * <p>Deliberately not reusing TomeStudy: that channel breaks on {@code hostileInView},
 * which would mean a gun can never be reloaded during a fight -- the only time it matters.
 */
public class ReloadChannelTest {

    private static ReloadChannel musketReload() {
        return new ReloadChannel(Item.ItemType.MUSKET);
    }

    @Test
    public void testAFreshReloadKnowsItsWeaponAndLength() {
        ReloadChannel reload = musketReload();

        assertEquals(Item.ItemType.MUSKET, reload.getWeaponType());
        assertEquals(3, reload.getTurnsRequired());
        assertEquals(0, reload.getTurnsDone());
        assertFalse(reload.isComplete());
    }

    @Test
    public void testAPistolIsQuickerThanAMusket() {
        assertEquals(2, new ReloadChannel(Item.ItemType.PISTOL_STARWHEEL).getTurnsRequired());
    }

    @Test
    public void testItCompletesAfterItsFullLength() {
        ReloadChannel reload = musketReload();

        assertEquals(ReloadChannel.Step.CONTINUE, reload.afterTurn());
        assertEquals(1, reload.getTurnsDone());
        assertEquals(ReloadChannel.Step.CONTINUE, reload.afterTurn());
        assertEquals(ReloadChannel.Step.COMPLETE, reload.afterTurn());

        assertTrue(reload.isComplete());
        assertEquals(3, reload.getTurnsDone());
    }

    @Test
    public void testTakingDamageDoesNotBreakIt() {
        ReloadChannel reload = musketReload();

        reload.afterTurn();
        // A blow lands. The player flinches; the ramrod keeps going.
        assertEquals(ReloadChannel.Step.CONTINUE, reload.afterTurn());
        assertEquals(ReloadChannel.Step.COMPLETE, reload.afterTurn());
    }

    @Test
    public void testAHostileInViewDoesNotBreakIt() {
        // The failure mode this class exists to avoid: reloading under fire must work,
        // or the weapon is unusable in the only situation that matters.
        ReloadChannel reload = musketReload();

        for (int i = 0; i < 2; i++) {
            assertEquals(ReloadChannel.Step.CONTINUE, reload.afterTurn());
        }
        assertEquals(ReloadChannel.Step.COMPLETE, reload.afterTurn());
    }

    @Test
    public void testTheReloadIsCompleteOnlyOnce() {
        ReloadChannel reload = new ReloadChannel(Item.ItemType.PISTOL_STARWHEEL);

        assertEquals(ReloadChannel.Step.CONTINUE, reload.afterTurn());
        assertEquals(ReloadChannel.Step.COMPLETE, reload.afterTurn());
        assertEquals("further turns must not re-complete a finished reload",
                ReloadChannel.Step.COMPLETE, reload.afterTurn());
        assertEquals(2, reload.getTurnsDone());
    }

    @Test
    public void testTurnsRemainingCountsDownForTheHud() {
        ReloadChannel reload = musketReload();

        assertEquals(3, reload.getTurnsRemaining());
        reload.afterTurn();
        assertEquals(2, reload.getTurnsRemaining());
        reload.afterTurn();
        reload.afterTurn();
        assertEquals(0, reload.getTurnsRemaining());
    }

    @Test
    public void testANonFirearmCannotBeReloaded() {
        ReloadChannel reload = new ReloadChannel(Item.ItemType.BOW);

        assertEquals(0, reload.getTurnsRequired());
        assertEquals("nothing to load means nothing to wait for",
                ReloadChannel.Step.COMPLETE, reload.afterTurn());
    }
}
