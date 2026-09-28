package com.bpm.minotaur.ui;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The one place an enum constant turns into something a player should read.
 *
 * <p>Half a dozen screens were printing {@code name()} straight onto the panel, and because
 * {@code intellivision.ttf} draws underscore as a left arrow the result did not even look like a
 * debug string -- {@code GIANT_ANT} rendered as {@code GIANT<-ANT} and read like a typo. The audit
 * found it on the level-up banner, the altar's sacrifice list, the stash, the chronicle, the
 * hearth's pantry and the workshop's forge.
 *
 * <p>The rules below get the overwhelming majority of constants right, so this is a formatter with
 * an override table rather than a table of several thousand strings that nobody would keep in
 * sync. {@link #OVERRIDES} exists only for names the rules cannot know about: acronyms, ruleset
 * markers, and the handful of constants whose enum name is not the player-facing noun.
 *
 * <p><b>Items are different.</b> {@code Item.getDisplayName()} already resolves the friendly name
 * from {@code weapons.json} / {@code armor.json} and handles identification, beatitude and bone
 * naming. Screens must call that, not this. {@link #ofItemType} is the fallback for the rare place
 * that has an {@code ItemType} and no {@code Item}.
 */
public final class UiNames {

    private UiNames() {
    }

    /** Words that stay lowercase inside a name, but not at the start of one. */
    private static final String[] SMALL_WORDS = {
            "of", "the", "and", "a", "an", "to", "in", "on", "for", "with", "at", "by", "or", "from"
    };

    /** Tokens that stay as written rather than being title-cased. */
    private static final Map<String, String> TOKEN_OVERRIDES = new HashMap<>();

    /** Whole enum names whose formatted form the rules get wrong. */
    private static final Map<String, String> OVERRIDES = new HashMap<>();

    static {
        // Stat and resource abbreviations keep their casing.
        for (String acronym : new String[]{"HP", "MP", "XP", "AC", "STR", "DEX", "CON", "INT", "WIS", "CHA", "TOX", "AGI"}) {
            TOKEN_OVERRIDES.put(acronym, acronym);
        }
        // Ruleset marker. "POTION_INVISIBILITY_5E" is one potion, not a fifth-edition potion.
        TOKEN_OVERRIDES.put("5E", "");

        // Possessives: the enum cannot carry an apostrophe, so the rules produce "Berserkers".
        OVERRIDES.put("BERSERKERS_GAUNTLETS", "Berserker's Gauntlets");
        OVERRIDES.put("ALCHEMISTS_BELT", "Alchemist's Belt");
        OVERRIDES.put("BARDS_FRIEND", "Bard's Friend");
        OVERRIDES.put("DRAGONS_PAW", "Dragon's Paw");
        OVERRIDES.put("FLAIL_FOOTMANS", "Footman's Flail");
        OVERRIDES.put("FLAIL_HORSEMANS", "Horseman's Flail");
        OVERRIDES.put("MACE_FOOTMANS", "Footman's Mace");
        OVERRIDES.put("MACE_HORSEMANS", "Horseman's Mace");
        OVERRIDES.put("PICK_FOOTMANS", "Footman's Pick");
        OVERRIDES.put("PICK_HORSEMANS", "Horseman's Pick");
        OVERRIDES.put("KNIFE_WIDOWS", "Widow's Knife");
        OVERRIDES.put("SWORD_MARINERS", "Mariner's Sword");
        OVERRIDES.put("WHIP_MASTERS", "Master's Whip");
        OVERRIDES.put("GLADIATORS_FRIEND_FOOTMANS", "Gladiator's Friend (Footman's)");
        OVERRIDES.put("GLADIATORS_FRIEND_HORSEMANS", "Gladiator's Friend (Horseman's)");

        // Gibs. "GIB_FLESH" is a lump of flesh, not a gib called Flesh.
        OVERRIDES.put("GIB_FLESH", "Flesh");
        OVERRIDES.put("GIB_BONE", "Bone Shard");
        OVERRIDES.put("GIB_ORGAN", "Organ");
        OVERRIDES.put("GIB_BILE", "Bile");
        OVERRIDES.put("GIB_GLAZE", "Glaze");

        // Item categories that read badly word-by-word.
        OVERRIDES.put("WAR_WEAPON", "War Weapon");
        OVERRIDES.put("SPIRITUAL_WEAPON", "Spiritual Weapon");
        OVERRIDES.put("USEFUL", "Useful Item");
        OVERRIDES.put("MISC", "Miscellany");

        // Monsters whose enum name is not the noun a player would use.
        OVERRIDES.put("PLAYER_GHOST", "Ghost of a Delver");
    }

    /**
     * The player-facing name of an enum constant.
     *
     * <p>Null returns an empty string rather than "null", because a missing name should leave a
     * gap on the panel, not print the word.
     */
    public static String of(Enum<?> value) {
        if (value == null) {
            return "";
        }
        return fromConstant(value.name());
    }

    /**
     * The player-facing name of an enum constant, upper-cased for a Label or Display role.
     *
     * <p>Use this instead of {@code of(x).toUpperCase()} so the small-word and acronym handling
     * still runs first.
     */
    public static String caps(Enum<?> value) {
        return of(value).toUpperCase(Locale.ROOT);
    }

    /**
     * The player-facing name for a raw {@code SCREAMING_SNAKE_CASE} constant name.
     *
     * <p>Exposed for the places that hold an enum's name as a String -- save data, unlock ids,
     * codex keys -- and never for text a caller assembled itself.
     */
    public static String fromConstant(String constantName) {
        if (constantName == null || constantName.isEmpty()) {
            return "";
        }

        String override = OVERRIDES.get(constantName);
        if (override != null) {
            return override;
        }

        StringBuilder out = new StringBuilder(constantName.length());
        for (String rawToken : constantName.split("_")) {
            String token = stripIdSuffix(rawToken);
            if (token.isEmpty()) {
                continue;
            }

            String tokenOverride = TOKEN_OVERRIDES.get(token.toUpperCase(Locale.ROOT));
            if (tokenOverride != null) {
                if (tokenOverride.isEmpty()) {
                    continue;
                }
                append(out, tokenOverride);
                continue;
            }

            String lower = token.toLowerCase(Locale.ROOT);
            if (out.length() > 0 && isSmallWord(lower)) {
                append(out, lower);
            } else {
                append(out, Character.toUpperCase(lower.charAt(0)) + lower.substring(1));
            }
        }

        // The rules cannot produce an unsupported glyph, but an override could, and the point of
        // this class is that nothing downstream has to think about the font.
        return UiGlyphs.sanitize(out.toString());
    }

    /**
     * Fallback name for an {@code ItemType} held without its {@code Item}.
     *
     * <p>Prefer {@code Item.getDisplayName()} wherever an {@code Item} is in hand: it knows the
     * friendly name from the data files, whether the item has been identified, and its beatitude.
     */
    public static String ofItemType(Enum<?> itemType) {
        return of(itemType);
    }

    /**
     * Turns a count and a noun into a phrase that is not wrong at one.
     *
     * <p>The altar showed {@code +1 Divinities}; the stash showed {@code 00 / 30}. Both are the
     * same mistake -- formatting written once for the plural case and never read back at one.
     */
    public static String plural(int count, String singular, String pluralForm) {
        return count + " " + (count == 1 ? singular : pluralForm);
    }

    /** {@link #plural(int, String, String)} for nouns that just take an -s. */
    public static String plural(int count, String singular) {
        return plural(count, singular, singular + "s");
    }

    private static boolean isSmallWord(String lower) {
        for (String small : SMALL_WORDS) {
            if (small.equals(lower)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Drops the disambiguating digit some generated constants carry.
     *
     * <p>{@code POLEARM_SPETUM3} exists because the source table had several spetums; the player
     * should read "Spetum Polearm", not "Spetum3 Polearm" (WORK-5). Only a trailing digit on a
     * token that is otherwise letters is dropped, so {@code LANCE_HEAVY_HORSE1} loses its 1 while
     * a token that is nothing but digits is left for the caller's own formatting to deal with.
     */
    private static String stripIdSuffix(String token) {
        int end = token.length();
        while (end > 0 && Character.isDigit(token.charAt(end - 1))) {
            end--;
        }
        if (end == 0) {
            // Nothing but digits: a disambiguator like the trailing "2" in SHIELD_BODY_2.
            return "";
        }
        if (end == token.length()) {
            return token;
        }
        String head = token.substring(0, end);
        for (int i = 0; i < head.length(); i++) {
            if (!Character.isLetter(head.charAt(i))) {
                return token;
            }
        }
        return head;
    }

    private static void append(StringBuilder out, String word) {
        if (out.length() > 0) {
            out.append(' ');
        }
        out.append(word);
    }
}
