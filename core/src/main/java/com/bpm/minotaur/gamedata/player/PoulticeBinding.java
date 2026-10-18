package com.bpm.minotaur.gamedata.player;

import com.badlogic.gdx.assets.AssetManager;
import com.bpm.minotaur.gamedata.GameEvent;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.ItemDataManager;
import com.bpm.minotaur.managers.GameEventManager;

/**
 * Binding herbs into a Poultice by hand, anywhere: using a herb from the pack wraps it in cloth.
 * The two recipes match the workbench's (#190): Moss Clump with Twisted Root, or Glowing Lichen
 * alone. Only carried herbs count; the workbench can still draw on the Shelter Chest.
 */
public final class PoulticeBinding {

    private PoulticeBinding() {
    }

    public static boolean isReagent(Item.ItemType type) {
        return type == Item.ItemType.MOSS_CLUMP || type == Item.ItemType.TWISTED_ROOT
                || type == Item.ItemType.GLOWING_LICHEN;
    }

    /** Binds a Poultice from {@code used} (and its partner herb, if the recipe needs one). */
    public static boolean bind(Player player, Item used, ItemDataManager items, AssetManager assets,
                               GameEventManager events) {
        if (player == null || used == null || !isReagent(used.getType())) return false;
        Item partner = null;
        if (used.getType() != Item.ItemType.GLOWING_LICHEN) {
            Item.ItemType need = used.getType() == Item.ItemType.MOSS_CLUMP
                    ? Item.ItemType.TWISTED_ROOT : Item.ItemType.MOSS_CLUMP;
            partner = carried(player, need);
            if (partner == null) {
                post(events, "A poultice needs Moss Clump and Twisted Root together, or Glowing Lichen alone.");
                return false;
            }
        }
        if (items == null) return false;
        Item poultice = items.createItem(Item.ItemType.POULTICE, 0, 0, null, assets);
        if (poultice == null) return false;
        player.getInventory().consumeOne(used);
        if (partner != null) player.getInventory().consumeOne(partner);
        if (!player.getInventory().pickupToBackpack(poultice)) {
            post(events, "You bind a poultice, but your pack is full.");
            return false;
        }
        post(events, "You bind the herbs in cloth: a Poultice.");
        return true;
    }

    private static Item carried(Player player, Item.ItemType type) {
        for (Item i : player.getInventory().getAllItems()) {
            if (i != null && i.getType() == type) return i;
        }
        return null;
    }

    private static void post(GameEventManager events, String msg) {
        if (events != null) events.addEvent(new GameEvent(msg, 2.5f));
    }
}
