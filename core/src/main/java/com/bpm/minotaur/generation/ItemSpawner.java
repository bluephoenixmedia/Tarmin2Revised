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

        // Dynamic Spell Scroll Generation
        if (selectedCategory == ItemCategory.SCROLL) {
            Map.Entry<String, ItemTemplate> dynamicScroll = spawnDynamicSpellScroll(ctx != null ? ctx.edl() : 1);
            if (dynamicScroll != null) {
                return dynamicScroll;
            }
        }

        // Dynamic Spell Book Generation
        if (selectedCategory == ItemCategory.BOOK) {
            Map.Entry<String, ItemTemplate> dynamicBook = spawnDynamicSpellBook(ctx != null ? ctx.edl() : 1);
            if (dynamicBook != null) {
                return dynamicBook;
            }
        }

        // Step 2: Specific Item Selection
        return selectSpecificItem(selectedCategory);
    }

    /**
     * Filters out spells belonging to an Arcane Attunement circle that hasn't been
     * unsealed at the Shelter Altar yet. Spells outside the attunement circles are
     * always eligible.
     */
    private List<com.bpm.minotaur.gamedata.spells.SpellTemplate> filterUnsealed(
            List<com.bpm.minotaur.gamedata.spells.SpellTemplate> spells) {
        if (spells == null) return null;
        com.bpm.minotaur.gamedata.progression.ShelterAltar altar = com.bpm.minotaur.gamedata.progression.ShelterAltar.getInstance();
        List<com.bpm.minotaur.gamedata.spells.SpellTemplate> result = new ArrayList<>();
        for (com.bpm.minotaur.gamedata.spells.SpellTemplate spell : spells) {
            if (!altar.isSpellSealed(spell.id)) {
                result.add(spell);
            }
        }
        return result;
    }

    /**
     * Spawns a dynamic spell scroll from spells.json matching the floor's EDL bracket.
     */
    public Map.Entry<String, ItemTemplate> spawnDynamicSpellScroll(int edl) {
        int spellLevel;
        if (edl <= 3) spellLevel = 1;
        else if (edl <= 6) spellLevel = 2;
        else if (edl <= 9) spellLevel = 3;
        else if (edl <= 12) spellLevel = 4;
        else spellLevel = 5;

        com.bpm.minotaur.gamedata.spells.SpellDataManager sdm = com.bpm.minotaur.gamedata.spells.SpellDataManager.getInstance();
        List<com.bpm.minotaur.gamedata.spells.SpellTemplate> spells = filterUnsealed(sdm.getSpellsByLevel(spellLevel));
        if (spells == null || spells.isEmpty()) {
            spells = filterUnsealed(sdm.getSpellsByLevel(1));
        }
        if (spells == null || spells.isEmpty()) {
            return null;
        }

        com.bpm.minotaur.gamedata.spells.SpellTemplate spell = spells.get(rng.rn2(spells.size()));

        ItemTemplate scrollTmpl = new ItemTemplate();
        scrollTmpl.friendlyName = "Scroll of " + spell.name + " (" + spell.id + ")";
        scrollTmpl.description = spell.description != null ? spell.description : "A magical scroll that can be transcribed into your spellbook.";
        scrollTmpl.isUsable = true;
        scrollTmpl.isScrollAppearance = true;
        scrollTmpl.baseValue = 50 * Math.max(1, spell.level);

        ItemTemplate baseScroll = registry.get("SCROLL");
        if (baseScroll != null) {
            scrollTmpl.texturePath = baseScroll.texturePath;
            scrollTmpl.spriteData = baseScroll.spriteData;
        }

        return Map.entry("SCROLL", scrollTmpl);
    }

    /**
     * Spawns a dynamic spell book from spells.json matching the floor's EDL bracket.
     */
    public Map.Entry<String, ItemTemplate> spawnDynamicSpellBook(int edl) {
        int spellLevel;
        if (edl <= 3) spellLevel = 1;
        else if (edl <= 6) spellLevel = 2;
        else if (edl <= 9) spellLevel = 3;
        else if (edl <= 12) spellLevel = 4;
        else spellLevel = 5;

        com.bpm.minotaur.gamedata.spells.SpellDataManager sdm = com.bpm.minotaur.gamedata.spells.SpellDataManager.getInstance();
        List<com.bpm.minotaur.gamedata.spells.SpellTemplate> spells = filterUnsealed(sdm.getSpellsByLevel(spellLevel));
        if (spells == null || spells.isEmpty()) {
            spells = filterUnsealed(sdm.getSpellsByLevel(1));
        }
        if (spells == null || spells.isEmpty()) {
            return null;
        }

        com.bpm.minotaur.gamedata.spells.SpellTemplate spell = spells.get(rng.rn2(spells.size()));

        ItemTemplate bookTmpl = new ItemTemplate();
        bookTmpl.friendlyName = "Spellbook: " + spell.name + " (" + spell.id + ")";
        bookTmpl.description = spell.description != null ? spell.description : "An arcane tome detailing the principles of " + spell.name + ".";
        bookTmpl.isUsable = true;
        bookTmpl.isWeapon = false;
        bookTmpl.spellId = spell.id;
        bookTmpl.baseValue = 100 * Math.max(1, spell.level);

        ItemTemplate baseBook = registry.get("BOOK");
        if (baseBook != null) {
            bookTmpl.texturePath = baseBook.texturePath;
            bookTmpl.spriteData = baseBook.spriteData;
            bookTmpl.scale = baseBook.scale;
        }

        return Map.entry("BOOK", bookTmpl);
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
            weights.put(ItemCategory.AMMUNITION, 12);

        } else if (ctx.isGehennom()) {
            weights.put(ItemCategory.WAR_WEAPON, 10);
            weights.put(ItemCategory.SPIRITUAL_WEAPON, 10); // 50/50 Split 20%
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
            weights.put(ItemCategory.AMMUNITION, 10);
            // weights.put(ItemCategory.GOLD, 0);
        } else {
            // Default Context - Balanced for "More Items/Weapons/Armor"
            weights.put(ItemCategory.FOOD, 30); // 20 -> 30
            weights.put(ItemCategory.POTION, 20); // 16 -> 20
            weights.put(ItemCategory.SCROLL, 25); // 16 -> 25 (Scrolls requested)
            weights.put(ItemCategory.WAR_WEAPON, 15); // 50/50 split
            weights.put(ItemCategory.SPIRITUAL_WEAPON, 15);// 50/50 split
            weights.put(ItemCategory.ARMOR, 20); // 10 -> 20 (Armor requested)
            weights.put(ItemCategory.WAND, 15); // 4 -> 15 (Wands requested)

            // Standard/Rare
            weights.put(ItemCategory.TOOL, 8);
            weights.put(ItemCategory.GEM, 8);
            weights.put(ItemCategory.BOOK, 8); // 4 -> 8
            weights.put(ItemCategory.RING, 6); // 3 -> 6
            weights.put(ItemCategory.AMULET, 2); // 1 -> 2
            weights.put(ItemCategory.AMMUNITION, 15); // Arrows, bolts, shot
        }

        // Subsistence shift: If ctx.edl() >= 6, drop prepared food spawn weight by 80%
        if (ctx.edl() >= 6) {
            int currentFood = weights.getOrDefault(ItemCategory.FOOD, 0);
            weights.put(ItemCategory.FOOD, Math.max(1, (int) (currentFood * 0.20f)));
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

            if (t.unlockGated && !com.bpm.minotaur.managers.UnlockManager.getInstance().isUnlocked(entry.getKey())) {
                continue; // Skip locked items
            }

            if (t.probability > 0 && isCategoryMatch(t, category)) {
                int weight = t.probability;

                if (t.unlockGated) {
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
            case AMMUNITION:
                return t.isAmmunition || isAmmunitionFallback(t);
            case WAR_WEAPON:
                return t.isWeapon && !t.isAmmunition && !isAmmunitionFallback(t) && !"SPIRITUAL".equalsIgnoreCase(t.damageType);
            case SPIRITUAL_WEAPON:
                return (t.isWeapon && "SPIRITUAL".equalsIgnoreCase(t.damageType))
                        || (t.friendlyName != null && (t.friendlyName.contains("Cross") || t.friendlyName.contains("Book")
                                || t.friendlyName.contains("Spiritual") || t.friendlyName.contains("Holy")));
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
            case GEM:
                return t.isGem;
            case BOOK:
                return "BOOK".equals(t.category) || (t.friendlyName != null && t.friendlyName.contains("Book") && !t.isWeapon);
            default:
                return false;
        }
    }

    private boolean isAmmunitionFallback(ItemTemplate t) {
        if (t == null) return false;
        if (t.friendlyName != null) {
            String name = t.friendlyName.toLowerCase();
            return name.contains("arrow") || name.contains("quarrel") || name.contains("quiver")
                    || name.contains("bullet") || name.contains("dart");
        }
        return false;
    }
}
