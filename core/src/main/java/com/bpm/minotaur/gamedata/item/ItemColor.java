package com.bpm.minotaur.gamedata.item;

import com.badlogic.gdx.graphics.Color;

public enum ItemColor {
    // War Weapons & Armor Colors
    TAN("Tan", "Regular", new Color(0.82f, 0.71f, 0.55f, 1f), 1.0f),
    ORANGE("Orange", "Greater", new Color(1f, 0.5f, 0f, 1f), 1.2f),
    BLUE_STEEL("Blue Steel", "Fair", new Color(0.27f, 0.51f, 0.71f, 1f), 1.1f),
    GRAY("Gray", "Medium", Color.GRAY, 1.3f),
    YELLOW("Yellow", "High", Color.YELLOW, 1.5f),
    WHITE("White", "Super", Color.WHITE, 2.0f),
    GREEN("Green", "Regular", Color.GREEN, 1.0f), // <--- Added Green

    // Spiritual Weapons & Rings Colors
    BLUE("Blue", "Regular", Color.BLUE, 1.0f),
    GRAY_SPIRITUAL("Gray", "Fair", Color.GRAY, 1.2f),
    WHITE_SPIRITUAL("White", "Medium", Color.WHITE, 1.4f),
    PINK("Pink", "High", Color.PINK, 1.6f),
    RED("Red", "High", Color.RED, 1.8f),
    PURPLE("Purple", "Super", Color.PURPLE, 2.2f),
    // Container & Key Colors

    CONTAINER_TAN("Tan", "Mild Quality", new Color(0.82f, 0.71f, 0.55f, 1f), 1.0f),
    CONTAINER_ORANGE("Orange", "Good Quality", new Color(1f, 0.5f, 0f, 1f), 1.5f),
    CONTAINER_BLUE("Blue", "Best Quality", Color.BLUE, 2.0f);


    private final String name;
    private final String powerLevel;
    private final Color color;
    private final float multiplier;

    ItemColor(String name, String powerLevel, Color color, float multiplier) {
        this.name = name;
        this.powerLevel = powerLevel;
        this.color = color;
        this.multiplier = multiplier;
    }



    public String getName() {
        return name;
    }

    public String getPowerLevel() {
        return powerLevel;
    }

    public Color getColor() {
        return color;
    }

    public float getMultiplier() {
        return multiplier;
    }
}
