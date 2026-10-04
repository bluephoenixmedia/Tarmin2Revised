package com.bpm.minotaur.gamedata.events;

/** A condition a choice needs before its button can be pressed. */
public class EventGate {
    public enum Type {
        ATTRIBUTE_MIN, HAS_ITEM, GOLD_MIN
    }

    public Type type;
    /** For ATTRIBUTE_MIN: one of STR, DEX, CON, INT, WIS, AGI, CHA. */
    public String attribute;
    /** The minimum attribute or gold. */
    public int value;
    /** For HAS_ITEM: an {@link ItemKind} name; or {@link #itemId} for one exact item type. */
    public String itemKind;
    public String itemId;
    public int count = 1;
    /** Shown on the button while the gate is unmet, e.g. "Needs a ranged weapon". */
    public String label;
}
