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

    public static final String FALLBACK_MP_BONUS_ID = "TOME_BOON_MAX_MP";

    /** Unlearned curated spells for this Tome within its level reach. */
    public static List<String> curatedCandidates(Tome tome, Collection<String> knownIds, Perks perks) {
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

    /** Unlearned general (non-exclusive) spells within the Tome's level reach. */
    public static List<String> generalCandidates(Tome tome, Collection<String> knownIds, Perks perks) {
        int maxLevel = tome == Tome.TARMIN ? perks.tarminMaxLevel() : tome.getMaxSpellLevel();
        List<String> candidates = new ArrayList<>();
        for (SpellTemplate spell : SpellDataManager.getInstance().getAllSpells()) {
            String id = spell.getId().toUpperCase(Locale.ROOT);
            if (!spell.isInTomePool(tome) && !id.equals(FALLBACK_MP_BONUS_ID) && spell.getLevel() <= maxLevel && !knownIds.contains(id)) {
                candidates.add(id);
            }
        }
        Collections.sort(candidates);
        return candidates;
    }

    /** Every eligible candidate (curated first, general fallback, or MP bonus if all known). */
    public static List<String> candidates(Tome tome, Collection<String> knownIds, Perks perks) {
        List<String> curated = curatedCandidates(tome, knownIds, perks);
        if (!curated.isEmpty()) {
            return curated;
        }
        List<String> general = generalCandidates(tome, knownIds, perks);
        if (!general.isEmpty()) {
            return general;
        }
        return Collections.singletonList(FALLBACK_MP_BONUS_ID);
    }

    /** Draws a mixed hand: curated exclusive first, then general unlearned, or MP bonus if exhausted. */
    public static List<String> drawHand(Tome tome, Collection<String> knownIds, Perks perks, Random rng) {
        List<String> curated = new ArrayList<>(curatedCandidates(tome, knownIds, perks));
        List<String> general = new ArrayList<>(generalCandidates(tome, knownIds, perks));
        Collections.shuffle(curated, rng);
        Collections.shuffle(general, rng);

        List<String> hand = new ArrayList<>();
        for (String id : curated) {
            if (hand.size() < perks.options()) {
                hand.add(id);
            }
        }
        for (String id : general) {
            if (hand.size() < perks.options()) {
                hand.add(id);
            }
        }
        if (hand.isEmpty()) {
            hand.add(FALLBACK_MP_BONUS_ID);
        }
        return hand;
    }

    /** Draws a Tome Choice, offering a mixed hand of curated and fallback spells. */
    public static TomeChoice offer(Tome tome, Item tomeItem, Collection<String> knownIds, Perks perks, Random rng) {
        List<String> hand = drawHand(tome, knownIds, perks, rng);
        return new TomeChoice(tome, tomeItem, perks, hand);
    }

    /** Rebuilds a Tome Choice exactly as it was saved, so a reload never redraws it. */
    public static TomeChoice restore(Tome tome, Item tomeItem, List<String> options, int rerollsLeft, Perks perks) {
        TomeChoice choice = new TomeChoice(tome, tomeItem, perks, new ArrayList<>(options));
        choice.rerollsLeft = rerollsLeft;
        return choice;
    }

    /** Whether a reroll is left and the pool still holds spells not on offer now. */
    public boolean canReroll(Collection<String> knownIds) {
        if (rerollsLeft <= 0) {
            return false;
        }
        List<String> pool = new ArrayList<>(curatedCandidates(tome, knownIds, perks));
        pool.addAll(generalCandidates(tome, knownIds, perks));
        pool.removeAll(options);
        return !pool.isEmpty();
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

        List<String> freshCurated = new ArrayList<>(curatedCandidates(tome, knownIds, perks));
        freshCurated.removeAll(options);
        Collections.shuffle(freshCurated, rng);

        List<String> freshGeneral = new ArrayList<>(generalCandidates(tome, knownIds, perks));
        freshGeneral.removeAll(options);
        Collections.shuffle(freshGeneral, rng);

        List<String> drawn = new ArrayList<>();
        for (String id : freshCurated) {
            if (drawn.size() < perks.options()) {
                drawn.add(id);
            }
        }
        for (String id : freshGeneral) {
            if (drawn.size() < perks.options()) {
                drawn.add(id);
            }
        }
        if (drawn.size() < perks.options()) {
            List<String> previous = new ArrayList<>(options);
            Collections.shuffle(previous, rng);
            for (String id : previous) {
                if (drawn.size() < perks.options() && !drawn.contains(id)) {
                    drawn.add(id);
                }
            }
        }
        if (drawn.isEmpty()) {
            drawn.add(FALLBACK_MP_BONUS_ID);
        }
        options = drawn;
        return true;
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
