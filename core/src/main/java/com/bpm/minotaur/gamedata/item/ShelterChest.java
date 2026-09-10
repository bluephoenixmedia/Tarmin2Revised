package com.bpm.minotaur.gamedata.item;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonWriter;

import com.bpm.minotaur.managers.SaveManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Persistent Stash Chest located in the Starting Shelter.
 * Items stored here survive player deaths and persist across expeditions.
 */
public class ShelterChest {

    private static ShelterChest instance;
    private static final int DEFAULT_CAPACITY = 30;

    private String getSaveFilePath() {
        return SaveManager.getInstance().getActiveSlotFilePath("shelter_chest.json");
    }

    private final int capacity;
    private final List<Item> items;
    private final Json json;

    private ShelterChest() {
        this(DEFAULT_CAPACITY);
    }

    public ShelterChest(int capacity) {
        this.capacity = capacity;
        this.items = new ArrayList<>();
        this.json = new Json();
        this.json.setOutputType(JsonWriter.OutputType.json);
        this.json.setIgnoreUnknownFields(true);
    }

    public static synchronized ShelterChest getInstance() {
        if (instance == null) {
            instance = new ShelterChest();
        }
        return instance;
    }

    public int getCapacity() {
        return capacity;
    }

    public int getItemCount() {
        return items.size();
    }

    public boolean isFull() {
        return items.size() >= capacity;
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    public boolean addItem(Item item) {
        if (item == null || isFull()) {
            return false;
        }
        items.add(item);
        return true;
    }

    public Item getItem(int index) {
        if (index >= 0 && index < items.size()) {
            return items.get(index);
        }
        return null;
    }

    public Item removeItem(int index) {
        if (index >= 0 && index < items.size()) {
            return items.remove(index);
        }
        return null;
    }

    public boolean removeItem(Item item) {
        return items.remove(item);
    }

    public List<Item> getItems() {
        return Collections.unmodifiableList(items);
    }

    public void clear() {
        items.clear();
    }

    /**
     * Serializes chest items to disk.
     */
    public void save() {
        try {
            FileHandle file = Gdx.files.local(getSaveFilePath());
            file.parent().mkdirs();
            SaveManager.getInstance().atomicWriteJson(file, items);
            Gdx.app.log("ShelterChest", "Saved " + items.size() + " items to " + getSaveFilePath());
        } catch (Exception e) {
            Gdx.app.error("ShelterChest", "Failed to save shelter chest", e);
        }
    }

    /**
     * Deserializes chest items from disk.
     */
    public void load() {
        try {
            FileHandle file = Gdx.files.local(getSaveFilePath());
            if (file.exists()) {
                items.clear();
                @SuppressWarnings("unchecked")
                ArrayList<Item> loaded = json.fromJson(ArrayList.class, Item.class, file);
                if (loaded != null) {
                    items.addAll(loaded);
                    Gdx.app.log("ShelterChest", "Loaded " + items.size() + " items from " + getSaveFilePath());
                }
            }
        } catch (Exception e) {
            Gdx.app.error("ShelterChest", "Failed to load shelter chest", e);
        }
    }
}
