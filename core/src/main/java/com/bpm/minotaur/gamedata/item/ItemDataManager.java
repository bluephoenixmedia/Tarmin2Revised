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

public class ItemDataManager {

    private final ObjectMap<ItemType, ItemTemplate> itemTemplates;

    public ItemDataManager() {
        this.itemTemplates = new ObjectMap<>();
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
}
