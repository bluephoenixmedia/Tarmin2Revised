package com.bpm.minotaur.gamedata.save;

import com.badlogic.gdx.assets.AssetManager;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.ItemColor;
import com.bpm.minotaur.gamedata.item.ItemDataManager;
import com.bpm.minotaur.gamedata.item.ItemModifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Lightweight DTO for serializing Items in inventory, equipment, chests, and corpses.
 * Prevents LibGDX Json from serializing volatile runtime graphic textures.
 */
public class ItemSaveData {
    public Item.ItemType type;
    public ItemColor color;
    public List<ItemModifier> modifiers = new ArrayList<>();
    /** Blood on the item's paperdoll art, keyed by drawn part. Null when clean. */
    public java.util.HashMap<String, com.bpm.minotaur.gamedata.gore.BloodCoat> blood;
    /** Turns already spent studying a Tome. */
    public int studyProgress;
    public String spellId;
    public String friendlyName;

    public ItemSaveData() {
    }

    public ItemSaveData(Item.ItemType type, ItemColor color) {
        this.type = type;
        this.color = color;
    }

    public ItemSaveData(Item item) {
        if (item != null) {
            this.type = item.getType();
            this.color = item.getItemColor();
            if (item.getModifiers() != null) {
                this.modifiers.addAll(item.getModifiers());
            }
            this.blood = item.getBloodCoats();
            this.studyProgress = item.getStudyProgress();
            this.spellId = item.getSpellId();
            this.friendlyName = item.getFriendlyName();
        }
    }

    public Item toItem(ItemDataManager dataManager, AssetManager assetManager) {
        if (type == null) {
            return null;
        }
        Item item = new Item(type, 0, 0, color, dataManager, assetManager);
        if (modifiers != null) {
            for (ItemModifier mod : modifiers) {
                item.addModifier(mod);
            }
        }
        item.setBloodCoats(blood);
        item.setStudyProgress(studyProgress);
        if (spellId != null) {
            item.setSpellId(spellId);
        }
        if (friendlyName != null) {
            item.setFriendlyName(friendlyName);
            item.setName(friendlyName);
        }
        return item;
    }
}
