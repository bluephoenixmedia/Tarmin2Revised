package com.bpm.minotaur.gamedata;

import java.util.ArrayList;
import java.util.List;

public class Inventory {
    private final Item[] backpack = new Item[6];
    private Item rightHand = null;
    private Item leftHand = null;

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

    public Item getRightHand() {
        return rightHand;
    }

    public Item getLeftHand() {
        return leftHand;
    }

    public Item[] getBackpack() {
        return backpack;
    }
}
