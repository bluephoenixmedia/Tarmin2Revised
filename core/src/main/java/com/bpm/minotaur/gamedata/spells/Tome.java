package com.bpm.minotaur.gamedata.spells;

import com.bpm.minotaur.gamedata.item.Item;

/**
 * The four milestone Tomes: the Spell Slot each unlocks, how long a field study
 * takes, and the highest spell level its Tome Choice offers. Pool membership is
 * the {@code tomePools} tag on each spell in spells.json.
 */
public enum Tome {
    INITIATE(Item.ItemType.TOME_OF_THE_INITIATE, "Tome of the Initiate", 2, 10, 1),
    ELEMENTS(Item.ItemType.TOME_OF_ELEMENTS, "Tome of Elements", 3, 15, 3),
    ARCANE(Item.ItemType.TOME_OF_THE_ARCANE, "Tome of the Arcane", 4, 20, 5),
    TARMIN(Item.ItemType.TOME_OF_TARMIN, "Tome of Tarmin", 5, 25, 7);

    private final Item.ItemType itemType;
    private final String displayName;
    private final int slotNumber;
    private final int studyTurns;
    private final int maxSpellLevel;

    Tome(Item.ItemType itemType, String displayName, int slotNumber, int studyTurns, int maxSpellLevel) {
        this.itemType = itemType;
        this.displayName = displayName;
        this.slotNumber = slotNumber;
        this.studyTurns = studyTurns;
        this.maxSpellLevel = maxSpellLevel;
    }

    /** The Tome an item is, or null for anything else. */
    public static Tome of(Item.ItemType type) {
        for (Tome tome : values()) {
            if (tome.itemType == type) {
                return tome;
            }
        }
        return null;
    }

    /** The Tome that unlocks the given 1-based Spell Slot, or null for slot 1. */
    public static Tome unlockingSlot(int slotNumber) {
        for (Tome tome : values()) {
            if (tome.slotNumber == slotNumber) {
                return tome;
            }
        }
        return null;
    }

    public String getDisplayName() {
        return displayName;
    }

    /** 1-based Spell Slot this Tome unlocks. */
    public int getSlotNumber() {
        return slotNumber;
    }

    public int getStudyTurns() {
        return studyTurns;
    }

    public int getMaxSpellLevel() {
        return maxSpellLevel;
    }
}
