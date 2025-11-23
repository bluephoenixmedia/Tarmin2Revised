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
        // 1. Try Main Inventory (Backpack)
        if (mainInventory.size() < MAX_BACKPACK_SIZE) {
            mainInventory.add(item);
            return true;
        }

        // 2. Try Quick Slots (HUD)
        for (int i = 0; i < quickSlots.length; i++) {
            if (quickSlots[i] == null) {
                quickSlots[i] = item;
                return true;
            }
        }

        // 3. Hand logic is tricky (auto-equip?)
        // Usually games don't auto-equip to hands unless hands are empty and it's a weapon.
        // For now, let's stick to storage.

        return false; // Inventory Full
    }

    public void swapHands() {
        Item temp = rightHand;
        rightHand = leftHand;
        leftHand = temp;
    }

    public java.util.List<Item> getAllItems() {
        java.util.List<Item> allItems = new java.util.ArrayList<>();

        if (rightHand != null) allItems.add(rightHand);
        if (leftHand != null) allItems.add(leftHand);

        for (Item item : quickSlots) {
            if (item != null) allItems.add(item);
        }

        allItems.addAll(mainInventory);
        return allItems;
    }

    public void rotatePack() {
        // Rotates only the Quick Slots
        if (quickSlots.length < 6) return;

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

    public Item getRightHand() { return rightHand; }
    public void setRightHand(Item item) { this.rightHand = item; }

    public Item getLeftHand() { return leftHand; }
    public void setLeftHand(Item item) { this.leftHand = item; }

    public Item[] getQuickSlots() {
        return quickSlots;
    }

    public Item[] getBackpack() {
        return quickSlots;
    }

    public List<Item> getMainInventory() {
        return mainInventory;
    }
}
