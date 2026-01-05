package com.bpm.minotaur.gamedata.item;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.ObjectMap;
import com.bpm.minotaur.gamedata.item.Item.ItemType;
import com.bpm.minotaur.managers.DiscoveryManager;
import com.bpm.minotaur.managers.UnlockManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class ItemDataManager {

    private final ObjectMap<ItemType, ItemTemplate> itemTemplates;

    private DiscoveryManager discoveryManager;
    private final Random random = new Random();

    public ItemDataManager() {
        this.itemTemplates = new ObjectMap<>();
    }

    /**
     * Sets the DiscoveryManager. This must be called after DiscoveryManager is
     * constructed in GameScreen, but before any items are created.
     */
    public void setDiscoveryManager(DiscoveryManager discoveryManager) {
        this.discoveryManager = discoveryManager;
    }

    public void reloadAll() {
        // Clear and reload all data
        // load() re-initializes the map, so it effectively clears it.
        load();
        loadWeapons();
        loadArmor();
    }

    public void load() {
        // If map exists, clear it instead of newing
        itemTemplates.clear();
        Json json = new Json();
        FileHandle file = Gdx.files.internal("data/items.json");
        // ... rest of load() implementation ...
        JsonValue root = new JsonReader().parse(file);

        for (ItemType type : ItemType.values()) {
            JsonValue data = root.get(type.name());

            if (data != null) {
                ItemTemplate template = json.readValue(ItemTemplate.class, data);

                // FIX: Set default probability if missing to ensure they spawn
                if (template.probability == 0) {
                    template.probability = 10;
                }

                itemTemplates.put(type, template);
            } else {
                // Skip error logging for procedural/generated items
                String name = type.name();
                boolean isProcedural = name.startsWith("SCROLL_") ||
                        name.startsWith("WAND_") ||
                        name.startsWith("POTION_") ||
                        name.equals("CORPSE") ||
                        name.equals("MEAT") ||
                        name.equals("COOKED_MEAT") ||
                        name.equals("BONE") ||
                        name.equals("CHITIN") ||
                        name.equals("TOOTH") ||
                        name.equals("CLAW") ||
                        name.equals("NAIL") ||
                        name.equals("BLOOD_VIAL") ||
                        name.equals("ORGAN") ||
                        name.equals("LEATHER_SCRAP") ||
                        name.equals("MYSTERIOUS_PORTAL") || // defined in code or json?
                        name.equals("MONSTER_EYE");

                if (!isProcedural) {
                    Gdx.app.error("ItemDataManager", "No JSON data found for item type: " + type.name());
                }
            }
        }

        if (itemTemplates.containsKey(ItemType.BOW)) {
            itemTemplates.get(ItemType.BOW).unlockId = "item_bow";
            Gdx.app.log("ItemDataManager", "TESTING: Locked BOW with id 'item_bow'");
        }

        // --- Generate Randomized Templates (Scrolls/Wands) ---
        // If they don't exist in JSON, we create them from base templates.

        // Scrolls
        ItemTemplate baseScroll = itemTemplates.get(ItemType.SCROLL);
        if (baseScroll != null) {
            for (ItemType type : ItemType.values()) {
                if (type.name().startsWith("SCROLL_") && !itemTemplates.containsKey(type)) {
                    ItemTemplate newTemplate = new ItemTemplate();
                    // Copy fields (simplified, assuming shallow copy where safe or primitives)
                    newTemplate.friendlyName = "Labeled Scroll"; // Base appearance name
                    newTemplate.description = baseScroll.description; // "A scroll with strange writing."
                    newTemplate.texturePath = baseScroll.texturePath; // Same sprite for now
                    newTemplate.spriteData = baseScroll.spriteData;
                    newTemplate.baseValue = baseScroll.baseValue;
                    newTemplate.isUsable = true;
                    newTemplate.isScrollAppearance = true;
                    newTemplate.scale = baseScroll.scale;

                    itemTemplates.put(type, newTemplate);
                }
            }
        }

        // Wands (Use Stick as base if Wand missing, or just assume Stick)
        // Actually, let's assume STICK is a good base for Wands
        ItemTemplate baseWand = itemTemplates.get(ItemType.STICK);
        if (baseWand != null) {
            for (ItemType type : ItemType.values()) {
                if (type.name().startsWith("WAND_") && !itemTemplates.containsKey(type)) {
                    ItemTemplate newTemplate = new ItemTemplate();
                    newTemplate.friendlyName = "Wand";
                    newTemplate.description = "A smooth stick with magical energy.";
                    newTemplate.texturePath = baseWand.texturePath;
                    newTemplate.spriteData = baseWand.spriteData; // Reuse stick sprite
                    newTemplate.baseValue = 100;
                    newTemplate.isUsable = true;
                    newTemplate.isWandAppearance = true;
                    newTemplate.scale = baseWand.scale;
                    itemTemplates.put(type, newTemplate);
                }
            }

            // --- Generic WAND Template ---
            if (!itemTemplates.containsKey(ItemType.WAND)) {
                ItemTemplate newTemplate = new ItemTemplate();
                newTemplate.friendlyName = "Wand";
                newTemplate.description = "A smooth stick with magical energy.";
                newTemplate.texturePath = baseWand.texturePath;
                newTemplate.spriteData = baseWand.spriteData;
                newTemplate.baseValue = 100;
                newTemplate.isUsable = true;
                newTemplate.isWandAppearance = true;
                newTemplate.scale = baseWand.scale;
                itemTemplates.put(ItemType.WAND, newTemplate);
            }
        }

        // Rings (Ensure they are marked as ring appearances)
        ItemTemplate baseRing = itemTemplates.get(ItemType.SMALL_RING);
        if (baseRing != null) {
            List<ItemType> ringTypes = new ArrayList<>();
            ringTypes.add(ItemType.SMALL_RING);
            ringTypes.add(ItemType.LARGE_RING);
            ringTypes.add(ItemType.RING_BLUE);
            ringTypes.add(ItemType.RING_PINK);
            ringTypes.add(ItemType.RING_PURPLE);

            for (ItemType type : ringTypes) {
                ItemTemplate t = itemTemplates.get(type);
                if (t != null) {
                    t.isRingAppearance = true;
                    t.isRing = true;
                } else {
                    // Create if missing (e.g. if JSON only has SMALL_RING)
                    ItemTemplate newTemplate = new ItemTemplate();
                    newTemplate.friendlyName = "Ring";
                    newTemplate.description = "A ring.";
                    newTemplate.texturePath = baseRing.texturePath; // Placeholder
                    newTemplate.spriteData = baseRing.spriteData;
                    newTemplate.baseValue = 100;
                    newTemplate.isUsable = false; // Rings are worn, not used? Or Apply?
                    newTemplate.isRing = true;
                    newTemplate.isRingAppearance = true;
                    newTemplate.scale = baseRing.scale;
                    itemTemplates.put(type, newTemplate);
                }
            }
        }

        initializeMissingTemplates();

        Gdx.app.log("ItemDataManager", "Loaded " + itemTemplates.size + " item templates.");
    }

    /**
     * Loads weapon definitions from weapons.json.
     * Called explicitly generally from Tarmin2.java.
     */
    public void loadWeapons() {
        Json json = new Json();
        FileHandle file = Gdx.files.internal("data/weapons.json");

        if (!file.exists()) {
            Gdx.app.error("ItemDataManager", "weapons.json missing!");
            return;
        }

        JsonValue root = new JsonReader().parse(file);
        int loadedCount = 0;

        // Iterate over the JSON keys explicitly efficiently
        for (JsonValue entry = root.child; entry != null; entry = entry.next) {
            String typeName = entry.name;
            try {
                ItemType type = ItemType.valueOf(typeName);
                ItemTemplate template = json.readValue(ItemTemplate.class, entry);

                // FIX: Set default probability if missing to ensure they spawn
                if (template.probability == 0) {
                    template.probability = 10;
                }

                itemTemplates.put(type, template);
                loadedCount++;
            } catch (IllegalArgumentException e) {
                Gdx.app.error("ItemDataManager", "Skipping unknown weapon type in JSON: " + typeName);
            }
        }

        Gdx.app.log("ItemDataManager", "Loaded " + loadedCount + " new weapons.");
    }

    /**
     * Loads armor definitions from armor.json.
     */
    public void loadArmor() {
        Json json = new Json();
        FileHandle file = Gdx.files.internal("data/armor.json");

        if (!file.exists()) {
            Gdx.app.error("ItemDataManager", "armor.json missing!");
            return;
        }

        JsonValue root = new JsonReader().parse(file);
        int loadedCount = 0;

        for (JsonValue entry = root.child; entry != null; entry = entry.next) {
            String typeName = entry.name;
            try {
                ItemType type = ItemType.valueOf(typeName);
                ItemTemplate template = json.readValue(ItemTemplate.class, entry);

                // FIX: Set default probability if missing
                if (template.probability == 0) {
                    template.probability = 10;
                }

                itemTemplates.put(type, template);
                if (type == ItemType.LEATHER_BOOTS) {
                    Gdx.app.log("IDM_DEBUG", "Loaded LEATHER_BOOTS. isBoots=" + template.isBoots);
                }
                loadedCount++;
            } catch (IllegalArgumentException e) {
                Gdx.app.error("ItemDataManager", "Skipping unknown armor type in JSON: " + typeName);
            }
        }

        Gdx.app.log("ItemDataManager", "Loaded " + loadedCount + " new armor items.");
    }

    public ItemTemplate getTemplate(ItemType type) {
        ItemTemplate template = itemTemplates.get(type);
        if (template == null) {
            throw new NullPointerException("No template loaded for item type: " + type);
        }
        return template;
    }

    public void queueAssets(AssetManager assetManager) {
        // Queue Debris Atlas
        assetManager.load("packed/debris.atlas", TextureAtlas.class);
        // Queue Items Atlas
        assetManager.load("packed/items.atlas", TextureAtlas.class);
        // Queue Armor Atlas
        assetManager.load("packed/armor.atlas", TextureAtlas.class);

        for (ItemTemplate template : itemTemplates.values()) {
            // Load 2D Texture
            if (template.texturePath != null && !template.texturePath.isEmpty()) {
                // FORCE LOAD: We need standalone textures for EntityRenderer's current logic
                // if (!template.texturePath.contains("images/debris") &&
                // !template.texturePath.contains("images/items")) {
                assetManager.load(template.texturePath, Texture.class);
                // }
            }

            // Load 3D Model
            if (template.modelPath != null && !template.modelPath.isEmpty()) {
                assetManager.load(template.modelPath, com.badlogic.gdx.graphics.g3d.Model.class);
            }
        }
    }

    /**
     * Creates a new Item instance based on its type.
     * This now handles the special logic for randomized potions.
     */
    public Item createItem(ItemType type, int x, int y, ItemColor color, AssetManager assetManager) {

        Gdx.app.log("ItemDataManager [DEBUG]", "createItem called for: " + type.name());

        // --- FIX: Randomize Generic SCROLL ---
        if (type == ItemType.SCROLL) {
            ItemType[] scrolls = {
                    ItemType.SCROLL_A, ItemType.SCROLL_B, ItemType.SCROLL_C, ItemType.SCROLL_D,
                    ItemType.SCROLL_E, ItemType.SCROLL_F, ItemType.SCROLL_G, ItemType.SCROLL_H
            };
            type = scrolls[random.nextInt(scrolls.length)];
            Gdx.app.log("ItemDataManager", "Converted generic SCROLL to " + type.name());
        }
        // -------------------------------------

        ItemTemplate template = getTemplate(type);

        if (template.unlockId != null && !UnlockManager.getInstance().isUnlocked(template.unlockId)) {
            Gdx.app.log("ItemDataManager",
                    "Item locked: " + type.name() + " (Requires: " + template.unlockId + "). Spawning fallback.");
            // Recursively create a fallback item (AXE) which we assume is unlocked.
            // Ensure we don't infinitely recurse if AXE is also locked (it shouldn't be).
            if (type != ItemType.AXE) {
                return createItem(ItemType.AXE, x, y, ItemColor.GRAY, assetManager);
            }
        }

        // Standard item creation
        Item item = new Item(type, x, y, color, this, assetManager);

        // --- NEW: THEMED DICE INTEGRATION ---
        // Basic mapping for now. Ideally this is data-driven in items.json,
        // but for this phase we hardcode the mapping to test the 10 themes.
        com.bpm.minotaur.gamedata.dice.Die themedDie = null;

        // Map ItemTypes/Names to Dice Themes
        // 1. Rusty Iron Die
        if (type == ItemType.KNIFE || type == ItemType.AXE || type == ItemType.RUSTY_SWORD) { // Basic weapons
            themedDie = com.bpm.minotaur.gamedata.dice.DiceFactory.create("Rusty Iron Die");
        }
        // 2. Warrior's Red Die
        else if (type == ItemType.SWORD || type == ItemType.TWO_HANDED_SWORD) {
            themedDie = com.bpm.minotaur.gamedata.dice.DiceFactory.create("Warrior's Red Die");
        }
        // 3. Guardian's Steel Die
        else if (type == ItemType.SMALL_SHIELD || type == ItemType.LARGE_SHIELD) {
            themedDie = com.bpm.minotaur.gamedata.dice.DiceFactory.create("Guardian's Steel Die");
        }
        // 4. Archer's Precision Die
        else if (type == ItemType.BOW || type == ItemType.CROSSBOW) {
            themedDie = com.bpm.minotaur.gamedata.dice.DiceFactory.create("Archer's Precision Die");
        }
        // 5. Pyromancer's Ember Die
        else if (type == ItemType.SCROLL && template.friendlyName.contains("Fire")) { // Requires precise naming or
                                                                                      // assumption
            themedDie = com.bpm.minotaur.gamedata.dice.DiceFactory.create("Pyromancer's Ember Die");
        }
        // 6. Frostbound Die
        else if (type == ItemType.SCROLL && template.friendlyName.contains("Ice")) {
            themedDie = com.bpm.minotaur.gamedata.dice.DiceFactory.create("Frostbound Die");
        }
        // 7. Priest's Holy Die
        else if (type == ItemType.AMULET || type == ItemType.SCROLL && template.friendlyName.contains("Heal")) {
            themedDie = com.bpm.minotaur.gamedata.dice.DiceFactory.create("Priest's Holy Die");
        }
        // 8. Rogue's Shadow Die
        else if (type == ItemType.DART || type == ItemType.RING_PURPLE) {
            themedDie = com.bpm.minotaur.gamedata.dice.DiceFactory.create("Rogue's Shadow Die");
        }
        // 9. Necromancer's Bone Die
        else if (type == ItemType.BONES || type == ItemType.SKULL) {
            themedDie = com.bpm.minotaur.gamedata.dice.DiceFactory.create("Necromancer's Bone Die");
        }
        // 10. Gambler's Gold Die
        else if (type == ItemType.COINS || type == ItemType.RING_GOLD) {
            themedDie = com.bpm.minotaur.gamedata.dice.DiceFactory.create("Gambler's Gold Die");
        }

        if (themedDie != null) {
            item.setGrantedDie(themedDie);
            // Gdx.app.log("ItemDataManager", "Granted " + themedDie.getName() + " to " +
            // item.getName());
        }
        // ------------------------------------

        // --- NEW POTION LOGIC ---
        if (template.isPotionAppearance) {

            Gdx.app.log("ItemDataManager [DEBUG]", "Type is a potion appearance.");
            if (discoveryManager == null) {
                Gdx.app.log("ItemDataManager [DEBUG]", "ERROR: DiscoveryManager is NULL at item creation time!");
                return item; // Return a "dud" potion
            }

            // 1. Get the randomized effect from the manager
            PotionEffectType effect = discoveryManager.getPotionEffect(type);

            Gdx.app.log("ItemDataManager [DEBUG]",
                    "Got effect from DiscoveryManager: " + (effect == null ? "NULL" : effect.name()));

            if (effect != null) {
                // 2. Check if this effect is already identified
                boolean isIdentified = discoveryManager.isPotionIdentified(effect);

                Gdx.app.log("ItemDataManager [DEBUG]",
                        "Setting trueEffect (" + effect.name() + ") and identified (" + isIdentified + ") on item.");

                // 3. Set the hidden (true) properties on the item instance
                item.setTrueEffect(effect);
                item.setIdentified(isIdentified);

                // 4. Set the visible name
                if (isIdentified) {
                    item.setName("Potion of " + effect.getBaseName());
                } else {
                    item.setName(template.friendlyName);
                }

            } else {
                // This is a potion appearance with no matching effect (e.g., 10 appearances, 8
                // effects)
                Gdx.app.log("ItemDataManager [DEBUG]",
                        "ERROR: Effect was NULL. This potion (" + type.name() + ") will be a dud.");
            }
        }

        // --- NEW SCROLL LOGIC ---
        if (template.isScrollAppearance) {
            if (discoveryManager != null) {
                ScrollEffectType effect = discoveryManager.getScrollEffect(type);
                if (effect != null) {
                    item.setScrollEffect(effect);
                    if (discoveryManager.isScrollIdentified(effect)) {
                        item.setName("Scroll of " + effect.getBaseName());
                        item.setIdentified(true);
                    } else {
                        item.setName("Labeled Scroll");
                    }
                    // TODO: We need a way to set category if it is not mutable.
                    // For now, removing the invalid setCategory call to fix syntax.
                    // implementation_plan.md noted checking ItemDataManager OR items.json.
                    // If we can't set it here, we must rely on items.json having the correct
                    // category.
                }
            }
        }

        // --- NEW WAND LOGIC ---
        if (template.isWandAppearance) {
            if (discoveryManager != null) {

                WandEffectType effect = discoveryManager.getWandEffect(type);
                if (effect != null) {
                    item.setWandEffect(effect);
                    // Wands start with random charges?
                    item.setCharges(random.nextInt(6) + 3); // 3 to 8 charges

                    boolean isIdentified = discoveryManager.isWandIdentified(effect);
                    item.setIdentified(isIdentified);
                    if (isIdentified) {
                        item.setName("Wand of " + effect.getBaseName());
                    } else {
                        item.setName("Wand");
                    }
                }
            }
        }

        // --- NEW RING LOGIC ---
        if (template.isRingAppearance) {
            if (discoveryManager != null) {
                RingEffectType effect = discoveryManager.getRingEffect(type);
                if (effect != null) {
                    item.setRingEffect(effect);
                    boolean isIdentified = discoveryManager.isRingIdentified(effect);
                    item.setIdentified(isIdentified);
                    if (isIdentified) {
                        item.setName("Ring of " + effect.getBaseName());
                    } else {
                        // Default name from template (e.g. "Small Ring")
                    }
                }
            }
        }

        // --- NEW: Apply Modifiers based on Item Color/Rarity ---
        // REMOVED: Modifiers are now handled by SpawnManager to ensure
        // level-appropriate loot.
        // We do NOT want to force modifiers based on color here, as it overrides the
        // LootTable logic.

        return item;
    }

    /**
     * Gets a list of all ItemTypes that are flagged as potion appearances.
     * Used by PotionManager to build its initial random map.
     */
    public List<ItemType> getAllPotionAppearanceTypes() {
        List<ItemType> potionTypes = new ArrayList<>();
        for (ItemType type : itemTemplates.keys()) {
            if (itemTemplates.get(type).isPotionAppearance) {
                potionTypes.add(type);
            }
        }
        return potionTypes;
    }

    /**
     * Selects a valid ItemVariant (color/tier) for a given item type at a specific
     * level.
     * 
     * @param type  The item type (e.g., BOW).
     * @param level The current dungeon level.
     * @return A valid ItemVariant, or null if none are found.
     */
    public ItemVariant getRandomVariantForItem(ItemType type, int level) {
        ItemTemplate template = getTemplate(type);
        if (template.variants == null || template.variants.isEmpty()) {
            Gdx.app.error("ItemDataManager", "No 'variants' defined for item type: " + type.name());
            return null;
        }

        // 1. Filter variants by level
        List<ItemVariant> validVariants = new ArrayList<>();
        int totalWeight = 0;

        for (ItemVariant variant : template.variants) {
            if (level >= variant.minLevel && level <= variant.maxLevel) {

                // --- FIXED: Balance Safety Clamp ---
                // variant.color is already an ItemColor Enum. No need for valueOf().
                if (variant.color != null && level < 5 && variant.color.getMultiplier() > 1.4f) {
                    // Skip this OP variant
                    continue;
                }
                // ---------------------------------

                validVariants.add(variant);
                totalWeight += variant.weight;
            }
        }

        // 2. Handle no valid variants
        if (validVariants.isEmpty()) {
            // Try to find ANY variant that fits the level range, ignoring the safety clamp
            for (ItemVariant variant : template.variants) {
                if (level >= variant.minLevel && level <= variant.maxLevel) {
                    return variant;
                }
            }
            // If still nothing, return first available
            if (!template.variants.isEmpty())
                return template.variants.get(0);

            Gdx.app.error("ItemDataManager", "No valid variants found for " + type.name() + " at level " + level);
            return null;
        }

        // 3. Perform weighted random selection
        int randomWeight = random.nextInt(totalWeight);
        int currentWeight = 0;

        for (ItemVariant variant : validVariants) {
            currentWeight += variant.weight;
            if (randomWeight < currentWeight) {
                return variant;
            }
        }

        return validVariants.get(0);
    }

    private void initializeMissingTemplates() {
        // --- NEW: Generate Templates for Corpses & Resources if missing ---
        if (!itemTemplates.containsKey(ItemType.CORPSE)) {
            ItemTemplate t = new ItemTemplate();
            t.friendlyName = "Corpse";
            t.description = "The remains of a creature.";
            t.baseValue = 0;
            ItemTemplate bones = itemTemplates.get(ItemType.BONES);
            if (bones != null) {
                t.texturePath = bones.texturePath;
                t.spriteData = bones.spriteData;
                t.scale = bones.scale;
            } else {
                t.friendlyName = "Corpse (Error)";
                t.spriteData = new String[] { "??", "??" };
                t.scale = createDefaultScale();
            }
            itemTemplates.put(ItemType.CORPSE, t);
        }

        // Resources
        createResourceTemplate(ItemType.MEAT, "Meat", "Raw meat.", ItemType.FOOD);
        createResourceTemplate(ItemType.COOKED_MEAT, "Cooked Meat", "Savory cooked meat.", ItemType.FOOD);
        createResourceTemplate(ItemType.BONE, "Bone", "A sturdy bone.", ItemType.BONES);
        createResourceTemplate(ItemType.CHITIN, "Chitin", "Hard insect carapace.", ItemType.LARGE_SHIELD);
        createResourceTemplate(ItemType.TOOTH, "Tooth", "A sharp tooth.", ItemType.DART);
        createResourceTemplate(ItemType.CLAW, "Claw", "A sharp claw.", ItemType.KNIFE);
        createResourceTemplate(ItemType.NAIL, "Nail", "A rusty nail.", ItemType.BENT_NAIL);
        createResourceTemplate(ItemType.BLOOD_VIAL, "Blood Vial", "A vial of blood.", ItemType.POTION_BLUE);
        createResourceTemplate(ItemType.ORGAN, "Organ", "Typically useful organ.", ItemType.FOOD);
        createResourceTemplate(ItemType.LEATHER_SCRAP, "Leather Scrap", "Scrap of hide.", ItemType.DIRTY_CLOTH);

        // Debris fallback (just in case)
        createResourceTemplate(ItemType.STICK, "Stick", "A wooden stick.", null);
        createResourceTemplate(ItemType.SMALL_ROCK, "Rock", "A small rock.", null);
        createResourceTemplate(ItemType.FLINT_SHARD, "Flint", "Sharp stone.", ItemType.SMALL_ROCK);
        createResourceTemplate(ItemType.BROKEN_HILT, "Hilt", "Broken hilt.", ItemType.KNIFE);
        createResourceTemplate(ItemType.METAL_SCRAP, "Scrap", "Metal scrap.", ItemType.AXE);
    }

    private void createResourceTemplate(ItemType type, String name, String desc, ItemType baseType) {
        if (!itemTemplates.containsKey(type)) {
            ItemTemplate t = new ItemTemplate();
            t.friendlyName = name;
            t.description = desc;
            t.baseValue = 1;

            ItemTemplate base = (baseType != null) ? itemTemplates.get(baseType) : null;
            if (base != null) {
                t.texturePath = base.texturePath;
                t.spriteData = base.spriteData;
                t.scale = base.scale;

                // Copy Properties
                t.isFood = base.isFood;
                t.isTreasure = base.isTreasure;
                t.isWeapon = base.isWeapon;
                t.isArmor = base.isArmor;
                t.isPotion = base.isPotion;
                t.isUsable = base.isUsable;
                t.baseValue = base.baseValue; // Override base value? No, passed value implies 1. Use base if better?
                // The method sets baseValue to 1. Let's keep that default but maybe allow
                // override.
            } else {
                t.spriteData = new String[] { "??", "??" }; // Stub
                t.scale = createDefaultScale();
            }
            itemTemplates.put(type, t);
        }
    }

    private ItemTemplate.Vector2Wrapper createDefaultScale() {
        ItemTemplate.Vector2Wrapper v = new ItemTemplate.Vector2Wrapper();
        v.x = 1.0f;
        v.y = 1.0f;
        return v;
    }
}
