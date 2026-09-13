package com.bpm.minotaur.gamedata.spells;

public enum SpellType {
    MAGIC_ARROW("Magic Arrow", 4, true, "Shoots a magical bolt of energy."),
    HEAL("Heal", 8, false, "Restores health."),
    TELEPORT("Teleport", 12, false, "Teleports you to a random location."),
    IRON_SKIN("Iron Skin", 10, false, "Temporarily increases defense."),
    MOTE_OF_LIGHT("Mote of Light", 0, false, "Conjures an orbiting wisp of light that illuminates darkness.");

    private final String displayName;
    private final int mpCost;
    private final boolean isTargeted;
    private final String description;

    SpellType(String displayName, int mpCost, boolean isTargeted, String description) {
        this.displayName = displayName;
        this.mpCost = mpCost;
        this.isTargeted = isTargeted;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getMpCost() {
        return mpCost;
    }

    public boolean isTargeted() {
        return isTargeted;
    }

    public String getDescription() {
        return description;
    }
}
