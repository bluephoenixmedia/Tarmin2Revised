package com.bpm.minotaur.gamedata;

import com.badlogic.gdx.graphics.Color;

public enum MonsterColor {
    // Bad Monsters
    BLUE("Blue", Color.BLUE, 1.0f),
    PINK("Pink", Color.PINK, 1.2f),
    PURPLE("Purple", Color.PURPLE, 1.4f),

    // Nasty Monsters
    WHITE("White", Color.WHITE, 1.0f),
    GRAY("Gray", Color.GRAY, 1.2f),
    ORANGE("Orange", new Color(1f, 0.5f, 0f, 1f), 1.4f),

    // Horrible Monsters
    YELLOW("Yellow", Color.YELLOW, 1.0f),
    TAN("Tan", new Color(0.82f, 0.71f, 0.55f, 1f), 1.2f);

    private final String name;
    private final Color color;
    private final float strengthMultiplier;

    MonsterColor(String name, Color color, float strengthMultiplier) {
        this.name = name;
        this.color = color;
        this.strengthMultiplier = strengthMultiplier;
    }

    public String getName() {
        return name;
    }

    public Color getColor() {
        return color;
    }

    public float getStrengthMultiplier() {
        return strengthMultiplier;
    }
}
