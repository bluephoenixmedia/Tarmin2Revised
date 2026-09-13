package com.bpm.minotaur.gamedata;

import com.bpm.minotaur.gamedata.item.Item;
import java.util.ArrayList;
import java.util.List;

public class Inventory {
    // "Quick Slots" - The 6 items visible on the HUD
    private final Item[] quickSlots = new Item[6];

    // "Main Inventory" - The expanded storage (Backpack Screen)
    // For now, let's cap it at 30 to match the UI grid
    private final List<Item> mainInventory = new ArrayList<>();
    private final int MAX_BACKPACK_SIZE = 30;

    private Item rightHand = null;
    private Item leftHand = null;

    /**
     * Tries to add an item to the Main Inventory (Backpack) first.
     * If full, tries Quick Slots.
     * If both full, returns false.
     */
    public boolean pickup(Item item) {
        if (item == null)
            return false;

        // 1. Consumables and usable tools automatically route into empty Quick Slots first
        if (item.isConsumableOrTool()) {
            for (int i = 0; i < quickSlots.length; i++) {
                if (quickSlots[i] == null) {
                    quickSlots[i] = item;
                    return true;
                }
            }
            // If quick slots are full, spill over to main backpack
            if (mainInventory.size() < MAX_BACKPACK_SIZE) {
                mainInventory.add(item);
                return true;
            }
            return false;
        }

        // 2. Equipment and general loot prioritize Main Inventory (Backpack)
        if (mainInventory.size() < MAX_BACKPACK_SIZE) {
            mainInventory.add(item);
            return true;
        }

        // 3. Fallback: if backpack is full, place in empty quick slot
        for (int i = 0; i < quickSlots.length; i++) {
            if (quickSlots[i] == null) {
                quickSlots[i] = item;
                return true;
            }
        }

        return false; // Inventory Full
    }

    public boolean addItem(Item item) {
        return pickup(item);
    }

    /**
     * Tries to add an item to the Main Inventory (Backpack) FIRST, then Quick
     * Slots.
     * Use this for bulk loot like butchering results.
     */
    public boolean pickupToBackpack(Item item) {
        // 1. Try Main Inventory (Backpack)
        if (mainInventory.size() < MAX_BACKPACK_SIZE) {
            mainInventory.add(item);
            return true;
        }

        // 2. Try Quick Slots
        for (int i = 0; i < quickSlots.length; i++) {
            if (quickSlots[i] == null) {
                quickSlots[i] = item;
                return true;
            }
        }

        return false; // Inventory Full
    }

    public void swapHands() {
        Item temp = rightHand;
        rightHand = leftHand;
        leftHand = temp;
    }

    public java.util.List<Item> getAllItems() {
        java.util.List<Item> allItems = new java.util.ArrayList<>();

        if (rightHand != null)
            allItems.add(rightHand);
        if (leftHand != null)
            allItems.add(leftHand);

        for (Item item : quickSlots) {
            if (item != null)
                allItems.add(item);
        }

        allItems.addAll(mainInventory);
        return allItems;
    }

    public boolean hasItemOfType(Item.ItemType type) {
        for (Item item : getAllItems()) {
            if (item != null && item.getType() == type) {
                return true;
            }
        }
        return false;
    }

    public void rotatePack() {
        // Rotates only the Quick Slots
        if (quickSlots.length < 6)
            return;

        Item pos0 = quickSlots[0];
        Item pos1 = quickSlots[1];
        Item pos2 = quickSlots[2];
        Item pos3 = quickSlots[3];
        Item pos4 = quickSlots[4];
        Item pos5 = quickSlots[5];

        quickSlots[0] = pos3;
        quickSlots[1] = pos0;
        quickSlots[2] = pos1;
        quickSlots[3] = pos4;
        quickSlots[4] = pos5;
        quickSlots[5] = pos2;
    }

    public void swapWithPack() {
        // Swaps Right Hand with Quick Slot 2 (Top Right)
        Item temp = rightHand;
        rightHand = quickSlots[2];
        quickSlots[2] = temp;
    }

    // --- Getters & Setters ---

    public Item getRightHand() {
        return rightHand;
    }

    public void setRightHand(Item item) {
        this.rightHand = item;
        // Two-handed weapon automatically unequips off-hand shield/weapon to backpack
        if (item != null && item.isTwoHanded() && leftHand != null) {
            Item offhand = leftHand;
            leftHand = null;
            pickupToBackpack(offhand);
        }
    }

    public Item getLeftHand() {
        return leftHand;
    }

    public void setLeftHand(Item item) {
        // If holding a two-handed weapon, unequip it to backpack when equipping left hand
        if (item != null && rightHand != null && rightHand.isTwoHanded()) {
            Item twoHander = rightHand;
            rightHand = null;
            pickupToBackpack(twoHander);
        }
        this.leftHand = item;
    }

    /**
     * Resolves the active damage die: uses versatile 2H die if left hand is empty,
     * or standard 1H die if wielding a shield or offhand item.
     */
    public String getActiveDamageDice(Item weapon) {
        if (weapon == null) return "1d2";
        if (weapon.isVersatile() && leftHand == null && weapon.getVersatileDamageDice() != null) {
            return weapon.getVersatileDamageDice();
        }
        return weapon.getDamageDice() != null ? weapon.getDamageDice() : "1d4";
    }

    public Item[] getQuickSlots() {
        return quickSlots;
    }

    public Item[] getBackpack() {
        return mainInventory.toArray(new Item[0]);
    }

    public List<Item> getMainInventory() {
        return mainInventory;
    }

    public boolean removeItem(Item item) {
        if (rightHand == item) {
            rightHand = null;
            return true;
        }
        if (leftHand == item) {
            leftHand = null;
            return true;
        }
        for (int i = 0; i < quickSlots.length; i++) {
            if (quickSlots[i] == item) {
                quickSlots[i] = null;
                return true;
            }
        }
        return mainInventory.remove(item);
    }

    public void clear() {
        rightHand = null;
        leftHand = null;
        for (int i = 0; i < quickSlots.length; i++) {
            quickSlots[i] = null;
        }
        mainInventory.clear();
    }
}
