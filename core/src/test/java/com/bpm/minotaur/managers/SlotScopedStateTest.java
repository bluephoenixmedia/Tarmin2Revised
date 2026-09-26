package com.bpm.minotaur.managers;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Progression must follow the active slot.
 *
 * <p>Several singletons wrote to correct per-slot paths but loaded exactly once
 * in a lazy constructor and were never re-read on a slot change. The result was
 * not merely showing the wrong character's data: the next save wrote that data
 * over the other slot's file. DoomManager was the worst, loading once at boot,
 * so one character's death count drove another's bridge integrity.
 *
 * <p>These cover the contract itself. The singletons' own file I/O needs a
 * LibGDX Files backend the test classpath does not carry.
 */
public class SlotScopedStateTest {

    /** Records what it was told, standing in for a progression singleton. */
    private static final class Spy implements SlotScopedState {
        final List<String> calls = new ArrayList<>();

        @Override
        public void reloadForActiveSlot() {
            calls.add("reload");
        }

        @Override
        public void resetForNewGame() {
            calls.add("reset");
        }
    }

    @Test
    public void theContractDistinguishesReloadingFromResetting() {
        // These are genuinely different events and were previously conflated:
        // startNewGame reset two singletons by hand and a slot switch reset
        // none. A reload re-reads the new slot; a reset wipes to a new
        // character. Answering a slot switch with a reset would silently erase
        // the slot being switched to.
        Spy spy = new Spy();

        spy.reloadForActiveSlot();
        assertEquals(List.of("reload"), spy.calls);

        spy.resetForNewGame();
        assertEquals(List.of("reload", "reset"), spy.calls);
    }

    @Test
    public void everyProgressionSingletonImplementsTheContract() {
        // The bug was one of omission: startNewGame named two singletons by
        // hand and quietly missed the altar, banked divinities and torment
        // pacts. Naming the types here means a new progression singleton that
        // forgets to opt in is a failing test rather than a cross-slot leak
        // discovered in play.
        Class<?>[] mustBeSlotScoped = {
                UnlockManager.class,
                DoomManager.class,
                DivinityManager.class,
                TormentManager.class,
                com.bpm.minotaur.gamedata.progression.ShelterAltar.class,
        };

        List<String> missing = new ArrayList<>();
        for (Class<?> type : mustBeSlotScoped) {
            if (!SlotScopedState.class.isAssignableFrom(type)) {
                missing.add(type.getSimpleName());
            }
        }
        assertEquals("These hold per-character progression but do not follow the active slot: "
                + missing, 0, missing.size());
    }

    @Test
    public void unlocksAreNoLongerStoredInOneGlobalFile() {
        // saves/unlocks.json was shared by all three slots and had no
        // production reset path at all, so unlocked content, deepest level and
        // lifetime kill counts were common to every character ever played.
        assertEquals("The per-slot file name must not carry a saves/ prefix; it is"
                        + " resolved against the active slot directory",
                "unlocks.json", UnlockManager.SLOT_SAVE_FILE);
        assertFalse("SLOT_SAVE_FILE must be a bare name, not a global path",
                UnlockManager.SLOT_SAVE_FILE.contains("/"));
    }
}
