package com.bpm.minotaur.gamedata.dice;

/**
 * The result of a physical die roll.
 */
public class DieResult {
    private final Die sourceDie;
    private final DieFace rolledFace;

    public DieResult(Die sourceDie, DieFace rolledFace) {
        this.sourceDie = sourceDie;
        this.rolledFace = rolledFace;
    }

    public Die getSourceDie() {
        return sourceDie;
    }

    public DieFace getRolledFace() {
        return rolledFace;
    }

    @Override
    public String toString() {
        return sourceDie.getName() + " -> " + rolledFace.toString();
    }
}
