package com.bpm.minotaur.gamedata.spawntables;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * A helper class to perform weighted random selection from a list of entries.
 */
public class WeightedRandomList<T extends SpawnTableEntry> {

    private final List<T> entries = new ArrayList<>();
    private int totalWeight = 0;
    private final Random random = new Random();

    public WeightedRandomList() {}

    /**
     * Adds a spawn entry to this list and updates the total weight.
     * @param entry The entry to add.
     */
    public void add(T entry) {
        if (entry.weight > 0) {
            entries.add(entry);
            totalWeight += entry.weight;
        }
    }

    /**
     * Checks if this list is empty (i.e., no valid entries were added).
     * @return True if empty, false otherwise.
     */
    public boolean isEmpty() {
        return entries.isEmpty();
    }

    /**
     * Gets a random entry from the list, respecting the weights of all entries.
     * @return A randomly selected entry, or null if the list is empty.
     */
    public T getRandomEntry() {
        if (isEmpty()) {
            return null;
        }

        int randomWeight = random.nextInt(totalWeight);
        int currentWeight = 0;

        for (T entry : entries) {
            currentWeight += entry.weight;
            if (randomWeight < currentWeight) {
                return entry;
            }
        }

        // Fallback (should never be hit if totalWeight is correct, but safe)
        return entries.get(0);
    }
}
