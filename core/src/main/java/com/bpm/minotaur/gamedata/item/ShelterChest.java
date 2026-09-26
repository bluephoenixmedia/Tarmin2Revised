package com.bpm.minotaur.gamedata.item;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonWriter;

import com.badlogic.gdx.assets.AssetManager;
import com.bpm.minotaur.gamedata.save.ItemSaveData;
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
     * Serializes chest items to disk via lightweight ItemSaveData DTOs.
     */
    public void save() {
        try {
            FileHandle file = SaveManager.getInstance().getFileHandle(getSaveFilePath());
            file.parent().mkdirs();
            List<ItemSaveData> saveData = new ArrayList<>();
            for (Item item : items) {
                if (item != null && item.getType() != null) {
                    saveData.add(new ItemSaveData(item));
                }
            }
            SaveManager.getInstance().atomicWriteJson(file, saveData);
            if (Gdx.app != null) {
                Gdx.app.log("ShelterChest", "Saved " + saveData.size() + " items to " + getSaveFilePath());
            }
        } catch (Exception e) {
            if (Gdx.app != null) {
                Gdx.app.error("ShelterChest", "Failed to save shelter chest", e);
            }
        }
    }

    /**
     * Deserializes chest items from disk and re-hydrates textures and templates.
     */
    /**
     * Replaces the chest contents, but only when parsing actually produced a
     * result. A null {@code parsed} means the file could not be read in any
     * format, and the right answer then is to keep what is already in memory:
     * an unreadable file is not evidence that the player owns nothing.
     *
     * @return true when the contents were replaced
     */
    static boolean adopt(java.util.List<Item> target, java.util.List<Item> parsed) {
        if (target == null || parsed == null) return false;
        target.clear();
        target.addAll(parsed);
        return true;
    }

    public void load(ItemDataManager dataManager, AssetManager assetManager) {
        try {
            FileHandle file = SaveManager.getInstance().getFileHandle(getSaveFilePath());
            if (file.exists()) {
                // Parse into a scratch list and only adopt it once a parse has
                // actually succeeded. This used to clear() first, so a single
                // unreadable entry -- an ItemType name no longer in the enum is
                // enough -- emptied the stash in memory, and the next save wrote
                // that empty list over the player's persistent storage.
                ArrayList<Item> parsed = null;

                try {
                    @SuppressWarnings("unchecked")
                    ArrayList<ItemSaveData> loaded = json.fromJson(ArrayList.class, ItemSaveData.class, file);
                    if (loaded != null) {
                        parsed = new ArrayList<>();
                        for (ItemSaveData isd : loaded) {
                            if (isd != null) {
                                Item item = isd.toItem(dataManager, assetManager);
                                if (item != null) {
                                    parsed.add(item);
                                }
                            }
                        }
                    }
                } catch (Exception parseException) {
                    Gdx.app.log("ShelterChest", "Attempting fallback load for legacy chest data: " + parseException.getMessage());
                }

                if (parsed == null) {
                    // Legacy format fallback
                    try {
                        @SuppressWarnings("unchecked")
                        ArrayList<Item> legacy = json.fromJson(ArrayList.class, Item.class, file);
                        if (legacy != null) {
                            parsed = new ArrayList<>();
                            for (Item it : legacy) {
                                if (it != null && it.getType() != null) {
                                    Item rehydrated = dataManager != null
                                            ? dataManager.createItem(it.getType(), 0, 0, it.getItemColor(), assetManager)
                                            : it;
                                    parsed.add(rehydrated);
                                }
                            }
                        }
                    } catch (Exception legacyException) {
                        Gdx.app.error("ShelterChest",
                                "Chest file is unreadable in both formats; keeping the contents already in memory rather than discarding them",
                                legacyException);
                    }
                }

                if (adopt(items, parsed)) {
                    Gdx.app.log("ShelterChest", "Loaded " + items.size() + " items from " + getSaveFilePath());
                }
            }
        } catch (Exception e) {
            Gdx.app.error("ShelterChest", "Failed to load shelter chest", e);
        }
    }

    /**
     * Parameterless fallback load for tests or un-initialized managers.
     */
    public void load() {
        load(null, null);
    }
}
