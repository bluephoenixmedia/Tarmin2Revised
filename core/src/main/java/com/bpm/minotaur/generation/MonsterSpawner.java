package com.bpm.minotaur.generation;

import com.bpm.minotaur.gamedata.monster.MonsterTemplate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Service for spawning monsters using NetHack's distribution logic.
 * Implements the "Bag" algorithm for weighted random selection.
 * Adapted to use MonsterTemplate.
 */
public class MonsterSpawner {

    // Registry maps ID -> Template. We might just need values, but Map is safer if
    // we need IDs.
    private final Map<String, MonsterTemplate> registry; // Or just List<MonsterTemplate>
    // Assuming passed collection is templates. If we need IDs, templates usually
    // don't self-identify unless we use map key?
    // MonsterTemplate doesn't have an "id" field in the code I saw, only
    // friendlyName.
    // The previous implementation assumed 'id' was part of the archetype.
    // Let's assume the registry passed is a Map<String, MonsterTemplate> so we can
    // track uniques by ID.

    private final NetHackRNG rng;

    public MonsterSpawner(Map<String, MonsterTemplate> registry, NetHackRNG rng) {
        this.registry = Map.copyOf(registry);
        this.rng = rng;
    }

    /**
     * Spawns a random monster based on the current context.
     *
     * @param ctx The spawn context (depth, level, flags)
     * @return Optional containing the selected Monster ID and Template, or empty.
     */
    public Optional<Map.Entry<String, MonsterTemplate>> spawnRandomMonster(SpawnContext ctx) {

        TreeMap<Long, Map.Entry<String, MonsterTemplate>> heavyBag = new TreeMap<>();
        long totalWeight = 0;

        for (Map.Entry<String, MonsterTemplate> entry : registry.entrySet()) {
            String id = entry.getKey();
            MonsterTemplate template = entry.getValue();

            // --- Filter ---

            // Skip if NO_GEN (G_NOGEN in bitmask?)
            if ((template.generationFlags & MonsterTemplate.G_NOGEN) != 0) {
                continue;
            }

            // Skip if UNIQUE and already spawned
            if ((template.generationFlags & MonsterTemplate.G_UNIQ) != 0 && ctx.isUniqueSpawned(id)) {
                continue;
            }

            // Depth Check: Skip if baseLevel < depth / 6
            if (template.baseLevel < ctx.depth() / 6) {
                continue;
            }

            // Depth Check: Skip if baseLevel > (depth + playerLevel) / 2
            if (template.baseLevel > (ctx.depth() + ctx.playerLevel()) / 2) {
                continue;
            }

            // Gehennom Logic
            boolean noHell = (template.generationFlags & MonsterTemplate.G_NOHELL) != 0;
            boolean hellOnly = (template.generationFlags & MonsterTemplate.G_HELL) != 0;

            if (ctx.isGehennom()) {
                if (noHell)
                    continue;
            } else {
                if (hellOnly)
                    continue;
            }

            // --- Weight Calculation ---
            long weight = template.frequency;

            // Alignment bonus
            if (template.alignment == ctx.levelAlignment()) {
                weight += 5;
            }

            // Level boost logic
            long effectiveWeight = weight;
            if (template.baseLevel <= ctx.playerLevel() * 2) {
                effectiveWeight += 1;
            }

            if (effectiveWeight > 0) {
                totalWeight += effectiveWeight;
                heavyBag.put(totalWeight, entry);
            }
        }

        if (totalWeight <= 0 || heavyBag.isEmpty()) {
            return Optional.empty();
        }

        // --- Selection ---
        long pick = rng.nextLong(totalWeight);

        var selectedEntry = heavyBag.higherEntry(pick);
        if (selectedEntry == null) {
            return Optional.empty();
        }

        return Optional.of(selectedEntry.getValue());
    }
}
