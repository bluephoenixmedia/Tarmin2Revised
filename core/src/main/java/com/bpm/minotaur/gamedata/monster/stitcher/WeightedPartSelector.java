package com.bpm.minotaur.gamedata.monster.stitcher;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Stateless utility: selects a part id for a given socket by applying
 * biome modifier overrides to the DNA's base weight table, then
 * performing a weighted random draw.
 */
public class WeightedPartSelector {

    /**
     * @param dna    The creature's DNA descriptor.
     * @param socket Socket name to select a part for, e.g. "HEAD".
     * @param biome  Current biome environmental context.
     * @param seed   Random seed (typically monster instance id for determinism).
     * @return Selected part id, or null if no weights are defined for this socket.
     */
    public String select(CreatureDna dna, String socket, BiomeFilterContext biome, long seed) {
        Map<String, Integer> weights = parseWeights(dna.partWeights.get(socket));
        if (weights.isEmpty()) return null;

        applyBiomeModifiers(dna, socket, biome, weights);

        return weightedDraw(weights, new Random(seed ^ socket.hashCode()));
    }

    // --- Private helpers ---

    private Map<String, Integer> parseWeights(List<String> entries) {
        Map<String, Integer> result = new HashMap<>();
        if (entries == null) return result;
        for (String entry : entries) {
            int colon = entry.lastIndexOf(':');
            if (colon < 1) continue;
            String partId = entry.substring(0, colon).trim();
            try {
                int w = Integer.parseInt(entry.substring(colon + 1).trim());
                result.put(partId, w);
            } catch (NumberFormatException ignored) {}
        }
        return result;
    }

    private void applyBiomeModifiers(CreatureDna dna, String socket,
                                      BiomeFilterContext biome, Map<String, Integer> weights) {
        if (dna.biomeModifiers == null || biome == null) return;

        // Check each active biome tag and replace weights for matching socket
        applyTagModifier(dna, socket, biome.biomeTag, weights);
        if (biome.isCold()) applyTagModifier(dna, socket, "cold",  weights);
        if (biome.isHot())  applyTagModifier(dna, socket, "hot",   weights);
        if (biome.isWet())  applyTagModifier(dna, socket, "wet",   weights);
        if (biome.isArid()) applyTagModifier(dna, socket, "arid",  weights);
    }

    private void applyTagModifier(CreatureDna dna, String socket, String tag,
                                   Map<String, Integer> weights) {
        if (tag == null || tag.isEmpty()) return;
        Map<String, List<String>> tagMap = dna.biomeModifiers.get(tag);
        if (tagMap == null) return;
        List<String> override = tagMap.get(socket);
        if (override == null || override.isEmpty()) return;

        // Replace weight table for this socket entirely
        weights.clear();
        weights.putAll(parseWeights(override));
    }

    private String weightedDraw(Map<String, Integer> weights, Random rng) {
        int total = 0;
        for (int w : weights.values()) total += w;
        if (total <= 0) return weights.isEmpty() ? null : weights.keySet().iterator().next();

        int roll = rng.nextInt(total);
        int cumulative = 0;
        for (Map.Entry<String, Integer> e : weights.entrySet()) {
            cumulative += e.getValue();
            if (roll < cumulative) return e.getKey();
        }
        return weights.keySet().iterator().next();
    }
}
