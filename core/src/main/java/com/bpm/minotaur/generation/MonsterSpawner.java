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

        // NetHack Out-of-Depth (OOD) generation:
        // Surface (Z <= 1): 0% OOD chance (baseLevel <= ctx.edl())
        // Subterranean (Z >= 2): 15% OOD chance rolled ONCE per spawn attempt.
        int maxLevel = ctx.edl();
        boolean isOod = false;
        if (ctx.isSubterranean() && rng.rn2(100) < 15) {
            isOod = true;
            // On shallow strata, clamp OOD bonus so early delve remains survivable:
            // Strata 1 (depth <= 2): max OOD bonus +1, capped at baseLevel 3 (e.g. Orc/Wererat, never Ogres/Umber Hulks).
            // Strata 2 (depth == 3): max OOD bonus +1, capped at baseLevel 4 (e.g. Gargoyle, never Ogres/Umber Hulks).
            // Deeper floors (depth >= 4): +1 + rn2(3).
            int oodBonus = (ctx.depth() <= 3) ? 1 : (1 + rng.rn2(3));
            maxLevel = ctx.edl() + oodBonus;
            if (ctx.depth() <= 2) {
                maxLevel = Math.min(3, maxLevel);
            } else if (ctx.depth() == 3) {
                maxLevel = Math.min(4, maxLevel);
            }
        }

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

            // NetHack lower-bound pruning: Skip if baseLevel < max(1, ctx.edl() / 4)
            // Capped at 14 to preserve highest-tier foes (baseLevel 14-18) at extreme EDL.
            int minLevel = Math.min(14, Math.max(1, ctx.edl() / 4));
            if (template.baseLevel < minLevel) {
                continue;
            }

            if (template.baseLevel > maxLevel) {
                continue;
            }

            // Surface Near-Shelter Threat Bounding (within 2 chunks of shelter 0,0)
            // Strictly bounds foes to Tier 1 (MCR <= 100, baseLevel <= 2).
            if (ctx.depth() <= 1 && ctx.chunkId() != null
                    && Math.abs(ctx.chunkId().x) <= 2 && Math.abs(ctx.chunkId().y) <= 2) {
                int mcr = calculateMCR(template);
                if (mcr > 100 || template.baseLevel > 2) {
                    continue;
                }
            }

            // Strata 1 Near-Shelter Column Threat Bounding (depth == 2, within 2 chunks of shelter column 0,0)
            // Strictly bounds foes to early delve tier (MCR <= 110, baseLevel <= 3).
            // Prevents any Tier 4+ monsters or high-threat beasts (Ogres, Owlbears, Umber Hulks) from spawning right under shelter.
            if (ctx.depth() == 2 && ctx.chunkId() != null
                    && Math.abs(ctx.chunkId().x) <= 2 && Math.abs(ctx.chunkId().y) <= 2) {
                int mcr = calculateMCR(template);
                if (mcr > 110 || template.baseLevel > 3) {
                    continue;
                }
            }

            // Strata 2 Near-Origin Column Threat Bounding (depth == 3, within 2 chunks of origin 0,0)
            // Strictly bounds foes to Tier 2 (MCR <= 160, baseLevel <= 4).
            // Guarantees no Tier 5/6 behemoths (Ogres, Trolls, Umber Hulks) near the central descent shaft.
            if (ctx.depth() == 3 && ctx.chunkId() != null
                    && Math.abs(ctx.chunkId().x) <= 2 && Math.abs(ctx.chunkId().y) <= 2) {
                int mcr = calculateMCR(template);
                if (mcr > 160 || template.baseLevel > 4) {
                    continue;
                }
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

            // Out-of-Depth discount: if this monster exceeds native EDL, discount its weight
            // so out-of-depth spawns are rare encounters rather than dominant spawns.
            if (isOod && template.baseLevel > ctx.edl()) {
                effectiveWeight = Math.max(1, effectiveWeight / 3);
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

    public static int calculateMCR(MonsterTemplate template) {
        if (template == null) return 0;
        int score = 0;
        score += template.maxHP;
        score += template.maxMP;
        score += template.armorClass * 3;
        score += template.dexterity;
        if (template.hasRangedAttack) {
            score += 15;
        }
        return score;
    }
}
