package com.bpm.minotaur.gamedata.item;

import com.bpm.minotaur.gamedata.ModifierType;

public class ItemModifier {
    public ModifierType type;
    public int value;
    public String displayName; // e.g., "of Brawn", "Sharp"

    public ItemModifier(ModifierType type, int value, String displayName) {
        this.type = type;
        this.value = value;
        this.displayName = displayName;
    }
}
