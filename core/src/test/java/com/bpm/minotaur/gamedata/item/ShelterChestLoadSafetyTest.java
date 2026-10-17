package com.bpm.minotaur.gamedata.item;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * An unreadable chest file must never be read as "the player owns nothing".
 *
 * <p>{@code load()} used to call {@code items.clear()} before parsing. A single
 * entry the parser could not read -- an ItemType name no longer in the enum is
 * enough -- left the stash empty in memory, and the next save wrote that empty
 * list over the player's persistent storage. The chest is where the game asks
 * players to put the things they most want to keep.
 *
 * <p>This covers the decision that was wrong, not the file I/O around it: the
 * surrounding parse needs a LibGDX Files backend that the test classpath does
 * not carry.
 */
public class ShelterChestLoadSafetyTest {

    private List<Item> stashOf(int n) {
        List<Item> l = new ArrayList<>();
        for (int i = 0; i < n; i++) l.add(new Item());
        return l;
    }

    @Test
    public void aFailedParseLeavesTheStashUntouched() {
        List<Item> stash = stashOf(7);
        List<Item> before = new ArrayList<>(stash);

        assertFalse("A null parse must report that nothing was adopted",
                ShelterChest.adopt(stash, null));

        assertEquals("An unreadable file must not empty the stash", 7, stash.size());
        assertEquals(before, stash);
    }

    @Test
    public void aSuccessfulParseReplacesTheContents() {
        List<Item> stash = stashOf(3);
        List<Item> loaded = stashOf(5);

        assertTrue(ShelterChest.adopt(stash, loaded));

        assertEquals("A good parse must replace, not append", 5, stash.size());
    }

    @Test
    public void anEmptyFileIsStillAValidEmptyChest() {
        // Distinct from a failed parse: a chest the player emptied on purpose
        // parses to an empty list, and that must be honoured.
        List<Item> stash = stashOf(4);

        assertTrue(ShelterChest.adopt(stash, new ArrayList<>()));

        assertEquals("A legitimately empty chest must be adopted", 0, stash.size());
    }
}
