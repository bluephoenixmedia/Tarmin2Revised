package com.bpm.minotaur.gamedata.item;

import java.util.ArrayList;
import java.util.List;

/**
 * An item's stored name, turned into something a player should read.
 *
 * <p>{@code weapons.json} and {@code armor.json} store names category-first so they alphabetise by
 * weapon class: "Axe, Battle", "Shield, Round Wooden", "Javelin, Stone, Two-Handed". That is the
 * right shape for data and the wrong shape for display, and 168 of 266 weapons carry a comma -- so
 * for most of the armoury the stored form was reaching the HUD, the inventory and pickup messages
 * verbatim.
 *
 * <p>The first two parts swap into natural order. Anything after them is kept in parentheses
 * rather than dropped, because those qualifiers are mechanically meaningful -- "Two-Handed"
 * changes how a weapon is used, so losing it to tidy the label would be a worse bug than the one
 * being fixed.
 */
public final class ItemName {

    /** The weapon class, e.g. "Javelin". Never empty for a parsed name. */
    public final String category;
    /** The leading qualifier, e.g. "Stone". Empty when the name had no comma. */
    public final String qualifier;
    /** Any further qualifiers, joined, e.g. "Two-Handed". Empty when there are none. */
    public final String extra;

    private ItemName(String category, String qualifier, String extra) {
        this.category = category;
        this.qualifier = qualifier;
        this.extra = extra;
    }

    public static ItemName parse(String storedName) {
        List<String> parts = new ArrayList<>();
        if (storedName != null) {
            for (String raw : storedName.split(",")) {
                String part = raw.trim();
                if (!part.isEmpty()) {
                    parts.add(part);
                }
            }
        }

        if (parts.isEmpty()) {
            // Nothing but separators. Do not invent a category out of punctuation.
            return new ItemName("", "", "");
        }
        if (parts.size() == 1) {
            return new ItemName(parts.get(0), "", "");
        }

        StringBuilder rest = new StringBuilder();
        for (int i = 2; i < parts.size(); i++) {
            if (rest.length() > 0) {
                rest.append(", ");
            }
            rest.append(parts.get(i));
        }
        return new ItemName(parts.get(0), parts.get(1), rest.toString());
    }

    /**
     * The display form: "Axe, Battle" becomes "Battle Axe", "Javelin, Stone, Two-Handed" becomes
     * "Stone Javelin (Two-Handed)".
     *
     * <p>Null and blank pass straight through, so callers can use this on any name without having
     * to guard it first.
     */
    public static String natural(String storedName) {
        if (storedName == null || storedName.trim().isEmpty()) {
            return storedName;
        }
        if (storedName.indexOf(',') < 0) {
            return storedName;
        }

        ItemName name = parse(storedName);
        if (name.category.isEmpty()) {
            return storedName;
        }
        if (name.qualifier.isEmpty()) {
            return name.category;
        }

        String base = name.qualifier + " " + name.category;
        return name.extra.isEmpty() ? base : base + " (" + name.extra + ")";
    }
}
