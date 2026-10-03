package com.bpm.minotaur.gamedata;

import com.bpm.minotaur.gamedata.item.Item;
import java.util.ArrayList;
import java.util.List;

public class Inventory {
    // "Quick Slots" - The 6 items visible on the HUD
    private final Item[] quickSlots = new Item[6];

    // "Main Inventory" - The expanded storage (Backpack Screen)
    // Matches the modern inventory UI's 8x6 backpack grid (BackpackPanel.SLOT_COUNT).
    private final List<Item> mainInventory = new ArrayList<>();
    private final int MAX_BACKPACK_SIZE = 48;

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

        com.bpm.minotaur.managers.UnlockManager.getInstance().recordItemEncountered(item);

        // 0. A ration joins the rations already carried rather than taking a slot.
        if (mergeIntoStack(item)) return true;

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
        if (item == null)
            return false;

        com.bpm.minotaur.managers.UnlockManager.getInstance().recordItemEncountered(item);

        // 0. A ration joins the rations already carried rather than taking a slot.
        if (mergeIntoStack(item)) return true;

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

    /** A carried stack {@code item} can join, or null. Hands first, then quick slots, then the pack. */
    private Item findStackFor(Item item) {
        if (item == null || !item.isStackable()) return null;
        if (item.canStackWith(rightHand)) return rightHand;
        if (item.canStackWith(leftHand)) return leftHand;
        for (Item q : quickSlots) {
            if (item.canStackWith(q)) return q;
        }
        for (Item m : mainInventory) {
            if (item.canStackWith(m)) return m;
        }
        return null;
    }

    /** Folds a stackable item into a stack already carried. False when there is none. */
    private boolean mergeIntoStack(Item item) {
        Item stack = findStackFor(item);
        if (stack == null) return false;
        stack.addToStack(item.getStackCount());
        return true;
    }

    /**
     * Uses up one of {@code item}: one ration off a stack, or the whole item
     * when it is the last (or does not stack). Eating, cooking and selling go
     * through here so a stack is never spent all at once.
     *
     * @return whether the item was carried
     */
    public boolean consumeOne(Item item) {
        if (item == null) return false;
        if (item.getStackCount() > 1 && contains(item)) {
            item.setStackCount(item.getStackCount() - 1);
            return true;
        }
        return removeItem(item);
    }

    /**
     * Merges loose stackable items into single stacks. Saves from before
     * rations stacked carry thirty loose rations in thirty slots; loading
     * folds them back into one.
     */
    public void consolidateStacks() {
        // A stackable in a quick slot folds into any other stack carried;
        // counts are conserved whichever one absorbs the other.
        for (int i = 0; i < quickSlots.length; i++) {
            Item q = quickSlots[i];
            if (q == null || !q.isStackable()) continue;
            Item stack = findStackFor(q);
            if (stack != null) {
                stack.addToStack(q.getStackCount());
                quickSlots[i] = null;
            }
        }
        for (int i = 0; i < mainInventory.size(); i++) {
            Item m = mainInventory.get(i);
            if (!m.isStackable()) continue;
            for (int j = mainInventory.size() - 1; j > i; j--) {
                Item other = mainInventory.get(j);
                if (m.canStackWith(other)) {
                    m.addToStack(other.getStackCount());
                    mainInventory.remove(j);
                }
            }
        }
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
        if (item != null) {
            com.bpm.minotaur.managers.UnlockManager.getInstance().recordItemEncountered(item);
        }
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
        if (item != null) {
            com.bpm.minotaur.managers.UnlockManager.getInstance().recordItemEncountered(item);
        }
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

    /** Number of discrete backpack grid slots currently occupied. */
    public int getCarriedCount() {
        return mainInventory.size();
    }

    /** Total discrete backpack grid slots available, matching the UI grid size. */
    public int getMaxBackpackSize() {
        return MAX_BACKPACK_SIZE;
    }

    /** Whether the item is carried anywhere: either hand, a quick slot, or the backpack. */
    public boolean contains(Item item) {
        if (item == null) {
            return false;
        }
        if (rightHand == item || leftHand == item || mainInventory.contains(item)) {
            return true;
        }
        for (Item quick : quickSlots) {
            if (quick == item) {
                return true;
            }
        }
        return false;
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

    public void clearQuickSlots() {
        for (int i = 0; i < quickSlots.length; i++) {
            quickSlots[i] = null;
        }
    }

    public void setQuickSlot(int index, Item item) {
        if (index >= 0 && index < quickSlots.length) {
            quickSlots[index] = item;
        }
    }
}
