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

        String[] rawParts = friendlyName.split(",");
        java.util.List<String> parts = new java.util.ArrayList<>();
        for (String raw : rawParts) {
            String part = raw.trim();
            if (!part.isEmpty()) {
                parts.add(part);
            }
        }

        if (parts.isEmpty()) {
            return new DiscoveryName("UNKNOWN", "");
        }
        if (parts.size() == 1) {
            return new DiscoveryName(parts.get(0).toUpperCase(), "");
        }

        String headline = (parts.get(1) + " " + parts.get(0)).toUpperCase();

        StringBuilder subtitle = new StringBuilder();
        for (int i = 2; i < parts.size(); i++) {
            if (subtitle.length() > 0) {
                subtitle.append(", ");
            }
            subtitle.append(parts.get(i));
        }

        return new DiscoveryName(headline, subtitle.toString());
    }
}
