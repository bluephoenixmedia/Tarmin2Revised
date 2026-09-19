package com.bpm.minotaur.rendering.weaponview;

import java.util.ArrayList;
import java.util.List;

/**
 * Walks the tuner through every weapon a hand can hold, one per [ or ].
 *
 * Holds item keys rather than items, so the stepping rules can be tested without
 * loading any data. Stepping stops at the ends rather than wrapping, as the paperdoll
 * editor's does, so reaching the end of a pass is visible.
 *
 * The first step starts from whatever is actually equipped when it is in the list, so
 * ] from a longsword shows the weapon after the longsword rather than jumping to the
 * top of a list of hundreds.
 */
public class WeaponViewCycle {

    private final List<String> keys;
    /** -1 while showing what is actually equipped. */
    private int index = -1;

    public WeaponViewCycle(List<String> keys) {
        this.keys = new ArrayList<String>(keys);
    }

    /** The key being previewed, or null when showing what is equipped. */
    public String current() {
        return index >= 0 ? keys.get(index) : null;
    }

    public boolean isPreviewing() {
        return index >= 0;
    }

    public void next(String equippedKey) {
        if (keys.isEmpty()) {
            return;
        }
        int from = index >= 0 ? index : keys.indexOf(equippedKey);
        index = from < 0 ? 0 : Math.min(from + 1, keys.size() - 1);
    }

    public void previous(String equippedKey) {
        if (keys.isEmpty()) {
            return;
        }
        int from = index >= 0 ? index : keys.indexOf(equippedKey);
        index = from < 0 ? 0 : Math.max(from - 1, 0);
    }

    /** Back to showing what is actually equipped. */
    public void reset() {
        index = -1;
    }

    /** 1-based, for display; 0 while showing what is equipped. */
    public int position() {
        return index + 1;
    }

    public int size() {
        return keys.size();
    }
}
