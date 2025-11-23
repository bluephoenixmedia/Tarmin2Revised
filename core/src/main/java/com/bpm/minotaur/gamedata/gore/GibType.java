package com.bpm.minotaur.gamedata.gore;

import com.badlogic.gdx.graphics.Color;

public enum GibType {
    MEAT_CHUNK(new String[]{
        " ## ",
        "####",
        "####",
        " ## "
    }, new Color(0.6f, 0.1f, 0.1f, 1f)), // Dark Red

    BONE_SHARD(new String[]{
        "  # ",
        " #  ",
        "#   ",
        "#   "
    }, Color.LIGHT_GRAY),

    RIB_CAGE(new String[]{
        "#  #",
        "#  #",
        "####",
        "#  #"
    }, Color.LIGHT_GRAY),

    INTESTINE(new String[]{
        " ## ",
        "#  #",
        " ## ",
        "#  #"
    }, new Color(0.8f, 0.4f, 0.4f, 1f)), // Pinkish

    EYEBALL(new String[]{
        " ### ",
        "# # #",
        "#####"
    }, Color.WHITE);

    public final String[] spriteData;
    public final Color defaultColor;

    GibType(String[] spriteData, Color defaultColor) {
        this.spriteData = spriteData;
        this.defaultColor = defaultColor;
    }
}
