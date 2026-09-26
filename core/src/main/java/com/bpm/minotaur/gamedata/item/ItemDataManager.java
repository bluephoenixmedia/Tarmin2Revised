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
import com.bpm.minotaur.gamedata.ModifierType;
import com.bpm.minotaur.managers.DiscoveryManager;
import com.bpm.minotaur.managers.UnlockManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class ItemDataManager {

    private final ObjectMap<ItemType, ItemTemplate> itemTemplates;

    private static final java.util.Map<String, ItemType> ITEM_TYPE_MAP = new java.util.HashMap<>();
    static {
        for (ItemType t : ItemType.values()) {
            ITEM_TYPE_MAP.put(t.name(), t);
        }
    }

    public static ItemType getSafeItemType(String name) {
        if (name == null) return null;
        return ITEM_TYPE_MAP.get(name);
    }

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
        json.setIgnoreUnknownFields(true);

        // Live Reload: Check for source file first
        FileHandle file = Gdx.files.local("assets/data/items.json");
        if (!file.exists()) {
            file = Gdx.files.internal("data/items.json");
        } else {
            Gdx.app.log("ItemDataManager", "Live Reloading items.json from source.");
        }
        // ... rest of load() implementation ...
        JsonValue root = new JsonReader().parse(file);

        for (JsonValue entry = root.child; entry != null; entry = entry.next) {
            ItemType type = getSafeItemType(entry.name);
            if (type != null) {
                ItemTemplate template = json.readValue(ItemTemplate.class, entry);
                if (template.probability == 0) {
                    template.probability = 10;
                }
                if (type == ItemType.QUIVER) {
                    template.isAmmunition = true;
                    if (template.probability < 20) {
                        template.probability = 20;
                    }
                }
                itemTemplates.put(type, template);
            }
        }


        initializeMissingTemplates();

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

        // Wands: use the WAND template from items.json as the base for WAND_A..H.
        // STICK is not defined in items.json so it was never a valid base.
        ItemTemplate baseWand = itemTemplates.get(ItemType.WAND);
        if (baseWand != null) {
            for (ItemType type : ItemType.values()) {
                if (type.name().startsWith("WAND_") && !itemTemplates.containsKey(type)) {
                    ItemTemplate newTemplate = new ItemTemplate();
                    newTemplate.friendlyName = "Wand";
                    newTemplate.description = "A smooth stick with magical energy.";
                    newTemplate.texturePath = baseWand.texturePath;
                    newTemplate.spriteData = baseWand.spriteData;
                    newTemplate.baseValue = 100;
                    newTemplate.isUsable = true;
                    newTemplate.isWandAppearance = true;
                    newTemplate.scale = baseWand.scale;
                    itemTemplates.put(type, newTemplate);
                }
            }
            // Ensure base WAND template is also marked usable (items.json may omit it)
            baseWand.isUsable = true;
        }

        // Rings (Ensure they are marked as ring appearances)
        ItemTemplate baseRing = itemTemplates.get(ItemType.SMALL_RING);
        if (baseRing != null) {
            List<ItemType> ringTypes = new ArrayList<>();
            ringTypes.add(ItemType.SMALL_RING);
            ringTypes.add(ItemType.LARGE_RING);
            ringTypes.add(ItemType.RING_BLUE);
            ringTypes.add(ItemType.RING_PINK);
            ringTypes.add(ItemType.RING_GREEN);
            ringTypes.add(ItemType.RING_PURPLE);
            ringTypes.add(ItemType.RING_GOLD);
            ringTypes.add(ItemType.RING_RED);
            ringTypes.add(ItemType.RING_YELLOW);
            ringTypes.add(ItemType.RING_WHITE);
            ringTypes.add(ItemType.RING_BLACK);
            ringTypes.add(ItemType.RING_ORANGE);
            ringTypes.add(ItemType.RING_SILVER);
            ringTypes.add(ItemType.RING_BRONZE);
            ringTypes.add(ItemType.RING_IVORY);

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

        applyUnlockGating();

        Gdx.app.log("ItemDataManager", "Loaded " + itemTemplates.size + " item templates.");
    }

    /**
     * Loads weapon definitions from weapons.json.
     * Called explicitly generally from Tarmin2.java.
     */
    public void loadWeapons() {
        Json json = new Json();
        json.setIgnoreUnknownFields(true);

        // Live Reload: Check for source file first
        FileHandle file = Gdx.files.local("assets/data/weapons.json");
        if (!file.exists()) {
            file = Gdx.files.internal("data/weapons.json");
        } else {
            Gdx.app.log("ItemDataManager", "Live Reloading weapons.json from source.");
        }

        if (!file.exists()) {
            Gdx.app.error("ItemDataManager", "weapons.json missing!");
            return;
        }

        JsonValue root = new JsonReader().parse(file);
        int loadedCount = 0;

        // Iterate over the JSON keys explicitly efficiently
        for (JsonValue entry = root.child; entry != null; entry = entry.next) {
            String typeName = entry.name;
            ItemType type = getSafeItemType(typeName);
            if (type == null) {
                continue;
            }
            ItemTemplate template = json.readValue(ItemTemplate.class, entry);

            if (template.probability == 0) {
                template.probability = 10;
            }

            if (typeName.startsWith("ARROW_") || typeName.startsWith("QUARREL_") || typeName.startsWith("SLING_BULLET_")
                    || typeName.startsWith("BLOWGUN_") || typeName.startsWith("DART")) {
                template.isAmmunition = true;
                if (template.probability < 15) {
                    template.probability = 15;
                }
            }

            if (template.rotation != 0) {
                Gdx.app.log("ItemDataManager", "Loaded weapon " + typeName + " with rotation " + template.rotation);
            }

            itemTemplates.put(type, template);
            loadedCount++;
        }

        applyUnlockGating();

        Gdx.app.log("ItemDataManager", "Loaded " + loadedCount + " new weapons.");
    }

    /**
     * Loads armor definitions from armor.json.
     */
    public void loadArmor() {
        Json json = new Json();
        json.setIgnoreUnknownFields(true);

        // Live Reload: Check for source file first
        FileHandle file = Gdx.files.local("assets/data/armor.json");
        if (!file.exists()) {
            file = Gdx.files.internal("data/armor.json");
        } else {
            Gdx.app.log("ItemDataManager", "Live Reloading armor.json from source.");
        }

        if (!file.exists()) {
            Gdx.app.error("ItemDataManager", "armor.json missing!");
            return;
        }

        JsonValue root = new JsonReader().parse(file);
        int loadedCount = 0;

        for (JsonValue entry = root.child; entry != null; entry = entry.next) {
            String typeName = entry.name;
            ItemType type = getSafeItemType(typeName);
            if (type == null) {
                continue;
            }
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
        }

        // Ensure shield aliases resolve bidirectionally between classic Tarmin and Open5e
        crossAlias(ItemType.SMALL_SHIELD, ItemType.SHIELD_SMALL);
        crossAlias(ItemType.LARGE_SHIELD, ItemType.SHIELD_LARGE);
        if (!itemTemplates.containsKey(ItemType.SHIELD)) {
            if (itemTemplates.containsKey(ItemType.SMALL_SHIELD)) {
                itemTemplates.put(ItemType.SHIELD, itemTemplates.get(ItemType.SMALL_SHIELD));
            } else if (itemTemplates.containsKey(ItemType.SHIELD_SMALL)) {
                itemTemplates.put(ItemType.SHIELD, itemTemplates.get(ItemType.SHIELD_SMALL));
            }
        }
        if (!itemTemplates.containsKey(ItemType.SHIELD_MEDIUM)) {
            if (itemTemplates.containsKey(ItemType.SHIELD_BODY)) {
                itemTemplates.put(ItemType.SHIELD_MEDIUM, itemTemplates.get(ItemType.SHIELD_BODY));
            } else if (itemTemplates.containsKey(ItemType.SMALL_SHIELD)) {
                itemTemplates.put(ItemType.SHIELD_MEDIUM, itemTemplates.get(ItemType.SMALL_SHIELD));
            }
        }

        applyUnlockGating();

        Gdx.app.log("ItemDataManager", "Loaded " + loadedCount + " new armor items.");
    }

    private void crossAlias(ItemType a, ItemType b) {
        if (itemTemplates.containsKey(a) && !itemTemplates.containsKey(b)) {
            itemTemplates.put(b, itemTemplates.get(a));
        } else if (itemTemplates.containsKey(b) && !itemTemplates.containsKey(a)) {
            itemTemplates.put(a, itemTemplates.get(b));
        }
    }

    /** Every type with a loaded template, in ItemType declaration order. */
    public List<ItemType> getLoadedTypes() {
        List<ItemType> types = new ArrayList<>();
        for (ItemType type : itemTemplates.keys()) {
            types.add(type);
        }
        java.util.Collections.sort(types);
        return types;
    }

    public void applyUnlockGating() {
        for (ObjectMap.Entry<ItemType, ItemTemplate> entry : itemTemplates.entries()) {
            ItemType type = entry.key;
            ItemTemplate t = entry.value;
            if (t == null) continue;
            if (type == ItemType.AXE) {
                t.unlockGated = false;
                continue;
            }
            int score = UnlockManager.calculateItemScore(t);
            t.unlockGated = (score >= UnlockManager.UNLOCK_SCORE_THRESHOLD);
        }
    }

    public ItemTemplate getTemplate(ItemType type) {
        ItemTemplate template = itemTemplates.get(type);
        if (template == null && type != null) {
            if (type == ItemType.SMALL_SHIELD && itemTemplates.containsKey(ItemType.SHIELD_SMALL)) {
                template = itemTemplates.get(ItemType.SHIELD_SMALL);
            } else if (type == ItemType.SHIELD_SMALL && itemTemplates.containsKey(ItemType.SMALL_SHIELD)) {
                template = itemTemplates.get(ItemType.SMALL_SHIELD);
            } else if (type == ItemType.LARGE_SHIELD && itemTemplates.containsKey(ItemType.SHIELD_LARGE)) {
                template = itemTemplates.get(ItemType.SHIELD_LARGE);
            } else if (type == ItemType.SHIELD_LARGE && itemTemplates.containsKey(ItemType.LARGE_SHIELD)) {
                template = itemTemplates.get(ItemType.LARGE_SHIELD);
            } else if (type == ItemType.SHIELD) {
                template = itemTemplates.get(ItemType.SMALL_SHIELD);
                if (template == null) template = itemTemplates.get(ItemType.SHIELD_SMALL);
            } else if (type == ItemType.SHIELD_MEDIUM) {
                template = itemTemplates.get(ItemType.SHIELD_BODY);
                if (template == null) template = itemTemplates.get(ItemType.SMALL_SHIELD);
            }
            if (template != null) {
                itemTemplates.put(type, template);
                return template;
            }
        }
        if (template == null) {
            Gdx.app.error("ItemDataManager", "Missing template for item type: " + type + ". Generating fallback template.");
            template = new ItemTemplate();
            template.friendlyName = (type != null) ? type.name() : "Unknown Item";
            template.description = "An enigmatic artifact of unknown origin.";
            template.scale = createDefaultScale();
            ItemTemplate defaultRef = itemTemplates.get(ItemType.TARMIN_TREASURE);
            if (defaultRef == null) defaultRef = itemTemplates.get(ItemType.SMALL_ROCK);
            if (defaultRef != null) {
                template.texturePath = defaultRef.texturePath;
                template.spriteData = defaultRef.spriteData;
            }
            if (type != null) {
                itemTemplates.put(type, template);
            }
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
        // Queue Weapons Atlas
        assetManager.load("packed/weapons.atlas", TextureAtlas.class);

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

        // --- FIX: Randomize Generic WAND ---
        if (type == ItemType.WAND) {
            ItemType[] wands = {
                    ItemType.WAND_A, ItemType.WAND_B, ItemType.WAND_C, ItemType.WAND_D,
                    ItemType.WAND_E, ItemType.WAND_F, ItemType.WAND_G, ItemType.WAND_H
            };
            type = wands[random.nextInt(wands.length)];
            Gdx.app.log("ItemDataManager", "Converted generic WAND to " + type.name());
        }
        // -----------------------------------

        ItemTemplate template = getTemplate(type);

        // Standard item creation
        Item item = new Item(type, x, y, color, this, assetManager);

        // Spellbook dynamic spell binding for non-weapon books
        if (type == ItemType.BOOK && item.getSpellId() == null) {
            assignDefaultSpellToBook(item);
        }

        // --- NEW: THEMED DICE INTEGRATION ---
        // Basic mapping for now. Ideally this is data-driven in items.json,
        // but for this phase we hardcode the mapping to test the 10 themes.
        com.bpm.minotaur.gamedata.dice.Die themedDie = null;

        // Map ItemTypes/Names to Dice Themes
        // 1. Rusty Iron Die
        if (type == ItemType.KNIFE || type == ItemType.AXE || type == ItemType.RUSTY_SWORD) { // Basic weapons
            themedDie = com.bpm.minotaur.gamedata.dice.DiceFactory.create("Rusty Iron Die");
        }
        // 1b. Priest's Holy Die (Spiritual Weapons)
        else if (type == ItemType.WOODEN_CROSS) {
            themedDie = com.bpm.minotaur.gamedata.dice.DiceFactory.create("Priest's Holy Die");
        }
        // 2. Warrior's Red Die
        else if (type == ItemType.SWORD || type == ItemType.TWO_HANDED_SWORD) {
            themedDie = com.bpm.minotaur.gamedata.dice.DiceFactory.create("Warrior's Red Die");
        }
        // 3. Guardian's Steel Die
        else if (Item.isShieldType(type)) {
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
            WandEffectType effect = null;
            if (type == Item.ItemType.WAND_OF_MAGIC_MISSILES) {
                effect = WandEffectType.MAGIC_MISSILE;
                item.setCharges(7);
                item.setIdentified(true);
            } else if (discoveryManager != null) {
                effect = discoveryManager.getWandEffect(type);
                if (effect == null && type == Item.ItemType.WAND) {
                    effect = WandEffectType.values()[random.nextInt(WandEffectType.values().length)];
                }
            } else {
                effect = WandEffectType.values()[random.nextInt(WandEffectType.values().length)];
            }

            if (effect != null) {
                item.setWandEffect(effect);
                if (item.getCharges() <= 0) {
                    item.setCharges(random.nextInt(6) + 3); // 3 to 8 charges
                }

                boolean isIdentified = (discoveryManager != null && discoveryManager.isWandIdentified(effect)) || item.isIdentified();
                item.setIdentified(isIdentified);
                if (isIdentified) {
                    item.setName("Wand of " + effect.getBaseName());
                } else {
                    item.setName("Wand");
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

        // Named build items: apply guaranteed modifiers and auto-identify
        applyNamedItemModifiers(type, item);

        return item;
    }

    private void assignDefaultSpellToBook(Item item) {
        try {
            com.bpm.minotaur.gamedata.spells.SpellDataManager sdm = com.bpm.minotaur.gamedata.spells.SpellDataManager.getInstance();
            List<com.bpm.minotaur.gamedata.spells.SpellTemplate> all = sdm.getAllSpells();
            if (all != null && !all.isEmpty()) {
                com.bpm.minotaur.gamedata.progression.ShelterAltar altar = com.bpm.minotaur.gamedata.progression.ShelterAltar.getInstance();
                List<com.bpm.minotaur.gamedata.spells.SpellTemplate> unsealed = new ArrayList<>();
                for (com.bpm.minotaur.gamedata.spells.SpellTemplate s : all) {
                    if (s != null && s.id != null && !altar.isSpellSealed(s.id)) {
                        unsealed.add(s);
                    }
                }
                List<com.bpm.minotaur.gamedata.spells.SpellTemplate> pool = unsealed.isEmpty() ? all : unsealed;
                com.bpm.minotaur.gamedata.spells.SpellTemplate chosen = pool.get(random.nextInt(pool.size()));
                item.setSpellId(chosen.id);
                String displayName = "Spellbook: " + chosen.getName();
                item.setName(displayName);
                item.setFriendlyName(displayName);
            }
        } catch (Exception ignored) {
        }
    }

    private void applyNamedItemModifiers(ItemType type, Item item) {
        switch (type) {
            case BELT_OF_GIANT_STRENGTH:
                item.setName("Belt of Giant Strength"); item.setIdentified(true);
                item.addModifier(new ItemModifier(ModifierType.BONUS_STRENGTH, 4, "of Giant Strength"));
                item.addModifier(new ItemModifier(ModifierType.BONUS_MAX_HP, 5, "Vigorous"));
                break;
            case BERSERKERS_GAUNTLETS:
                item.setName("Berserker's Gauntlets"); item.setIdentified(true);
                item.addModifier(new ItemModifier(ModifierType.BONUS_STRENGTH, 2, "Strong"));
                item.addModifier(new ItemModifier(ModifierType.BONUS_ABSORB, 2, "Iron"));
                item.addModifier(new ItemModifier(ModifierType.BONUS_STAMINA, 1, "Vigorous"));
                break;
            case BOOTS_OF_SWIFTNESS:
                item.setName("Boots of Swiftness"); item.setIdentified(true);
                item.addModifier(new ItemModifier(ModifierType.BONUS_AGILITY, 4, "of Shadows"));
                item.addModifier(new ItemModifier(ModifierType.BONUS_DODGE, 5, "Ghostly"));
                break;
            case CLOAK_OF_SHADOWS:
                item.setName("Cloak of Shadows"); item.setIdentified(true);
                item.addModifier(new ItemModifier(ModifierType.BONUS_AGILITY, 2, "Swift"));
                item.addModifier(new ItemModifier(ModifierType.BONUS_DODGE, 3, "Evasive"));
                item.addModifier(new ItemModifier(ModifierType.BONUS_DEXTERITY, 2, "Nimble"));
                break;
            case HOOD_OF_CLARITY:
                item.setName("Hood of Clarity"); item.setIdentified(true);
                item.addModifier(new ItemModifier(ModifierType.BONUS_INTELLIGENCE, 4, "of Insight"));
                item.addModifier(new ItemModifier(ModifierType.BONUS_MAX_MP, 5, "of Spirit"));
                break;
            case AMULET_OF_INSIGHT:
                item.setName("Amulet of Insight"); item.setIdentified(true);
                item.addModifier(new ItemModifier(ModifierType.BONUS_INTELLIGENCE, 2, "Brilliant"));
                item.addModifier(new ItemModifier(ModifierType.BONUS_WISDOM, 2, "Sage"));
                item.addModifier(new ItemModifier(ModifierType.BONUS_SPELL_POWER, 2, "Arcane"));
                break;
            case AMULET_OF_DIVINE_FAVOR:
                item.setName("Amulet of Divine Favor"); item.setIdentified(true);
                item.addModifier(new ItemModifier(ModifierType.BONUS_WISDOM, 4, "of the Oracle"));
                item.addModifier(new ItemModifier(ModifierType.BONUS_MAX_MP, 3, "of Spirit"));
                break;
            case HOLY_GAUNTLETS:
                item.setName("Holy Gauntlets"); item.setIdentified(true);
                item.addModifier(new ItemModifier(ModifierType.BONUS_WISDOM, 2, "Sage"));
                item.addModifier(new ItemModifier(ModifierType.BONUS_STRENGTH, 2, "Strong"));
                break;
            case ALCHEMISTS_BELT:
                item.setName("Alchemist's Belt"); item.setIdentified(true);
                item.addModifier(new ItemModifier(ModifierType.BONUS_TOXICITY_THRESHOLD, 20, "Venomwoven"));
                item.addModifier(new ItemModifier(ModifierType.BONUS_CONSTITUTION, 4, "of Endurance"));
                break;
            case TOXIC_VEIL:
                item.setName("Toxic Veil"); item.setIdentified(true);
                item.addModifier(new ItemModifier(ModifierType.BONUS_TOXICITY_THRESHOLD, 10, "Alchemical"));
                item.addModifier(new ItemModifier(ModifierType.RESIST_POISON, 8, "Antidotal"));
                item.addModifier(new ItemModifier(ModifierType.BONUS_CONSTITUTION, 2, "Hardy"));
                break;

            // --- Open5e Potions ---
            case POTION_OF_HEALING:
                item.setName("Potion of Healing"); item.setIdentified(true);
                item.setTrueEffect(PotionEffectType.HEALING);
                break;
            case POTION_HILL_GIANT:
                item.setName("Potion of Hill Giant Strength"); item.setIdentified(true);
                item.setTrueEffect(PotionEffectType.HILL_GIANT_STRENGTH);
                break;
            case POTION_FIRE_GIANT:
                item.setName("Potion of Fire Giant Strength"); item.setIdentified(true);
                item.setTrueEffect(PotionEffectType.FIRE_GIANT_STRENGTH);
                break;
            case POTION_STORM_GIANT:
                item.setName("Potion of Storm Giant Strength"); item.setIdentified(true);
                item.setTrueEffect(PotionEffectType.STORM_GIANT_STRENGTH);
                break;
            case POTION_OF_HEROISM:
                item.setName("Potion of Heroism"); item.setIdentified(true);
                item.setTrueEffect(PotionEffectType.HEROISM);
                break;
            case POTION_OF_INVISIBILITY:
                item.setName("Potion of Invisibility"); item.setIdentified(true);
                item.setTrueEffect(PotionEffectType.INVISIBILITY);
                break;
            case POTION_RESISTANCE: {
                item.setName("Potion of Resistance"); item.setIdentified(true);
                PotionEffectType[] resistances = {
                        PotionEffectType.RESISTANCE_FIRE, PotionEffectType.RESISTANCE_COLD,
                        PotionEffectType.RESISTANCE_LIGHTNING, PotionEffectType.RESISTANCE_ACID,
                        PotionEffectType.RESISTANCE_NECROTIC
                };
                item.setTrueEffect(resistances[(int) (Math.random() * resistances.length)]);
                break;
            }
            case OIL_OF_SHARPNESS:
                item.setName("Oil of Sharpness"); item.setIdentified(true);
                item.setTrueEffect(PotionEffectType.OIL_OF_SHARPNESS);
                break;
            case POTION_GREATER_HEALING:
                item.setName("Potion of Greater Healing"); item.setIdentified(true);
                item.setTrueEffect(PotionEffectType.GREATER_HEALING);
                break;
            case POTION_SUPERIOR_HEALING:
                item.setName("Potion of Superior Healing"); item.setIdentified(true);
                item.setTrueEffect(PotionEffectType.SUPERIOR_HEALING);
                break;
            case POTION_SUPREME_HEALING:
                item.setName("Potion of Supreme Healing"); item.setIdentified(true);
                item.setTrueEffect(PotionEffectType.SUPREME_HEALING);
                break;
            case POTION_SPEED:
                item.setName("Potion of Speed"); item.setIdentified(true);
                item.setTrueEffect(PotionEffectType.SPEED);
                break;
            case POTION_GIANT_STRENGTH:
                item.setName("Potion of Giant Strength"); item.setIdentified(true);
                item.setTrueEffect(PotionEffectType.GIANT_STRENGTH);
                break;
            case POTION_INVULNERABILITY:
                item.setName("Potion of Invulnerability"); item.setIdentified(true);
                item.setTrueEffect(PotionEffectType.INVULNERABILITY);
                break;
            case POTION_HEROISM:
                item.setName("Potion of Heroism"); item.setIdentified(true);
                item.setTrueEffect(PotionEffectType.HEROISM);
                break;
            case POTION_INVISIBILITY_5E:
                item.setName("Potion of Invisibility"); item.setIdentified(true);
                item.setTrueEffect(PotionEffectType.INVISIBILITY);
                break;
            case POTION_FLYING:
                item.setName("Potion of Flying"); item.setIdentified(true);
                item.setTrueEffect(PotionEffectType.LEVITATION);
                break;
            case POTION_CLIMBING:
                item.setName("Potion of Climbing"); item.setIdentified(true);
                item.setTrueEffect(PotionEffectType.CLIMBING);
                break;
            case POTION_DIMINUTION:
                item.setName("Potion of Diminution"); item.setIdentified(true);
                item.setTrueEffect(PotionEffectType.DIMINUTION);
                break;
            case POTION_GROWTH:
                item.setName("Potion of Growth"); item.setIdentified(true);
                item.setTrueEffect(PotionEffectType.GROWTH);
                break;
            case POTION_RESISTANCE_FIRE:
                item.setName("Potion of Fire Resistance"); item.setIdentified(true);
                item.setTrueEffect(PotionEffectType.RESISTANCE_FIRE);
                break;
            case POTION_RESISTANCE_COLD:
                item.setName("Potion of Cold Resistance"); item.setIdentified(true);
                item.setTrueEffect(PotionEffectType.RESISTANCE_COLD);
                break;
            case POTION_RESISTANCE_LIGHTNING:
                item.setName("Potion of Lightning Resistance"); item.setIdentified(true);
                item.setTrueEffect(PotionEffectType.RESISTANCE_LIGHTNING);
                break;
            case POTION_RESISTANCE_ACID:
                item.setName("Potion of Acid Resistance"); item.setIdentified(true);
                item.setTrueEffect(PotionEffectType.RESISTANCE_ACID);
                break;
            case POTION_RESISTANCE_NECROTIC:
                item.setName("Potion of Necrotic Resistance"); item.setIdentified(true);
                item.setTrueEffect(PotionEffectType.RESISTANCE_NECROTIC);
                break;
            case POTION_VITALITY:
                item.setName("Potion of Vitality"); item.setIdentified(true);
                item.setTrueEffect(PotionEffectType.VITALITY);
                break;
            case POTION_CLARITY:
                item.setName("Potion of Clarity"); item.setIdentified(true);
                item.setTrueEffect(PotionEffectType.CLARITY);
                break;
            case OIL_SLIPPERINESS:
                item.setName("Oil of Slipperiness"); item.setIdentified(true);
                item.setTrueEffect(PotionEffectType.SLIPPERINESS);
                break;
            case OIL_ETHEREALNESS:
                item.setName("Oil of Etherealness"); item.setIdentified(true);
                item.setTrueEffect(PotionEffectType.ETHEREALNESS);
                break;
            case ELIXIR_HEALTH:
                item.setName("Elixir of Health"); item.setIdentified(true);
                item.setTrueEffect(PotionEffectType.ELIXIR_HEALTH);
                break;

            // --- Open5e Magic Rings ---
            case RING_FREE_ACTION:
                item.setName("Ring of Free Action"); item.setIdentified(true);
                item.setRingEffect(RingEffectType.FREE_ACTION);
                break;
            case RING_WARMTH:
                item.setName("Ring of Warmth"); item.setIdentified(true);
                item.setRingEffect(RingEffectType.WARMTH);
                break;
            case RING_RESISTANCE_FIRE:
                item.setName("Ring of Fire Resistance"); item.setIdentified(true);
                item.setRingEffect(RingEffectType.RESISTANCE_FIRE);
                break;
            case RING_RESISTANCE_COLD:
                item.setName("Ring of Cold Resistance"); item.setIdentified(true);
                item.setRingEffect(RingEffectType.RESISTANCE_COLD);
                break;
            case RING_RESISTANCE_LIGHTNING:
                item.setName("Ring of Lightning Resistance"); item.setIdentified(true);
                item.setRingEffect(RingEffectType.RESISTANCE_LIGHTNING);
                break;
            case RING_RESISTANCE_ACID:
                item.setName("Ring of Acid Resistance"); item.setIdentified(true);
                item.setRingEffect(RingEffectType.RESISTANCE_ACID);
                break;
            case RING_RESISTANCE_NECROTIC:
                item.setName("Ring of Necrotic Resistance"); item.setIdentified(true);
                item.setRingEffect(RingEffectType.RESISTANCE_NECROTIC);
                break;
            case RING_RAM:
                item.setName("Ring of the Ram"); item.setIdentified(true);
                item.setRingEffect(RingEffectType.RAM);
                item.setMaxCharges(3);
                item.setCurrentCharges(3);
                break;
            case RING_EVASION_CHARGED:
                item.setName("Ring of Evasion"); item.setIdentified(true);
                item.setRingEffect(RingEffectType.EVASION_CHARGED);
                item.setMaxCharges(3);
                item.setCurrentCharges(3);
                break;
            case RING_SPELL_STORING:
                item.setName("Ring of Spell Storing"); item.setIdentified(true);
                item.setRingEffect(RingEffectType.SPELL_STORING);
                item.setMaxCharges(1);
                item.setCurrentCharges(0);
                break;
            case RING_SHOOTING_STARS:
                item.setName("Ring of Shooting Stars"); item.setIdentified(true);
                item.setRingEffect(RingEffectType.SHOOTING_STARS);
                item.setMaxCharges(6);
                item.setCurrentCharges(6);
                break;
            case RING_FEATHER_FALLING:
                item.setName("Ring of Feather Falling"); item.setIdentified(true);
                item.setRingEffect(RingEffectType.FEATHER_FALLING);
                break;
            default:
                break;
        }
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

    /**
     * Maps Effective Difficulty Level (EDL) to a NetHack Color Tier index (0..5):
     * 0: TAN (EDL 1-3)
     * 1: ORANGE (EDL 4-6)
     * 2: BLUE (EDL 7-9)
     * 3: WHITE (EDL 10-12)
     * 4: PINK (EDL 13-15)
     * 5: PURPLE (EDL 16+)
     */
    public static int getTierIndexForEDL(int edl) {
        if (edl <= 3) return 0;
        if (edl <= 6) return 1;
        if (edl <= 9) return 2;
        if (edl <= 12) return 3;
        if (edl <= 15) return 4;
        return 5;
    }

    /**
     * Rolls Out-Of-Depth (OOD) tier bonuses:
     * 1% chance for +2 tiers, 10% chance for +1 tier.
     */
    public int rollColorTierWithOOD(int baseTierIndex) {
        int tier = baseTierIndex;
        int oodRoll = random.nextInt(100);
        if (oodRoll == 0) {
            tier += 2;
        } else if (oodRoll < 11) {
            tier += 1;
        }
        return Math.min(5, tier);
    }

    /**
     * Returns an ItemColor corresponding to the given EDL, factoring in Out-Of-Depth chances.
     */
    public ItemColor getColorForEDL(int edl) {
        return getColorForEDL(edl, false);
    }

    public ItemColor getColorForEDL(int edl, boolean isSpiritual) {
        int baseTier = getTierIndexForEDL(edl);
        int rolledTier = rollColorTierWithOOD(baseTier);
        switch (rolledTier) {
            case 0: return ItemColor.TAN;
            case 1: return ItemColor.ORANGE;
            case 2: return isSpiritual ? ItemColor.BLUE : ItemColor.BLUE_STEEL;
            case 3: return isSpiritual ? ItemColor.WHITE_SPIRITUAL : ItemColor.WHITE;
            case 4: return ItemColor.PINK;
            case 5:
            default: return ItemColor.PURPLE;
        }
    }

    // 24x24 retro sprites for the Void salvage items. Textures come from tools/generate_void_laser_art.py.
    private static final String[] VOID_LASER_SPRITE = new String[] {
            "........................",
            "........................",
            "........................",
            "........................",
            "........................",
            "........................",
            "........................",
            "........................",
            "...............#...#....",
            ".............##########.",
            "...###########.#...#....",
            "...####################.",
            "...###########.#...#....",
            "...####################.",
            "...###########.#...#....",
            "...###########..........",
            ".....####...............",
            ".....####...............",
            ".....####...............",
            ".....####...............",
            ".....####...............",
            ".....####...............",
            "........................",
            "........................"
    };
    private static final String[] VOID_LASER_SPENT_SPRITE = new String[] {
            "........................",
            "........................",
            "........................",
            "........................",
            "........................",
            "........................",
            "........................",
            "........................",
            "...............#...#....",
            ".............##########.",
            "...###########.#...#....",
            "...###...##############.",
            "...###...#####.#...#....",
            "...###...##############.",
            "...###########.#...#....",
            "...###########..........",
            ".....####...............",
            ".....####...............",
            ".....####...............",
            ".....####...............",
            ".....####...............",
            ".....####...............",
            "........................",
            "........................"
    };
    private static final String[] RIFT_FILAMENT_SPRITE = new String[] {
            "........................",
            "........................",
            "........................",
            "........................",
            "........................",
            "........................",
            "........................",
            "........................",
            ".....#..#...#.#...#..#..",
            ".......#...#...#...##...",
            "..#.#.#..........#......",
            ".........#...#..........",
            "......#...#.....#.......",
            "...#...#...........##...",
            "............#...#.......",
            ".....#...##...#..#......",
            "..#.#...#..#...#..#..#..",
            "........................",
            "........................",
            "........................",
            "........................",
            "........................",
            "........................",
            "........................"
    };

    private void initializeMissingTemplates() {
        // --- NEW: Generate Templates for Corpses & Resources if missing ---
        if (!itemTemplates.containsKey(ItemType.WOODEN_CROSS)) {
            ItemTemplate cross = new ItemTemplate();
            cross.friendlyName = "Wooden Cross";
            cross.description = "A simple wooden cross that channels spiritual energy against chaotic fiends.";
            cross.texturePath = "images/weapons/wooden_cross.png";
            cross.isWeapon = true;
            cross.damageDice = "1d4";
            cross.baseValue = 10;
            cross.scale = createDefaultScale();
            cross.spriteData = new String[] {
                "........................",
                "........................",
                "........................",
                "..........####..........",
                "..........####..........",
                "..........####..........",
                "..........####..........",
                "..........####..........",
                "....################....",
                "....################....",
                "....################....",
                "..........####..........",
                "..........####..........",
                "..........####..........",
                "..........####..........",
                "..........####..........",
                "..........####..........",
                "..........####..........",
                "..........####..........",
                "..........####..........",
                "........................",
                "........................",
                "........................",
                "........................"
            };
            itemTemplates.put(ItemType.WOODEN_CROSS, cross);
        }

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

        // Portable Field Kits
        createResourceTemplate(ItemType.CRAFTING_TOOLKIT, "Field Crafting Toolkit",
                "A compact roll of tools. Lets you forge, salvage, and infuse gear from your pack while away from the shelter.",
                ItemType.HOME_CRAFTING_BENCH);
        createResourceTemplate(ItemType.COOKING_KIT, "Portable Cookware",
                "A travel pot and fire-starter kit. Lets you cook meals from your pack while away from the shelter.",
                ItemType.HOME_FIRE_POT);

        // Debris fallback (just in case)
        createResourceTemplate(ItemType.STICK, "Stick", "A wooden stick.", null);
        createResourceTemplate(ItemType.SMALL_ROCK, "Rock", "A small rock.", null);
        createResourceTemplate(ItemType.FLINT_SHARD, "Flint", "Sharp stone.", ItemType.SMALL_ROCK);
        createResourceTemplate(ItemType.BROKEN_HILT, "Hilt", "Broken hilt.", ItemType.KNIFE);
        createResourceTemplate(ItemType.METAL_SCRAP, "Scrap", "Metal scrap.", ItemType.AXE);

        // Lost Divinities
        if (!itemTemplates.containsKey(ItemType.LOST_DIVINITIES)) {
            ItemTemplate div = new ItemTemplate();
            div.friendlyName = "Lost Divinities";
            div.description = "Spiritual essence lost upon death. Step over to reclaim.";
            ItemTemplate treasure = itemTemplates.get(ItemType.TARMIN_TREASURE);
            if (treasure != null) {
                div.texturePath = treasure.texturePath;
                div.spriteData = treasure.spriteData;
                div.scale = treasure.scale;
            } else {
                div.texturePath = "images/items/tarmin_treasure.png";
                div.scale = createDefaultScale();
            }
            div.isTreasure = true;
            div.baseValue = 0;
            itemTemplates.put(ItemType.LOST_DIVINITIES, div);
        }

        // Cooked Meal
        if (!itemTemplates.containsKey(ItemType.MEAL)) {
            createResourceTemplate(ItemType.MEAL, "Cooked Meal", "A prepared dish that provides sustenance and temporary boons.", ItemType.FOOD);
            ItemTemplate meal = itemTemplates.get(ItemType.MEAL);
            if (meal != null) {
                meal.isFood = true;
                meal.isUsable = true;
                meal.baseValue = 10;
            }
        }

        // Closed-face Helm
        if (!itemTemplates.containsKey(ItemType.CLOSED_FACE)) {
            ItemTemplate t = new ItemTemplate();
            t.friendlyName = "Closed-face Helm";
            t.description = "Defense: 2";
            ItemTemplate greatHelm = itemTemplates.get(ItemType.GREAT_HELM);
            if (greatHelm != null) {
                t.texturePath = greatHelm.texturePath;
                t.spriteData = greatHelm.spriteData;
                t.scale = greatHelm.scale;
            } else {
                t.texturePath = "images/armor/great_helm.png";
                t.scale = createDefaultScale();
            }
            t.isArmor = true;
            t.isHelmet = true;
            t.armorClassBonus = 2;
            t.baseValue = 40;
            itemTemplates.put(ItemType.CLOSED_FACE, t);
        }

        // Milestone Tarmin Tomes
        registerTomeTemplate(ItemType.TOME_OF_THE_INITIATE, "Tome of the Initiate",
                "An ancient bound treatise on cantrips and foundational sorcery. Heavy cargo. Unlocks Spell Slot 2.", 20.0f);
        registerTomeTemplate(ItemType.TOME_OF_ELEMENTS, "Tome of Elements",
                "A dense parchment codex crackling with primal elemental energy. Heavy cargo. Unlocks Spell Slot 3.", 25.0f);
        registerTomeTemplate(ItemType.TOME_OF_THE_ARCANE, "Tome of the Arcane",
                "A heavy iron-clasped grimoire containing high arcanum formulas. Heavy cargo. Unlocks Spell Slot 4.", 30.0f);
        registerTomeTemplate(ItemType.TOME_OF_TARMIN, "Tome of Tarmin",
                "The legendary grand grimoire of Castle Tarmin itself, radiating forbidden power. Heavy cargo. Unlocks Spell Slot 5.", 35.0f);

        // --- Open5e Potions ---
        registerPotionTemplate(ItemType.POTION_GREATER_HEALING, "Potion of Greater Healing", "Restores 4d4+4 HP (min 35%).", 150);
        registerPotionTemplate(ItemType.POTION_SUPERIOR_HEALING, "Potion of Superior Healing", "Restores 8d4+8 HP (min 55%).", 300);
        registerPotionTemplate(ItemType.POTION_SUPREME_HEALING, "Potion of Supreme Healing", "Restores 10d4+20 HP (min 80%).", 600);
        registerPotionTemplate(ItemType.POTION_SPEED, "Potion of Speed", "Grants Haste for 20 turns. Beware the lethargic comedown!", 250);
        registerPotionTemplate(ItemType.POTION_GIANT_STRENGTH, "Potion of Giant Strength", "Sets effective Strength to 21 for 150 turns.", 200);
        registerPotionTemplate(ItemType.POTION_INVULNERABILITY, "Potion of Invulnerability", "Grants 50% resistance to all damage types for 20 turns.", 500);
        registerPotionTemplate(ItemType.POTION_HEROISM, "Potion of Heroism", "Grants 10 Temp HP and +2 to-hit for 150 turns.", 180);
        registerPotionTemplate(ItemType.POTION_INVISIBILITY_5E, "Potion of Invisibility", "Shrouds you from sight for 150 turns.", 220);
        registerPotionTemplate(ItemType.POTION_FLYING, "Potion of Flying", "Allows levitation above pits and traps for 150 turns.", 180);
        registerPotionTemplate(ItemType.POTION_CLIMBING, "Potion of Climbing", "Boosts agility and footwork for 150 turns.", 100);
        registerPotionTemplate(ItemType.POTION_DIMINUTION, "Potion of Diminution", "Shrinks user: +15% dodge chance for 150 turns.", 150);
        registerPotionTemplate(ItemType.POTION_GROWTH, "Potion of Growth", "Enlarges user: +2 bonus melee damage for 150 turns.", 150);
        registerPotionTemplate(ItemType.POTION_RESISTANCE_FIRE, "Potion of Fire Resistance", "Grants 50% fire damage reduction for 150 turns.", 120);
        registerPotionTemplate(ItemType.POTION_RESISTANCE_COLD, "Potion of Cold Resistance", "Grants 50% cold damage reduction for 150 turns.", 120);
        registerPotionTemplate(ItemType.POTION_RESISTANCE_LIGHTNING, "Potion of Lightning Resistance", "Grants 50% lightning damage reduction for 150 turns.", 120);
        registerPotionTemplate(ItemType.POTION_RESISTANCE_ACID, "Potion of Acid Resistance", "Grants 50% acid damage reduction for 150 turns.", 120);
        registerPotionTemplate(ItemType.POTION_RESISTANCE_NECROTIC, "Potion of Necrotic Resistance", "Grants 50% necrotic damage reduction for 150 turns.", 120);
        registerPotionTemplate(ItemType.POTION_VITALITY, "Potion of Vitality", "Cures exhaustion, disease, and poison.", 160);
        registerPotionTemplate(ItemType.POTION_CLARITY, "Potion of Clarity", "Restores 35 MP and sharpens arcane intellect.", 140);
        registerPotionTemplate(ItemType.OIL_SLIPPERINESS, "Oil of Slipperiness", "Grants Freedom of Movement and web immunity for 400 turns.", 150);
        registerPotionTemplate(ItemType.OIL_ETHEREALNESS, "Oil of Etherealness", "Grants ghostly evasion and phase-defense for 400 turns.", 250);
        registerPotionTemplate(ItemType.ELIXIR_HEALTH, "Elixir of Health", "Cures all afflictions and restores 20 HP.", 120);

        // --- Open5e Magic Rings ---
        registerRingTemplate(ItemType.RING_FREE_ACTION, "Ring of Free Action", "Grants permanent immunity to paralysis and movement slow.", 300, 0);
        registerRingTemplate(ItemType.RING_WARMTH, "Ring of Warmth", "Grants cold resistance and heavy thermal insulation.", 250, 0);
        registerRingTemplate(ItemType.RING_RESISTANCE_FIRE, "Ring of Fire Resistance", "Reduces incoming fire damage by 50%.", 250, 0);
        registerRingTemplate(ItemType.RING_RESISTANCE_COLD, "Ring of Cold Resistance", "Reduces incoming cold damage by 50%.", 250, 0);
        registerRingTemplate(ItemType.RING_RESISTANCE_LIGHTNING, "Ring of Lightning Resistance", "Reduces incoming lightning damage by 50%.", 250, 0);
        registerRingTemplate(ItemType.RING_RESISTANCE_ACID, "Ring of Acid Resistance", "Reduces incoming acid damage by 50%.", 250, 0);
        registerRingTemplate(ItemType.RING_RESISTANCE_NECROTIC, "Ring of Necrotic Resistance", "Reduces incoming dark/necrotic damage by 50%.", 250, 0);
        registerRingTemplate(ItemType.RING_RAM, "Ring of the Ram", "Strikes target with 2d10 force damage and 2-tile knockback on bump attack (3 charges).", 400, 3);
        registerRingTemplate(ItemType.RING_EVASION_CHARGED, "Ring of Evasion", "Cheat-death: Automatically negates fatal blow when struck (3 charges).", 500, 3);
        registerRingTemplate(ItemType.RING_SPELL_STORING, "Ring of Spell Storing", "Imbue 1 known spell for 0-MP free casting (1 charge).", 350, 1);
        registerRingTemplate(ItemType.RING_SHOOTING_STARS, "Ring of Shooting Stars", "Fires 2d6 light damage star motes during combat (6 charges).", 300, 6);
        registerRingTemplate(ItemType.RING_FEATHER_FALLING, "Ring of Feather Falling", "Slows falls and prevents pit trap drop damage.", 150, 0);
    
        // --- Void salvage: the traveling merchant's chain laser (spent / restored) and the Rift Filament ---
        if (!itemTemplates.containsKey(ItemType.VOID_CHAIN_LASER_SPENT)) {
            ItemTemplate spent = new ItemTemplate();
            spent.friendlyName = "Spent Void Chain Laser";
            spent.description = "Three barrels, a dead violet lens, and no way anyone in these depths could have made it. "
                    + "The cell is empty. Something from the Void might wake it.";
            spent.texturePath = "images/items/void_chain_laser_spent.png";
            spent.baseValue = 120;
            spent.scale = createDefaultScale();
            spent.spriteData = VOID_LASER_SPENT_SPRITE;
            itemTemplates.put(ItemType.VOID_CHAIN_LASER_SPENT, spent);
        }
        if (!itemTemplates.containsKey(ItemType.VOID_CHAIN_LASER)) {
            ItemTemplate laser = new ItemTemplate();
            laser.friendlyName = "Void Chain Laser";
            laser.description = "The cell hums. Light that shouldn't exist here pools behind the lens, waiting. "
                    + "Whoever carried this before the merchant didn't come from anywhere you know.";
            laser.texturePath = "images/items/void_chain_laser.png";
            laser.baseValue = 900;
            laser.scale = createDefaultScale();
            laser.spriteData = VOID_LASER_SPRITE;
            itemTemplates.put(ItemType.VOID_CHAIN_LASER, laser);
        }
        if (!itemTemplates.containsKey(ItemType.RIFT_FILAMENT)) {
            ItemTemplate filament = new ItemTemplate();
            filament.friendlyName = "Rift Filament";
            filament.description = "A thread of coherent light that only holds its shape inside the Void. "
                    + "It's warm, and it hums the same note as the merchant's weapon.";
            filament.texturePath = "images/items/rift_filament.png";
            filament.baseValue = 60;
            filament.scale = createDefaultScale();
            filament.spriteData = RIFT_FILAMENT_SPRITE;
            itemTemplates.put(ItemType.RIFT_FILAMENT, filament);
        }
    }

    private void registerPotionTemplate(ItemType type, String name, String desc, int baseValue) {
        if (!itemTemplates.containsKey(type)) {
            ItemTemplate t = new ItemTemplate();
            t.friendlyName = name;
            t.description = desc;
            t.isPotion = true;
            t.isUsable = true;
            t.baseValue = baseValue;
            t.scale = createDefaultScale();
            ItemTemplate basePotion = itemTemplates.get(ItemType.POTION_BLUE);
            if (basePotion == null) basePotion = itemTemplates.get(ItemType.POTION_OF_HEALING);
            if (basePotion != null) {
                t.texturePath = basePotion.texturePath;
                t.spriteData = basePotion.spriteData;
            } else {
                t.spriteData = new String[] { "..##..", ".####.", "######" };
            }
            itemTemplates.put(type, t);
        }
    }

    private void registerRingTemplate(ItemType type, String name, String desc, int baseValue, int maxCharges) {
        if (!itemTemplates.containsKey(type)) {
            ItemTemplate t = new ItemTemplate();
            t.friendlyName = name;
            t.description = desc;
            t.isRing = true;
            t.isUsable = true;
            t.baseValue = baseValue;
            t.maxCharges = maxCharges;
            t.scale = createDefaultScale();
            ItemTemplate baseRing = itemTemplates.get(ItemType.SMALL_RING);
            if (baseRing != null) {
                t.texturePath = baseRing.texturePath;
                t.spriteData = baseRing.spriteData;
            } else {
                t.spriteData = new String[] { ".####.", "##..##", ".####." };
            }
            itemTemplates.put(type, t);
        }
    }

    private void registerTomeTemplate(ItemType type, String name, String desc, float weight) {
        if (!itemTemplates.containsKey(type)) {
            ItemTemplate tome = new ItemTemplate();
            tome.friendlyName = name;
            tome.description = desc;
            tome.isUsable = true;
            tome.baseValue = 300;
            tome.weight = weight;
            tome.scale = createDefaultScale();
            ItemTemplate bookRef = itemTemplates.get(ItemType.BOOK);
            if (bookRef == null) bookRef = itemTemplates.get(ItemType.WAR_BOOK);
            if (bookRef != null) {
                tome.texturePath = bookRef.texturePath;
                tome.spriteData = bookRef.spriteData;
            } else {
                tome.spriteData = new String[] {
                    "################",
                    "#..............#",
                    "#..T O M E.....#",
                    "#..............#",
                    "################"
                };
            }
            itemTemplates.put(type, tome);
        }
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
