package com.bpm.minotaur.gamedata;

import com.badlogic.gdx.graphics.Color;

public enum MonsterColor {
    // Bad Monsters
    BLUE("Blue", Color.BLUE, 1.0f, 1.2f),
    PINK("Pink", Color.PINK, 1.2f, 1.3f),
    PURPLE("Purple", Color.PURPLE, 1.4f, 1.5f),

    // Nasty Monsters
    WHITE("White", Color.WHITE, 1.0f, 1.2f),
    GRAY("Gray", Color.GRAY, 1.2f, 1.3f),
    ORANGE("Orange", new Color(1f, 0.5f, 0f, 1f), 1.4f, 1.5f),

    // Horrible Monsters
    YELLOW("Yellow", Color.YELLOW, 1.0f, 1.4f),
    TAN("Tan", new Color(0.82f, 0.71f, 0.55f, 1f), 1.2f, 1.5f);

    private final String name;
    private final Color color;
    private final float strengthMultiplier;
    private final float xpMultiplier;


    MonsterColor(String name, Color color, float strengthMultiplier, float xpMultiplier) {
        this.name = name;
        this.color = color;
        this.strengthMultiplier = strengthMultiplier;
        this.xpMultiplier = xpMultiplier;
    }

    public String getName() {
        return name;
    }

    public Color getColor() {
        return color;
    }


    public float getXpMultiplier() {
        return xpMultiplier;
    }

    public float getStrengthMultiplier() {
        return strengthMultiplier;
    }
}
