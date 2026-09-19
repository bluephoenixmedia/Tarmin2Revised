package com.bpm.minotaur.gamedata.spells;

import com.bpm.minotaur.gamedata.item.Item;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * A Tome Choice: a handful of not-yet-known spells drawn from a Tome's curated
 * pool, from which the player picks one. The Altar's Arcane Attunement sets the
 * {@link Perks}: how many options, how many rerolls, and how high the Tome of
 * Tarmin reaches.
 */
public final class TomeChoice {

    /** What the Altar's Arcane Attunement tier adds to every Tome Choice. */
    public record Perks(int options, int rerolls, int tarminMaxLevel) {
    }

    private final Tome tome;
    private final Item tomeItem;
    private final Perks perks;
    private List<String> options;
    private int rerollsLeft;

    private TomeChoice(Tome tome, Item tomeItem, Perks perks, List<String> options) {
        this.tome = tome;
        this.tomeItem = tomeItem;
        this.perks = perks;
        this.options = options;
        this.rerollsLeft = perks.rerolls();
    }

    /** Every spell in the Tome's pool that is not yet known and within its level reach. */
    public static List<String> candidates(Tome tome, Collection<String> knownIds, Perks perks) {
        int maxLevel = tome == Tome.TARMIN ? perks.tarminMaxLevel() : tome.getMaxSpellLevel();
        List<String> candidates = new ArrayList<>();
        for (SpellTemplate spell : SpellDataManager.getInstance().getAllSpells()) {
            String id = spell.getId().toUpperCase(Locale.ROOT);
            if (spell.isInTomePool(tome) && spell.getLevel() <= maxLevel && !knownIds.contains(id)) {
                candidates.add(id);
            }
        }
        Collections.sort(candidates);
        return candidates;
    }

    /** Draws a Tome Choice, or returns null when the Tome has nothing left to teach. */
    public static TomeChoice offer(Tome tome, Item tomeItem, Collection<String> knownIds, Perks perks, Random rng) {
        List<String> candidates = candidates(tome, knownIds, perks);
        if (candidates.isEmpty()) {
            return null;
        }
        return new TomeChoice(tome, tomeItem, perks, draw(candidates, perks.options(), rng));
    }

    /** Whether a reroll is left and the pool still holds spells not on offer now. */
    public boolean canReroll(Collection<String> knownIds) {
        return rerollsLeft > 0 && !freshCandidates(knownIds).isEmpty();
    }

    /**
     * Spends a reroll to draw fresh options, favouring spells not just offered.
     *
     * @return false, spending nothing, when no reroll is left or none would show a new spell
     */
    public boolean reroll(Collection<String> knownIds, Random rng) {
        if (!canReroll(knownIds)) {
            return false;
        }
        rerollsLeft--;
        List<String> candidates = candidates(tome, knownIds, perks);
        List<String> fresh = freshCandidates(knownIds);
        List<String> drawn = draw(fresh, perks.options(), rng);
        if (drawn.size() < perks.options()) {
            List<String> previous = new ArrayList<>(options);
            previous.retainAll(candidates);
            drawn.addAll(draw(previous, perks.options() - drawn.size(), rng));
        }
        options = drawn;
        return true;
    }

    private List<String> freshCandidates(Collection<String> knownIds) {
        List<String> fresh = candidates(tome, knownIds, perks);
        fresh.removeAll(options);
        return fresh;
    }

    private static List<String> draw(List<String> from, int count, Random rng) {
        List<String> shuffled = new ArrayList<>(from);
        Collections.shuffle(shuffled, rng);
        return new ArrayList<>(shuffled.subList(0, Math.min(count, shuffled.size())));
    }

    public Tome getTome() {
        return tome;
    }

    /** The Tome item being studied; used up when a spell is chosen. */
    public Item getTomeItem() {
        return tomeItem;
    }

    public List<String> getOptions() {
        return Collections.unmodifiableList(options);
    }

    public int getRerollsLeft() {
        return rerollsLeft;
    }
}
