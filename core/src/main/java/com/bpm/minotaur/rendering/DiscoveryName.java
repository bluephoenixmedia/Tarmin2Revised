package com.bpm.minotaur.rendering;

/**
 * An item's stored name, turned into something worth printing on a trophy card.
 *
 * <p>{@code weapons.json} and {@code armor.json} store names sort-first so they alphabetise by
 * category: "Axe, Battle", "Shield, Round Wooden", "Javelin, Stone, Two-Handed". Printed verbatim
 * that reads as a database row, and it puts the least specific word in the headline -- unlocking
 * two axes would produce two cards both titled AXE.
 *
 * <p>So the first two parts are swapped into natural order for the headline, and anything left
 * over becomes a subtitle. Names with no comma (most of {@code items.json}) pass through.
 */
public final class DiscoveryName {

    public final String headline;
    public final String subtitle;

    private DiscoveryName(String headline, String subtitle) {
        this.headline = headline;
        this.subtitle = subtitle;
    }

    public static DiscoveryName of(String friendlyName) {
        if (friendlyName == null || friendlyName.trim().isEmpty()) {
            return new DiscoveryName("UNKNOWN", "");
        }

        // The split itself lives in ItemName, so the card and every other surface that shows an
        // item name agree on how a stored name is taken apart.
        com.bpm.minotaur.gamedata.item.ItemName parsed =
                com.bpm.minotaur.gamedata.item.ItemName.parse(friendlyName);

        if (parsed.category.isEmpty()) {
            return new DiscoveryName("UNKNOWN", "");
        }
        if (parsed.qualifier.isEmpty()) {
            return new DiscoveryName(parsed.category.toUpperCase(), "");
        }
        return new DiscoveryName(
                (parsed.qualifier + " " + parsed.category).toUpperCase(),
                parsed.extra);
    }
}
