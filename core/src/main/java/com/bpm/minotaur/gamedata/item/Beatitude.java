package com.bpm.minotaur.gamedata.item;

public enum Beatitude {
    CURSED(-1),
    UNCURSED(0),
    BLESSED(1);

    private final int value;

    Beatitude(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}
