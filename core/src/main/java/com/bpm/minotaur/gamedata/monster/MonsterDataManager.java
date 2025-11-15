package com.bpm.minotaur.gamedata.monster;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.ObjectMap;

public class MonsterDataManager {

    // Use libGDX's ObjectMap for better performance than HashMap
    private final ObjectMap<Monster.MonsterType, MonsterTemplate> monsterTemplates;

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
}
