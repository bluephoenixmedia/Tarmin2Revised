package com.bpm.minotaur.generation;

import com.bpm.minotaur.gamedata.item.ItemCategory;
import com.bpm.minotaur.gamedata.item.ItemTemplate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service for spawning items using hierarchical weighted probability.
 * Adapted to use ItemTemplate and ItemCategory.
 */
public class ItemSpawner {

    private final Map<String, ItemTemplate> registry; // Need IDs? Template doesn't hold ID usually.
    private final NetHackRNG rng;

    public ItemSpawner(Map<String, ItemTemplate> registry, NetHackRNG rng) {
        this.registry = Map.copyOf(registry);
        this.rng = rng;
    }

    /**
     * Spawns an item based on the context.
     *
     * @param ctx Spawn context
     * @return The selected Item ID and Template (or null fallback)
     */
    public Map.Entry<String, ItemTemplate> spawnItem(SpawnContext ctx) {
        // Step 1: Class Selection Strategy
        Map<ItemCategory, Integer> classWeights = getClassWeights(ctx);
        ItemCategory selectedCategory = selectItemCategory(classWeights);

        // Step 2: Specific Item Selection
        return selectSpecificItem(selectedCategory);
    }

    /**
     * Spawns an item from a specific category.
     * Used by MonsterFactory for equipping monsters.
     *
     * @param category The desired category
     * @return The selected Item ID and Template
     */
    public Map.Entry<String, ItemTemplate> spawnItemByCategory(ItemCategory category) {
        return selectSpecificItem(category);
    }

    private Map<ItemCategory, Integer> getClassWeights(SpawnContext ctx) {
        EnumMap<ItemCategory, Integer> weights = new EnumMap<>(ItemCategory.class);

        if (ctx.isContainer()) {
            weights.put(ItemCategory.WAR_WEAPON, 0); // Assuming WEAPON -> WAR_WEAPON
            weights.put(ItemCategory.SPIRITUAL_WEAPON, 0);
            weights.put(ItemCategory.ARMOR, 0);

            // "Potion 18, Scroll 18, Gem 18" -> Sum 54.
            weights.put(ItemCategory.POTION, 18);
            weights.put(ItemCategory.SCROLL, 18);
            weights.put(ItemCategory.GEM, 18);

            weights.put(ItemCategory.FOOD, 5);
            weights.put(ItemCategory.TOOL, 8);
            weights.put(ItemCategory.WAND, 6);
            weights.put(ItemCategory.RING, 6);
            weights.put(ItemCategory.BOOK, 6);
            weights.put(ItemCategory.AMULET, 2);
            weights.put(ItemCategory.GOLD, 9);

        } else if (ctx.isGehennom()) {
            weights.put(ItemCategory.WAR_WEAPON, 10);
            weights.put(ItemCategory.SPIRITUAL_WEAPON, 10); // Split 20%
            weights.put(ItemCategory.ARMOR, 20);
            weights.put(ItemCategory.POTION, 1);
            weights.put(ItemCategory.SCROLL, 1);

            weights.put(ItemCategory.FOOD, 5);
            weights.put(ItemCategory.GEM, 15);
            weights.put(ItemCategory.TOOL, 10);
            weights.put(ItemCategory.WAND, 10);

            weights.put(ItemCategory.RING, 8);
            weights.put(ItemCategory.BOOK, 5);
            weights.put(ItemCategory.AMULET, 5);
            // weights.put(ItemCategory.GOLD, 0);
        } else {
            // Default Context - Balanced for "More Items/Weapons/Armor"
            weights.put(ItemCategory.FOOD, 30); // 20 -> 30
            weights.put(ItemCategory.POTION, 20); // 16 -> 20
            weights.put(ItemCategory.SCROLL, 25); // 16 -> 25 (Scrolls requested)
            weights.put(ItemCategory.WAR_WEAPON, 15); // 5 -> 15 (Weapons requested)
            weights.put(ItemCategory.SPIRITUAL_WEAPON, 15);// 5 -> 15
            weights.put(ItemCategory.ARMOR, 20); // 10 -> 20 (Armor requested)
            weights.put(ItemCategory.WAND, 15); // 4 -> 15 (Wands requested)

            // Standard/Rare
            weights.put(ItemCategory.TOOL, 8);
            weights.put(ItemCategory.GEM, 8);
            weights.put(ItemCategory.BOOK, 8); // 4 -> 8
            weights.put(ItemCategory.RING, 6); // 3 -> 6
            weights.put(ItemCategory.AMULET, 2); // 1 -> 2
        }

        return weights;
    }

    private ItemCategory selectItemCategory(Map<ItemCategory, Integer> weights) {
        int totalWeight = weights.values().stream().mapToInt(Integer::intValue).sum();
        if (totalWeight <= 0) {
            return ItemCategory.FOOD;
        }

        int roll = rng.rn2(totalWeight);
        int current = 0;

        for (Map.Entry<ItemCategory, Integer> entry : weights.entrySet()) {
            current += entry.getValue();
            if (roll < current) {
                return entry.getKey();
            }
        }

        return ItemCategory.FOOD;
    }

    private Map.Entry<String, ItemTemplate> selectSpecificItem(ItemCategory category) {
        // Need to iterate map and filter by value type
        List<Map.Entry<String, ItemTemplate>> candidates = new ArrayList<>();
        List<Integer> effectiveWeights = new ArrayList<>();

        for (Map.Entry<String, ItemTemplate> entry : registry.entrySet()) {
            ItemTemplate t = entry.getValue();

            if (t.probability > 0 && isCategoryMatch(t, category)) {
                int weight = t.probability;

                if (t.locked) {
                    if (!com.bpm.minotaur.managers.UnlockManager.getInstance().isUnlocked(entry.getKey())) {
                        continue; // Skip locked items
                    }
                    // Boost weight for unlocked items to ensure they are seen
                    weight *= 3;
                }

                candidates.add(entry);
                effectiveWeights.add(weight);
            }
        }

        if (candidates.isEmpty()) {
            return null;
        }

        int sumProbability = effectiveWeights.stream().mapToInt(Integer::intValue).sum();
        if (sumProbability <= 0) {
            return candidates.get(rng.rn2(candidates.size()));
        }

        int roll = rng.rn2(sumProbability);

        for (int i = 0; i < candidates.size(); i++) {
            roll -= effectiveWeights.get(i);
            if (roll < 0) {
                return candidates.get(i);
            }
        }

        return candidates.get(candidates.size() - 1);
    }

    private boolean isCategoryMatch(ItemTemplate t, ItemCategory c) {
        switch (c) {
            case POTION:
                return t.isPotion;
            case FOOD:
                return t.isFood;
            case RING:
                return t.isRing || t.isRingAppearance; // catch all
            case ARMOR:
                return t.isArmor || t.isShield || t.isHelmet;
            case WAR_WEAPON:
                return t.isWeapon; // Distinction hard without more data
            case SPIRITUAL_WEAPON:
                return t.isWeapon; // Distinction hard
            // For others like SCROLL, WAND, BOOK - if no boolean, we can't spawn them
            // easily unless we add data.
            // Assumption: The JSON data will populate these fields if I add them, or I use
            // existing flags.
            // ItemTemplate had "isScrollAppearance", "isWandAppearance",
            // "isRingAppearance".
            case SCROLL:
                return t.isScrollAppearance;
            case WAND:
                return t.isWandAppearance;
            // Book?
            // "isUsable"?
            default:
                return false;
        }
    }
}
