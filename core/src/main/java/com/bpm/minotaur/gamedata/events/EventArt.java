package com.bpm.minotaur.gamedata.events;

import java.util.Locale;
import java.util.function.Predicate;

/**
 * Which picture an event shows, with the fallbacks that let art land in any order.
 *
 * <p>The scene image falls back to a per-biome placeholder; the backdrop falls back to the biome
 * default, and then to nothing (the window's scrim).
 */
public final class EventArt {

    private EventArt() {
    }

    public static String placeholderFor(String biome) {
        return "images/events/placeholders/" + slug(biome) + ".png";
    }

    /** The biome's default backdrop without its extension; art may arrive as PNG or JPEG. */
    public static String backgroundBaseFor(String biome) {
        return "images/events/backgrounds/" + slug(biome);
    }

    /** The scene image to load, given which asset paths exist. */
    public static String image(EventDefinition def, String biome, Predicate<String> exists) {
        if (def.imagePath != null && exists.test(def.imagePath)) {
            return def.imagePath;
        }
        String placeholder = placeholderFor(biome);
        return exists.test(placeholder) ? placeholder : null;
    }

    /** The backdrop to load, or null for the plain scrim. */
    public static String background(EventDefinition def, String biome, Predicate<String> exists) {
        if (def.backgroundPath != null && exists.test(def.backgroundPath)) {
            return def.backgroundPath;
        }
        for (String ext : new String[]{".png", ".jpg"}) {
            String fallback = backgroundBaseFor(biome) + ext;
            if (exists.test(fallback)) {
                return fallback;
            }
        }
        return null;
    }

    private static String slug(String biome) {
        return (biome != null ? biome : "MAZE").toLowerCase(Locale.ROOT);
    }
}
