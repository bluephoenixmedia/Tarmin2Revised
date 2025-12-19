package com.bpm.minotaur.gamedata.dice;

import com.badlogic.gdx.graphics.Color;

/**
 * A single face on a Die.
 */
public class DieFace {
    private DieFaceType type;
    private int value; // e.g., 2 Swords, or 5 Fire Damage
    private String label; // For UI or unique descriptions like "Vampiric Bite"
    private Color colorOverride; // Null means use Die color

    public DieFace(DieFaceType type, int value) {
        this(type, value, null);
    }

    public DieFace(DieFaceType type, int value, String label) {
        this.type = type;
        this.value = value;
        this.label = label;
    }

    public DieFaceType getType() {
        return type;
    }

    public int getValue() {
        return value;
    }

    public String getLabel() {
        return label;
    }

    public Color getColorOverride() {
        return colorOverride;
    }

    public void setColorOverride(Color colorOverride) {
        this.colorOverride = colorOverride;
    }

    @Override
    public String toString() {
        return (label != null ? label : type.toString()) + " (" + value + ")";
    }
}
