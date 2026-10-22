package com.bpm.minotaur.managers;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * "World Log: the Enduring survive the Shifting" (#289). A death's Shifting deletes the explored
 * chunks and nothing else (WorldManager.wipeExploredWorldOnDeath); every Enduring kind is kept
 * outside them, so it outlives the remaking and is placed anew:
 * <ul>
 *   <li>Houses and their lords, Gashes and their holders, Megabeasts, Nemeses and spent gods: the
 *   history, replayed from world.json (ADR 0004; HistoryManagerTest, NemesisHistoryTest, OldGodsTest);</li>
 *   <li>named graves and ghosts: the bones files;</li>
 *   <li>what the Nemeses took, and the World Log itself.</li>
 * </ul>
 * Minotaur generals are not yet things of the world; they come with Castle Tarmin's gauntlet.
 */
public class EnduringTest {

    @Test
    public void nothingEnduringIsKeptWhereTheShiftingDeletes() {
        SaveManager saves = SaveManager.getInstance();
        String shifted = saves.getActiveChunkSaveDirectory();
        String[] enduring = {
                saves.getActiveSlotFilePath("world.json"),
                saves.getActiveSlotFilePath("bones/"),
                saves.getActiveSlotFilePath("world_log.json"),
                saves.getActiveSlotFilePath("nemesis_hoards.json"),
                saves.getActiveSlotFilePath("doom_state.json")
        };
        for (String path : enduring) {
            assertFalse(path + " lies under the chunks the Shifting deletes", path.startsWith(shifted));
        }
    }
}
