package com.bpm.minotaur.managers;

/**
 * Progression state that belongs to one save slot.
 *
 * <p>Several progression singletons wrote to correct per-slot paths but loaded
 * exactly once, in a lazy constructor, and were never re-read when the active
 * slot changed. Switching slots therefore did not merely show the wrong
 * character's data: the next save wrote that data over the other slot's file.
 * {@code DoomManager} was the worst of them, loading once at boot against
 * {@code profile.lastPlayedSlot}, so one character's death count drove another
 * character's bridge integrity.
 *
 * <p>Implementors register with {@link SaveManager#registerSlotScoped} and are
 * told when the active slot changes and when a new game begins.
 */
public interface SlotScopedState {

    /**
     * Discard in-memory state and re-read it from the now-active slot.
     *
     * <p>Called after the active slot has changed, so implementors should
     * resolve their path through {@link SaveManager#getActiveSlotFilePath}
     * rather than caching it.
     */
    void reloadForActiveSlot();

    /**
     * Reset to the state a brand-new character starts with.
     *
     * <p>Called when a new game is started in a slot. Nothing earned in any
     * other slot, or in a previous run of this one, may survive this.
     */
    void resetForNewGame();
}
