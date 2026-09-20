package com.bpm.minotaur.generation;

import com.badlogic.gdx.assets.AssetManager;
import com.bpm.minotaur.gamedata.Alignment;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.Item.ItemType;
import com.bpm.minotaur.gamedata.item.ItemDataManager;
import com.bpm.minotaur.gamedata.item.ItemTemplate;
import com.bpm.minotaur.gamedata.item.ItemVariant;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * What a mimic was digesting.
 *
 * <p>Rolled when the mimic dies rather than stashed on the disguise at spawn. The
 * disguise is an Item whose contents would have to be moved onto a live Monster at
 * reveal, and {@code ChunkData.MonsterData} does not persist monster inventory -- so
 * loot carried by a mimic would evaporate if the chunk unloaded mid-fight. Rolling at
 * death sidesteps that entirely, and nothing is lost: the player never sees the
 * contents before the mimic is dead.
 *
 * <p>The payout is deliberately better than the chest it was impersonating. An ordinary
 * chest rolls the container pool once and can come up empty under Doom decay; a mimic
 * that has just cost the player a free hit and a fight should be worth strictly more,
 * or the correct play becomes "stop opening chests".
 */
public final class MimicHoard {

    /** Most items a mimic can have swallowed. */
    public static final int MAX_ITEMS = 3;

    private MimicHoard() {
    }

    /** How many items this mimic was sitting on: always at least one. */
    public static int rollItemCount(Random random) {
        return 1 + random.nextInt(MAX_ITEMS);
    }

    /**
     * Template registry for the loot spawner, built once.
     *
     * <p>Templates are immutable and there is one ItemDataManager per run, so rebuilding
     * this on every mimic death walked the whole ItemType enum for nothing. Keyed by
     * manager identity so a reload picks up a fresh one.
     */
    private static ItemDataManager cachedRegistryOwner;
    private static Map<String, ItemTemplate> cachedRegistry;

    private static synchronized Map<String, ItemTemplate> registryFor(ItemDataManager itemDataManager) {
        if (cachedRegistryOwner == itemDataManager && cachedRegistry != null) {
            return cachedRegistry;
        }

        Map<String, ItemTemplate> registry = new HashMap<>();
        for (ItemType type : ItemType.values()) {
            try {
                ItemTemplate template = itemDataManager.getTemplate(type);
                if (template != null) {
                    registry.put(type.name(), template);
                }
            } catch (Exception ignored) {
                // No template for this type: simply not a loot candidate.
            }
        }

        cachedRegistryOwner = itemDataManager;
        cachedRegistry = registry;
        return registry;
    }

    /**
     * Generates the loot a dead mimic spills, drawn from the container-tier pool so it
     * reads as the contents of the chest it was pretending to be.
     *
     * <p>Positions are left at the origin; the caller places each item.
     */
    public static List<Item> roll(ItemDataManager itemDataManager, AssetManager assetManager,
            int depth, int playerLevel, int luck, Random random) {
        List<Item> hoard = new ArrayList<>();
        if (itemDataManager == null) {
            return hoard;
        }

        ItemSpawner spawner = new ItemSpawner(registryFor(itemDataManager), new NetHackRNG(random));
        // isContainer = true is what routes this through the container loot weights
        // (potions, scrolls, gems and the like) rather than the floor-litter table.
        SpawnContext ctx = new SpawnContext(depth, playerLevel, false, Alignment.NEUTRAL, luck,
                new HashSet<>(), true);

        int count = rollItemCount(random);
        for (int i = 0; i < count; i++) {
            Map.Entry<String, ItemTemplate> result = spawner.spawnItem(ctx);
            if (result == null) {
                continue;
            }

            ItemType type;
            try {
                type = ItemType.valueOf(result.getKey());
            } catch (Exception e) {
                continue;
            }

            // A mimic's hoard goes through ItemSpawner directly, so the depth gate that
            // SpawnManager applies to floor loot has to be applied here too -- otherwise
            // a depth-1 mimic could disgorge a musket.
            if (!FirearmSpawnRule.canSpawnAtDepth(type.name(), depth)) {
                continue;
            }

            ItemVariant variant = itemDataManager.getRandomVariantForItem(type, depth);
            if (variant == null) {
                continue;
            }

            Item item = itemDataManager.createItem(type, 0, 0, variant.color, assetManager);
            if (item != null) {
                hoard.add(item);
            }
        }

        return hoard;
    }
}
