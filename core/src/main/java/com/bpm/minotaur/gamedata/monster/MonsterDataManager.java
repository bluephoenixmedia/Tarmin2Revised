package com.bpm.minotaur.gamedata.monster;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.ObjectMap;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class MonsterDataManager {

    // Use libGDX's ObjectMap for better performance than HashMap
    private final ObjectMap<Monster.MonsterType, MonsterTemplate> monsterTemplates;
    private final Random random = new Random(); // <-- ADD THIS FIELD
    public MonsterDataManager() {
        this.monsterTemplates = new ObjectMap<>();
    }

    /**
     * Reads the JSON file and populates the template map.
     */
    public void load() {
        Json json = new Json();
        FileHandle file = Gdx.files.internal("data/monsters.json");
        JsonValue root = new JsonReader().parse(file);

        // Iterate through all known MonsterTypes
        for (Monster.MonsterType type : Monster.MonsterType.values()) {
            JsonValue data = root.get(type.name());

            if (data != null) {
                // Parse the JSON data for this monster into a Template object
                MonsterTemplate template = json.readValue(MonsterTemplate.class, data);
                monsterTemplates.put(type, template);
            } else {
                Gdx.app.error("MonsterDataManager", "No JSON data found for monster type: " + type.name());
            }
        }

        Gdx.app.log("MonsterDataManager", "Loaded " + monsterTemplates.size + " monster templates.");
    }

    /**
     * Gets the loaded template for a given monster type.
     */
    public MonsterTemplate getTemplate(Monster.MonsterType type) {
        MonsterTemplate template = monsterTemplates.get(type);
        if (template == null) {
            throw new NullPointerException("No template loaded for monster type: " + type);
        }
        return template;
    }

    /**
     * Tells the AssetManager to load all textures defined in our templates.
     */
    public void queueAssets(AssetManager assetManager) {
        for (MonsterTemplate template : monsterTemplates.values()) {
            if (template.texturePath != null && !template.texturePath.isEmpty()) {
                assetManager.load(template.texturePath, Texture.class);
            }
        }
    }

    /**
     * Selects a valid MonsterVariant (color/tier) for a given monster type at a specific level.
     * @param type The monster type (e.g., GIANT_ANT).
     * @param level The current dungeon level.
     * @return A valid MonsterVariant, or null if none are found.
     */
    public MonsterVariant getRandomVariantForMonster(Monster.MonsterType type, int level) {
        MonsterTemplate template = getTemplate(type);
        if (template.variants == null || template.variants.isEmpty()) {
            Gdx.app.error("MonsterDataManager", "No 'variants' defined for monster type: " + type.name());
            return null;
        }

        // 1. Filter variants by level
        List<MonsterVariant> validVariants = new ArrayList<>();
        int totalWeight = 0;
        for (MonsterVariant variant : template.variants) {
            if (level >= variant.minLevel && level <= variant.maxLevel) {
                validVariants.add(variant);
                totalWeight += variant.weight;
            }
        }

        // 2. Handle no valid variants
        if (validVariants.isEmpty()) {
            Gdx.app.error("MonsterDataManager", "No valid variants found for " + type.name() + " at level " + level);
            return null;
        }

        // 3. Perform weighted random selection
        int randomWeight = random.nextInt(totalWeight);
        int currentWeight = 0;

        for (MonsterVariant variant : validVariants) {
            currentWeight += variant.weight;
            if (randomWeight < currentWeight) {
                return variant;
            }
        }

        // Fallback (shouldn't happen, but good practice)
        return validVariants.get(0);
    }
}
