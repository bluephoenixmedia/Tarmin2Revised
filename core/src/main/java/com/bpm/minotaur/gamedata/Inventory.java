package com.bpm.minotaur.gamedata;

import com.bpm.minotaur.gamedata.item.Item;

public class Inventory {
    private final Item[] backpack = new Item[6];
    private Item rightHand = null;
    private Item leftHand = null;
    //private Item activeProjectile = null;

    public boolean pickup(Item item) {
        if (rightHand == null) {
            rightHand = item;
            return true;
        }
        for (int i = 0; i < backpack.length; i++) {
            if (backpack[i] == null) {
                backpack[i] = item;
                return true;
            }
        }
        return false; // Inventory is full
    }

    public void swapHands() {
        Item temp = rightHand;
        rightHand = leftHand;
        leftHand = temp;
    }

    /**
     * Gets a new list containing all items in the inventory (hands and pack).
     * This is used for "identify" logic that needs to scan everything.
     * @return A new List<Item> of all non-null items.
     */
    public java.util.List<Item> getAllItems() {
        java.util.List<Item> allItems = new java.util.ArrayList<>();

        if (rightHand != null) {
            allItems.add(rightHand);
        }
        if (leftHand != null) {
            allItems.add(leftHand);
        }
        for (Item item : backpack) {
            if (item != null) {
                allItems.add(item);
            }
        }
        return allItems;
    }

    public void rotatePack() {
        if (backpack.length < 6) return; // Ensure the backpack is the correct size

        // Store the current items to avoid overwriting during the swap
        Item pos0 = backpack[0];
        Item pos1 = backpack[1];
        Item pos2 = backpack[2];
        Item pos3 = backpack[3];
        Item pos4 = backpack[4];
        Item pos5 = backpack[5];

        // Perform the clockwise rotation
        backpack[0] = pos3; // Item from bottom-left moves to top-left
        backpack[1] = pos0; // Item from top-left moves to top-middle
        backpack[2] = pos1; // Item from top-middle moves to top-right
        backpack[3] = pos4; // Item from bottom-middle moves to bottom-left
        backpack[4] = pos5; // Item from bottom-right moves to bottom-middle
        backpack[5] = pos2; // Item from top-right moves to bottom-right
    }

    public void swapWithPack() {
        // As per the manual, swap with the 3 o'clock pack position.
        // We'll designate backpack[2] as this slot.
        Item temp = rightHand;
        rightHand = backpack[2];
        backpack[2] = temp;
    }

    public Item getRightHand() {
        return rightHand;
    }

    public void setRightHand(Item item) {
        this.rightHand = item;
    }

    public Item getLeftHand() {
        return leftHand;
    }

    public Item[] getBackpack() {
        return backpack;
    }

    public void setLeftHand(Item item) {
        this.leftHand = item;
    }


}
