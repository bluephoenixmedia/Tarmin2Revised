package com.bpm.minotaur.gamedata.events;

import java.util.ArrayList;
import java.util.List;

/**
 * One choice event as written in {@code data/events.json}: a scene, where it may appear, and the
 * choices it offers. See {@code docs/DEsign/events.md}.
 */
public class EventDefinition {
    public String id;
    public String title;
    public String text;
    /** The scene art shown above the text. Falls back to the biome placeholder when missing. */
    public String imagePath;
    /** Art behind the window. Falls back to the biome default, then to the scrim. */
    public String backgroundPath;

    /** {@link com.bpm.minotaur.generation.Biome} names this event may be placed in. */
    public List<String> biomes = new ArrayList<>();
    public int minDepth = 1;
    public int maxDepth = 99;
    public int weight = 1;

    public List<EventChoice> choices = new ArrayList<>();

    public boolean allowsBiome(String biome) {
        return biome != null && biomes.contains(biome);
    }

    public boolean allowsDepth(int depth) {
        return depth >= minDepth && depth <= maxDepth;
    }
}
