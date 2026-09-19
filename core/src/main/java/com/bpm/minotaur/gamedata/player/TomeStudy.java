package com.bpm.minotaur.gamedata.player;

import com.bpm.minotaur.gamedata.item.Item;

/**
 * One field study of a Tome in progress: a channelled action that advances one
 * world turn at a time until the Tome's turns are spent, or until damage or a
 * hostile coming into view breaks it. Progress lives on the Tome item, so an
 * interrupted study resumes where it stopped.
 */
public final class TomeStudy {

    public enum Step { CONTINUE, COMPLETE, INTERRUPTED_BY_DAMAGE, INTERRUPTED_BY_HOSTILE }

    private final Item tome;
    private int lastHp;

    TomeStudy(Item tome, int currentHp) {
        this.tome = tome;
        this.lastHp = currentHp;
    }

    public static boolean isTome(Item.ItemType type) {
        return turnsToStudy(type) > 0;
    }

    /** World turns a field study of this Tome takes; 0 for anything that is not a Tome. */
    public static int turnsToStudy(Item.ItemType type) {
        if (type == null) {
            return 0;
        }
        switch (type) {
            case TOME_OF_THE_INITIATE: return 10;
            case TOME_OF_ELEMENTS: return 15;
            case TOME_OF_THE_ARCANE: return 20;
            case TOME_OF_TARMIN: return 25;
            default: return 0;
        }
    }

    /** The Spell Slot count this Tome raises the player to. */
    public static int slotsUnlockedBy(Item.ItemType type) {
        switch (type) {
            case TOME_OF_THE_INITIATE: return 2;
            case TOME_OF_ELEMENTS: return 3;
            case TOME_OF_THE_ARCANE: return 4;
            case TOME_OF_TARMIN: return 5;
            default: return 1;
        }
    }

    /** Resolves one world turn of study given the player's HP after that turn. */
    Step afterTurn(int currentHp, boolean hostileInView) {
        if (currentHp < lastHp) {
            return Step.INTERRUPTED_BY_DAMAGE;
        }
        if (hostileInView) {
            return Step.INTERRUPTED_BY_HOSTILE;
        }
        lastHp = currentHp;
        tome.setStudyProgress(tome.getStudyProgress() + 1);
        return tome.getStudyProgress() >= turnsToStudy(tome.getType()) ? Step.COMPLETE : Step.CONTINUE;
    }

    public Item getTome() {
        return tome;
    }

    public int getTurnsDone() {
        return tome.getStudyProgress();
    }

    public int getTurnsRequired() {
        return turnsToStudy(tome.getType());
    }
}
