package com.bpm.minotaur.gamedata.progression;

import com.badlogic.gdx.assets.AssetManager;
import com.bpm.minotaur.gamedata.Inventory;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.ItemColor;
import com.bpm.minotaur.gamedata.item.ItemDataManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Hands out the portable field kits a player has earned at the Shelter Altar.
 *
 * <p>Each kit is the travelling version of a station: the Crafting Bench teaches the field
 * toolkit, the Fire Pot teaches the portable cookware (see {@link ShelterAltar.Station#getPortableKit()}).
 * Owning the station means always setting out with its kit, so this runs both when a
 * station is bought and as a top-up on respawn.
 *
 * <p>This is the <em>only</em> source of either item. Nothing spawns, drops, crafts or sells
 * them, and both field crafting and field cooking hard-require one in the pack -- so this
 * rule alone decides whether a player has those systems at all. (A secondary source is a
 * deliberate follow-up, not an oversight.)
 *
 * <p>Previously both kits were handed out unconditionally on every respawn, making them a
 * reward for dying rather than for spending divinities.
 */
public final class FieldKitGrant {

    private FieldKitGrant() {
    }

    /**
     * What a grant actually managed to do. Separating the two lists matters because a full
     * pack is the one way a grant can fail, and the player needs telling -- silently
     * swallowing it is what made the kits seem to appear only sometimes.
     */
    public static final class Result {
        private final List<Item> granted;
        private final List<Item> noRoom;

        Result(List<Item> granted, List<Item> noRoom) {
            this.granted = Collections.unmodifiableList(granted);
            this.noRoom = Collections.unmodifiableList(noRoom);
        }

        /** Kits handed over and now in the pack. */
        public List<Item> getGranted() {
            return granted;
        }

        /** Kits the player has earned but could not carry. */
        public List<Item> getNoRoom() {
            return noRoom;
        }

        /** True when nothing was owed at all, as distinct from owed-but-undeliverable. */
        public boolean isEmpty() {
            return granted.isEmpty() && noRoom.isEmpty();
        }
    }

    /**
     * The kit types this player has paid for but is not currently carrying.
     *
     * <p>A kit already in the pack is never duplicated, and a kit carried <em>without</em>
     * the matching station is left alone -- death retention lets one survive independently
     * of the altar, and that is the player's to keep.
     */
    public static List<Item.ItemType> owedKits(Set<ShelterAltar.Station> unlockedStations, Inventory inventory) {
        List<Item.ItemType> owed = new ArrayList<>();
        if (unlockedStations == null || inventory == null) {
            return owed;
        }

        for (ShelterAltar.Station station : unlockedStations) {
            Item.ItemType kit = station.getPortableKit();
            if (kit != null && !inventory.hasItemOfType(kit)) {
                owed.add(kit);
            }
        }
        return owed;
    }

    /**
     * Creates and hands over every owed kit. Safe to call repeatedly: a kit already carried
     * is never duplicated.
     */
    public static Result grantOwed(Set<ShelterAltar.Station> unlockedStations, Inventory inventory,
            ItemDataManager itemDataManager, AssetManager assetManager) {
        List<Item> granted = new ArrayList<>();
        List<Item> noRoom = new ArrayList<>();

        if (itemDataManager == null) {
            return new Result(granted, noRoom);
        }

        for (Item.ItemType kitType : owedKits(unlockedStations, inventory)) {
            Item kit = itemDataManager.createItem(kitType, 0, 0, ItemColor.GRAY, assetManager);
            if (kit == null) {
                continue;
            }
            if (inventory.pickupToBackpack(kit)) {
                granted.add(kit);
            } else {
                noRoom.add(kit);
            }
        }
        return new Result(granted, noRoom);
    }
}
