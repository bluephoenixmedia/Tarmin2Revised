package com.bpm.minotaur.managers;

import com.bpm.minotaur.gamedata.item.Item;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Chooses which locked items a run unlocks, so the player does not receive the same kind of thing
 * over and over.
 *
 * <p>The roll used to shuffle one flat list of every eligible item, so whichever category had the
 * most entries took most of the unlocks: a long armour list meant armour, again and again. The pick
 * is now in two steps. A category is chosen first, weighted towards the ones the player has not
 * received lately, and then an item is chosen within it. Pool size no longer decides the category.
 * Nothing is scripted: a long-neglected category is likelier, never certain.
 */
public final class UnlockRotation {

    public enum Category {
        WEAPON, ARMOR, ITEM
    }

    /** How many past unlocks are remembered, most recent first. */
    public static final int HISTORY_LENGTH = 6;

    /** The most extra weight a category can earn by being neglected. */
    private static final int MAX_STALENESS = 4;

    public static final class Candidate {
        public final Item.ItemType type;
        public final Category category;

        public Candidate(Item.ItemType type, Category category) {
            this.type = type;
            this.category = category;
        }
    }

    private UnlockRotation() {
    }

    /**
     * @param pool    what may still be unlocked; not modified
     * @param recent  categories of recent unlocks, most recent first
     * @param count   how many to pick
     * @return the picked candidates, in the order picked
     */
    public static List<Candidate> pick(List<Candidate> pool, List<Category> recent, int count, Random rng) {
        List<Candidate> remaining = new ArrayList<>(pool);
        List<Category> history = new ArrayList<>(recent != null ? recent : new ArrayList<Category>());
        List<Candidate> picked = new ArrayList<>();

        for (int n = 0; n < count && !remaining.isEmpty(); n++) {
            Category category = chooseCategory(remaining, history, rng);
            List<Candidate> inCategory = new ArrayList<>();
            for (Candidate c : remaining) {
                if (c.category == category) {
                    inCategory.add(c);
                }
            }
            Candidate chosen = inCategory.get(rng.nextInt(inCategory.size()));
            remaining.remove(chosen);
            picked.add(chosen);
            // A second pick in the same roll sees the first as the most recent unlock.
            history.add(0, category);
        }
        return picked;
    }

    /** The weight a category carries: 1, plus one for each recent unlock that was not it. */
    static int weight(Category category, List<Category> recent) {
        int sinceLast = 0;
        for (Category c : recent) {
            if (c == category) {
                break;
            }
            sinceLast++;
        }
        return 1 + Math.min(sinceLast, MAX_STALENESS);
    }

    private static Category chooseCategory(List<Candidate> remaining, List<Category> recent, Random rng) {
        int[] weights = new int[Category.values().length];
        int total = 0;
        for (Category category : Category.values()) {
            boolean available = false;
            for (Candidate c : remaining) {
                if (c.category == category) {
                    available = true;
                    break;
                }
            }
            if (available) {
                weights[category.ordinal()] = weight(category, recent);
                total += weights[category.ordinal()];
            }
        }
        int roll = rng.nextInt(total);
        for (Category category : Category.values()) {
            roll -= weights[category.ordinal()];
            if (roll < 0) {
                return category;
            }
        }
        return remaining.get(0).category;
    }
}
