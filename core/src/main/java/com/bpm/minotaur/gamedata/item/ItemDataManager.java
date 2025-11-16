package com.bpm.minotaur.gamedata.item;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.ObjectMap;
import com.bpm.minotaur.gamedata.item.Item.ItemType; // We'll update Item.java soon
import com.bpm.minotaur.managers.PotionManager;

import java.util.ArrayList;
import java.util.List;

public class ItemDataManager {

    private final ObjectMap<ItemType, ItemTemplate> itemTemplates;


    private PotionManager potionManager;

    public ItemDataManager() {
        this.itemTemplates = new ObjectMap<>();
    }
    /**
     * Sets the PotionManager. This must be called after PotionManager is
     * constructed in GameScreen, but before any items are created.
     */
    public void setPotionManager(PotionManager potionManager) {
        this.potionManager = potionManager;
    }

    public void load() {
        Json json = new Json();
        FileHandle file = Gdx.files.internal("data/items.json");
        JsonValue root = new JsonReader().parse(file);

        for (ItemType type : ItemType.values()) {
            JsonValue data = root.get(type.name());

            if (data != null) {
                ItemTemplate template = json.readValue(ItemTemplate.class, data);
                itemTemplates.put(type, template);
            } else {
                Gdx.app.error("ItemDataManager", "No JSON data found for item type: " + type.name());
            }
        }

        Gdx.app.log("ItemDataManager", "Loaded " + itemTemplates.size + " item templates.");
    }

    public ItemTemplate getTemplate(ItemType type) {
        ItemTemplate template = itemTemplates.get(type);
        if (template == null) {
            throw new NullPointerException("No template loaded for item type: " + type);
        }
        return template;
    }

    public void queueAssets(AssetManager assetManager) {
        for (ItemTemplate template : itemTemplates.values()) {
            if (template.texturePath != null && !template.texturePath.isEmpty()) {
                assetManager.load(template.texturePath, Texture.class);
            }
        }
    }

    /**
     * Creates a new Item instance based on its type.
     * This now handles the special logic for randomized potions.
     */
    public Item createItem(ItemType type, int x, int y, ItemColor color, AssetManager assetManager) {

        Gdx.app.log("ItemDataManager [DEBUG]", "createItem called for: " + type.name());
        ItemTemplate template = getTemplate(type);

        // Standard item creation
        Item item = new Item(type, x, y, color, this, assetManager);

        // --- NEW POTION LOGIC ---
        if (template.isPotionAppearance) {

            Gdx.app.log("ItemDataManager [DEBUG]", "Type is a potion appearance.");
            if (potionManager == null) {
                Gdx.app.log("ItemDataManager [DEBUG]", "ERROR: PotionManager is NULL at item creation time!");
                return item; // Return a "dud" potion
            }

            // 1. Get the randomized effect from the manager
            PotionEffectType effect = potionManager.getEffectForAppearance(type.name());

            Gdx.app.log("ItemDataManager [DEBUG]", "Got effect from PotionManager: " + (effect == null ? "NULL" : effect.name()));

            if (effect != null) {
                // 2. Check if this effect is already identified
                boolean isIdentified = potionManager.isEffectIdentified(effect);

                Gdx.app.log("ItemDataManager [DEBUG]", "Setting trueEffect ("+effect.name()+") and identified ("+isIdentified+") on item.");

                // 3. Set the hidden (true) properties on the item instance
                item.setTrueEffect(effect);
                item.setIdentified(isIdentified);

                // 4. Set the visible name
                item.setName(potionManager.getPotionDisplayName(template, effect, isIdentified));

            } else {
                // This is a potion appearance with no matching effect (e.g., 10 appearances, 8 effects)
                Gdx.app.log("ItemDataManager [DEBUG]", "ERROR: Effect was NULL. This potion ("+type.name()+") will be a dud.");            }
        }

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

}
